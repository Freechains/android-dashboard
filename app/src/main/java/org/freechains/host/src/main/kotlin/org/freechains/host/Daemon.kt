package org.freechains.host

import org.freechains.common.*
import com.goterl.lazysodium.exceptions.SodiumException
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileNotFoundException
import java.net.*
import java.util.*
import kotlin.collections.HashSet
import kotlin.concurrent.thread

class Daemon (loc_: Host) {
    private val listenLists = mutableMapOf<String,MutableSet<DataOutputStream>>()
    private val server = ServerSocket(loc_.port)
    private val loc = loc_

    // must use "plain-by-value" b/c of different refs in different connections of the same node
    private fun getLock (chain: String) : String {
        return (loc.root+chain).intern()
    }

    private fun chainsLoadSync (name: String) : Chain {
        return synchronized(this.getLock(name)) {
            loc.chainsLoad(name)
        }
    }

    fun daemon () {
        //System.err.println("local start: $host")
        while (true) {
            try {
                val remote = server.accept()
                remote.soTimeout = 5000
                System.err.println("remote connect: $loc <- ${remote.inetAddress.hostAddress}")
                thread {
                    try {
                        handle(remote)
                    } catch (e: Throwable) {
                        remote.close()
                        System.err.println(
                            e.message ?: e.toString()
                        )
                        System.err.println(e.stackTrace.contentToString())
                    }
                }
            } catch (e: SocketException) {
                assert_(e.message == "Socket closed")
                break
            }
        }
    }

    private fun signal (chain: String, n: Int) {
        val has1 = synchronized (listenLists) { listenLists.containsKey(chain) }
        if (has1) {
            val wrs = synchronized (listenLists) { listenLists[chain]!!.toList() }
            for (wr in wrs) {
                try {
                    wr.writeLineX(n.toString())
                } catch (e: Throwable) {
                    synchronized (listenLists) { listenLists[chain]!!.remove(wr) }
                }
            }
        }
        val has2 = synchronized (listenLists) { listenLists.containsKey("*") }
        if (has2) {
            val wrs = synchronized (listenLists) { listenLists["*"]!!.toList() }
            for (wr in wrs) {
                try {
                    wr.writeLineX(n.toString() + " " + chain)
                } catch (e: Throwable) {
                    synchronized (listenLists) { listenLists["*"]!!.remove(wr) }
                }
            }
        }
    }

