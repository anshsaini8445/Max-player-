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

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (20 * d).toInt(), (20 * d).toInt(), (18 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0A0A10"))
                cornerRadius = 24f * d
                setStroke((1.5f * d).toInt(), Color.WHITE)
            }
        }

        val dialog = AlertDialog.Builder(context).setView(card).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (10 * d).toInt())
        }

        val tvTitle = TextView(context).apply {
            text = "8K / 4K Network Stream Player"
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnClose = TextView(context).apply {
            text = "X"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((32 * d).toInt(), (32 * d).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#28FFFFFF"))
            }
            setOnClickListener { dialog.dismiss() }
        }
        headerRow.addView(tvTitle)
        headerRow.addView(btnClose)
        card.addView(headerRow)

        val tvSub = TextView(context).apply {
            text = "Enter or paste any direct video link (HTTP, HTTPS, M3U8 Live Stream, MP4, MKV, DASH):"
            setTextColor(Color.parseColor("#B0C4DE"))
            textSize = 12.5f
            setPadding(0, 0, 0, (12 * d).toInt())
        }
        card.addView(tvSub)

        val etStreamUrl = EditText(context).apply {
            hint = "https://example.com/stream.m3u8"
            setHintTextColor(Color.parseColor("#7F92A8"))
            setTextColor(Color.WHITE)
            textSize = 13.5f
            setSingleLine(true)
            setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141E30"))
                cornerRadius = 14f * d
                setStroke((1.2f * d).toInt(), Color.WHITE)
            }
        }
        card.addView(etStreamUrl)

        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (14 * d).toInt() }
        }

        val btnPasteClipboard = TextView(context).apply {
            text = "Paste URL"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f).apply {
                marginEnd = (6 * d).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#24FFFFFF"))
                cornerRadius = 14f * d
                setStroke((1 * d).toInt(), Color.WHITE)
            }
            setOnClickListener {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty().trim()
                if (clip.isNotEmpty()) {
                    etStreamUrl.setText(clip)
                } else {
                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnPlayStream = TextView(context).apply {
            text = "PLAY STREAM NOW"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f).apply {
                marginStart = (6 * d).toInt()
            }
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 14f * d
            }
            setOnClickListener {
                val url = etStreamUrl.text.toString().trim()
                if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("rtmp://")) {
                    Toast.makeText(context, "Please enter a valid http:// or https:// stream URL", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                val intent = Intent(context, PlayerActivity::class.java).apply {
                    putExtra("VIDEO_PATH", url)
                    putExtra("VIDEO_TITLE", "Network Stream")
                    addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                context.startActivity(intent)
            }
        }

        buttonsRow.addView(btnPasteClipboard)
        buttonsRow.addView(btnPlayStream)
        card.addView(buttonsRow)

        dialog.show()
    }
}
