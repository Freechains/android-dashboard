package org.freechains.android.dashboard

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.freechains.cli.main_cli
import org.freechains.cli.main_cli_assert
import org.freechains.common.fsRoot
import org.freechains.common.listSplit
import org.freechains.host.main_host
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity ()
{
    lateinit var local: Local

    override fun onDestroy () {
        main_cli(arrayOf("host", "stop"))
        super.onDestroy()
    }

    override fun onCreate (savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_main)

        findNavController(R.id.nav_host_fragment).let {
            this.setupActionBarWithNavController (
                it,
                AppBarConfiguration(setOf(R.id.nav_home, R.id.nav_chains, R.id.nav_peers, R.id.nav_sync))
            )
            findViewById<BottomNavigationView>(R.id.nav_view).setupWithNavController(it)
        }
        findViewById<ProgressBar>(R.id.progress).max = 0 // 0=ready

        //println(">>> VERSION = $VERSION")
        fsRoot = this.applicationContext.filesDir.toString()
        //File(fsRoot!!, "/").deleteRecursively() ; error("OK")

        // background start
        thread { main_host(arrayOf("start","/data/")) }
        Thread.sleep(250)

        if (!Local_fsExists()) {
            Local().fsSave()
        }
        this.local = Local_fsLoad()
    }

    fun onClickSync (menuItem: MenuItem?): Boolean {
        thread { this.sync() }
        return true
    }

    @Synchronized
    fun sync () {
        val progress = findViewById<ProgressBar>(R.id.progress)
        progress.max = 0
        progress.progress = 0

        val recvs = mutableMapOf<String,Int>()
        var syncs = 0
        val chains = main_cli_assert(arrayOf("chains", "list")).listSplit()

        val tot = chains.size * this.local.peers.size * 2
        //println("+++ todo $tot")
        if (tot == 0) {
            return
        }

        this.runOnUiThread {
            if (progress.max == 0) {
                progress.visibility = View.VISIBLE
                showToast("Synchronizing...")
            }
            progress.max += tot
        }

        for (chain in chains) {
            fun one (action: String, v: Pair<Boolean,String>) {
                this.runOnUiThread {
                    progress.progress += 1
                }
                if (!v.first) return
                val (s1, _) = Regex("(\\d+) / (\\d+)").find(v.second)!!.destructured
                val n1 = s1.toInt()
                syncs += n1
                if (n1 > 0) {
                    if (action == "recv") {
                        recvs[chain] = n1
                    }
                    this.runOnUiThread {
                        showToast("${chain.chain2out()}: $action $n1")
                    }
                }
            }

            for (peer in this.local.peers) {
                one("send", main_cli(arrayOf("peer", peer, "send", chain)))
                one("recv", main_cli(arrayOf("peer", peer, "recv", chain)))
            }
        }

        this.runOnUiThread {
            if (progress.progress == progress.max) {
                //println("+++ done ${progress.max}")
                val news_str = recvs.toList()
                    .map { "${it.second} ${it.first}" }
                    .joinToString("\n")
                if (news_str.isNotEmpty()) {
                    this.showNotification("New blocks:", news_str)
                }
                //println("SYNC $syncs")
                this.showToast("Synchronized $syncs blocks.")
                progress.visibility = android.view.View.INVISIBLE
            }
        }
    }

    ////////////////////////////////////////

    fun <T> fg (f: () -> T): T {
        var ret: T? = null
        thread {
            ret = f()
        }.join()
        return ret!!
    }

    fun <T> bg (f: ()->T, g: (T)->Unit): Thread {
        this.setWaiting(true)
        return thread {
            var ret: T = f()
            this.runOnUiThread {
                this.setWaiting(false)
                g(ret)
            }
        }
    }

    fun setWaiting (v: Boolean) {
        val wait = findViewById<View>(R.id.wait)
        if (v) {
            wait.visibility = View.VISIBLE
            this.getWindow().setFlags (
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            );
        } else {
            wait.visibility = View.INVISIBLE
            this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        }
    }

    private fun showNotification (title: String, message: String) {
        val mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("FREECHAINS_CHANNEL_ID",
                "FREECHAINS_CHANNEL_NAME",
                NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "FREECHAINS_NOTIFICATION_CHANNEL_DESCRIPTION"
            mNotificationManager.createNotificationChannel(channel)
        }
        val mBuilder = NotificationCompat.Builder(this.applicationContext, "FREECHAINS_CHANNEL_ID")
            .setSmallIcon(R.drawable.ic_freechains_notify) // notification icon
            .setContentTitle(title) // title for notification
            .setContentText(message)// message for notification
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true) // clear notification after click
        val intent = Intent(this.applicationContext, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        mBuilder.setContentIntent(pi)
        mNotificationManager.notify(0, mBuilder.build())
    }

    private val queue = mutableListOf<String>()
    fun showToast (s: String) {
        this.queue.add(s)
        if (this.queue.size == 1) {
            this.nextToast()
        }
    }
    private fun nextToast () {
        if (this.queue.isEmpty()) {
            return
        }
        val s = this.queue.elementAt(0)
        Toast.makeText(this.applicationContext,s,Toast.LENGTH_SHORT).show()
        //println(">>> $s")
        thread {
            Thread.sleep(1000)
            this.runOnUiThread {
                this.queue.removeAt(0)
                this.nextToast()
            }
        }
    }

    ////////////////////////////////////////

    fun chains_join_ask (chain: String = "", cb: () -> Unit) {
        val view = View.inflate(this, R.layout.frag_chains_join, null)
        view.findViewById<EditText>(R.id.edit_name).setText(chain)
        AlertDialog.Builder(this)
            .setTitle("Join chain:")
            .setView(view)
            .setNegativeButton ("Cancel", null)
            .setPositiveButton("OK") { _,_ ->
                val name  = view.findViewById<EditText>(R.id.edit_name) .text.toString()
                val pions = view.findViewById<EditText>(R.id.edit_pioneers).text.toString()
                val pass1 = view.findViewById<EditText>(R.id.edit_pass1).text.toString()
                val pass2 = view.findViewById<EditText>(R.id.edit_pass2).text.toString()
                when {
                    name.startsWith('$') -> {
                        if (pass1.length>=LEN10_shared && pass1==pass2) {
                            fg_chain_join(name, emptyList(), pass1)
                            cb()
                        } else {
                            this.showToast("Invalid password.")
                        }
                    }
                    name.startsWith('#') -> {
                        val l = pions.listSplit()
                        if (l.size > 0) {
                            fg_chain_join(name, l, null)
                            cb()
                        } else {
                            this.showToast("Invalid pioneers.")
                        }
                    }
                    name.startsWith('@') -> {
                        fg_chain_join(name, emptyList(), null)
                    }
                    else -> {
                        this.showToast("Invalid name.")
                    }
                }
            }
            .show()
    }

    fun fg_chain_join (chain: String, pioneers: List<String>, pass: String? = null) {
        this.fg {
            when {
                chain.startsWith('$') -> {
                    val key = main_cli_assert(arrayOf("crypto", "shared", pass!!))
                    main_cli_assert(arrayOf("chains", "join", chain, key))
                }
                chain.startsWith('#') -> {
                    main_cli_assert(arrayOf("chains", "join", chain) + pioneers)
                }
                else -> {
                    main_cli_assert(arrayOf("chains", "join", chain))
                }
            }
        }
        this.showToast("Added chain ${chain.chain2out()}.")
    }

    fun rem_ask (pre: String, cur: String, act: ()->Unit) {
        AlertDialog.Builder(this)
            .setTitle("Remove $pre $cur?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(android.R.string.yes, { _, _ ->
                act()
                this.showToast("Removed $pre $cur.")
            })
            .setNegativeButton(android.R.string.no, null).show()
    }
}
