package com.app.mediaplayer

import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.bumptech.glide.Glide
import java.io.File
import kotlin.concurrent.thread

class Mp3ConverterActivity : AppCompatActivity() {

    private lateinit var btnStartConversion: Button
    private lateinit var progressConverter: ProgressBar
    private lateinit var tvConvertStatus: TextView
    private lateinit var imgConvertThumbnail: ImageView
    private lateinit var tvConvertVideoTitle: TextView

    private var videoPath: String = ""
    private var videoTitle: String = ""
    private var isConverting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mp3_converter)

        videoPath = intent.getStringExtra("VIDEO_PATH") ?: ""
        videoTitle = intent.getStringExtra("VIDEO_TITLE") ?: "Unknown_Video"

        initViews()
        setupData()
    }

    private fun initViews() {
        btnStartConversion = findViewById(R.id.btnStartConversion)
        progressConverter = findViewById(R.id.progressConverter)
        tvConvertStatus = findViewById(R.id.tvConvertStatus)
        imgConvertThumbnail = findViewById(R.id.imgConvertThumbnail)
        tvConvertVideoTitle = findViewById(R.id.tvConvertVideoTitle)

        findViewById<ImageView>(R.id.btnBackConverter).setOnClickListener {
            if (isConverting) {
                Toast.makeText(this, "Conversion in progress. Please wait.", Toast.LENGTH_SHORT).show()
            } else {
                finish()
            }
        }

        btnStartConversion.setOnClickListener {
            if (!isConverting) {
                startConversion()
            }
        }
    }

    private fun setupData() {
        tvConvertVideoTitle.text = videoTitle
        Glide.with(this)
            .load(videoPath)
            .placeholder(android.R.color.darker_gray)
            .centerCrop()
            .into(imgConvertThumbnail)
    }

    private fun startConversion() {
        if (videoPath.isEmpty()) {
            Toast.makeText(this, "Invalid video path", Toast.LENGTH_SHORT).show()
            return
        }

        isConverting = true
        btnStartConversion.isEnabled = false
        btnStartConversion.text = "CONVERTING..."
        progressConverter.visibility = View.VISIBLE
        tvConvertStatus.text = "Extracting Audio (MP3)... Please do not close."
        tvConvertStatus.setTextColor(android.graphics.Color.parseColor("#00FFFF"))

        thread {
            try {
                val musicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "MAX_MP3")
                if (!musicDir.exists()) {
                    musicDir.mkdirs()
                }

                val cleanTitle = videoTitle.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                val outputPath = File(musicDir, "${cleanTitle}_Audio.mp3").absolutePath
                val ffmpegCommand = "-y -i \"$videoPath\" -vn -ar 44100 -ac 2 -b:a 192k \"$outputPath\""

                val session = FFmpegKit.execute(ffmpegCommand)
                val returnCode = session.returnCode

                runOnUiThread {
                    isConverting = false
                    progressConverter.visibility = View.GONE
                    btnStartConversion.isEnabled = true

                    if (ReturnCode.isSuccess(returnCode)) {
                        tvConvertStatus.text = "Success! Saved to Music/MAX_MP3/"
                        tvConvertStatus.setTextColor(android.graphics.Color.GREEN)
                        btnStartConversion.text = "CONVERSION COMPLETE"
                        Toast.makeText(this@Mp3ConverterActivity, "Audio Saved Successfully!", Toast.LENGTH_LONG).show()
                    } else {
                        tvConvertStatus.text = "Conversion Failed! Error Code: ${returnCode.value}"
                        tvConvertStatus.setTextColor(android.graphics.Color.RED)
                        btnStartConversion.text = "RETRY CONVERSION"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    isConverting = false
                    progressConverter.visibility = View.GONE
                    btnStartConversion.isEnabled = true
                    tvConvertStatus.text = "Critical Error Occurred"
                    tvConvertStatus.setTextColor(android.graphics.Color.RED)
                    btnStartConversion.text = "RETRY CONVERSION"
                }
            }
        }
    }
}
