package com.example.messagegenerator.presentation


import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.util.Log

class HeadlessSmsSendService : Service() {

    private val TAG = "BASE_TAG" + HeadlessSmsSendService::class.java

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not used for headless service
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //if (intent?.action == "android.intent.action.RESPOND_VIA_MESSAGE") { // Corrected line
        try {
            if (intent == null) { return START_NOT_STICKY }

            val number = Uri.decode(intent.dataString!!.removePrefix("sms:").removePrefix("smsto:").removePrefix("mms").removePrefix("mmsto:").trim())
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrEmpty()) {
                val addresses = listOf(number)
                //  val subId = Settings.DEFAULT_SUBSCRIPTION_ID
                // sendMessageCompat(text, addresses, subId, emptyList())
            }
        } catch (ignored: Exception) {
            Log.e(TAG, "onStartCommand: ", ignored)
        }

        return super.onStartCommand(intent, flags, startId)
    }
}