    private fun handle (client: Socket) {
        val reader = DataInputStream(client.getInputStream()!!)
        val writer = DataOutputStream(client.getOutputStream()!!)
        val ln = reader.readLineX()

        try {
            val (v1,v2,_,cmds_) =
                Regex("FC v(\\d+)\\.(\\d+)\\.(\\d+) (.*)").find(ln)!!.destructured
            //println("$MAJOR/${v1.toInt()}  --  $MINOR/${v2.toInt()}")
            assert_(MAJOR == v1.toInt() && MINOR >= v2.toInt()) { "incompatible versions" }
            val cmds = cmds_.split(' ')

            //println("addr = ${remote.inetAddress!!}")
            if (!client.inetAddress!!.toString().equals("/127.0.0.1")) {
                //println("no = ${remote.inetAddress!!}")
                assert_(
                        cmds[0].equals("_peer_") && (
                                cmds[1].equals("_send_") || cmds[1].equals("_recv_") ||
                                        cmds[1].equals("_ping_") || cmds[1].equals("_chains_")
                                )
                ) {
                    "invalid remote address"
                }
                //println("ok = ${remote.inetAddress!!}")
            }

            //println("[handle] $cmd1 // $cmd2")
            when (cmds[0]) {
                "host" -> when (cmds[1]) {
                    "stop" -> {
                        writer.writeLineX("true")
                        server.close()
                        System.err.println("host stop: $loc")
                    }
                    "path" -> {
                        writer.writeLineX(loc.root)
                        System.err.println("host path: ${loc.root}")
                    }
                    "now" -> {
                        val now = cmds[2].toLong()
                        setNow(now)
                        writer.writeLineX("true")
                        System.err.println("host now: $now")
                    }
                }
                "peer" -> {
                    val remote = cmds[1]
                    fun peer(): Pair<DataInputStream, DataOutputStream> {
                        val s = remote.to_Addr_Port().let {
                            Socket_5s(it.first, it.second)
                        }
                        val r = DataInputStream(s.getInputStream()!!)
                        val w = DataOutputStream(s.getOutputStream()!!)
                        return Pair(r, w)
                    }
                    when (cmds[2]) {
                        "ping" -> {
                            val (r, w) = peer()
                            val now = getNow()
                            w.writeLineX("$PRE _peer_ _ping_")
                            val ret = r.readLineX().let {
                                if (it == "true") {
                                    (getNow() - now).toString()
                                } else {
                                    ""
                                }
                            }
                            writer.writeLineX(ret)
                            System.err.println("peer ping: $ret")
                        }
                        "chains" -> {
                            val (r, w) = peer()
                            w.writeLineX("$PRE _peer_ _chains_")
                            val ret = r.readLineX()
                            writer.writeLineX(ret)
                            System.err.println("peer chains")
                        }
                        "send" -> {
                            val chain = cmds[3]
                            val (r, w) = peer()
                            w.writeLineX("$PRE _peer_ _recv_ $chain")
                            val (nmin, nmax) = peerSend(r, w, chain)
                            System.err.println("peer send: $chain: ($nmin/$nmax)")
                            writer.writeLineX("$nmin / $nmax")
                        }
                        "recv" -> {
                            val chain = cmds[3]
                            val (r, w) = peer()
                            w.writeLineX("$PRE _peer_ _send_ $chain")
                            val (nmin, nmax) = peerRecv(r, w, chain)
                            System.err.println("peer recv: $chain: ($nmin/$nmax)")
                            writer.writeLineX("$nmin / $nmax")
                            if (nmin > 0) {
                                thread {
                                    signal(chain, nmin)
                                }
                            }
                        }
                    }
                }
                "_peer_" -> {
                    when (cmds[1]) {
                        "_ping_" -> {
                            writer.writeLineX("true")
                            System.err.println("_peer_ _ping_")
                        }
                        "_chains_" -> {
                            val ret = loc.chainsList().joinToString(" ")
                            writer.writeLineX(ret)
                            System.err.println("_peer_ _chains_: $ret")
                        }
                        "_send_" -> {
                            val chain = cmds[2]
                            val (nmin, nmax) = peerSend(reader, writer, chain)
                            System.err.println("_peer_ _send_: $chain: ($nmin/$nmax)")
                        }
                        "_recv_" -> {
                            val name = cmds[2]
                            val (nmin, nmax) = peerRecv(reader, writer, name)
                            System.err.println("_peer_ _recv_: $name: ($nmin/$nmax)")
                            if (nmin > 0) {
                                thread {
                                    signal(name, nmin)
                                }
                            }
                        }
                    }
                }
                "crypto" -> when (cmds[1]) {
                    "shared" -> {
                        val pass = reader.readLineX()
                        writer.writeLineX(pass.toShared())
                    }
                    "pubpvt" -> {
                        val pass = reader.readLineX()
                        val keys = pass.toPubPvt()
                        //println("PUBPVT: ${keys.publicKey.asHexString} // ${keys.secretKey.asHexString}")
                        writer.writeLineX(
                            keys.publicKey.asHexString + ' ' +
                                    keys.secretKey.asHexString
                        )
                    }
                }
                "chains" -> when (cmds[1]) {
                    "join" -> {
                        val name = cmds[2]
                        val chain = synchronized (getLock(name)) {
                            loc.chainsJoin(name, if (cmds.size == 3) null else cmds[3])
                        }
                        writer.writeLineX(chain.hash)
                        System.err.println("chains join: $name (${chain.hash})")
                    }
                    "leave" -> {
                        val name = cmds[2]
                        val ret = loc.chainsLeave(name)
                        writer.writeLineX(ret.toString())
                        System.err.println("chains leave: $name -> $ret")
                    }
                    "list" -> {
                        val ret = loc.chainsList().joinToString(" ")
                        writer.writeLineX(ret)
                        System.err.println("chains list: $ret")
                    }
                    "listen" -> {
                        client.soTimeout = 0
                        synchronized(listenLists) {
                            if (!listenLists.containsKey("*")) {
                                listenLists["*"] = mutableSetOf()
                            }
                            listenLists["*"]!!.add(writer)
                        }
                    }
                }
                "chain" -> {
                    val name = cmds[1]
                    when (cmds[2]) {
                        "listen" -> {
                            client.soTimeout = 0
                            synchronized(listenLists) {
                                if (!listenLists.containsKey(name)) {
                                    listenLists[name] = mutableSetOf()
                                }
                                listenLists[name]!!.add(writer)
                            }
                        }
                        else -> {
                            val chain = this.chainsLoadSync(name)
                            when (cmds[2]) {
                                "genesis" -> {
                                    val hash = chain.getGenesis()
                                    writer.writeLineX(hash)
                                    System.err.println("chain genesis: $hash")
                                }
                                "heads" -> {
                                    val heads = chain.getHeads(cmds[3].toState()).joinToString(" ")
                                    writer.writeLineX(heads)
                                    System.err.println("chain heads: $heads")
                                }
                                "traverse" -> {
                                    val heads = chain.getHeads(cmds[3].toState())
                                    val downto = cmds.drop(4)
                                    //println("H=$heads // D=$downto")
                                    val all = chain
                                            .bfsBacks(heads, false) {
                                                //println("TRY ${it.hash} -> ${downto.contains(it.hash)}")
                                                !downto.contains(it.hash)
                                            }
                                            .map { it.hash }
                                            .reversed()
                                    //println("H=$heads // D=$downto")
                                    val ret = all.joinToString(" ")
                                    writer.writeLineX(ret)
                                    System.err.println("chain traverse: $ret")
                                }
                                "get" -> {
                                    val hash = cmds[4]
                                    val decrypt= if (cmds[5] == "null") null else cmds[5]
                                    try {
                                        val ret = when (cmds[3]) {
                                            "block" -> {
                                                val blk = chain.fsLoadBlock(hash)
                                                val blk_ = Block_ (
                                                    blk.hash, blk.local, blk.immut.time,
                                                    blk.immut.pay, blk.immut.like, blk.sign,
                                                    blk.immut.prev, blk.immut.backs,
                                                    chain.fronts.get(blk.hash)!!.toTypedArray()
                                                )
                                                blk_.toJson()
                                            }
                                            "payload" -> chain.fsLoadPay1(hash, decrypt)
                                            else -> error("impossible case")
                                        }
                                        writer.writeLineX(ret.length.toString())
                                        writer.writeBytes(ret)
                                    } catch (e: FileNotFoundException) {
                                        writer.writeLineX("! block not found")
                                    }
                                    //writer.writeLineX("\n")
                                    System.err.println("chain get: $hash")
                                }
                                "reps" -> {
                                    val ref = cmds[3]
                                    val likes =
                                            if (ref.hashIsBlock()) {
                                                val (pos, neg) = chain.repsPost(ref)
                                                pos - neg
                                            } else {
                                                chain.repsAuthor(ref,
                                                        getNow(), chain.getHeads(
                                                        State.ALL
                                                ))
                                            }
                                    writer.writeLineX(likes.toString())
                                    System.err.println("chain reps: $likes")
                                }

                                // all others need "synchronized"
                                // they affect the chain in the disk, which is shared across connections

                                "remove" -> {
                                    val hash = cmds[3]
                                    synchronized(getLock(chain.name)) {
                                        chain.blockRemove(hash)
                                    }
                                    writer.writeLineX("true")
                                    System.err.println("chain remove: $hash")
                                }
                                "post" -> {
                                    val sign = cmds[3]
                                    val len = cmds[5].toInt()
                                    val pay = reader.readNBytesX(len).toString(Charsets.UTF_8)
                                    assert_(pay.length <= S128_pay) { "post is too large" }

                                    var ret: String
                                    try {
                                        synchronized(getLock(chain.name)) {
                                            val blk = chain.blockNew(
                                                    Immut(
                                                            0,
                                                            Payload(false, ""),
                                                            null,
                                                            null,
                                                            emptyArray()
                                                    ),
                                                    pay,
                                                    if (sign == "anon") null else sign,
                                                    cmds[4].toBoolean()
                                            )
                                            ret = blk.hash
                                        }
                                        thread {
                                            signal(name, 1)
                                        }
                                    } catch (e: Throwable) {
                                        System.err.println(e.stackTrace.contentToString())
                                        ret = "! " + e.message!!
                                    }
                                    writer.writeLineX(ret)
                                    System.err.println("chain post: $ret")
                                }
                                "like" -> {
                                    val pay = reader.readNBytesX(cmds[6].toInt()).toString(Charsets.UTF_8)
                                    assert_(pay.length <= S128_pay) { "post is too large" }
                                    var ret: String
                                    try {
                                        synchronized(getLock(chain.name)) {
                                            val blk = chain.blockNew(
                                                    Immut(
                                                            0,
                                                            Payload(false, ""),
                                                            null,
                                                            Like(
                                                                    cmds[3].toInt(),
                                                                    cmds[4]
                                                            ),
                                                            emptyArray()
                                                    ),
                                                    pay,
                                                    cmds[5],
                                                    false
                                            )
                                            ret = blk.hash
                                        }
                                    } catch (e: Throwable) {
                                        System.err.println(e.stackTrace.contentToString())
                                        ret = e.message!!
                                    }
                                    writer.writeLineX(ret)
                                    thread {
                                        signal(name, 1)
                                    }
                                    System.err.println("chain like: $ret")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: AssertionError) {
            writer.writeLineX("! " + e.message!!)
        } catch (e: ConnectException) {
            assert_(e.message == "Connection refused (Connection refused)")
            writer.writeLineX("! connection refused")
        } catch (e: SodiumException) {
            writer.writeLineX("! " + e.message!!)
        } catch (e: FileNotFoundException) {
            writer.writeLineX("! chain does not exist")
        } catch (e: SocketException) {
            System.err.println("! connection closed")
            //writer.writeLineX("! connection closed")
        } catch (e: SocketTimeoutException) {
            System.err.println("! connection timeout")
            //writer.writeLineX("! connection timeout")
        } catch (e: Throwable) {
            //println("XxXxXxXxXxXxX - $e - ${e.message}")
            System.err.println(e.stackTrace.contentToString())
            writer.writeLineX("! TODO - $e - ${e.message}")
        }
    }

    fun peerSend (reader: DataInputStream, writer: DataOutputStream, chain_: String) : Pair<Int,Int> {
        // - receives most recent timestamp
        // - DFS in heads
        //   - asks if contains hash
        //   - aborts path if reaches timestamp+24h
        //   - pushes into toSend
        // - sends toSend

        val chain = this.chainsLoadSync(chain_)
        val visited = HashSet<Hash>()
        var nmin    = 0
        var nmax    = 0

        // for each local head
        val heads = chain.getHeads(State.ALL)
        val nout = heads.size
        writer.writeLineX(nout.toString())                              // 1
        for (head in heads) {
            val pending = ArrayDeque<Hash>()
            pending.push(head)

            val toSend = mutableSetOf<Hash>()

            // for each head path of blocks
            while (pending.isNotEmpty()) {
                val hash = pending.pop()
                if (visited.contains(hash)) {
                    continue
                }
                visited.add(hash)

                val blk = chain.fsLoadBlock(hash)

                writer.writeLineX(hash)                             // 2: asks if contains hash
                val has = reader.readLineX().toBoolean()   // 3: receives yes or no
                if (has) {
                    continue                             // already has: finishes subpath
                }

                // sends this one and visits children
                //println("[add] $hash")
                toSend.add(hash)
                for (back in blk.immut.backs) {
                    pending.push(back)
                }
            }

            writer.writeLineX("")                     // 4: will start sending nodes
            writer.writeLineX(toSend.size.toString())    // 5: how many
            val nin = toSend.size
            val sorted = toSend.sortedWith(compareBy{it.toHeight()})
            for (hash in sorted) {
                val out = chain.fsLoadBlock(hash)
                val json = out.toJson()
                writer.writeLineX(json.length.toString()) // 6
                writer.writeBytes(json)
                val pay = when (chain.blockState(out, getNow())) {
                    State.HIDDEN -> ""
                    else         -> chain.fsLoadPay0(hash)
                }
                writer.writeLineX(pay.length.toString())
                writer.writeBytes(pay)
                writer.writeLineX("")
            }
            val nin2 = reader.readLineX().toInt()    // 7: how many blocks again
            assert_(nin >= nin2)
            nmin += nin2
            nmax += nin
        }
        val nout2 = reader.readLineX().toInt()       // 8: how many heads again
        assert_(nout == nout2)

        return Pair(nmin,nmax)
    }

    fun peerRecv (reader: DataInputStream, writer: DataOutputStream, chain_: String) : Pair<Int,Int> {
        // - sends most recent timestamp
        // - answers if contains each host
        // - receives all

        val chain = this.chainsLoadSync(chain_)
        var nmax = 0
        var nmin = 0

        // list of received hidden blocks (empty payloads)
        // will check if are really hidden
        val hiddens = mutableListOf<Block>()

        // for each remote head
        val nout = reader.readLineX().toInt()        // 1
        for (i in 1..nout) {
            // for each head path of blocks
            while (true) {
                val hash = reader.readLineX()   // 2: receives hash in the path
                //println("[recv-1] $hash")
                if (hash.isEmpty()) {                   // 4
                    break                               // nothing else to answer
                } else {
                    writer.writeLineX(chain.fsExistsBlock(hash).toString())   // 3: have or not block
                }
            }

            // receive blocks
            val nin = reader.readLineX().toInt()    // 5
            nmax += nin
            var nin2 = 0

            xxx@for (j in 1..nin) {
                try {
                    val len1 = reader.readLineX().toInt() // 6
                    val blk = reader.readNBytesX(len1).toString(Charsets.UTF_8).jsonToBlock().copy() // .copy() recalculates .local
                    val len2 = reader.readLineX().toInt()
                    assert_(len2 <= S128_pay) { "post is too large" }
                    val pay = reader.readNBytesX(len2).toString(Charsets.UTF_8)
                    reader.readLineX()
                    assert_(chain.getHeads(State.BLOCKED).size <= N16_blockeds) { "too many blocked blocks" }

                    // reject peers with different keys
                    if (chain.isDollar()) {
                        pay.decrypt(chain.key!!)  // throws exception if fails
                    }

                    //println("[recv] ${blk.hash} // len=$len2 // ${pay.length}")
                    synchronized(getLock(chain.name)) {
                        chain.blockChain(blk,pay)
                    }
                    if (pay=="" && blk.immut.pay.hash!="".calcHash()) {
                        hiddens.add(blk)
                    } // else: payload is really an empty string

                    nmin++
                    nin2++
                } catch (e: Throwable) {
                    System.err.println(e.message)
                    System.err.println(e.stackTrace.contentToString())
                }
            }
            writer.writeLineX(nin2.toString())             // 7
        }
        writer.writeLineX(nout.toString())                // 8

        for (blk in hiddens) {
            assert_(
                    chain.blockState(
                            blk,
                            getNow()
                    ) == State.HIDDEN
            ) { "bug found: expected hidden state" }
        }

        return Pair(nmin,nmax)
    }
}
