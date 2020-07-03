package org.freechains.android.dashboard

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import org.freechains.common.fsRoot
import java.io.File
import kotlin.concurrent.thread


class HomeFragment : Fragment ()
{
    private lateinit var main: MainActivity

    override fun onCreateView (inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        this.main = this.activity as MainActivity
        inflater.inflate(R.layout.frag_home, container, false).let { view ->
            view.findViewById<ImageView>(R.id.but_chains).setOnClickListener { it ->
                it.findNavController().navigate(R.id.nav_chains)
            }
            view.findViewById<ImageView>(R.id.but_peers).setOnClickListener { it ->
                it.findNavController().navigate(R.id.nav_peers)
            }
            view.findViewById<ImageView>(R.id.but_identity).setOnClickListener { it ->
                it.findNavController().navigate(R.id.nav_identity)
            }
            view.findViewById<ImageView>(R.id.but_contacts).setOnClickListener { it ->
                it.findNavController().navigate(R.id.nav_contacts)
            }
            view.findViewById<ImageView>(R.id.but_sync).setOnClickListener {
                thread { this.main.sync.sync_all() }
            }
            view.findViewById<ImageView>(R.id.but_reset).setOnClickListener { _ ->
                AlertDialog.Builder(this.main)
                    .setTitle("!!! Reset Freechains !!!")
                    .setMessage("Delete all data?")
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes, { _, _ ->
                        File(fsRoot!!, "/").deleteRecursively()
                        this.main.finishAffinity()
                        this.main.findViewById<View>(R.id.wait).visibility = View.VISIBLE
                        this.main.getWindow().setFlags (
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        );
                        this.main.showToast("Please, restart Freechains...")
                    })
                    .setNegativeButton(android.R.string.no, null).show()
            }
            return view
        }
    }
}