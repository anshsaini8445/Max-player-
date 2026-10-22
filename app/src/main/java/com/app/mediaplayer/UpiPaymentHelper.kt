package com.app.mediaplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object UpiPaymentHelper {

    fun launchUpiPayment(
        context: Context,
        upiId: String,
        payeeName: String = "OG 8K Player VIP",
        amount: String = "99.00",
        note: String = "OG 8K Player Lifetime VIP"
    ) {
        try {
            val cleanUpi = upiId.trim().ifEmpty { "anshsaini@upi" }
            val uri = Uri.Builder()
                .scheme("upi")
                .authority("pay")
                .appendQueryParameter("pa", cleanUpi)
                .appendQueryParameter("pn", payeeName)
                .appendQueryParameter("tn", note)
                .appendQueryParameter("am", amount)
                .appendQueryParameter("cu", "INR")
                .build()

            val intent = Intent(Intent.ACTION_VIEW, uri)
            val chooser = Intent.createChooser(intent, "Pay with UPI App")
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "No UPI app found on this phone. Please copy the UPI ID.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
