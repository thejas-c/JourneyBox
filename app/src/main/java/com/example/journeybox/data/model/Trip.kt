package com.example.journeybox.data.model

import com.google.firebase.firestore.DocumentId

data class Trip(
    @DocumentId val id: String = "",
    val name: String = "",
    val startPoint: String = "",
    val destination: String = "",
    val description: String = "",
    val startDate: Long = 0L,
    val endDate: Long = 0L,
    val notes: String = "",
    val imageUrl: String = "",
    val ownerId: String = "",
    val collaborators: List<String> = emptyList()
)
