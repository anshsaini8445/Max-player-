package com.app.mediaplayer

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

object FakeVaultHelper {

    private const val PREF_NAME = "OG_VAULT_SECURITY_PREFS"

    fun lockMediaIntoVault(context: Context, path: String, title: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val set = HashSet(prefs.getStringSet("locked_vault_paths", emptySet()) ?: emptySet())
        set.add(path)
        prefs.edit().putStringSet("locked_vault_paths", set).apply()
        Toast.makeText(
            context,
            "Locked in Privacy Vault: $title",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun getLockedCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return (prefs.getStringSet("locked_vault_paths", emptySet()) ?: emptySet()).size
    }

    fun openSmartDualPinVaultDialog(activity: Activity) {
        // Prevent Screenshots & Screen Recording inside Privacy Vault
        activity.window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val realPin = prefs.getString("vault_real_pin", "") ?: ""
        val fakePin = prefs.getString("vault_fake_pin", "0000") ?: "0000"

        if (realPin.isEmpty()) {
            showSetupDualPinsDialog(activity)
            return
        }

        val d = activity.resources.displayMetrics.density
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (20 * d).toInt(), (20 * d).toInt(), (16 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#13263B"))
                cornerRadius = 22f * d
                setStroke((1.5f * d).toInt(), Color.parseColor("#FFD700"))
            }
        }

        val tvTitle = TextView(activity).apply {
            text = "OG Private Space (Screenshot Blocked)"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }
        val etPin = EditText(activity).apply {
            hint = "Enter 4-Digit Real PIN (or Fake Decoy PIN)"
            setHintTextColor(Color.parseColor("#99AACC"))
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        layout.addView(tvTitle)
        layout.addView(etPin)

        val dialog = AlertDialog.Builder(activity)
            .setView(layout)
            .setPositiveButton("Unlock Vault") { _, _ ->
                val entered = etPin.text.toString().trim()
                when (entered) {
                    realPin -> {
                        prefs.edit().putInt("failed_attempts", 0).apply()
                        showUnlockedVaultContentsDialog(activity, isFakeDecoy = false)
                    }
                    fakePin -> {
                        // Opens decoy empty vault so friends suspect nothing
                        showUnlockedVaultContentsDialog(activity, isFakeDecoy = true)
                    }
                    else -> {
                        val fails = prefs.getInt("failed_attempts", 0) + 1
                        prefs.edit().putInt("failed_attempts", fails).apply()
                        if (fails >= 3) {
                            Toast.makeText(
                                activity,
                                "Security Alert: 3 Wrong PIN attempts logged with timestamp!",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(activity, "Wrong PIN! Attempt $fails/3", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNeutralButton("Reset / Change PINs") { _, _ ->
                showSetupDualPinsDialog(activity)
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun showSetupDualPinsDialog(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val col = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 20)
        }
        val etReal = EditText(activity).apply {
            hint = "Set Real Secret PIN (e.g. 1234)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.BLACK)
        }
        val etFake = EditText(activity).apply {
            hint = "Set Fake Decoy PIN (e.g. 0000 - Opens Empty Vault)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.BLACK)
        }
        col.addView(etReal)
        col.addView(etFake)

        AlertDialog.Builder(activity)
            .setTitle("Setup Real & Fake Vault PIN")
            .setView(col)
            .setPositiveButton("Save PINs") { _, _ ->
                val r = etReal.text.toString().trim().ifEmpty { "1234" }
                val f = etFake.text.toString().trim().ifEmpty { "0000" }
                prefs.edit()
                    .putString("vault_real_pin", r)
                    .putString("vault_fake_pin", f)
                    .apply()
                Toast.makeText(activity, "Real PIN ($r) & Fake PIN ($f) Saved!", Toast.LENGTH_LONG).show()
                showUnlockedVaultContentsDialog(activity, isFakeDecoy = false)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showUnlockedVaultContentsDialog(activity: Activity, isFakeDecoy: Boolean) {
        val d = activity.resources.displayMetrics.density
        val prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lockedPaths = if (isFakeDecoy) {
            emptyList()
        } else {
            (prefs.getStringSet("locked_vault_paths", emptySet()) ?: emptySet()).toList()
        }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * d).toInt(), (18 * d).toInt(), (18 * d).toInt(), (16 * d).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0D1B2A"))
                cornerRadius = 22f * d
                setStroke((1.5f * d).toInt(), Color.parseColor("#5CE1E6"))
            }
        }

        val header = TextView(activity).apply {
            text = if (isFakeDecoy) "Privacy Vault (0 Files)" else "Privacy Vault (${lockedPaths.size} Secret Files)"
            setTextColor(Color.parseColor("#5CE1E6"))
            textSize = 16.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (12 * d).toInt())
        }
        root.addView(header)

        if (lockedPaths.isEmpty()) {
            val tvEmpty = TextView(activity).apply {
                text = "Vault is currently empty.\nLock any video from the 3-dots menu to keep it secret here."
                setTextColor(Color.WHITE)
                textSize = 13.5f
                gravity = Gravity.CENTER
                setPadding((12 * d).toInt(), (30 * d).toInt(), (12 * d).toInt(), (30 * d).toInt())
            }
            root.addView(tvEmpty)
        } else {
            val scroll = ScrollView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (320 * d).toInt()
                )
            }
            val col = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

            lockedPaths.forEach { path ->
                val f = File(path)
                val name = f.name.ifEmpty { "Secret Media" }
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, (10 * d).toInt(), 0, (10 * d).toInt())
                }
                val tvName = TextView(activity).apply {
                    text = name
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        // Clicking the locked video plays it directly inside the full-featured 8K Player
                        val playIntent = Intent(activity, PlayerActivity::class.java).apply {
                            putExtra("VIDEO_PATH", path)
                            putExtra("VIDEO_TITLE", name)
                            putStringArrayListExtra("VIDEO_LIST", ArrayList(lockedPaths))
                        }
                        activity.startActivity(playIntent)
                    }
                }
                val btnUnlock = TextView(activity).apply {
                    text = "Unlock"
                    setTextColor(Color.BLACK)
                    textSize = 11.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding((10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt())
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#FFD700"))
                        cornerRadius = 12f * d
                    }
                    setOnClickListener {
                        val updated = HashSet(prefs.getStringSet("locked_vault_paths", emptySet()) ?: emptySet())
                        updated.remove(path)
                        prefs.edit().putStringSet("locked_vault_paths", updated).apply()
                        Toast.makeText(activity, "Restored to public library: $name", Toast.LENGTH_SHORT).show()
                        row.visibility = android.view.View.GONE
                    }
                }
                row.addView(tvName)
                row.addView(btnUnlock)
                col.addView(row)
            }
            scroll.addView(col)
            root.addView(scroll)
        }

        val dialog = AlertDialog.Builder(activity)
            .setView(root)
            .setPositiveButton("Add Video to Vault") { _, _ ->
                if (!isFakeDecoy) {
                    showQuickAddVideoToVaultDialog(activity)
                }
            }
            .setNegativeButton("Close Vault", null)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun showQuickAddVideoToVaultDialog(activity: Activity) {
        val allVideos = DeepMediaScanner.scanAllVideosEverywhere(activity).take(40)
        if (allVideos.isEmpty()) {
            Toast.makeText(activity, "No videos found", Toast.LENGTH_SHORT).show()
            return
        }
        val names = allVideos.map { it.title }.toTypedArray()
        AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Select Video to Lock in Vault")
            .setItems(names) { _, which ->
                val v = allVideos[which]
                lockMediaIntoVault(activity, v.path, v.title)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
