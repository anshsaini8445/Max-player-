package com.app.mediaplayer

import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.concurrent.thread

class MusicFragment : Fragment() {

    private lateinit var rvAudioList: RecyclerView
    private lateinit var audioAdapter: AudioAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_music, container, false)
        rvAudioList = view.findViewById(R.id.rvAudioList)
        rvAudioList.layoutManager = LinearLayoutManager(requireContext())
        loadAudioSafely()
        return view
    }

    private fun loadAudioSafely() {
        thread {
            try {
                val audioList = fetchAllAudio()
                activity?.runOnUiThread {
                    if (!isAdded || context == null) return@runOnUiThread
                    
                    if (audioList.isEmpty()) {
                        Toast.makeText(requireContext(), "No audio found", Toast.LENGTH_SHORT).show()
                    } else {
                        audioAdapter = AudioAdapter(requireContext(), audioList)
                        rvAudioList.adapter = audioAdapter
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchAllAudio(): List<AudioModel> {
        val audios = mutableListOf<AudioModel>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )
        
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        val contentResolver = context?.contentResolver ?: return audios
        
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Unknown"
                val path = cursor.getString(dataCol) ?: ""
                val artist = cursor.getString(artistCol) ?: "<unknown>"
                val duration = cursor.getLong(durCol)
                audios.add(AudioModel(id, title, path, artist, duration))
            }
        }
        return audios
    }
}
