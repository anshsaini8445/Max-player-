package com.app.mediaplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject
import kotlin.concurrent.thread

object AudioLyricsHelper {

    fun loadLyricsForSong(
        context: Context,
        audioPath: String,
        songTitle: String,
        songArtist: String,
        onResult: (String) -> Unit
    ) {
        val prefs = context.getSharedPreferences("MAX_LYRICS_CACHE", Context.MODE_PRIVATE)
        val cached = prefs.getString("lyrics_$audioPath", null)
        if (!cached.isNullOrEmpty()) {
            onResult(cached)
            return
        }

        // 1. Check local .lrc or .txt file next to the audio file
        try {
            val audioFile = File(audioPath)
            val parent = audioFile.parentFile
            val baseName = audioFile.nameWithoutExtension
            if (parent != null && parent.exists()) {
                val lrcFile = File(parent, "$baseName.lrc")
                val txtFile = File(parent, "$baseName.txt")
                val targetFile = when {
                    lrcFile.exists() -> lrcFile
                    txtFile.exists() -> txtFile
                    else -> null
                }
                if (targetFile != null && targetFile.canRead()) {
                    val rawText = targetFile.readText()
                    val cleaned = rawText.replace(Regex("\\[\\d{2}:\\d{2}(\\.\\d{1,3})?]"), "").trim()
                    if (cleaned.isNotEmpty()) {
                        prefs.edit().putString("lyrics_$audioPath", cleaned).apply()
                        onResult(cleaned)
                        return
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Try automatic online lyrics lookup in background
        val cleanTitle = songTitle
            .substringBeforeLast(".")
            .replace("_", " ")
            .replace(Regex("\\(.*?\\)|\\[.*?]"), "")
            .trim()
        val cleanArtist = if (songArtist.equals("Unknown Artist", true)) "" else songArtist.trim()

        thread {
            var fetchedLyrics = ""
            try {
                if (cleanArtist.isNotEmpty() && cleanTitle.isNotEmpty()) {
                    val encodedArtist = URLEncoder.encode(cleanArtist, "UTF-8")
                    val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")
                    val apiUrl = "https://api.lyrics.ovh/v1/$encodedArtist/$encodedTitle"
                    val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 4500
                        readTimeout = 4500
                        requestMethod = "GET"
                    }
                    if (conn.responseCode == 200) {
                        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(responseText)
                        fetchedLyrics = json.optString("lyrics", "").trim()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (fetchedLyrics.isNotEmpty()) {
                prefs.edit().putString("lyrics_$audioPath", fetchedLyrics).apply()
                onResult(fetchedLyrics)
            } else {
                val fallbackText = "♪ $cleanTitle ♪\n" +
                    (if (cleanArtist.isNotEmpty()) "Artist: $cleanArtist\n\n" else "\n") +
                    "No local .lrc file found for this track.\n" +
                    "Tap 'Search Lyrics Online' below to view full lyrics."
                onResult(fallbackText)
            }
        }
    }

    fun openOnlineLyricsSearch(context: Context, songTitle: String, songArtist: String) {
        try {
            val cleanTitle = songTitle.substringBeforeLast(".").replace("_", " ")
            val queryStr = if (songArtist.equals("Unknown Artist", true)) {
                "$cleanTitle song lyrics"
            } else {
                "$cleanTitle$songArtist lyrics"
            }
            val encoded = URLEncoder.encode(queryStr, "UTF-8")
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=$encoded")
            )
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
