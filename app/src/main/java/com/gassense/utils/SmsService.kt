package com.gassense.utils

import com.gassense.BuildConfig
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

object SmsService {

    private const val SEMAPHORE_SENDER = "GasSense"
    private const val SEMAPHORE_URL = "https://api.semaphore.co/api/v4/messages"
    private const val ALERT_MESSAGE = "CRITICAL GAS ALERT: GasSense has detected a critical hazard level on your device. Immediate action required!"

    private val client = OkHttpClient()
    private val database = FirebaseDatabase.getInstance("https://gas-sense-95d60-default-rtdb.asia-southeast1.firebasedatabase.app/")

    suspend fun sendCriticalAlert(recipientNumber: String, deviceId: String): Boolean =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("apikey", BuildConfig.SEMAPHORE_API_KEY)
                .add("number", recipientNumber)
                .add("message", ALERT_MESSAGE)
                .add("sendername", SEMAPHORE_SENDER)
                .build()

            val request = Request.Builder()
                .url(SEMAPHORE_URL)
                .post(body)
                .build()

            val success = try {
                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                false
            }

            logToFirebase(recipientNumber, deviceId, success)
            success
        }

    private fun logToFirebase(recipient: String, deviceId: String, success: Boolean) {
        val log = mapOf(
            "recipient" to recipient,
            "message" to ALERT_MESSAGE,
            "timestamp" to System.currentTimeMillis(),
            "status" to if (success) "sent" else "failed"
        )
        database.getReference("devices/$deviceId/smsLogs").push().setValue(log)
    }
}
