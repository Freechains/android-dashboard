package org.freechains.cli

import org.freechains.common.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

val help = """
freechains $VERSION

Usage:
    freechains chains join  <chain> [<shared>]
    freechains chains leave <chain>
    freechains chains list
    freechains chains listen
    
    freechains chain <chain> genesis
    freechains chain <chain> heads (all | linked | blocked)
    freechains chain <chain> get (block | payload) <hash>
    freechains chain <chain> post (file | inline | -) [<path_or_text>]
    freechains chain <chain> (like | dislike) <hash>
    freechains chain <chain> reps <hash_or_pub>
    freechains chain <chain> remove <hash>
    freechains chain <chain> traverse (all | linked) <hashes>...
    freechains chain <chain> listen
    
    freechains peer <addr:port> ping
    freechains peer <addr:port> chains
    freechains peer <addr:port> (send | recv) <chain>

    freechains crypto (shared | pubpvt) <passphrase>
    
Options:
    --help              [none]            displays this help
    --version           [none]            displays software version
    --host=<addr:port>  [all]             sets host address and port to connect [default: localhost:$PORT_8330]
    --port=<port>       [all]             sets host port to connect [default: $PORT_8330]
    --sign=<pvt>        [post|(dis)like]  signs post with given private key
    --encrypt           [post]            encrypts post with public key (only in public identity chains)
    --decrypt=<pvt>     [get]             decrypts post with private key (only in public identity chains)
    --why=<text>        [(dis)like]       explains reason for the like

More Information:

    http://www.freechains.org/

    Please report bugs at <http://github.com/Freechains/README/>.
"""

fun main (args: Array<String>) {
    main_ { main_cli(args) }
}

fun main_cli_assert (args: Array<String>) : String {
    return main_assert_ { main_cli(args) }
}

