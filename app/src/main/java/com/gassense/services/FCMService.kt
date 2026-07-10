package com.gassense.services

import com.gassense.utils.SafetyNotificationService
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.data["title"] ?: remoteMessage.notification?.title ?: "Gas Sense Alert"
        val message = remoteMessage.data["message"] ?: remoteMessage.notification?.body ?: "Check your sensor status."
        val severity = remoteMessage.data["severity"] ?: "WARNING"

        SafetyNotificationService.showNotification(this, title, message, severity)
    }

    override fun onNewToken(token: String) {
        // Here you would send the token to your server if needed
    }
}
