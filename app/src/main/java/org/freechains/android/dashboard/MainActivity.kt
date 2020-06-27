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
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.freechains.bootstrap.Chain as Boot_Chain
import org.freechains.cli.main_cli
import org.freechains.cli.main_cli_assert
import org.freechains.host.main_host
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import kotlin.concurrent.thread

fun makeLinkClickable (strBuilder: SpannableStringBuilder, span: URLSpan, cb: (String)->Unit) {
    val start: Int = strBuilder.getSpanStart(span)
    val end: Int = strBuilder.getSpanEnd(span)
    val flags: Int = strBuilder.getSpanFlags(span)
    val clickable: ClickableSpan = object : ClickableSpan() {
        override fun onClick(view: View) {
            cb(span.url)
        }
    }
    strBuilder.setSpan(clickable, start, end, flags)
    strBuilder.removeSpan(span)
}

fun TextView.setTextViewHTML (html: String, cb: (String)->Unit) {
    val sequence: CharSequence = Html.fromHtml(html)
    val strBuilder = SpannableStringBuilder(sequence)
    val urls = strBuilder.getSpans(0, sequence.length, URLSpan::class.java)
    for (span in urls) {
        makeLinkClickable(strBuilder, span!!, cb)
    }
    this.text = strBuilder
    this.movementMethod = LinkMovementMethod.getInstance()
}

const val T5m_sync    = 30*hour
const val LEN1000_pay = 1000
const val LEN10_shared = 10
const val LEN20_pubpbt = 20
const val BOOT = "\$bootstrap"

class MainActivity : AppCompatActivity ()
{
    lateinit var boot: Boot_Chain

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
        LOCAL.load()

