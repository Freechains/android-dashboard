package org.freechains.host

import org.freechains.common.*
import com.goterl.lazysodium.LazySodium
import com.goterl.lazysodium.interfaces.Sign
import com.goterl.lazysodium.utils.Key
import kotlin.math.max

fun Chain.fromOwner (blk: Block) : Boolean {
    return this.isAt().let { it!=null && blk.isFrom(it) }
}

// STATE

fun Chain.hashState (hash: Hash, now: Long) : State {
    return when {
        ! this.fsExistsBlock(hash) -> State.MISSING
        else -> this.blockState(this.fsLoadBlock(hash), now)
    }
}

fun Chain.blockState (blk: Block, now: Long) : State {
    val prev = blk.immut.prev
    val ath = when {
        (blk.sign == null) -> 0     // anon post, no author reps
        (prev == null)     -> 0     // no prev post, no author reps
        else               -> this.repsAuthor(blk.sign.pub, now, listOf(prev))
    }
    val (pos,neg) = this.repsPost(blk.hash)

    val unit = blk.hash.toHeight().toReps()

    // number of blocks that point back to it (-1 myself)
    //val fronts = max(0, this.bfsAll(blk.hash).count{ this.blockState(it)==State.ACCEPTED } - 1)

    //println("rep ${blk.hash} = reps=$pos-$neg + ath=$ath // ${blk.immut.time}")
    return when {
        // unchangeable
        (blk.hash.toHeight() <= 1)  -> State.ACCEPTED       // first two blocks
        this.fromOwner(blk)         -> State.ACCEPTED       // owner signature
        this.isDollar()              -> State.ACCEPTED       // chain with trusted hosts/authors only
        (blk.immut.like != null)    -> State.ACCEPTED       // a like

        // changeable
        (pos==0 && ath<unit)        -> State.BLOCKED        // no likes && noob author
        (neg>= LK5_dislikes && LK2_factor *neg>=pos) -> State.HIDDEN   // too much dislikes
        else                        -> State.ACCEPTED
    }
}

// NEW

fun Chain.blockNew (imm_: Immut, pay0: String, sign: HKey?, pubpvt: Boolean) : Block {
    assert_(imm_.time == 0.toLong()) { "time must not be set" }
    assert_(imm_.pay.hash == "") { "pay must not be set" }
    assert_(imm_.prev == null) { "prev must not be set" }

    assert_(imm_.backs.isEmpty())
    val backs = this.getHeads(State.LINKED)
        .let { backs ->
            backs.plus (
                // must point to liked blocked block
                when {
                    (imm_.like == null) -> emptyArray()
                    backs.any {
                        this.bfsFrontsIsFromTo(
                            imm_.like.hash,
                            it
                        )  // already does indirectly (it points to some of the heads)
                    } -> emptyArray()
                    else -> arrayOf(imm_.like.hash)  // point directly (it was blocked)
                }
            )
        }
        .let { backs ->
            this.bfsCleanHeads(backs.toList())
        }

    val imm = imm_.copy (
        time = max (
                getNow(),
            1 + backs.map { this.fsLoadBlock(it).immut.time }.max()!!
        ),
        pay = imm_.pay.copy (
            crypt = this.isDollar() || pubpvt,
            hash  = pay0.calcHash()
        ),
        prev  = sign?.let { this.bfsBacksFindAuthor(it.pvtToPub()) } ?.hash,
        backs = backs.sorted().toTypedArray()   // sort for deterministic tests
    )
    val pay1 = when {
        this.isDollar() -> pay0.encryptShared(this.key!!)
        pubpvt         -> pay0.encryptPublic(this.isAt()!!)
        else           -> pay0
    }
    val hash = imm.toHash()

    // signs message if requested (pvt provided or in pvt chain)
    val signature=
        if (sign == null)
            null
        else {
            val sig = ByteArray(Sign.BYTES)
            val msg = lazySodium.bytes(hash)
            val pvt = Key.fromHexString(sign).asBytes
            lazySodium.cryptoSignDetached(sig, msg, msg.size.toLong(), pvt)
            val sig_hash = LazySodium.toHex(sig)
            Signature(sig_hash, sign.pvtToPub())
        }

    val new = Block(imm, hash, signature)
    this.blockChain(new,pay1)
    return new
}

