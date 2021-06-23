package org.freechains.store

import org.freechains.cli.main_cli
import org.freechains.common.*
import org.freechains.cli.main_cli_assert
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import kotlin.concurrent.thread

class Store (chain: String, port: Int) {
    private var last: String? = null

    val chain = chain
    val port  = port
    val port_ = "--port=$port"

    @get:Synchronized
    val data : MutableMap<String,MutableMap<String,String>> = mutableMapOf()

    @get:Synchronized
    val cbs: MutableList<(String,String,String)->Unit> = mutableListOf()

    init {
        this.update()
        thread {
            try {
                val socket = Socket("localhost", port)
                val writer = DataOutputStream(socket.getOutputStream()!!)
                val reader = DataInputStream(socket.getInputStream()!!)
                writer.writeLineX("$PRE chain $chain listen")
                while (true) {
                    reader.readLineX().listSplit()
                    this.update()
                }
            } catch (e: java.io.EOFException) {
                System.err.println("! connection closed")
            }
        }
    }

    @Synchronized
    fun store (v1: String, v2: String, v3: String) {
        main_cli_assert(arrayOf(port_, "chain", this.chain, "post", "inline", "$v1 $v2 $v3"))
        this.store_(false, v1, v2, v3)
    }

    fun getKeys (key: String) : List<String> {
        return this.data[key].let {
            if (it == null) emptyList() else it.keys.toList()
        }
    }

    fun getPairs (key: String) : List<Pair<String,String>> {
        return this.data[key].let {
            if (it == null) emptyList() else it.toList()
        }
    }

    private fun store_ (call: Boolean, v1: String, v2: String, v3: String) {
        if (!this.data.containsKey(v1)) {
            this.data.set(v1, mutableMapOf())
        }
        if (v3 == "REM") {
            this.data[v1]!!.remove(v2)
        } else {
            this.data[v1]!!.set(v2,v3)
        }
        if (call) {
            for (cb in this.cbs) {
                cb(v1, v2, v3)
            }
        }
    }

    @Synchronized
    private fun update () {
        //println(">>> last = $last")
        if (this.last == null) {
            //println(">>> update ${this.chain} // ${main_cli_assert(arrayOf(port_, "chains", "list"))}")
            this.last = main_cli_assert(arrayOf(port_, "chain", this.chain, "genesis"))
        }

        val hs = main_cli_assert(arrayOf(port_, "chain", this.chain, "traverse", "all", this.last!!)).listSplit()
        for (h in hs) {
            val v = main_cli_assert(arrayOf(port_, "chain", this.chain, "get", "payload", h))
            //println(">>> v = $v")
            val cmd = v.split(' ')
            assert_(cmd.size == 3) { "invalid store command" }
            this.store_(true, cmd[0], cmd[1], cmd[2])
        }

        if (hs.isNotEmpty()) {
            this.last = hs.last()
        }
    }
}