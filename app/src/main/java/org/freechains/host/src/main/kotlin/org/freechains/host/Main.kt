package org.freechains.host

import org.freechains.common.*
import java.io.DataInputStream
import java.io.DataOutputStream

val help = """
freechains-host $VERSION

Usage:
    freechains-host start <dir>
    freechains-host stop
    freechains-host path
    freechains-host now <time>
    
Options:
    --help          displays this help
    --version       displays software version
    --port=<port>   sets port to connect [default: $PORT_8330]

More Information:

    http://www.freechains.org/

    Please report bugs at <http://github.com/Freechains/README/>.
"""

fun main (args: Array<String>) {
    main_ { main_host(args) }
}

fun main_host_assert (args: Array<String>) : String {
    return main_assert_ { main_host(args) }
}

fun main_host (args: Array<String>) : Pair<Boolean,String> {
    return main_catch_("freechains-host", VERSION, help, args) { cmds, opts ->
        val port = opts["--port"]?.toInt() ?: PORT_8330
        when (cmds[0]) {
            "start" -> {
                assert_(cmds.size == 2) { "invalid number of arguments" }
                val host = Host_load(cmds[1], port)
                println("Freechains $VERSION")
                println("Waiting for connections on $host...")
                Daemon(host).daemon()
                Pair(true, "")
            }
            else -> {
                val socket = Socket_5s("localhost", port)
                val writer = DataOutputStream(socket.getOutputStream()!!)
                val reader = DataInputStream(socket.getInputStream()!!)
                val ret = when (cmds[0]) {
                    "stop" -> {
                        assert_(cmds.size == 1) { "invalid number of arguments" }
                        writer.writeLineX("$PRE host stop")
                        assert_(reader.readLineX() == "true")
                        Pair(true, "")
                    }
                    "path" -> {
                        assert_(cmds.size == 1) { "invalid number of arguments" }
                        writer.writeLineX("$PRE host path")
                        val path = reader.readLineX()
                        Pair(true, path)
                    }
                    "now" -> {
                        assert_(cmds.size == 2) { "invalid number of arguments" }
                        writer.writeLineX("$PRE host now ${cmds[1]}")
                        assert_(reader.readLineX() == "true")
                        Pair(true, "")
                    }
                    else -> Pair(false, help)
                }
                socket.close()
                ret
            }
        }
    }
}