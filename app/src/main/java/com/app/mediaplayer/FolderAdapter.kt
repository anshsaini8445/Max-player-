package com.app.mediaplayer

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FolderAdapter(private val context: Context, private val folderList: List<FolderModel>) :
    RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvFolderName: TextView = view.findViewById(R.id.tvFolderName)
        val tvVideoCount: TextView = view.findViewById(R.id.tvVideoCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_folder, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = folderList[position]
        holder.tvFolderName.text = folder.folderName
        holder.tvVideoCount.text = "${folder.videoCount} Videos"

        holder.itemView.setOnClickListener {
            val intent = Intent(context, FolderVideosActivity::class.java)
            intent.putExtra("BUCKET_ID", folder.bucketId)
            intent.putExtra("FOLDER_NAME", folder.folderName)
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = folderList.size
}
