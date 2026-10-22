package com.app.mediaplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object GlobalPaymentHelper {

    fun openGlobalPayment(context: Context, paymentUrl: String) {
        try {
            val cleanUrl = paymentUrl.trim().ifEmpty { "https://paypal.me/maxplayerpro" }
            val formattedUrl = if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
                cleanUrl
            } else {
                "https://$cleanUrl"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Unable to open global payment link.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
