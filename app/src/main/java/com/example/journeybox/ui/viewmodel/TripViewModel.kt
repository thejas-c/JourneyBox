package com.example.journeybox.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.journeybox.data.model.Expense
import com.example.journeybox.data.model.Trip
import com.example.journeybox.data.model.TripActivity
import com.example.journeybox.data.model.TripDocument
import com.example.journeybox.data.repository.FirebaseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TripViewModel : ViewModel() {
    private val repository = FirebaseRepository()

    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    private val _itinerary = MutableStateFlow<List<TripActivity>>(emptyList())
    val itinerary: StateFlow<List<TripActivity>> = _itinerary.asStateFlow()

    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()

    private val _documents = MutableStateFlow<List<TripDocument>>(emptyList())
    val documents: StateFlow<List<TripDocument>> = _documents.asStateFlow()

    val currentUserId: String?
        get() = repository.userId

    init {
        loadTrips()
    }

    private fun loadTrips() {
        viewModelScope.launch {
            repository.getTrips().collect {
                _trips.value = it
            }
        }
    }

    fun getTripById(tripId: String): Flow<Trip?> {
        return repository.getTripById(tripId)
    }

    fun addTrip(trip: Trip) {
        viewModelScope.launch {
            repository.addTrip(trip)
        }
    }

    fun deleteTrip(tripId: String) {
        viewModelScope.launch {
            try {
                repository.deleteTrip(tripId)
            } catch (e: Exception) {
                Log.e("TripViewModel", "Failed to delete trip: ${e.message}")
            }
        }
    }

    fun updateTripDescription(tripId: String, description: String) {
        viewModelScope.launch {
            try {
                repository.updateTripDescription(tripId, description)
            } catch (e: Exception) {
                Log.e("TripViewModel", "Failed to update trip description: ${e.message}")
            }
        }
    }

    fun loadTripDetails(tripId: String) {
        viewModelScope.launch {
            repository.getItinerary(tripId).collect { _itinerary.value = it }
        }
        viewModelScope.launch {
            repository.getExpenses(tripId).collect { _expenses.value = it }
        }
        viewModelScope.launch {
            repository.getDocuments(tripId).collect { _documents.value = it }
        }
    }

    fun addActivity(tripId: String, activity: TripActivity) {
        viewModelScope.launch {
            repository.addActivity(tripId, activity)
        }
    }

    fun clearItinerary(tripId: String) {
        viewModelScope.launch {
            try {
                repository.clearItinerary(tripId)
            } catch (e: Exception) {
                Log.e("TripViewModel", "Failed to clear itinerary: ${e.message}")
            }
        }
    }

    fun addExpense(tripId: String, expense: Expense) {
        viewModelScope.launch {
            repository.addExpense(tripId, expense)
        }
    }

    fun addScannedBill(tripId: String, description: String, amount: Double, category: String) {
        viewModelScope.launch {
            try {
                Log.d("TripViewModel", "Adding scanned bill for trip $tripId")
                
                val expense = Expense(
                    description = description,
                    amount = amount,
                    category = category,
                    date = System.currentTimeMillis()
                )
                
                val document = TripDocument(
                    title = "Bill: $description",
                    details = "Amount: ₹$amount, Category: $category",
                    category = "Bill",
                    isScannedBill = true
                )
                
                repository.addScannedBill(tripId, expense, document)
                Log.d("TripViewModel", "Atomic add of scanned bill successful")
            } catch (e: Exception) {
                Log.e("TripViewModel", "Error adding scanned bill: ${e.message}", e)
            }
        }
    }

    fun deleteExpense(tripId: String, expenseId: String) {
        viewModelScope.launch {
            try {
                repository.deleteExpense(tripId, expenseId)
            } catch (e: Exception) {
                Log.e("TripViewModel", "Failed to delete expense: ${e.message}")
            }
        }
    }

    fun addDocument(tripId: String, document: TripDocument) {
        viewModelScope.launch {
            repository.addDocument(tripId, document)
        }
    }
}
