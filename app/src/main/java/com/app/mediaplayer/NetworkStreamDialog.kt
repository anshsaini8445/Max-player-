package com.app.mediaplayer

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
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
            setPadding((20 * d).toInt(), (20 * d).toInt(), (20 * d).toInt(), (18 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0A0A10"))
                cornerRadius = 24f * d
                setStroke((1.8f * d).toInt(), Color.parseColor("#00FFFF"))
            }
        }

        val dialog = AlertDialog.Builder(context).setView(root).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = TextView(context).apply {
            text = "🌐  8K Cloud & Network Link Streamer"
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (6 * d).toInt())
        }

        val tvSub = TextView(context).apply {
            text = "Paste any direct Video/Audio URL (MP4, MKV, M3U8, Live Stream, Cloud Link) to play in 8K without downloading:"
            setTextColor(Color.parseColor("#B0C4DE"))
            textSize = 12.5f
            setPadding(0, 0, 0, (12 * d).toInt())
        }

        val etUrl = EditText(context).apply {
            setText(lastUrl)
            hint = "https://example.com/movie.mp4 or .m3u8"
            setHintTextColor(Color.parseColor("#6C7A89"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141824"))
                cornerRadius = 14f * d
                setStroke((1.2f * d).toInt(), Color.parseColor("#00FFFF"))
            }
        }

        val btnPasteClipboard = TextView(context).apply {
            text = "📋  Paste Link from Clipboard"
            setTextColor(Color.parseColor("#00FFFF"))
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((4 * d).toInt(), (10 * d).toInt(), (4 * d).toInt(), (10 * d).toInt())
            setOnClickListener {
                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clipText = cb?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                if (!clipText.isNullOrEmpty()) {
                    etUrl.setText(clipText)
                } else {
                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, (10 * d).toInt(), 0, 0)
        }

        val btnCancel = TextView(context).apply {
            text = "Cancel"
            setTextColor(Color.WHITE)
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((18 * d).toInt(), (10 * d).toInt(), (18 * d).toInt(), (10 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#22FFFFFF"))
                cornerRadius = 16f * d
            }
            setOnClickListener { dialog.dismiss() }
        }

        val btnStream = TextView(context).apply {
            text = "▶  Stream in 8K Player"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((18 * d).toInt(), (10 * d).toInt(), (18 * d).toInt(), (10 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (10 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00FFFF"))
                cornerRadius = 16f * d
            }
            setOnClickListener {
                val url = etUrl.text.toString().trim()
                if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("rtmp://")) {
                    prefs.edit().putString("last_stream_url", url).apply()
                    dialog.dismiss()
                    val intent = Intent(context, PlayerActivity::class.java).apply {
                        putExtra("VIDEO_PATH", url)
                        putExtra("VIDEO_TITLE", "Network Stream")
                    }
                    context.startActivity(intent)
                } else {
                    Toast.makeText(context, "Please enter a valid http/https stream link", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnRow.addView(btnCancel)
        btnRow.addView(btnStream)

        root.addView(tvTitle)
        root.addView(tvSub)
        root.addView(etUrl)
        root.addView(btnPasteClipboard)
        root.addView(btnRow)

        dialog.show()
    }
}
