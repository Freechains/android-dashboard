package org.freechains.host

import com.goterl.lazysodium.interfaces.GenericHash
import com.goterl.lazysodium.utils.Key
import org.freechains.common.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.io.File
import java.lang.Integer.max
import java.lang.Integer.min
import kotlin.math.ceil

// internal methods are private but are used in tests

// not Map b/c of sorted keys (deterministic tests)
typealias Fronts = MutableList<Pair<String, MutableList<Hash>>>

@Serializable
data class Chain (
    var root   : String,
    val name   : String,
    val key    : HKey?
) {
    val hash   : String = this.name.calcHash()
    var heads  : List<Hash> = emptyList()
    val fronts : Fronts = mutableListOf()
}

fun Fronts.get (hash: String): MutableList<Hash>? {
    return this.firstOrNull { it.first==hash } ?.second
}

// TODO: change to contract/constructor assertion
fun Chain.validate () : Chain {
    val rest = when (this.name.first()) {
        '#' -> this.name.drop(1)
        '$' -> this.name.drop(1)
        '@' -> this.name.drop( if (this.name.drop(1).first() == '!') 2 else 1)
        else -> null
    }
    assert_(rest != null && rest.all { it.isLetterOrDigit() || it == '.' }) {
        "invalid chain name: $this"
    }
    if (this.isDollar()) {
        assert_(this.key != null) { "expected shared key" }
    } else {
        assert_(this.key == null) { "unexpected shared key" }
    }
    return this
}

fun Chain.isAt () : HKey? {
    return when {
        this.name.startsWith("@!") -> this.name.drop(2)
        this.name.startsWith('@')   -> this.name.drop(1)
        else -> null
    }
}

fun Chain.isAtBang () : Boolean {
    return this.name.startsWith("@!")
}

fun Chain.isDollar () : Boolean {
    return this.name.first() == '$'
}

fun Chain.path () : String {
    return this.root + "/chains/" + this.name + "/"
}

// JSON

fun Chain.toJson () : String {
    @OptIn(UnstableDefault::class)
    val json = Json(JsonConfiguration(prettyPrint=true))
    return json.stringify(Chain.serializer(), this)
}

fun String.fromJsonToChain () : Chain {
    @OptIn(UnstableDefault::class)
    val json = Json(JsonConfiguration(prettyPrint=true))
    return json.parse(Chain.serializer(), this)
}

// GENESIS

fun Chain.getGenesis () : Hash {
    return "0_" + this.hash
}

// HASH

val zeros = ByteArray(GenericHash.BYTES)
fun String.calcHash () : String {
    return lazySodium.cryptoGenericHash(this, Key.fromBytes(zeros))
}

fun Immut.toHash () : Hash {
    fun Array<Hash>.backsToHeight () : Int {
        return when {
            this.isEmpty() -> 0
            else -> 1 + this.map { it.toHeight() }.max()!!
        }
    }
    return this.backs.backsToHeight().toString() + "_" + this.toJson().calcHash()
}

// HEADS

fun Chain.getHeads (want: State) : List<Hash> {
    val now = getNow()

    fun aux (hash: Hash) : List<Hash> {
        val blk = this.fsLoadBlock(hash)
        val state = this.blockState(blk,now)
        return when (want) {
            State.ALL -> listOf(hash)
            State.LINKED -> if (state > State.LINKED)  listOf(hash) else blk.immut.backs.map(::aux).flatten()
            State.BLOCKED -> if (state == State.BLOCKED) listOf(hash) else emptyList()
            else -> error("impossible case")
        }
    }

    return this.heads
        .map (::aux)
        .flatten ()
        .let { this.bfsCleanHeads(it) }
}

// REPUTATION

fun Int.toReps () : Int {
    return ceil(this.toFloat() / 10).toInt()
}

fun Chain.repsPost (hash: String) : Pair<Int,Int> {
    val likes = this
        .bfsFrontsAll(hash)
        .filter { it.immut.like != null }           // only likes
        .filter { it.immut.like!!.hash == hash }    // only likes to this post
        .map    { it.immut.like!!.n * it.hash.toHeight().toReps() }

    val pos = likes.filter { it > 0 }.map { it }.sum()
    val neg = likes.filter { it < 0 }.map { it }.sum()

    //println("$hash // chk=$chkRejected // pos=$pos // neg=$neg")
    return Pair(pos,-neg)
}

fun Chain.repsAuthor (pub: String, now: Long, heads: List<Hash>) : Int {
    val gen = this.fronts.get(this.getGenesis())!!.let {
        when {
            it.isEmpty() -> 0
            this.fsLoadBlock(it.first()).isFrom((pub)) -> LK30_max
            else         -> 0
        }
    }

    val mines = this.bfsBacksAuthor(heads,pub)

    val posts = mines                                    // mines
        .filter { it.immut.like == null }                     // not likes
        .let { list ->
            val pos = list
                .filter { now >= it.immut.time + T1D_reps }   // posts older than 1 day
                .map    { it.hash.toHeight().toReps() }       // get reps of each post height
                .sum    ()                                    // sum everything
            val neg = list
                .filter { now <  it.immut.time + T1D_reps }   // posts newer than 1 day
                .map    { it.hash.toHeight().toReps() }       // get reps of each post height
                .sum    ()                                    // sum everything
            //println("gen=$gen // pos=$pos // neg=$neg // now=$now")
            max(gen,min(LK30_max,pos)) - neg
        }

    val recv = this.bfsBacksAll(heads)                     // all pointing from heads to genesis
        .filter { it.immut.like != null }                       // which are likes
        .filter {                                               // and are to me
            this.fsLoadBlock(it.immut.like!!.hash).let {
                (it.sign!=null && it.sign.pub==pub)
            }
        }
        .map    { it.immut.like!!.n * it.hash.toHeight().toReps() } // get likes N
        .sum()                                                      // likes I received

    val gave = mines
        .filter { it.immut.like != null }                       // likes I gave
        //.let { println(it) ; it }
        .map { it.hash.toHeight().toReps() }                    // doesn't matter the signal
        .sum()

    //println("posts=$posts + recv=$recv - gave=$gave")
    return posts + recv - gave
}

// FILE SYSTEM

internal fun Chain.fsSave () {
    val dir = File(this.path() + "/blocks/")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    File(this.path() + "/" + "chain").writeText(this.toJson())
}

fun Chain.fsLoadBlock (hash: Hash) : Block {
    return File(this.path() + "/blocks/" + hash + ".blk").readText().jsonToBlock()
}

fun Chain.fsLoadPay1 (hash: Hash, pubpvt: HKey?) : String {
    val blk = this.fsLoadBlock(hash)
    val pay = this.fsLoadPay0(hash)
    return when {
        !blk.immut.pay.crypt -> pay
        this.isDollar()       -> pay.decrypt(this.key!!)
        (pubpvt == null)     -> pay
        else                 -> pay.decrypt(pubpvt)
    }
}

fun Chain.fsLoadPay0 (hash: Hash) : String {
    return File(this.path() + "/blocks/" + hash + ".pay").readBytes().toString(Charsets.UTF_8)
}

fun Chain.fsExistsBlock (hash: Hash) : Boolean {
    return (this.hash == hash) ||
            File(this.path() + "/blocks/" + hash + ".blk").exists()
}

fun Chain.fsSaveBlock (blk: Block) {
    File(this.path() + "/blocks/" + blk.hash + ".blk").writeText(blk.toJson()+"\n")
}

fun Chain.fsSavePay (hash: Hash, pay: String) {
    File(this.path() + "/blocks/" + hash + ".pay").writeText(pay)
}