fun Chain.blockChain (blk: Block, pay: String) {
    this.blockAssert(blk)

    // add this block as front of its backs
    for (bk in blk.immut.backs) {
        val fronts = this.fronts.get(bk)!!
        assert_(!fronts.contains(blk.hash)) { "bug found (1): " + bk + " already points to " + blk.hash }
        fronts.add(blk.hash)
        fronts.sort() // sort for deterministic tests
    }
    this.fronts.add(Pair(blk.hash,mutableListOf()))
    this.fronts.sortBy{ it.first } // sort for deterministic tests

    this.fsSaveBlock(blk)
    this.fsSavePay(blk.hash, pay)

    this.heads = this.bfsCleanHeads(this.heads + blk.hash)

    this.fsSave()
}

fun Chain.blockRemove (hash: Hash) {
    val blk = this.fsLoadBlock(hash)
    assert_(this.blockState(blk, getNow()) == State.BLOCKED) { "can only remove blocked block" }
    this.heads = this.bfsCleanHeads(this.heads.minus(hash) + blk.immut.backs.toList())
    this.fsSave()
}

fun Chain.backsAssert (blk: Block) {
    for (bk in blk.immut.backs) {
        //println("$it <- ${blk.hash}")
        assert_(this.fsExistsBlock(bk)) { "back must exist" }
        this.fsLoadBlock(bk).let { bbk ->
            assert_(bbk.immut.time <= blk.immut.time) { "back must be older" }
            when {
                (this.blockState(bbk,blk.immut.time) != State.BLOCKED) -> true
                (blk.immut.prev == null)                                -> false
                else -> this.bfsBacksFindAuthor(blk.sign!!.pub).let {
                    (it!=null && this.blockState(it,blk.immut.time)!= State.BLOCKED)
                }
            }.let {
                assert_(it) { "backs must be accepted" }
            }
        }
    }
}

fun Chain.blockAssert (blk: Block) {
    val imm = blk.immut
    val now = getNow()
    //println(">>> ${blk.hash} vs ${imm.toHash()}")

    this.backsAssert(blk) // backs exist and are older
    if (blk.hash.toHeight() > 0) {
        assert_(blk.hash == imm.toHash()) { "hash must verify" }
        assert_(imm.time >= now - T120D_past) { "too old" }
        if (this.isAt()!=null && this.isAtBang()) {
            assert_(this.fromOwner(blk)) { "must be from owner" }
        }
    }
    assert_(imm.time <= now + T30M_future) { "from the future" }

    val gen = this.getGenesis() // unique genesis front (unique 1_xxx)
    if (imm.backs.contains(gen)) {
        this
            .bfsFrontsAll()
            .filter { it.hash.toHeight() == 1 }
            .let {
                assert_(it.isEmpty()) { "genesis is already referred" }
            }
    }

    if (blk.sign != null) {                 // sig.hash/blk.hash/sig.pubkey all match
        val sig = LazySodium.toBin(blk.sign.hash)
        val msg = lazySodium.bytes(blk.hash)
        val key = Key.fromHexString(blk.sign.pub).asBytes
        assert_(lazySodium.cryptoSignVerifyDetached(sig, msg, msg.size, key)) { "invalid signature" }

        // check if new post leads to latest post from author currently in the chain
        this.bfsBacksFirst(this.getHeads(State.ALL)) { it.isFrom(blk.sign.pub) }.let {
            //if (it != null) println("${imm.prev} --> ${blk.hash} = ${this.isFromTo(imm.prev!!,blk.hash)}")
            assert_(
                    if (it == null)
                        (imm.prev == null)
                    else
                        (imm.prev == it.hash)
            ) { "must point to author's previous post" }
        }
    }

    if (imm.like != null) {
        assert_(blk.sign != null) { "like must be signed" }
        // may receive out of order // may point to rejected post
        //assert_(this.fsExistsBlock(imm.like.hash)) { "like must have valid target" }
        if (this.fsExistsBlock(imm.like.hash)) {
            this.fsLoadBlock(imm.like.hash).let {
                assert_(!it.isFrom(blk.sign!!.pub)) { "like must not target itself" }
            }
        }
        assert_(
                this.fromOwner(blk) ||   // owner has infinite reputation
                        this.isDollar() ||   // dont check reps (private chain)
                        this.repsAuthor(blk.sign!!.pub, imm.time, imm.backs.toList()) >= blk.hash.toHeight().toReps()
        ) {
            "like author must have reputation"
        }
    }
}
