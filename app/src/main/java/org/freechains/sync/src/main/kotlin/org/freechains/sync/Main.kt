package org.freechains.sync

import org.freechains.cli.main_cli
import org.freechains.common.*
import org.freechains.store.Store

val help = """
freechains-sync $VERSION

Usage:
    freechains-sync <chain> [<peer>]

Options:
    --help          displays this help
    --version       displays software version
    --port          port to connect [default: $PORT_8330]

More Information:

    http://www.freechains.org/

    Please report bugs at <http://github.com/Freechains/README/>.
"""

fun main (args: Array<String>) {
    main_ { main_sync(args) }
}

fun main_sync_assert (args: Array<String>) : String {
    return main_assert_ { main_sync(args) }
}

fun main_sync (args: Array<String>) : Pair<Boolean,String> {
    return main_catch_("freechains-sync", VERSION, help, args) { cmds, opts ->
        assert_(cmds.size >= 1) { "invalid number of arguments" }

        val port = opts["--port"]?.toInt() ?: PORT_8330
        val port_ = "--port=$port"
        val chain = cmds[0]

        main_cli(arrayOf(port_, "chains", "join", chain))
        val sync = Sync (
            Store(chain, port),
            if (cmds.size==1) null else cmds[1],
            CBs (
                { n -> if (n>0) println("Synchronizing $n items...") },
                { p,a,c,r -> println("    --> $p $a $c ($r)") },
                { n -> if (n>0) println("    --> DONE") }
            )
        )
        sync.loop()
        Pair(true, "")
    }
}
