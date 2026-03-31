package com.example.journeybox.data.model

import com.google.firebase.firestore.DocumentId

data class User(
    @DocumentId val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val email: String = "",
    val profilePictureUrl: String = "",
    val phoneNumber: String = "",
    val friends: List<String> = emptyList() // List of friend UIDs
)
