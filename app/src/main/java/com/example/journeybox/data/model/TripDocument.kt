package com.example.journeybox.data.model

import com.google.firebase.firestore.DocumentId

data class TripDocument(
    @DocumentId val id: String = "",
    val title: String = "",
    val details: String = "",
    val category: String = "Other", // Flight, Hotel, Insurance, Bill, Other
    val fileUrl: String = "",
    val fileName: String = "",
    val isScannedBill: Boolean = false
)
