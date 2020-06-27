package org.freechains.android.dashboard

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.freechains.cli.main_cli
import org.freechains.cli.main_cli_assert
import kotlin.concurrent.thread

data class XPeer (
    val name   : String,
    var ping   : String = "?",
    var chains : List<String> = emptyList()
)

class PeersFragment : Fragment ()
{
    private val outer = this
    private lateinit var main: MainActivity
    private lateinit var data: List<XPeer>

    fun bg_reload () {
        val ret = mutableListOf<XPeer>()
        this.main.bg (
            {
                this.main.boot.peers
                    .map {
                        thread {
                            val ms = main_cli(arrayOf("peer", it, "ping")).let {
                                if (!it.first) "down" else it.second + "ms"
                            }
                            val chains = main_cli(arrayOf("peer", it, "chains")).let {
                                if (!it.first || it.second.isEmpty()) emptyList() else it.second.split(' ')
                            }
                            ret.add(XPeer(it, ms, chains))
                        }
                    }
                    .map { it.join() }
            },
            {
                this.data = ret
                this.adapter.notifyDataSetChanged()
            }
        )
    }

    override fun onCreateView (inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        this.main = this.activity as MainActivity
        this.data = emptyList()
        this.bg_reload()

        inflater.inflate(R.layout.frag_peers, container, false).let { view ->
            view.findViewById<ExpandableListView>(R.id.list).let {
                it.setAdapter(this.adapter)
                it.setOnItemLongClickListener { _,view,_,_ ->
                    if (view is LinearLayout && view.tag is String) {
                        val peer = view.tag.toString()
                        AlertDialog.Builder(this.main)
                            .setTitle("Remove peer $peer?")
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setPositiveButton(android.R.string.yes, { _, _ ->
                                this.main.fg {
                                    main_cli_assert(arrayOf("chain", BOOT, "post", "inline", "peers rem $peer"))
                                }
                                Toast.makeText(
                                    this.main.applicationContext,
                                    "Removed peer $peer.", Toast.LENGTH_LONG
                                ).show()
                            })
                            .setNegativeButton(android.R.string.no, null).show()
                        true
                    } else {
                        false
                    }
                }
            }
            view.findViewById<FloatingActionButton>(R.id.but_add).let {
                it.setOnClickListener {
                    val input = EditText(this.main)
                    input.inputType = InputType.TYPE_CLASS_TEXT
                    AlertDialog.Builder(this.main)
                        .setTitle("Add peer:")
                        .setView(input)
                        .setNegativeButton ("Cancel", null)
                        .setPositiveButton("OK") { _,_ ->
                            val peer = input.text.toString()
                            this.main.fg {
                                main_cli_assert(arrayOf("chain", BOOT, "post", "inline", "peers add $peer"))
                            }
                            Toast.makeText(
                                    this.main.applicationContext,
                                    "Added peer $peer.",
                                    Toast.LENGTH_SHORT
                            ).show()
                        }
                        .show()
                }
            }
            return view
        }
    }

    private val adapter = object : BaseExpandableListAdapter() {
        override fun hasStableIds(): Boolean {
            return false
        }
        override fun isChildSelectable (i: Int, j: Int): Boolean {
            return true
        }
        override fun getChild (i: Int, j: Int): Any? {
            return outer.data[i].chains[j]
        }
        override fun getChildId (i: Int, j: Int): Long {
            return i*10+j.toLong()
        }
        override fun getChildView (i: Int, j: Int, isLast: Boolean,
                                   convertView: View?, parent: ViewGroup?): View? {
            val view = View.inflate(outer.main, R.layout.frag_peers_chain,null)
            val chain = outer.data[i].chains[j]
            view.findViewById<TextView>(R.id.chain).text = chain.chain2id()
            if (!LOCAL.read { it.chains.any { it.name == outer.data[i].chains[j] } }) {
                view.findViewById<ImageButton>(R.id.add).let {
                    it.visibility = View.VISIBLE
                    it.setOnClickListener {
                        if (chain.startsWith('$')) {
                            outer.main.chains_join_ask(chain) {
                                outer.bg_reload()
                            }
                        } else {
                            outer.main.fg_chain_join(chain)
                        }
                    }
                }
            }
            return view
        }
        override fun getChildrenCount (i: Int): Int {
            return outer.data[i].chains.size
        }
        override fun getGroupCount(): Int {
            return outer.data.size
        }
        override fun getGroup (i: Int): Any {
            return outer.data[i]
        }
        override fun getGroupId (i: Int): Long {
            return i.toLong()
        }
        override fun getGroupView (i: Int, isExpanded: Boolean, convertView: View?, parent: ViewGroup?): View? {
            val view = View.inflate(outer.main, R.layout.frag_peers_host,null)
            view.findViewById<TextView>(R.id.ping).text = outer.data[i].ping
            view.findViewById<TextView>(R.id.host).text = outer.data[i].name
            view.tag = outer.data[i].name
            return view
        }
    }
}