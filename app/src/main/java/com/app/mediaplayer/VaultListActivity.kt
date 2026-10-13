package com.app.mediaplayer

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.concurrent.thread

class VaultListActivity : AppCompatActivity() {

    private lateinit var rvVaultVideos: RecyclerView
    private lateinit var llVaultEmpty: LinearLayout
    private lateinit var vaultAdapter: VaultAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vault_list)

        findViewById<ImageView>(R.id.btnBackVault).setOnClickListener { finish() }

        rvVaultVideos = findViewById(R.id.rvVaultVideos)
        llVaultEmpty = findViewById(R.id.llVaultEmpty)
        rvVaultVideos.layoutManager = LinearLayoutManager(this)

        loadVaultFilesSafely()
    }

    private fun loadVaultFilesSafely() {
        thread {
            try {
                val videos = VaultHelper.getVaultVideos(this)
                runOnUiThread {
                    if (videos.isEmpty()) {
                        showEmptyState()
                    } else {
                        rvVaultVideos.visibility = View.VISIBLE
                        llVaultEmpty.visibility = View.GONE
                        
                        vaultAdapter = VaultAdapter(this, videos.toMutableList()) {
                            showEmptyState()
                        }
                        rvVaultVideos.adapter = vaultAdapter
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showEmptyState() {
        rvVaultVideos.visibility = View.GONE
        llVaultEmpty.visibility = View.VISIBLE
    }
}
