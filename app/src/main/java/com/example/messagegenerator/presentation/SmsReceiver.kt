package com.example.messagegenerator.presentation


import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.messagegenerator.generator.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
class SmsReceiver : BroadcastReceiver() {
    private val TAG = "BASE_TAG" + SmsReceiver::class.java

    override fun onReceive(context: Context, intent: Intent) {  }
}