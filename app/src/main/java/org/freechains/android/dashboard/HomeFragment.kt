package org.freechains.android.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController


class HomeFragment : Fragment ()
{
    override fun onCreateView (inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
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
                (this.activity as MainActivity).peers_sync(true)
            }
            view.findViewById<ImageView>(R.id.but_reset).setOnClickListener { it ->
                (this.activity as MainActivity).host_recreate_ask()
            }
            return view
        }
    }
}