package org.freechains.host

import org.freechains.common.*
import java.io.File

data class Host (
    val root: String,
    val port: Int
)

fun Host_load (dir: String, port: Int = PORT_8330) : Host {
    assert_(dir.startsWith("/")) { "path must be absolute" }
    val loc = Host(fsRoot + dir + "/", port)
    File(loc.root).let {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
    return loc
}

// CHAINS

fun Host.chainsLoad (name: String) : Chain {
    val file = File(this.root + "/chains/" + name + "/" + "chain")
    val chain = file.readText().fromJsonToChain()
    chain.root = this.root
    return chain
}

fun Host.chainsJoin (name: String, pass: String? = null) : Chain {
    val chain = Chain(this.root, name, pass).validate()
    val file = File(chain.path() + "/chain")
    assert_(!file.exists()) { "chain already exists: $chain" }
    chain.fsSave()

    val gen = Block(
        Immut(
            0,
            Payload(false, ""),
            null,
            null,
            emptyArray()
        ),
        chain.getGenesis(),
        null
    )
    chain.blockChain(gen, "")

    return file.readText().fromJsonToChain()
}

fun Host.chainsLeave (name: String) : Boolean {
    val chain = Chain(this.root, name, null)
    val file = File(chain.path())
    return file.exists() && file.deleteRecursively()
}

fun Host.chainsList () : List<String> {
    return File(this.root + "/chains/").list().let {
        if (it == null) emptyList() else it.toList()
    }
}