        this.fg {
            // background start
            thread { main_host(arrayOf("start","/data/")) }
            Thread.sleep(500)

            // bootstrap chain
            main_cli(arrayOf("chains", "join", BOOT, "36EE6324B91D6F04BA321B0EA6A09F9854E75DE5C0959FA73570A15EA385AB34"))
            main_cli(arrayOf("peer", "192.168.1.100", "recv", BOOT))

            var recvs = mutableMapOf<String,Int>()
            var syncs = 0
            val progress = findViewById<ProgressBar>(R.id.progress)

            this.boot = Boot_Chain(BOOT, PORT_8330,
                { tot ->
                    this.runOnUiThread {
                        if (tot > 0) {
                            if (progress.max == 0) {
                                progress.visibility = View.VISIBLE
                                Toast.makeText(
                                    this.applicationContext,
                                    //"Total steps: ${progress.max}",
                                    "Synchronizing...",
                                    Toast.LENGTH_LONG
                                ).show()
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
                            Toast.makeText(
                                this.applicationContext,
                                "${chain}: $action $n1", Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                {
                    this.runOnUiThread {
                        if (progress.progress == progress.max) {
                            recvs = mutableMapOf<String,Int>()
                            syncs = 0
                            progress.max = 0
                            val news_str = recvs.toList()
                                .map { "${it.second} ${it.first}" }
                                .joinToString("\n")
                            if (news_str.isNotEmpty()) {
                                this.showNotification("New blocks:", news_str)
                            }
                            progress.visibility = View.INVISIBLE
                            Toast.makeText(
                                this.applicationContext,
                                "Synchronized $syncs blocks.", Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            )
        }

        // background listen
        thread {
            val socket = Socket("localhost", PORT_8330)
            val writer = DataOutputStream(socket.getOutputStream()!!)
            val reader = DataInputStream(socket.getInputStream()!!)
            writer.writeLineX("$PRE chains listen")
            while (true) {
                val v = reader.readLineX()
                val (n,chain) = Regex("(\\d+) (.*)").find(v)!!.destructured
                if (n.toInt() > 0) {
                    this.showNotification("New blocks:", v)
                }
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
                val pass1 = view.findViewById<EditText>(R.id.edit_pass1).text.toString()
                val pass2 = view.findViewById<EditText>(R.id.edit_pass2).text.toString()
                if (!name.startsWith('$') || (pass1.length>=LEN10_shared && pass1==pass2)) {
                    fg_chain_join(name,pass1)
                    cb()
                } else {
                    Toast.makeText(
                        this.applicationContext,
                        "Invalid password.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }

    fun fg_chain_join (chain: String, pass: String? = null) {
        this.fg {
            val key = if (!chain.startsWith('$')) "" else {
                " " + main_cli_assert(arrayOf("crypto", "shared", pass!!))
            }
            main_cli_assert(arrayOf("chain", BOOT, "post", "inline", "chains add $chain" + key))
        }
        Toast.makeText(
            this.applicationContext,
            "Added chain ${chain.chain2id()}.",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun fg_chain_get (chain: String, mode: String, block: String) {
        val pay = this.fg {
            main_cli_assert(arrayOf("chain", chain, "get", mode, block)).take(LEN1000_pay)
        }
        AlertDialog.Builder(this)
            .setTitle("Block ${block.block2id()}:")
            //.setView(msg)
            .setMessage(pay)
            .show()
    }

    ////////////////////////////////////////

    fun ids_add_ask (cb: ()->Unit) {
        val view = View.inflate(this, R.layout.frag_ids_add, null)
        AlertDialog.Builder(this)
            .setTitle("New identity:")
            .setView(view)
            .setNegativeButton ("Cancel", null)
            .setPositiveButton("OK") { _,_ ->
                val nick  = view.findViewById<EditText>(R.id.edit_nick).text.toString()
                val pass1 = view.findViewById<EditText>(R.id.edit_pass1).text.toString()
                val pass2 = view.findViewById<EditText>(R.id.edit_pass2).text.toString()
                if (pass1.length>=LEN20_pubpbt && pass1==pass2) {
                    thread {
                        val pub = main_cli_assert(arrayOf("crypto", "pubpvt", pass1)).split(' ')[0]
                        var ok = LOCAL.write(true) {
                            if (it.ids.none { it.nick==nick || it.pub==pub }) {
                                it.ids += Id(nick, pub)
                                true
                            } else {
                                false
                            }
                        }
                        this.runOnUiThread {
                            val ret = if (ok) {
                                this.fg_chain_join("@" + LOCAL.read { it.ids.first { it.nick == nick }.pub })
                                "Added identity $nick."
                            } else {
                                "Identity already exists."
                            }
                            Toast.makeText(
                                this.applicationContext,
                                ret,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    Toast.makeText(
                        this.applicationContext,
                        "Invalid password.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }

    fun ids_remove_ask (nick: String) {
        AlertDialog.Builder(this)
            .setTitle("Remove identity $nick?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(android.R.string.yes, { _, _ ->
                LOCAL.write(true) {
                    it.ids = it.ids.filter { it.nick != nick }
                }
                Toast.makeText(
                    this.applicationContext,
                    "Removed identity $nick.", Toast.LENGTH_LONG
                ).show()
            })
            .setNegativeButton(android.R.string.no, null).show()
    }

    ////////////////////////////////////////

    fun cts_add_ask (cb: ()->Unit) {
        val view = View.inflate(this, R.layout.frag_cts_add, null)
        AlertDialog.Builder(this)
            .setTitle("New contact:")
            .setView(view)
            .setNegativeButton ("Cancel", null)
            .setPositiveButton("OK") { _,_ ->
                val nick = view.findViewById<EditText>(R.id.edit_nick).text.toString()
                val pub  = view.findViewById<EditText>(R.id.edit_pub) .text.toString()

                val ok = LOCAL.write_tst(true) {
                    if (it.cts.none { it.nick==nick || it.pub==pub }) {
                        it.cts += Id(nick, pub)
                        true
                    } else {
                        false
                    }
                }
                val ret =
                    if (ok) {
                        this.fg_chain_join("@" + pub)
                        "Added contact $nick."
                    } else {
                        "Contact already exists."
                    }
                Toast.makeText(
                    this.applicationContext,
                    ret,
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }

    fun cts_remove_ask (nick: String) {
        AlertDialog.Builder(this)
            .setTitle("Remove contact $nick?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(android.R.string.yes, { _, _ ->
                LOCAL.write(true) {
                    it.cts = it.cts.filter { it.nick != nick }
                }
                Toast.makeText(
                    this.applicationContext,
                    "Removed contact $nick.", Toast.LENGTH_LONG
                ).show()
            })
            .setNegativeButton(android.R.string.no, null).show()
    }
}
