package org.freechains.android.dashboard

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.freechains.cli.main_cli_assert
import org.freechains.common.listSplit

data class Chain (
    val name   : String,
    val heads  : List<String>,
    val blocks : List<String>
)

class ChainsFragment : Fragment ()
{
    private val outer = this
    private lateinit var main: MainActivity
    private lateinit var data: List<Chain>

    fun fg_reload () {
        this.data = this.main.fg {
            this.main.store.getKeys("chains")
                .map { chain ->
                    //println(">>> $chain")
                    val heads= main_cli_assert(arrayOf("chain", chain, "heads", "all")).split(' ')
                    val gen= main_cli_assert(arrayOf("chain", chain, "genesis"))
                    val blocks= main_cli_assert(arrayOf("chain", chain, "traverse", "all", gen)).listSplit().reversed().plus(gen)
                    Chain(chain, heads, blocks)
                }
        }
        this.adapter.notifyDataSetChanged()
    }

    override fun onCreateView (inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        this.main = this.activity as MainActivity
        this.fg_reload()

        inflater.inflate(R.layout.frag_chains, container, false).let { view ->
            view.findViewById<ExpandableListView>(R.id.list).let {
                it.setAdapter(this.adapter)
                it.setOnItemLongClickListener { _,view,_,_ ->
                    if (view is LinearLayout && view.tag is String) {
                        val chain = view.tag.toString()
                        this.main.rem_ask("chain", chain) {
                            this.main.fg {
                                this.main.store.store("chains", chain, "REM")
                            }
                            this.fg_reload()
                        }
                        true
                    } else {
                        false
                    }
                }
            }
            view.findViewById<FloatingActionButton>(R.id.but_join).let {
                it.setOnClickListener {
                    this.main.chains_join_ask() {
                        this.fg_reload()
                    }
                }
            }
            return view
        }
    }

    fun fg_chain_get (chain: String, mode: String, block: String) {
        val pay = this.main.fg {
            main_cli_assert(arrayOf("chain", chain, "get", mode, block)).take(LEN1000_pay)
        }
        AlertDialog.Builder(this.main)
            .setTitle("Block ${block.block2out()}:")
            //.setView(msg)
            .setMessage(pay)
            .show()
    }

    private val adapter = object : BaseExpandableListAdapter () {
        override fun hasStableIds(): Boolean {
            return false
        }
        override fun isChildSelectable (i: Int, j: Int): Boolean {
            return true
        }
        override fun getChild (i: Int, j: Int): Any? {
            return outer.data[i].blocks[j]
        }
        override fun getChildId (i: Int, j: Int): Long {
            return i*10+j.toLong()
        }
        override fun getChildView (i: Int, j: Int, isLast: Boolean,
                                   convertView: View?, parent: ViewGroup?): View? {
            val chain = outer.data[i]
            val block = chain.blocks[j]
            val view = View.inflate(outer.main, android.R.layout.activity_list_item,null)
            view.findViewById<TextView>(android.R.id.text1).text = block.block2out()

            view.setOnLongClickListener {
                outer.fg_chain_get(chain.name, "block", block)
                true
            }
            view.setOnClickListener {
                outer.fg_chain_get(chain.name, "payload", block)
            }
            return view
        }
        override fun getChildrenCount (i: Int): Int {
            return outer.data[i].blocks.size
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
            val view = View.inflate(outer.main, R.layout.frag_chains_chain,null)
            val chain = outer.data[i]
            view.findViewById<TextView>(R.id.chain).text = outer.main.chain2out(chain.name)
            view.findViewById<TextView>(R.id.heads).text = chain.heads
                .map { it.block2out() }
                .joinToString("\n")
            view.tag = chain.name
            return view
        }
    }
}