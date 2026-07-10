package com.gassense.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Authenticated : AuthState()
    object Unauthenticated : AuthState()
    data class Error(val message: String, val isFieldSpecific: Boolean = false) : AuthState()
}

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val fullName: String = "",
    val mobileNumber: String = "",
    val deviceId: String = "",
    val role: String = "user"
)

class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                
                // Fetch profile and subscribe
                val uid = auth.currentUser?.uid
                if (uid != null) {
                    val doc = firestore.collection("users").document(uid).get().await()
                    val profile = doc.toObject(UserProfile::class.java)
                    _userProfile.value = profile
                    
                    val deviceId = profile?.deviceId
                    if (deviceId != null) {
                        FirebaseMessaging.getInstance().subscribeToTopic("device_$deviceId").await()
                    }
                }
                
                _authState.value = AuthState.Authenticated
            } catch (e: Exception) {
                _authState.value = AuthState.Error(mapError(e))
            }
        }
    }

    fun fetchProfile() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val doc = firestore.collection("users").document(uid).get().await()
                val profile = doc.toObject(UserProfile::class.java)
                _userProfile.value = profile
                android.util.Log.d("AuthViewModel", "Profile loaded: uid=$uid role=${profile?.role}")
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "fetchProfile failed: ${e.message}")
            }
        }
    }

    fun register(fullName: String, email: String, mobileNumber: String, deviceId: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                // 1. Create Auth User
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val uid = authResult.user?.uid ?: throw Exception("Auth creation failed")

                // We use a nested try-catch to ensure that if Firestore fails, we delete the Auth user (Rollback)
                try {
                    // 2. Check Device ID uniqueness (This might trigger Permission Denied if rules are strict)
                    val existingDevice = firestore.collection("users")
                        .whereEqualTo("deviceId", deviceId)
                        .get()
                        .await()

                    if (!existingDevice.isEmpty) {
                        throw Exception("DEVICE_ALREADY_EXISTS")
                    }

                    // 3. Create Firestore Profile
                    val userProfileMap = hashMapOf(
                        "uid" to uid,
                        "email" to email,
                        "fullName" to fullName,
                        "mobileNumber" to mobileNumber,
                        "deviceId" to deviceId,
                        "role" to "user",
                        "createdAt" to FieldValue.serverTimestamp()
                    )

                    firestore.collection("users").document(uid).set(userProfileMap).await()
                    
                    // Subscribe to device topic
                    FirebaseMessaging.getInstance().subscribeToTopic("device_$deviceId").await()
                    
                    // Update local state
                    _userProfile.value = UserProfile(uid, email, fullName, mobileNumber, deviceId, "user")
                    _authState.value = AuthState.Authenticated

                } catch (e: Exception) {
                    // ROLLBACK: Delete the auth user if any part of the Firestore profile creation fails
                    auth.currentUser?.delete()?.await()
                    
                    val errorMessage = when {
                        e.message == "DEVICE_ALREADY_EXISTS" -> "This Device ID is already registered."
                        e.localizedMessage?.contains("permission") == true -> 
                            "Database Permission Error: Please check your Firestore security rules. Ensure users can write to /users/{uid}."
                        else -> "Profile creation failed: ${e.localizedMessage}"
                    }
                    _authState.value = AuthState.Error(errorMessage, isFieldSpecific = (e.message == "DEVICE_ALREADY_EXISTS"))
                }

            } catch (e: Exception) {
                _authState.value = AuthState.Error(mapError(e))
            }
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                auth.sendPasswordResetEmail(email).await()
                _authState.value = AuthState.Idle // Or a specific success state
            } catch (e: Exception) {
                _authState.value = AuthState.Error(mapError(e))
            }
        }
    }

    fun logout() {
        auth.signOut()
        _userProfile.value = null
        _authState.value = AuthState.Unauthenticated
    }

    private fun mapError(e: Exception): String {
        return if (e is FirebaseAuthException) {
            when (e.errorCode) {
                "ERROR_INVALID_EMAIL" -> "Invalid email address format."
                "ERROR_WRONG_PASSWORD" -> "Incorrect password."
                "ERROR_USER_NOT_FOUND" -> "No account found with this email."
                "ERROR_EMAIL_ALREADY_IN_USE" -> "This email is already registered."
                "ERROR_NETWORK_REQUEST_FAILED" -> "Network error. Please check your connection."
                "ERROR_WEAK_PASSWORD" -> "Password is too weak."
                else -> e.localizedMessage ?: "An authentication error occurred."
            }
        } else {
            e.localizedMessage ?: "An unexpected error occurred."
        }
    }
}