fun main_cli (args: Array<String>) : Pair<Boolean,String> {
    return main_catch_("freechains", VERSION, help, args) { cmds,opts ->
        val (addr, port_) = (opts["--host"] ?: "localhost:$PORT_8330").to_Addr_Port()
        val port = opts["--port"]?.toInt() ?: port_
        val socket = Socket_5s(addr, port)
        val writer = DataOutputStream(socket.getOutputStream()!!)
        val reader = DataInputStream(socket.getInputStream()!!)

        //println("+++ $cmds")
        @Suppress("UNREACHABLE_CODE")
        val ret: String = when (cmds[0]) {
            "crypto" -> {
                assert_(cmds.size == 3) { "invalid number of arguments" }
                writer.writeLineX("$PRE crypto ${cmds[1]}")
                assert_(!cmds[2].contains('\n')) { "invalid password" }
                writer.writeLineX(cmds[2])
                reader.readLineX()
            }
            "peer" -> {
                assert_(cmds.size in 3..4) { "invalid number of arguments" }
                val remote = cmds[1]
                when (cmds[2]) {
                    "ping" -> {
                        assert_(cmds.size == 3) { "invalid number of arguments" }
                        writer.writeLineX("$PRE peer $remote ping")
                        reader.readLineX()
                    }
                    "chains" -> {
                        assert_(cmds.size == 3) { "invalid number of arguments" }
                        writer.writeLineX("$PRE peer $remote chains")
                        reader.readLineX()
                    }
                    "send" -> {
                        assert_(cmds.size == 4) { "invalid number of arguments" }
                        writer.writeLineX("$PRE peer $remote send ${cmds[3]}")
                        reader.readLineX()
                    }
                    "recv" -> {
                        assert_(cmds.size == 4) { "invalid number of arguments" }
                        writer.writeLineX("$PRE peer $remote recv ${cmds[3]}")
                        reader.readLineX()
                    }
                    else -> "!"
                }
            }
            "chains" -> {
                assert_(cmds.size in 2..4) { "invalid number of arguments" }
                when (cmds[1]) {
                    "list" -> {
                        assert_(cmds.size == 2) { "invalid number of arguments" }
                        writer.writeLineX("$PRE chains list")
                        reader.readLineX()
                    }
                    "leave" -> {
                        assert_(cmds.size == 3) { "invalid number of arguments" }
                        writer.writeLineX("$PRE chains leave ${cmds[2]}")
                        reader.readLineX()
                    }
                    "join" -> {
                        assert_(cmds.size in 3..4) { "invalid number of arguments" }
                        val shared = if (cmds.size == 3) "" else " " + cmds[3]
                        writer.writeLineX("$PRE chains join ${cmds[2]}$shared")
                        reader.readLineX()
                    }
                    "listen" -> {
                        assert_(cmds.size == 2) { "invalid number of arguments" }
                        writer.writeLineX("$PRE chains listen")
                        while (true) {
                            val n_name = reader.readLineX()
                            println(n_name)
                        }
                        "!"
                    }
                    else -> "!"
                }
            }
            "chain" -> {
                assert_(cmds.size >= 3) { "invalid number of arguments" }
                val chain = cmds[1]

                fun like(lk: String): String {
                    assert_(cmds.size == 4) { "invalid number of arguments" }
                    assert_(opts["--sign"] is String) { "expected `--sign`" }
                    val (len, pay) = opts["--why"].let {
                        if (it == null) {
                            Pair(0, "")
                        } else {
                            Pair(it.length.toString(), it)
                        }
                    }
                    writer.writeLineX("$PRE chain $chain like $lk ${cmds[3]} ${opts["--sign"]} $len")
                    writer.writeLineX(pay)
                    return reader.readLineX()
                }

                when (cmds[2]) {
                    "genesis" -> {
                        assert_(cmds.size == 3) { "invalid number of arguments" }
                        writer.writeLineX("$PRE chain $chain genesis")
                        reader.readLineX()
                    }
                    "heads" -> {
                        assert_(cmds.size == 4) { "invalid number of arguments" }
                        writer.writeLineX("$PRE chain $chain heads ${cmds[3]}")
                        reader.readLineX()
                    }
                    "get" -> {
                        assert_(cmds.size == 5) { "invalid number of arguments" }
                        val decrypt = opts["--decrypt"].toString() // null or pvtkey

                        writer.writeLineX("$PRE chain $chain get ${cmds[3]} ${cmds[4]} $decrypt")
                        val len = reader.readLineX()
                        if (len.startsWith('!')) {
                            len
                        } else {
                            reader.readNBytesX(len.toInt()).toString(Charsets.UTF_8)
                        }
                    }
                    "post" -> {
                        assert_(cmds.size in 4..5) { "invalid number of arguments" }
                        val sign = opts["--sign"] ?: "anon"
                        val encrypt = opts.containsKey("--encrypt").toString() // null (false) or empty (true)

                        val pay = when (cmds[3]) {
                            "inline" -> cmds[4]
                            "file" -> File(cmds[4]).readBytes().toString(Charsets.UTF_8)
                            "-" -> DataInputStream(System.`in`).readAllBytesX().toString(Charsets.UTF_8)
                            else -> error("impossible case")
                        }

                        writer.writeLineX("$PRE chain $chain post $sign $encrypt ${pay.length}")
                        writer.writeBytes(pay)

                        reader.readLineX()
                    }
                    "traverse" -> {
                        assert_(cmds.size >= 5) { "invalid number of arguments" }
                        val downto = cmds.drop(3).joinToString(" ")
                        writer.writeLineX("$PRE chain $chain traverse ${cmds[3]} $downto")
                        reader.readLineX()
                    }
                    "reps" -> {
                        assert_(cmds.size == 4) { "invalid number of arguments" }
                        writer.writeLineX("$PRE chain $chain reps ${cmds[3]}")
                        reader.readLineX()
                    }
                    "like" -> like("1")
                    "dislike" -> like("-1")
                    "remove" -> {
                        assert_(cmds.size == 4) { "invalid number of arguments" }
                        writer.writeLineX("$PRE chain $chain remove ${cmds[3]}")
                        assert_(reader.readLineX() == "true")
                        ""
                    }
                    "listen" -> {
                        assert_(cmds.size == 3) { "invalid number of arguments" }
                        writer.writeLineX("$PRE chain $chain listen")
                        while (true) {
                            val n = reader.readLineX()
                            println(n)
                        }
                        "!"
                    }
                    else -> "!"
                }
            }
            else -> "!"
        }
        when {
            ret.startsWith("!") -> Pair(false, ret)
            else -> Pair(true, ret)
        }
    }
}