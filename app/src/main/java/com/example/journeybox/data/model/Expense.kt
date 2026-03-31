package com.example.journeybox.data.model

import com.google.firebase.firestore.DocumentId

data class Expense(
    @DocumentId val id: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val category: String = "Other", // Food, Travel, Shopping, etc.
    val date: Long = 0L,
    val receiptUrl: String = "",
    val addedBy: String = "" // UID of the user who added this expense
)
