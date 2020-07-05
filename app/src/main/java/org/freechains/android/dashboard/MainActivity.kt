package org.freechains.android.dashboard

import org.freechains.common.*
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import org.freechains.host.main_host
import org.freechains.store.Store
import org.freechains.sync.CBs
import org.freechains.sync.Sync
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity ()
{
    lateinit var store: Store
    lateinit var sync:  Sync
    lateinit var login_alert: AlertDialog

    //private var isActive = true
    //override fun onPause()  { super.onPause()  ; this.isActive=false }
    //override fun onResume() { super.onResume() ; this.isActive=true  }

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
                AppBarConfiguration(setOf(R.id.nav_home, R.id.nav_chains, R.id.nav_peers, R.id.nav_identity))
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

        val sync = this.fg {
            main_cli_assert(arrayOf("chains", "list"))
                .listSplit()
                .firstOrNull { it.startsWith("\$sync.") } ?: ""
        }

        if (sync.isEmpty()) {
            this.login()
        } else {
            this.start(sync)
        }
    }

    fun login () {
        val view = View.inflate(this, R.layout.login, null)
        this.login_alert = AlertDialog.Builder(this)
            .setTitle("Welcome to Freechains:")
            .setView(view)
            .setCancelable(false)
            .show()
    }

    fun login_new (v: View) {
        val view = View.inflate(this, R.layout.frag_ids_add, null)
        AlertDialog.Builder(this)
            .setTitle("New account:")
            .setView(view)
            .setNegativeButton ("Cancel", null)
            .setPositiveButton("OK") { _,_ ->
                val nick  = view.findViewById<EditText>(R.id.edit_nick).text.toString()
                val pass1 = view.findViewById<EditText>(R.id.edit_pass1).text.toString()
                val pass2 = view.findViewById<EditText>(R.id.edit_pass2).text.toString()
                if (nick.isNotEmpty() && pass1.length>=LEN20_pubpbt && pass1==pass2) {
                    this.login_alert.hide()
                    this.bg({
                        val pub = main_cli_assert(arrayOf("crypto", "pubpvt", pass1)).split(' ')[0]
                        val sha = main_cli_assert(arrayOf("crypto", "shared", pass1))
                        val sync = "\$sync.$pub"
                        main_cli_assert(arrayOf("chains", "join", sync, sha))
                        this.start(sync)
                        this.store.store("ids", pub, nick)
                        this.store.store("chains", "@" + pub, "ADD")
                        this.store.store("chains", sync, sha)
                    }, {
                    })
                } else {
                    this.showToast("Invalid nickname or password.")
                }
            }
            .show()
    }

    fun login_sync (v: View) {
        val view = View.inflate(this, R.layout.login_sync, null)
        AlertDialog.Builder(this)
            .setTitle("Sync account:")
            .setView(view)
            .setNegativeButton ("Cancel", null)
            .setPositiveButton("OK") { _,_ ->
                val pass = view.findViewById<EditText>(R.id.edit_pass).text.toString()
                val peer = view.findViewById<EditText>(R.id.edit_peer).text.toString()
                this.login_alert.hide()
                this.bg({
                    val pub = main_cli_assert(arrayOf("crypto", "pubpvt", pass)).split(' ')[0]
                    val sha = main_cli_assert(arrayOf("crypto", "shared", pass))
                    val sync = "\$sync.$pub"
                    val (v1,v2) = main_cli(arrayOf("peer", peer, "chains"))
                    //println(">>>>>> $v1 $v2")
                    if (v1 && v2.listSplit().contains(sync)) {
                        main_cli_assert(arrayOf("chains", "join", sync, sha))
                        this.start(sync)
                        main_cli_assert(arrayOf("peer", peer, "recv", sync))
                        //println("> CHAINS > ${main_cli_assert(arrayOf("chains", "list"))}")
                        true
                    } else {
                        false
                    }
                }, { ok ->
                    if (!ok) {
                        this.showToast("Invalid password or peer.")
                        this.login_alert.show()
                    }
                })
            }
            .show()
        //main_cli(arrayOf("peer", "192.168.1.100", "recv", SYNC))
    }

    fun start (sync: String) {
        var recvs = mutableMapOf<String,Int>()
        var syncs = 0
        val progress = findViewById<ProgressBar>(R.id.progress)

        this.fg {
            this.store = Store(sync, PORT_8330)
        }
        this.sync = Sync(this.store, CBs(
            { tot ->
                //println("+++ max $tot")
                this.runOnUiThread {
                    //println("+++ todo $tot")
                    if (tot > 0) {
                        if (progress.max == 0) {
                            progress.visibility = View.VISIBLE
                            this.showToast("Synchronizing...")
                        }
                        progress.max += tot
                    }
                }
            },
            { chain, action, (ok,nn) ->
                this.runOnUiThread {
                    progress.progress += 1
                    if (!ok) return@runOnUiThread
                    val (s1, _) = Regex("(\\d+) / (\\d+)").find(nn)!!.destructured
                    val n1 = s1.toInt()
                    syncs += n1
                    if (n1 > 0) {
                        if (action == "recv") {
                            recvs[chain] = n1
                        }
                        this.showToast("${this.chain2out(chain)}: $action $n1")
                    }
                }
            },
            {
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
                        progress.visibility = View.INVISIBLE
                        recvs = mutableMapOf<String,Int>()
                        syncs = 0
                        progress.max = 0
                        progress.progress = 0
                    }
                }
            }
        ))
        //this.sync.sync_all()
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

    fun chain2out (chain: String) : String {
        return chain.chain2out(this.store.getPairs("ids") + this.store.getPairs("cts"))
    }

    fun chains_join_ask (chain: String = "", cb: () -> Unit) {
        val view = View.inflate(this, R.layout.frag_chains_join, null)
        view.findViewById<EditText>(R.id.edit_name).setText(chain)
        AlertDialog.Builder(this)
            .setTitle("Join chain:")
            .setView(view)
            .setNegativeButton ("Cancel", null)
            .setPositiveButton("OK") { _,_ ->
                val name  = view.findViewById<EditText>(R.id.edit_name) .text.toString()
                val pass1 = view.findViewById<EditText>(R.id.edit_pass1).text.toString()
                val pass2 = view.findViewById<EditText>(R.id.edit_pass2).text.toString()
                if (!name.startsWith('$') || (pass1.length>=LEN10_shared && pass1==pass2)) {
                    fg_chain_join(name,pass1)
                    cb()
                } else {
                    this.showToast("Invalid password.")
                }
            }
            .show()
    }

    fun fg_chain_join (chain: String, pass: String? = null) {
        this.fg {
            val key = if (!chain.startsWith('$')) "ADD" else {
                main_cli_assert(arrayOf("crypto", "shared", pass!!))
            }
            this.store.store("chains", chain, key)
        }
        this.showToast("Added chain ${this.chain2out(chain)}.")
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
