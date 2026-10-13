package com.app.mediaplayer

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class VaultPinActivity : AppCompatActivity() {

    private var currentPin = ""
    private var isSettingUp = false
    private var tempSetupPin = ""
    private val PREF_NAME = "VaultPrefs"
    private val KEY_PIN = "vault_pin"

    private lateinit var dots: Array<ImageView>
    private lateinit var tvPinInstruction: TextView
    private lateinit var tvPinError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vault_pin)

        dots = arrayOf(
            findViewById(R.id.dot1),
            findViewById(R.id.dot2),
            findViewById(R.id.dot3),
            findViewById(R.id.dot4)
        )
        tvPinInstruction = findViewById(R.id.tvPinInstruction)
        tvPinError = findViewById(R.id.tvPinError)

        findViewById<ImageView>(R.id.btnBackFromPin).setOnClickListener { finish() }

        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedPin = prefs.getString(KEY_PIN, null)

        if (savedPin == null) {
            isSettingUp = true
            tvPinInstruction.text = "Set a 4-Digit PIN"
        } else {
            isSettingUp = false
            tvPinInstruction.text = "Enter 4-Digit PIN"
        }

        setupKeypad()
    }

    private fun setupKeypad() {
        val buttons = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, 
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        )

        for (id in buttons) {
            findViewById<Button>(id).setOnClickListener {
                if (currentPin.length < 4) {
                    currentPin += (it as Button).text
                    updateDots()
                }
            }
        }

        findViewById<ImageButton>(R.id.btnClearPin).setOnClickListener {
            if (currentPin.isNotEmpty()) {
                currentPin = currentPin.dropLast(1)
                updateDots()
            }
        }
    }

    private fun updateDots() {
        tvPinError.visibility = View.INVISIBLE
        for (i in 0..3) {
            if (i < currentPin.length) {
                dots[i].setColorFilter(Color.parseColor("#00FFFF"))
            } else {
                dots[i].setColorFilter(Color.parseColor("#888888"))
            }
        }

        if (currentPin.length == 4) {
            processPin()
        }
    }

    private fun processPin() {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        
        if (isSettingUp) {
            if (tempSetupPin.isEmpty()) {
                tempSetupPin = currentPin
                currentPin = ""
                tvPinInstruction.text = "Confirm PIN"
                resetDots()
            } else {
                if (tempSetupPin == currentPin) {
                    prefs.edit().putString(KEY_PIN, currentPin).apply()
                    openVaultList()
                } else {
                    tempSetupPin = ""
                    currentPin = ""
                    tvPinInstruction.text = "PIN mismatch! Try Again"
                    tvPinError.visibility = View.VISIBLE
                    resetDots()
                }
            }
        } else {
            val savedPin = prefs.getString(KEY_PIN, "")
            if (currentPin == savedPin) {
                openVaultList()
            } else {
                currentPin = ""
                tvPinError.visibility = View.VISIBLE
                resetDots()
            }
        }
    }

    private fun resetDots() {
        for (dot in dots) {
            dot.setColorFilter(Color.parseColor("#888888"))
        }
    }

    private fun openVaultList() {
        val intent = Intent(this, VaultListActivity::class.java)
        startActivity(intent)
        finish()
    }
}
