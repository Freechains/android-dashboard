package org.freechains.android.dashboard

import org.freechains.common.HKey
import org.freechains.common.fsRoot
import org.freechains.cli.main_cli_assert
import kotlinx.serialization.Serializable
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.io.File
import kotlin.concurrent.thread

typealias Wait = (() -> Unit)

fun String.block2id () : String {
    return this.take(5) + "..." + this.takeLast(3)
}

fun String.pub2id () : String {
    return this.take(3) + "..." + this.takeLast(3)
}

fun String.chain2id () : String {
    return when {
        this.startsWith('@') -> {
            val n = if (this.startsWith("@!")) 5 else 4
            this.take(n) + "..." + this.takeLast(3)
        }
        this.startsWith("\$bootstrap.") -> {
            this.take("\$bootstrap.123".length) + "..." + this.takeLast(3)
        }
        else -> this
    }
}

@Serializable
data class Peer (
    val name   : String,
    var ping   : String = "?",              // x
    var chains : List<String> = emptyList() // x
)

@Serializable
data class Chain (
    val name   : String,
    val key    : HKey?,
    var heads  : List<String>,              // x
    var blocks : List<String>               // x
)

@Serializable
data class Id (
    val nick : String,
    //var desc : String,
    val pub  : HKey
)

@Serializable
data class Local (
    var peers  : List<Peer>,
    var chains : List<Chain>,
    var ids    : List<Id>,
    var cts    : List<Id>
)

object LOCAL {
    var data: Local?  = null
    var path: String? = null
    val cbs: MutableSet<()->Unit> = mutableSetOf()

    @Synchronized
    fun load () {
        this.path = fsRoot!! + "/" + "local.json"
        val file = File(this.path!!)
        if (!file.exists()) {
            this.data = Local(emptyList(), emptyList(), emptyList(), emptyList())
            this.save(false)
        } else {
            @OptIn(UnstableDefault::class)
            this.data = Json(JsonConfiguration(prettyPrint=true)).parse(Local.serializer(), file.readText())
        }
    }

    private fun save (post: Boolean) {
        @OptIn(UnstableDefault::class)
        File(this.path!!).writeText(Json(JsonConfiguration(prettyPrint=true)).stringify(Local.serializer(), this.data!!))
        if (post) {
            this.data!!.chains
                .filter  { it.name.startsWith("\$bootstrap.") && it.heads.isNotEmpty() && !it.heads.first().startsWith("0_") }
                .forEach {
                    thread {
                        main_cli_assert(arrayOf("chain", it.name, "post", "file", this.path!!))
                    }
                }
        }
    }

    @Synchronized
    fun <T> read (f: (Local)->T) : T {
        return f(this.data!!)
    }

    @Synchronized
    fun <T> write (post: Boolean, f: (Local)->T) : T {
        var ret = f(this.data!!)
        this.save(post)
        this.cbs.forEach { it() }
        return ret
    }

    @Synchronized
    fun write_tst (post: Boolean, f: (Local)->Boolean) : Boolean {
        val ret = f(this.data!!)
        if (ret) {
            this.save(post)
            this.cbs.forEach { it() }
        }
        return ret
    }
}
