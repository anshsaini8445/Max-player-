package com.app.mediaplayer

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class VaultListActivity : AppCompatActivity() {

    private lateinit var rvVaultVideos: RecyclerView
    private lateinit var llVaultEmpty: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vault_list)

        findViewById<ImageView>(R.id.btnBackVault).setOnClickListener { finish() }

        rvVaultVideos = findViewById(R.id.rvVaultVideos)
        llVaultEmpty = findViewById(R.id.llVaultEmpty)

        rvVaultVideos.layoutManager = LinearLayoutManager(this)

        loadVaultFiles()
    }

    private fun loadVaultFiles() {
        try {
            val vaultDir = File(getExternalFilesDir(null), ".MAX_Vault")
            if (!vaultDir.exists()) {
                vaultDir.mkdirs()
            }

            val files = vaultDir.listFiles()
            if (files == null || files.isEmpty()) {
                rvVaultVideos.visibility = View.GONE
                llVaultEmpty.visibility = View.VISIBLE
            } else {
                rvVaultVideos.visibility = View.VISIBLE
                llVaultEmpty.visibility = View.GONE
                // Detailed List Adapter logic will be fully implemented in Step 8
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
