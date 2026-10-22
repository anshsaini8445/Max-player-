package com.app.mediaplayer

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

object NetworkStreamDialog {

    fun showNetworkStreamInputDialog(context: Context) {
        val d = context.resources.displayMetrics.density
        val prefs = context.getSharedPreferences("MAX_PLAYER_PREFS", Context.MODE_PRIVATE)
        val lastUrl = prefs.getString("last_stream_url", "") ?: ""

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (20 * d).toInt(), (20 * d).toInt(), (14 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#13263B"))
                cornerRadius = 22f * d
                setStroke((1.5f * d).toInt(), Color.parseColor("#5CE1E6"))
            }
        }

        val tvTitle = TextView(context).apply {
            text = "8K Cloud & Network Link Streamer"
            setTextColor(Color.parseColor("#5CE1E6"))
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (8 * d).toInt())
        }

        val tvSub = TextView(context).apply {
            text = "Paste any direct Video/Audio URL (MP4, MKV, M3U8, Live Stream, Cloud Link) to play without downloading:"
            setTextColor(Color.parseColor("#D0E8FF"))
            textSize = 12.5f
            setPadding(0, 0, 0, (12 * d).toInt())
        }

        val etUrl = EditText(context).apply {
            setText(lastUrl)
            hint = "https://example.com/movie.mp4 or .m3u8"
            setHintTextColor(Color.parseColor("#88A0B8"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0D1B2A"))
                cornerRadius = 14f * d
                setStroke((1 * d).toInt(), Color.parseColor("#5CE1E6"))
            }
        }

        root.addView(tvTitle)
        root.addView(tvSub)
        root.addView(etUrl)

        val dialog = AlertDialog.Builder(context)
            .setView(root)
            .setPositiveButton("Stream in 8K Player") { _, _ ->
                val url = etUrl.text.toString().trim()
                if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("rtmp://")) {
                    prefs.edit().putString("last_stream_url", url).apply()
                    val intent = Intent(context, PlayerActivity::class.java).apply {
                        putExtra("VIDEO_PATH", url)
                        putExtra("VIDEO_TITLE", "Network Stream")
                    }
                    context.startActivity(intent)
                } else {
                    Toast.makeText(context, "Please enter a valid http/https stream link", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }
}
