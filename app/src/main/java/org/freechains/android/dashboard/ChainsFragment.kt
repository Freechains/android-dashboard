package org.freechains.android.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.ExpandableListView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton

class ChainsFragment : Fragment ()
{
    private val outer = this
    private lateinit var main: MainActivity

    private var data: List<Chain> = LOCAL.read { it.chains }
    private val cb = {
        this.data = LOCAL.read { it.chains }
        this.adapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        this.main.adapters.remove(this.cb)
        super.onDestroyView()
    }

    override fun onCreateView (inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        this.main = this.activity as MainActivity
        this.main.adapters.add(this.cb)
        inflater.inflate(R.layout.frag_chains, container, false).let { view ->
            view.findViewById<ExpandableListView>(R.id.list).let {
                it.setAdapter(this.adapter)
                it.setOnItemLongClickListener { _,view,_,_ ->
                    if (view is LinearLayout && view.tag is String) {
                        this.main.chains_leave_ask(view.tag.toString())
                        true
                    } else {
                        false
                    }
                }
            }
            view.findViewById<FloatingActionButton>(R.id.but_join).let {
                it.setOnClickListener {
                    this.main.chains_join_ask()
                }
            }
            return view
        }
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
            view.findViewById<TextView>(android.R.id.text1).text = block.block2id()

            view.setOnLongClickListener {
                outer.main.chain_get(chain.name, "block", block)
                true
            }
            view.setOnClickListener {
                outer.main.chain_get(chain.name, "payload", block)
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
            view.findViewById<TextView>(R.id.chain).text = chain.name.chain2id()
            view.findViewById<TextView>(R.id.heads).text = chain.heads
                .map { it.block2id() }
                .joinToString("\n")
            view.tag = chain.name
            return view
        }
    }
}