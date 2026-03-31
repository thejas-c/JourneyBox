package com.example.journeybox.data.model

import com.google.firebase.firestore.DocumentId

data class FriendRequest(
    @DocumentId val id: String = "",
    val fromUid: String = "",
    val fromUsername: String = "",
    val toUid: String = "",
    val status: String = "PENDING", // PENDING, ACCEPTED, DECLINED
    val timestamp: Long = System.currentTimeMillis()
)
