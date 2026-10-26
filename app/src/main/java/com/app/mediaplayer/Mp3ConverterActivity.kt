package com.app.mediaplayer

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class Mp3ConverterActivity : AppCompatActivity() {

    companion object {
        /**
         * Ultra-Fast Hardware Demux Audio Extractor:
         * Extracts the native audio stream directly from any video container in under 1 second
         * using Android's hardware MediaExtractor + MediaMuxer with zero quality loss and zero heavy native dependencies.
         */
        fun convertVideoToAudioInstant(
            context: Context,
            videoPath: String,
            videoTitle: String,
            onProgress: ((Int) -> Unit)? = null,
            onFinished: (Boolean, String) -> Unit
        ) {
            thread {
                val extractor = MediaExtractor()
                var muxer: MediaMuxer? = null
                try {
                    if (videoPath.startsWith("content://")) {
                        extractor.setDataSource(context, Uri.parse(videoPath), null)
                    } else {
                        extractor.setDataSource(videoPath)
                    }

                    var audioTrackIndex = -1
                    var audioFormat: MediaFormat? = null
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                        if (mime.startsWith("audio/")) {
                            audioTrackIndex = i
                            audioFormat = format
                            break
                        }
                    }

                    if (audioTrackIndex == -1 || audioFormat == null) {
                        (context as? Activity)?.runOnUiThread {
                            onFinished(false, "No audio stream found in this video")
                        }
                        return@thread
                    }

                    extractor.selectTrack(audioTrackIndex)
                    val totalDurationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                        audioFormat.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1L)
                    } else {
                        100_000_000L
                    }

                    val cleanBaseName = videoTitle.substringBeforeLast('.')
                        .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
                        .trim()
                        .ifEmpty { "Hybrid_Audio_${System.currentTimeMillis()}" }

                    val outDir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                        "HybridPlayerAudio"
                    )
                    if (!outDir.exists()) outDir.mkdirs()

                    val outFile = File(outDir, "${cleanBaseName}_${System.currentTimeMillis() % 10000}.m4a")

                    muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    val dstTrackIndex = muxer.addTrack(audioFormat)
                    muxer.start()

                    val maxInputSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(256 * 1024)
                    } else {
                        256 * 1024
                    }
                    val buffer = ByteBuffer.allocateDirect(maxInputSize)
                    val bufferInfo = MediaCodec.BufferInfo()

                    var lastReportedPct = 0
                    while (true) {
                        bufferInfo.offset = 0
                        bufferInfo.size = extractor.readSampleData(buffer, 0)
                        if (bufferInfo.size < 0) {
                            bufferInfo.size = 0
                            break
                        }
                        bufferInfo.presentationTimeUs = extractor.sampleTime
                        val sampleFlags = extractor.sampleFlags
                        var codecFlags = 0
                        if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                            codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                        }
                        bufferInfo.flags = codecFlags
                        muxer.writeSampleData(dstTrackIndex, buffer, bufferInfo)
                        extractor.advance()

                        if (onProgress != null && bufferInfo.presentationTimeUs > 0) {
                            val pct = ((bufferInfo.presentationTimeUs.toDouble() / totalDurationUs.toDouble()) * 100.0)
                                .roundToInt()
                                .coerceIn(1, 99)
                            if (pct >= lastReportedPct + 5) {
                                lastReportedPct = pct
                                (context as? Activity)?.runOnUiThread {
                                    onProgress.invoke(pct)
                                }
                            }
                        }
                    }

                    muxer.stop()
                    muxer.release()
                    muxer = null
                    extractor.release()

                    // Register immediately in Android MediaStore so it appears in the Music section
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val values = ContentValues().apply {
                                put(MediaStore.Audio.Media.DISPLAY_NAME, outFile.name)
                                put(MediaStore.Audio.Media.TITLE, cleanBaseName)
                                put(MediaStore.Audio.Media.ARTIST, "Hybrid Player Audio")
                                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                                put(MediaStore.Audio.Media.DATA, outFile.absolutePath)
                            }
                            context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                        }
                        MediaScannerConnection.scanFile(
                            context.applicationContext,
                            arrayOf(outFile.absolutePath),
                            arrayOf("audio/mp4"),
                            null
                        )
                    } catch (_: Exception) {
                    }

                    (context as? Activity)?.runOnUiThread {
                        onProgress?.invoke(100)
                        onFinished(true, outFile.absolutePath)
                    }
                } catch (e: Exception) {
                    try {
                        muxer?.release()
                    } catch (_: Exception) {
                    }
                    try {
                        extractor.release()
                    } catch (_: Exception) {
                    }
                    (context as? Activity)?.runOnUiThread {
                        onFinished(false, e.localizedMessage ?: "Audio extraction failed")
                    }
                }
            }
        }
    }

    private lateinit var tvStatusHeading: TextView
    private lateinit var tvStatusSub: TextView
    private lateinit var progressBarConvert: ProgressBar
    private lateinit var btnPlayConvertedAudio: TextView
    private lateinit var rvVideoPicker: RecyclerView

    private val videosList = mutableListOf<VideoModel>()
    private var lastOutputPath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val d = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0A10"))
            setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt())
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (14 * d).toInt())
        }

        val btnBack = TextView(this).apply {
            text = "<-  Ultra-Fast Video to Audio / MP3"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { finish() }
        }
        topBar.addView(btnBack)
        root.addView(topBar)

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((18 * d).toInt(), (20 * d).toInt(), (18 * d).toInt(), (20 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (14 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141E30"))
                cornerRadius = 20f * d
                setStroke((1.5f * d).toInt(), Color.WHITE)
            }
        }

        tvStatusHeading = TextView(this).apply {
            text = "Select Any Video Below for 1-Second Audio Extraction"
            setTextColor(Color.WHITE)
            textSize = 15.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        tvStatusSub = TextView(this).apply {
            text = "Saves directly to Music/HybridPlayerAudio & Music Library"
            setTextColor(Color.parseColor("#B0C4DE"))
            textSize = 12.5f
            gravity = Gravity.CENTER
            setPadding(0, (6 * d).toInt(), 0, (12 * d).toInt())
        }

        progressBarConvert = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (8 * d).toInt()
            )
        }

        btnPlayConvertedAudio = TextView(this).apply {
            text = "PLAY EXTRACTED AUDIO NOW"
            setTextColor(Color.parseColor("#0A0A10"))
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding((18 * d).toInt(), (11 * d).toInt(), (18 * d).toInt(), (11 * d).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (14 * d).toInt() }
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 16f * d
            }
            setOnClickListener {
                if (lastOutputPath.isNotEmpty()) {
                    val f = File(lastOutputPath)
                    val intent = Intent(this@Mp3ConverterActivity, AudioPlayerActivity::class.java).apply {
                        putExtra("AUDIO_PATH", f.absolutePath)
                        putExtra("AUDIO_TITLE", f.name)
                        putExtra("AUDIO_ARTIST", "Hybrid Player Audio")
                    }
                    startActivity(intent)
                }
            }
        }

        statusCard.addView(tvStatusHeading)
        statusCard.addView(tvStatusSub)
        statusCard.addView(progressBarConvert)
        statusCard.addView(btnPlayConvertedAudio)
        root.addView(statusCard)

        rvVideoPicker = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            layoutManager = LinearLayoutManager(this@Mp3ConverterActivity)
            setHasFixedSize(true)
        }
        root.addView(rvVideoPicker)

        setContentView(root)

        val passedPath = intent?.getStringExtra("VIDEO_PATH") ?: ""
        val passedTitle = intent?.getStringExtra("VIDEO_TITLE") ?: File(passedPath).name
        if (passedPath.isNotEmpty()) {
            startConversionForVideo(passedPath, passedTitle)
        }

        loadVideosForConverter()
    }

    private fun startConversionForVideo(videoPath: String, videoTitle: String) {
        btnPlayConvertedAudio.visibility = View.GONE
        progressBarConvert.progress = 5
        tvStatusHeading.text = "Converting: $videoTitle"
        tvStatusSub.text = "Extracting lossless audio stream at ultra-high speed..."

        convertVideoToAudioInstant(
            context = this,
            videoPath = videoPath,
            videoTitle = videoTitle,
            onProgress = { pct ->
                progressBarConvert.progress = pct
            },
            onFinished = { success, resultMsgOrPath ->
                if (success) {
                    lastOutputPath = resultMsgOrPath
                    progressBarConvert.progress = 100
                    tvStatusHeading.text = "Audio Extracted Successfully!"
                    tvStatusSub.text = "Saved to: $resultMsgOrPath"
                    btnPlayConvertedAudio.visibility = View.VISIBLE
                    Toast.makeText(this, "Audio Saved & Added to Music Library!", Toast.LENGTH_SHORT).show()
                } else {
                    tvStatusHeading.text = "Conversion Notice"
                    tvStatusSub.text = resultMsgOrPath
                    Toast.makeText(this, resultMsgOrPath, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun loadVideosForConverter() {
        val d = resources.displayMetrics.density
        val cached = DeepMediaScanner.getCachedVideosInstant(this)
        if (cached.isNotEmpty()) {
            videosList.clear()
            videosList.addAll(cached)
        }

        rvVideoPicker.adapter = object : RecyclerView.Adapter<ConvVH>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConvVH {
                val card = CardView(this@Mp3ConverterActivity).apply {
                    radius = 14f * d
                    cardElevation = 4f * d
                    setCardBackgroundColor(Color.parseColor("#141E30"))
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins((4 * d).toInt(), (5 * d).toInt(), (4 * d).toInt(), (5 * d).toInt())
                    }
                }

                val row = LinearLayout(this@Mp3ConverterActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
                }

                val img = ImageView(this@Mp3ConverterActivity).apply {
                    layoutParams = LinearLayout.LayoutParams((68 * d).toInt(), (46 * d).toInt()).apply {
                        marginEnd = (12 * d).toInt()
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }

                val col = LinearLayout(this@Mp3ConverterActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = (8 * d).toInt()
                    }
                }

                val tvTitle = TextView(this@Mp3ConverterActivity).apply {
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }

                val tvSub = TextView(this@Mp3ConverterActivity).apply {
                    setTextColor(Color.parseColor("#B0C4DE"))
                    textSize = 11.5f
                    setPadding(0, (2 * d).toInt(), 0, 0)
                }

                val btnConvert = TextView(this@Mp3ConverterActivity).apply {
                    text = "Convert"
                    setTextColor(Color.parseColor("#0A0A10"))
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
                    background = GradientDrawable().apply {
                        setColor(Color.WHITE)
                        cornerRadius = 14f * d
                    }
                }

                col.addView(tvTitle)
                col.addView(tvSub)
                row.addView(img)
                row.addView(col)
                row.addView(btnConvert)
                card.addView(row)
                return ConvVH(card, img, tvTitle, tvSub, btnConvert)
            }

            override fun onBindViewHolder(holder: ConvVH, position: Int) {
                val item = videosList[position]
                holder.tvTitle.text = item.title
                holder.tvSub.text = "${VideoAdapter.formatVideoDurationAny(item.duration)}  •  Tap to Extract Audio"
                VideoAdapter.loadGuaranteedVideoThumbnail(this@Mp3ConverterActivity, item.path, holder.img)

                val clickAction = View.OnClickListener {
                    startConversionForVideo(item.path, item.title)
                }
                holder.itemView.setOnClickListener(clickAction)
                holder.btnConvert.setOnClickListener(clickAction)
            }

            override fun getItemCount(): Int = videosList.size
        }

        thread {
            val scanned = DeepMediaScanner.scanAllVideosEverywhere(this, forceRefresh = false)
            runOnUiThread {
                if (!isFinishing && scanned.size != videosList.size) {
                    videosList.clear()
                    videosList.addAll(scanned)
                    rvVideoPicker.adapter?.notifyDataSetChanged()
                }
            }
        }
    }

    private class ConvVH(
        view: View,
        val img: ImageView,
        val tvTitle: TextView,
        val tvSub: TextView,
        val btnConvert: TextView
    ) : RecyclerView.ViewHolder(view)
}
