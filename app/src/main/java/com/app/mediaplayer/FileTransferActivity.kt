package com.app.mediaplayer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteOrder
import kotlin.concurrent.thread

class FileTransferActivity : AppCompatActivity() {

    private lateinit var tvMyIpAddress: TextView
    private lateinit var tvReceiverStatus: TextView
    private lateinit var tvSenderStatus: TextView
    private lateinit var btnStartReceiver: Button
    private lateinit var etReceiverIp: EditText
    
    private val PORT = 8988
    private var isReceiving = false
    private var serverSocket: ServerSocket? = null
    private val uiHandler = Handler(Looper.getMainLooper())

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val receiverIp = etReceiverIp.text.toString().trim()
                if (receiverIp.isNotEmpty()) {
                    sendFile(uri, receiverIp)
                } else {
                    Toast.makeText(this, "Please enter Receiver IP first", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_transfer)

        tvMyIpAddress = findViewById(R.id.tvMyIpAddress)
        tvReceiverStatus = findViewById(R.id.tvReceiverStatus)
        tvSenderStatus = findViewById(R.id.tvSenderStatus)
        btnStartReceiver = findViewById(R.id.btnStartReceiver)
        etReceiverIp = findViewById(R.id.etReceiverIp)

        findViewById<ImageView>(R.id.btnBackTransfer).setOnClickListener { finish() }

        displayLocalIp()

        btnStartReceiver.setOnClickListener {
            if (!isReceiving) {
                startReceiverServer()
            }
        }

        findViewById<Button>(R.id.btnSelectAndSend).setOnClickListener {
            if (etReceiverIp.text.toString().trim().isEmpty()) {
                Toast.makeText(this, "Enter Receiver's IP Address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            filePickerLauncher.launch(intent)
        }
    }

    private fun displayLocalIp() {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            var ipAddress = wifiManager.connectionInfo.ipAddress
            
            if (ipAddress == 0) {
                tvMyIpAddress.text = "IP: Check Hotspot/WiFi"
                return
            }
            
            if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                ipAddress = Integer.reverseBytes(ipAddress)
            }
            
            val ipByteArray = ByteArray(4)
            ipByteArray[0] = (ipAddress shr 24 and 0xFF).toByte()
            ipByteArray[1] = (ipAddress shr 16 and 0xFF).toByte()
            ipByteArray[2] = (ipAddress shr 8 and 0xFF).toByte()
            ipByteArray[3] = (ipAddress and 0xFF).toByte()

            val ipString = InetAddress.getByAddress(ipByteArray).hostAddress
            tvMyIpAddress.text = "Your IP: $ipString"
        } catch (e: Exception) {
            tvMyIpAddress.text = "IP Error"
            e.printStackTrace()
        }
    }

    private fun startReceiverServer() {
        isReceiving = true
        btnStartReceiver.text = "RECEIVING (LISTENING...)"
        btnStartReceiver.isEnabled = false
        tvReceiverStatus.text = "Status: Waiting for sender on Port $PORT"
        
        thread {
            try {
                serverSocket = ServerSocket(PORT)
                val clientSocket = serverSocket!!.accept()
                
                uiHandler.post { tvReceiverStatus.text = "Status: Connected. Receiving file..." }
                
                val inputStream = clientSocket.getInputStream()
                
                // Read file name length and name
                val nameLength = inputStream.read()
                val nameBytes = ByteArray(nameLength)
                inputStream.read(nameBytes)
                val fileName = String(nameBytes)
                
                val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val receivedFile = File(downloadsFolder, "MAX_$fileName")
                
                val outputStream = FileOutputStream(receivedFile)
                val buffer = ByteArray(4096)
                var bytesRead: Int
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                
                outputStream.close()
                inputStream.close()
                clientSocket.close()
                
                uiHandler.post {
                    tvReceiverStatus.text = "Status: Received Successfully in Downloads!"
                    tvReceiverStatus.setTextColor(android.graphics.Color.GREEN)
                    resetReceiverUI()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                uiHandler.post {
                    tvReceiverStatus.text = "Status: Error/Disconnected"
                    tvReceiverStatus.setTextColor(android.graphics.Color.RED)
                    resetReceiverUI()
                }
            } finally {
                serverSocket?.close()
            }
        }
    }

    private fun resetReceiverUI() {
        isReceiving = false
        btnStartReceiver.text = "START RECEIVER"
        btnStartReceiver.isEnabled = true
    }

    private fun sendFile(uri: Uri, ipAddress: String) {
        tvSenderStatus.text = "Status: Connecting to $ipAddress..."
        
        thread {
            try {
                val socket = Socket(ipAddress, PORT)
                val outputStream: OutputStream = socket.getOutputStream()
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                
                val fileName = getFileName(uri)
                val nameBytes = fileName.toByteArray()
                
                // Send name length and name
                outputStream.write(nameBytes.size)
                outputStream.write(nameBytes)
                
                uiHandler.post { tvSenderStatus.text = "Status: Sending $fileName..." }
                
                val buffer = ByteArray(4096)
                var bytesRead: Int
                if (inputStream != null) {
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
                
                inputStream?.close()
                outputStream.flush()
                outputStream.close()
                socket.close()
                
                uiHandler.post {
                    tvSenderStatus.text = "Status: Sent Successfully!"
                    tvSenderStatus.setTextColor(android.graphics.Color.GREEN)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                uiHandler.post {
                    tvSenderStatus.text = "Status: Failed to connect or send."
                    tvSenderStatus.setTextColor(android.graphics.Color.RED)
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "unknown_file"
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
