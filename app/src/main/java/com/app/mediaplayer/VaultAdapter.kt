package com.app.mediaplayer

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class VaultAdapter(
    private val context: Context, 
    private val vaultList: MutableList<VaultModel>,
    private val onEmptyList: () -> Unit
) : RecyclerView.Adapter<VaultAdapter.VaultViewHolder>() {

    class VaultViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvVaultTitle: TextView = view.findViewById(R.id.tvVaultTitle)
        val btnRestore: ImageView = view.findViewById(R.id.btnRestore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VaultViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_vault_video, parent, false)
        return VaultViewHolder(view)
    }

    override fun onBindViewHolder(holder: VaultViewHolder, position: Int) {
        val vaultVideo = vaultList[position]
        holder.tvVaultTitle.text = vaultVideo.title

        holder.itemView.setOnClickListener {
            // Cannot play encrypted file directly in normal player.
            // Would require decryption buffer, for now notify user.
            Toast.makeText(context, "Restore video to play it.", Toast.LENGTH_SHORT).show()
        }

        holder.btnRestore.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("Restore Video")
                .setMessage("Do you want to remove this video from the Vault and restore it to the Gallery?")
                .setPositiveButton("Restore") { _, _ ->
                    val success = VaultHelper.restoreVideo(context, vaultVideo)
                    if (success) {
                        Toast.makeText(context, "Video Restored!", Toast.LENGTH_SHORT).show()
                        vaultList.removeAt(position)
                        notifyItemRemoved(position)
                        notifyItemRangeChanged(position, vaultList.size)
                        
                        if (vaultList.isEmpty()) {
                            onEmptyList.invoke()
                        }
                    } else {
                        Toast.makeText(context, "Failed to restore video.", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun getItemCount(): Int = vaultList.size
}
