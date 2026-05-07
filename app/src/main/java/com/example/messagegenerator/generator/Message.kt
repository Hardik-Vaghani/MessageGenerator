package com.example.messagegenerator.generator

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


data class Message(
    var id: Int,               // Unique identifier for the SMS message
    val sender: String,        // Sender's number or company name (e.g., AIRTEL) | Sender (e.g., phone number or company name)
    val messageBody: String,   // The body of the SMS message
    val timestamp: Long,       // Timestamp of when the SMS was sent or received
    val messageType:Int = Telephony.Sms.MESSAGE_TYPE_INBOX,       // Type of message (e.g., SENT, RECEIVED) | Message type (1 = inbox/received, 2 = sent)
    val status: Int = Telephony.Sms.STATUS_COMPLETE,        // Status of the SMS (e.g., "sent", "received")
    val messageIsRead: Int = 0    // Read status (e.g., "noRead", "read") |  (optional) Read status (0 = unread, 1 = read)
)

fun generateMessages(
    senderList: List<String>,
    messageBodyList: List<String>,
    generateTimestamp: () -> Long
): List<Message> {
    return List(senderList.size) { index ->
        Message(
            id = index + 1,
            sender = senderList[index % senderList.size],
            messageBody = messageBodyList[index % messageBodyList.size],
            timestamp = generateTimestamp(),
            messageType = 1, // 1 = RECEIVED (Inbox)
            status = Telephony.Sms.STATUS_COMPLETE,
            messageIsRead = 1
        )
    }
}

suspend fun insertMessages(context: Context, messages: List<Message>): Boolean = withContext(Dispatchers.IO) {

    try {
        var totalInserted = 0
        val groupedMessages = messages.groupBy { it.messageType }

        for ((type, group) in groupedMessages) {
            val insertUri = when (type) {
                Telephony.Sms.MESSAGE_TYPE_INBOX -> Uri.parse("content://sms/inbox")
                Telephony.Sms.MESSAGE_TYPE_SENT -> Uri.parse("content://sms/sent")
                Telephony.Sms.MESSAGE_TYPE_DRAFT -> Uri.parse("content://sms/draft")
                else -> Uri.parse("content://sms/inbox")
            }

            for (message in group) {
                val values = ContentValues().apply {
                    val threadId = getThreadIdForAnySender(context, message.sender) ?: 0L
                    put(Telephony.Sms.THREAD_ID, threadId)
                    put(Telephony.Sms.ADDRESS, message.sender)
                    put(Telephony.Sms.BODY, message.messageBody)
                    put(Telephony.Sms.DATE, message.timestamp)
                    put(Telephony.Sms.DATE_SENT, message.timestamp)
                    put(Telephony.Sms.READ, message.messageIsRead)
                    put(Telephony.Sms.SEEN, message.messageIsRead)
                    put(Telephony.Sms.TYPE, message.messageType)
                }

                val uri = context.contentResolver.insert(insertUri, values)
                if (uri != null) totalInserted++
            }
        }

        Log.d("TAG_insert", "Inserted $totalInserted/${messages.size} messages.")
        totalInserted == messages.size
    } catch (e: Exception) {
        Log.e("TAG_insert", "Failed to insert SMS batch", e)
        false
    }
}


private fun getThreadId(context: Context, sender: String): Long {
    val uri = Uri.parse("content://sms/")
    val projection = arrayOf("thread_id")
    val selection = "address = ?"
    val selectionArgs = arrayOf(sender)

    context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            return cursor.getLong(cursor.getColumnIndexOrThrow("thread_id"))
        }
    }
    return 0
}

fun getThreadIdForAnySender(context: Context, sender: String): Long {
    return try {
        // Normalize sender: remove surrounding spaces and validate
        val normalizedSender = sender.trim()

        if (normalizedSender.isNotEmpty()) {
            Telephony.Threads.getOrCreateThreadId(context, setOf(normalizedSender))
        } else {
            0L
        }
    } catch (e: Exception) {
        e.printStackTrace()
        0L
    }
}
