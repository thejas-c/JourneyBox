package com.example.journeybox.data.model

import com.google.firebase.firestore.DocumentId

data class TripActivity(
    @DocumentId val id: String = "",
    val title: String = "",
    val time: Long = 0L,
    val location: String = "",
    val notes: String = "",
    val day: Int = 1,
    val addedBy: String = "" // UID of the user who added this activity
)
