package com.app.mediaplayer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment

class MeFragment : Fragment() {
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_me, container, false)
        
        val btnPrivacyVault = view.findViewById<LinearLayout>(R.id.btnPrivacyVault)
        
        btnPrivacyVault.setOnClickListener {
            if (context != null) {
                val intent = Intent(requireContext(), VaultPinActivity::class.java)
                startActivity(intent)
            }
        }
        
        return view
    }
}
