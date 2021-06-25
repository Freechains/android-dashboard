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
import org.freechains.common.listSplit
import kotlin.concurrent.thread

data class Peer (
    val name   : String,
    var ping   : String = "?",
    var chains : List<String> = emptyList()
)

class PeersFragment : Fragment ()
{
    private val outer = this
    private lateinit var main:   MainActivity
    private lateinit var peers:  List<Peer>

    fun bg_reload () {
        val ret = mutableListOf<Peer>()
        this.main.bg (
            {
                this.main.local.peers
                    .map {
                        thread {
                            //println("ping " + it)
                            val ms = main_cli(arrayOf("peer", it, "ping")).let {
                                if (!it.first) "down" else it.second + "ms"
                            }
                            //println("ms " + ms)
                            val chains = main_cli(arrayOf("peer", it, "chains")).let {
                                if (!it.first || it.second.isEmpty()) emptyList() else it.second.split(' ')
                            }
                            ret.add(Peer(it, ms, chains))
                        }
                    }
                    .map { it.join() }
            },
            {
                this.peers = ret
                this.adapter.notifyDataSetChanged()
            }
        )
    }

    override fun onCreateView (inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        this.main = this.activity as MainActivity
        this.peers = emptyList()
        this.bg_reload()

        inflater.inflate(R.layout.frag_peers, container, false).let { view ->
            view.findViewById<ExpandableListView>(R.id.list).let {
                it.setAdapter(this.adapter)
                it.setOnItemLongClickListener { _,view,_,_ ->
                    if (view is LinearLayout && view.tag is String) {
                        val peer = view.tag.toString()
                        this.main.rem_ask("peer", peer) {
                            this.main.fg {
                                this.main.local.peers.remove(peer)
                                this.main.local.fsSave()
                            }
                            this.bg_reload()
                        }
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
                                this.main.local.peers.add(peer)
                                this.main.local.fsSave()
                            }
                            this.bg_reload()
                            this.main.showToast("Added peer $peer.")
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
            return outer.peers[i].chains[j]
        }
        override fun getChildId (i: Int, j: Int): Long {
            return i*10+j.toLong()
        }
        override fun getChildView (i: Int, j: Int, isLast: Boolean,
                                   convertView: View?, parent: ViewGroup?): View? {
            val view = View.inflate(outer.main, R.layout.frag_peers_chain,null)
            val chain = outer.peers[i].chains[j]
            view.findViewById<TextView>(R.id.chain).text = chain.chain2out()
            // why was this here? if cannot find the answer, remove it (03/07/20)
            //if (outer.main.store.getKeys("chains").none { it == outer.peers[i].chains[j] }) {
                view.findViewById<ImageButton>(R.id.add).let {
                    outer.main.fg {
                        val chains = main_cli_assert(arrayOf("chains", "list")).listSplit()
                        it.visibility = if (chains.contains(chain)) View.INVISIBLE else View.VISIBLE
                        it.setOnClickListener {
                            if (chain.startsWith('@')) {
                                outer.main.fg_chain_join(chain, emptyList(), null)
                                outer.bg_reload()
                            } else {
                                outer.main.chains_join_ask(chain) {
                                    outer.bg_reload()
                                }
                            }
                        }
                    }
                }
            //}
            return view
        }
        override fun getChildrenCount (i: Int): Int {
            return outer.peers[i].chains.size
        }
        override fun getGroupCount(): Int {
            return outer.peers.size
        }
        override fun getGroup (i: Int): Any {
            return outer.peers[i]
        }
        override fun getGroupId (i: Int): Long {
            return i.toLong()
        }
        override fun getGroupView (i: Int, isExpanded: Boolean, convertView: View?, parent: ViewGroup?): View? {
            val view = View.inflate(outer.main, R.layout.frag_peers_host,null)
            view.findViewById<TextView>(R.id.ping).text = outer.peers[i].ping
            view.findViewById<TextView>(R.id.host).text = outer.peers[i].name
            view.tag = outer.peers[i].name
            return view
        }
    }
}