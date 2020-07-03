package org.freechains.android.dashboard

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.android.synthetic.main.frag_ids_id.*
import org.freechains.cli.main_cli_assert
import org.freechains.common.listSplit
import kotlin.concurrent.thread

class IdsFragment : Fragment ()
{
    private val outer = this
    private lateinit var main: MainActivity
    private lateinit var data: List<Pair<String,String>>

    fun reload () {
        this.data = this.main.store.data["ids"].let {
            if (it == null) {
                emptyList()
            } else {
                it.toList()
            }
        }
        this.adapter.notifyDataSetChanged()
    }

    override fun onCreateView (inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        this.main = this.activity as MainActivity
        this.reload()

        inflater.inflate(R.layout.frag_ids, container, false).let { view ->
            view.findViewById<ExpandableListView>(R.id.list).let {
                it.setAdapter(this.adapter)
                it.setOnItemLongClickListener { _,view,_,_ ->
                    if (view is LinearLayout && view.tag is String) {
                        val pub = view.tag.toString()
                        this.main.rem_ask("identity", pub) {
                            this.main.fg {
                                this.main.store.store("ids", pub, "REM") // todo nao deveria sync
                                this.main.store.store("chains", "@"+pub, "REM")
                            }
                            this.reload()
                        }
                        true
                    } else {
                        false
                    }
                }
            }
            view.findViewById<FloatingActionButton>(R.id.but_add).let {
                it.setOnClickListener {
                    val view = View.inflate(this.main, R.layout.frag_ids_add, null)
                    AlertDialog.Builder(this.main)
                        .setTitle("New identity:")
                        .setView(view)
                        .setNegativeButton ("Cancel", null)
                        .setPositiveButton("OK") { _,_ ->
                            val nick  = view.findViewById<EditText>(R.id.edit_nick).text.toString()
                            val pass1 = view.findViewById<EditText>(R.id.edit_pass1).text.toString()
                            val pass2 = view.findViewById<EditText>(R.id.edit_pass2).text.toString()
                            if (pass1.length>=LEN20_pubpbt && pass1==pass2) {
                                this.main.fg {
                                    val pub = main_cli_assert(arrayOf("crypto", "pubpvt", pass1)).split(' ')[0]
                                    val ok = this.data.none { it.first==pub || it.second==nick }
                                    if (ok) {
                                        this.main.store.store("ids", pub, nick)
                                        this.main.store.store("chains", "@" + pub, "ADD")
                                        this.main.runOnUiThread {
                                            this.reload()
                                            this.main.showToast("Added identity $nick.")
                                        }
                                    } else {
                                        this.main.runOnUiThread {
                                            this.main.showToast("Identity already exists.")
                                        }
                                    }
                                }
                            } else {
                                this.main.showToast("Invalid password.")
                            }
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
            return outer.data[i]
        }
        override fun getChildId (i: Int, j: Int): Long {
            return i*10+j.toLong()
        }
        override fun getChildView (i: Int, j: Int, isLast: Boolean,
                                   convertView: View?, parent: ViewGroup?): View? {
            val view = View.inflate(outer.main, android.R.layout.simple_list_item_1,null)
            view.findViewById<TextView>(android.R.id.text1).text = outer.data[i].first
            return view
        }
        override fun getChildrenCount (i: Int): Int {
            return 1
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
            val view = View.inflate(outer.main, R.layout.frag_ids_id,null)
            view.findViewById<TextView>(R.id.nick).text = outer.data[i].second
            view.findViewById<TextView>(R.id.pub) .text = outer.data[i].first.pub2id()
            view.tag = outer.data[i].first
            return view
        }
    }
}