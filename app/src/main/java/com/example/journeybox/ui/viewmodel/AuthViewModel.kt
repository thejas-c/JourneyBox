package com.example.journeybox.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.journeybox.data.model.User
import com.example.journeybox.data.repository.FirebaseRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val repository = FirebaseRepository()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private val _usernameAvailable = MutableStateFlow<Boolean?>(null)
    val usernameAvailable: StateFlow<Boolean?> = _usernameAvailable

    private var checkUsernameJob: Job? = null
    private var verificationJob: Job? = null

    init {
        checkCurrentSession()
    }

    private fun checkCurrentSession() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            viewModelScope.launch {
                try {
                    // Refresh user to get latest verification status
                    currentUser.reload().await()
                    if (!currentUser.isEmailVerified && currentUser.providerData.any { it.providerId == "password" }) {
                        _authState.value = AuthState.WaitingForVerification(currentUser.email ?: "")
                        startEmailVerificationCheck()
                        return@launch
                    }

                    val profile = repository.getUserProfile(currentUser.uid).firstOrNull()
                    if (profile == null) {
                        _authState.value = AuthState.ProfileIncomplete(
                            email = currentUser.email ?: "",
                            displayName = currentUser.displayName ?: ""
                        )
                    } else {
                        _authState.value = AuthState.Success
                    }
                } catch (e: Exception) {
                    Log.e("AuthViewModel", "Error checking session", e)
                }
            }
        }
    }

    fun checkUsername(username: String) {
        checkUsernameJob?.cancel()
        if (username.length < 3) {
            _usernameAvailable.value = null
            return
        }
        
        _usernameAvailable.value = null
        
        checkUsernameJob = viewModelScope.launch {
            try {
                delay(300)
                val available = repository.isUsernameAvailable(username)
                _usernameAvailable.value = available
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Error checking username: ${e.message}")
                _usernameAvailable.value = null
            }
        }
    }

    fun signup(email: String, pass: String, username: String, displayName: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                if (!repository.isUsernameAvailable(username)) {
                    _authState.value = AuthState.Error("Username already taken")
                    return@launch
                }

                val authResult = auth.createUserWithEmailAndPassword(email, pass).await()
                val firebaseUser = authResult.user ?: throw Exception("Failed to create user")

                // Send real verification email
                firebaseUser.sendEmailVerification().await()

                // Save profile immediately but keep state as "WaitingForVerification"
                val userProfile = User(
                    uid = firebaseUser.uid,
                    username = username,
                    displayName = displayName,
                    email = email
                )
                repository.createUserProfile(userProfile)
                
                _authState.value = AuthState.WaitingForVerification(email)
                startEmailVerificationCheck()
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Signup error", e)
                _authState.value = AuthState.Error(e.message ?: "Signup failed")
            }
        }
    }

    private fun startEmailVerificationCheck() {
        verificationJob?.cancel()
        verificationJob = viewModelScope.launch {
            while (true) {
                delay(3000) // Check every 3 seconds
                val user = auth.currentUser
                user?.reload()?.await()
                if (user?.isEmailVerified == true) {
                    _authState.value = AuthState.Success
                    break
                }
            }
        }
    }

    fun resendVerificationEmail() {
        viewModelScope.launch {
            try {
                auth.currentUser?.sendEmailVerification()?.await()
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Resend error", e)
            }
        }
    }

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = auth.signInWithEmailAndPassword(email, pass).await()
                val user = result.user
                
                if (user != null && !user.isEmailVerified) {
                    _authState.value = AuthState.WaitingForVerification(email)
                    startEmailVerificationCheck()
                } else {
                    _authState.value = AuthState.Success
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Login error", e)
                _authState.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = auth.signInWithCredential(credential).await()
                val firebaseUser = result.user

                if (firebaseUser != null) {
                    val profile = repository.getUserProfile(firebaseUser.uid).firstOrNull()
                    if (profile == null) {
                        _authState.value = AuthState.ProfileIncomplete(
                            email = firebaseUser.email ?: "",
                            displayName = firebaseUser.displayName ?: ""
                        )
                    } else {
                        _authState.value = AuthState.Success
                    }
                } else {
                    _authState.value = AuthState.Error("Google Sign-In failed: No user")
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Google Sign-In error", e)
                _authState.value = AuthState.Error(e.message ?: "Google Sign-In failed")
            }
        }
    }

    fun completeProfile(email: String, username: String, displayName: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val uid = auth.currentUser?.uid ?: throw Exception("Not authenticated")
                
                if (!repository.isUsernameAvailable(username)) {
                    _authState.value = AuthState.Error("Username already taken")
                    return@launch
                }

                val userProfile = User(
                    uid = uid,
                    username = username,
                    displayName = displayName,
                    email = email
                )
                repository.createUserProfile(userProfile)
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Complete profile error", e)
                _authState.value = AuthState.Error(e.message ?: "Failed to save profile")
            }
        }
    }

    fun logout() {
        verificationJob?.cancel()
        auth.signOut()
        _authState.value = AuthState.Idle
    }
    
    fun resetState() {
        _authState.value = AuthState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        verificationJob?.cancel()
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class WaitingForVerification(val email: String) : AuthState()
    data class ProfileIncomplete(val email: String, val displayName: String) : AuthState()
    data class Error(val message: String) : AuthState()
}
