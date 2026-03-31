package com.example.journeybox.data.repository

import android.net.Uri
import android.util.Log
import com.example.journeybox.data.model.*
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.storage.storage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = Firebase.firestore
    private val storage = Firebase.storage

    init {
        try {
            val settings = firestoreSettings {
                setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            }
            db.firestoreSettings = settings
        } catch (e: Exception) {
            Log.d("FirebaseRepository", "Firestore settings already initialized")
        }
    }

    val userId: String?
        get() = auth.currentUser?.uid

    // User Operations
    suspend fun isUsernameAvailable(username: String): Boolean {
        val doc = db.collection("usernames").document(username.lowercase()).get().await()
        return !doc.exists()
    }

    suspend fun createUserProfile(user: User) {
        val uid = userId ?: return
        db.runBatch { batch ->
            batch.set(db.collection("users").document(uid), user)
            batch.set(db.collection("usernames").document(user.username.lowercase()), mapOf("uid" to uid))
        }.await()
    }

    fun getUserProfile(uid: String? = userId): Flow<User?> = callbackFlow {
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val subscription = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseRepository", "Error getting user profile", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(User::class.java))
            }
        awaitClose { subscription.remove() }
    }

    suspend fun updateProfile(displayName: String, profilePictureUrl: String) {
        val uid = userId ?: return
        db.collection("users").document(uid).update(
            mapOf(
                "displayName" to displayName,
                "profilePictureUrl" to profilePictureUrl
            )
        ).await()
    }

    suspend fun uploadProfilePicture(uri: Uri): String {
        val uid = userId ?: throw Exception("User not logged in")
        val storageRef = storage.reference.child("users/$uid/profile_pic.jpg")
        
        storageRef.putFile(uri).await()
        return storageRef.downloadUrl.await().toString()
    }

    suspend fun getUserByUsername(username: String): User? {
        return try {
            val usernameDoc = db.collection("usernames").document(username.lowercase()).get().await()
            if (!usernameDoc.exists()) return null
            val uid = usernameDoc.getString("uid") ?: return null
            db.collection("users").document(uid).get().await().toObject(User::class.java)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error getting user by username", e)
            null
        }
    }

    // Friend Operations
    suspend fun sendFriendRequest(toUid: String, toUsername: String) {
        val uid = userId ?: return
        val currentUser = db.collection("users").document(uid).get().await().toObject(User::class.java) ?: return
        val request = FriendRequest(
            fromUid = uid,
            fromUsername = currentUser.username,
            toUid = toUid,
            status = "PENDING"
        )
        db.collection("friendRequests").add(request).await()
    }

    fun getIncomingFriendRequests(): Flow<List<FriendRequest>> = callbackFlow {
        val uid = userId
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val subscription = db.collection("friendRequests")
            .whereEqualTo("toUid", uid)
            .whereEqualTo("status", "PENDING")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseRepository", "Error getting friend requests", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toObject(FriendRequest::class.java)?.copy(id = it.id) } ?: emptyList())
            }
        awaitClose { subscription.remove() }
    }

    suspend fun acceptFriendRequest(request: FriendRequest) {
        val uid = userId ?: return
        try {
            db.runBatch { batch ->
                // 1. Update request status
                batch.update(db.collection("friendRequests").document(request.id), "status", "ACCEPTED")
                
                // 2. Create friendship document
                val friendshipId = if (request.fromUid < request.toUid) 
                    "${request.fromUid}_${request.toUid}" 
                else 
                    "${request.toUid}_${request.fromUid}"
                
                val friendshipData = mapOf(
                    "uids" to listOf(request.fromUid, request.toUid),
                    "timestamp" to FieldValue.serverTimestamp()
                )
                batch.set(db.collection("friendships").document(friendshipId), friendshipData)
            }.await()
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error accepting friend request", e)
            throw e
        }
    }

    suspend fun declineFriendRequest(requestId: String) {
        db.collection("friendRequests").document(requestId).update("status", "DECLINED").await()
    }

    fun getFriends(): Flow<List<User>> = callbackFlow {
        val uid = userId
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val subscription = db.collection("friendships")
            .whereArrayContains("uids", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseRepository", "Error getting friendships", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val friendUids = snapshot?.documents?.mapNotNull { doc ->
                    val uids = doc.get("uids") as? List<*>
                    uids?.filter { it != uid }?.firstOrNull() as? String
                } ?: emptyList()

                if (friendUids.isEmpty()) {
                    trySend(emptyList())
                } else {
                    db.collection("users").whereIn(FieldPath.documentId(), friendUids)
                        .get().addOnSuccessListener { friendSnapshots ->
                            trySend(friendSnapshots.documents.mapNotNull { it.toObject(User::class.java) })
                        }.addOnFailureListener {
                            Log.e("FirebaseRepository", "Error fetching friend profiles", it)
                            trySend(emptyList())
                        }
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun removeFriend(friendUid: String) {
        val uid = userId ?: return
        val friendshipId = if (uid < friendUid) "${uid}_${friendUid}" else "${friendUid}_${uid}"
        // Only delete the friendship document. getFriends() will automatically update.
        db.collection("friendships").document(friendshipId).delete().await()
    }

    // Trip Operations
    fun getTrips(): Flow<List<Trip>> = callbackFlow {
        val uid = userId
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val globalTripsSub = db.collection("all_trips")
            .whereArrayContains("collaborators", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseRepository", "Error getting trips", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toObject(Trip::class.java)?.copy(id = it.id) } ?: emptyList())
            }
        awaitClose { globalTripsSub.remove() }
    }

    fun getTripById(tripId: String): Flow<Trip?> = callbackFlow {
        val subscription = db.collection("all_trips").document(tripId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseRepository", "Error getting trip by ID", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(Trip::class.java)?.copy(id = snapshot.id))
            }
        awaitClose { subscription.remove() }
    }
    
    suspend fun addTrip(trip: Trip) {
        val uid = userId ?: return
        val tripWithDetails = trip.copy(ownerId = uid, collaborators = trip.collaborators + uid)
        db.collection("all_trips").add(tripWithDetails).await()
    }

    suspend fun deleteTrip(tripId: String) {
        db.collection("all_trips").document(tripId).delete().await()
    }

    suspend fun updateTripDescription(tripId: String, description: String) {
        db.collection("all_trips").document(tripId).update("description", description).await()
    }

    suspend fun addCollaborator(tripId: String, friendUid: String) {
        db.collection("all_trips").document(tripId)
            .update("collaborators", FieldValue.arrayUnion(friendUid)).await()
    }

    suspend fun removeCollaborator(tripId: String, memberUid: String) {
        db.collection("all_trips").document(tripId)
            .update("collaborators", FieldValue.arrayRemove(memberUid)).await()
    }

    fun getItinerary(tripId: String): Flow<List<TripActivity>> = callbackFlow {
        val subscription = db.collection("all_trips").document(tripId)
            .collection("itinerary").orderBy("time")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseRepository", "Error getting itinerary", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toObject(TripActivity::class.java)?.copy(id = it.id) } ?: emptyList())
            }
        awaitClose { subscription.remove() }
    }

    suspend fun addActivity(tripId: String, activity: TripActivity) {
        db.collection("all_trips").document(tripId).collection("itinerary").add(activity).await()
    }

    suspend fun deleteActivity(tripId: String, activityId: String) {
        db.collection("all_trips").document(tripId).collection("itinerary").document(activityId).delete().await()
    }

    suspend fun clearItinerary(tripId: String) {
        val activities = db.collection("all_trips").document(tripId).collection("itinerary").get().await()
        db.runBatch { batch ->
            activities.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
        }.await()
    }

    fun getExpenses(tripId: String): Flow<List<Expense>> = callbackFlow {
        val subscription = db.collection("all_trips").document(tripId)
            .collection("expenses").orderBy("date")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseRepository", "Error getting expenses", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toObject(Expense::class.java)?.copy(id = it.id) } ?: emptyList())
            }
        awaitClose { subscription.remove() }
    }

    suspend fun addExpense(tripId: String, expense: Expense) {
        val uid = userId ?: return
        val expenseWithAuthor = expense.copy(addedBy = uid)
        db.collection("all_trips").document(tripId).collection("expenses").add(expenseWithAuthor).await()
    }

    suspend fun addScannedBill(tripId: String, expense: Expense, document: TripDocument) {
        val uid = userId ?: return
        val expenseWithAuthor = expense.copy(addedBy = uid)
        
        db.runBatch { batch ->
            val expenseRef = db.collection("all_trips").document(tripId).collection("expenses").document()
            batch.set(expenseRef, expenseWithAuthor)
            
            val documentRef = db.collection("all_trips").document(tripId).collection("documents").document(expenseRef.id)
            batch.set(documentRef, document)
        }.await()
    }

    suspend fun deleteExpense(tripId: String, expenseId: String) {
        db.runBatch { batch ->
            batch.delete(db.collection("all_trips").document(tripId).collection("expenses").document(expenseId))
            batch.delete(db.collection("all_trips").document(tripId).collection("documents").document(expenseId))
        }.await()
    }

    fun getDocuments(tripId: String): Flow<List<TripDocument>> = callbackFlow {
        val subscription = db.collection("all_trips").document(tripId)
            .collection("documents")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseRepository", "Error getting documents", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toObject(TripDocument::class.java)?.copy(id = it.id) } ?: emptyList())
            }
        awaitClose { subscription.remove() }
    }

    suspend fun addDocument(tripId: String, document: TripDocument) {
        db.collection("all_trips").document(tripId).collection("documents").add(document).await()
    }

    suspend fun uploadFile(tripId: String, uri: Uri, path: String): String {
        val fileName = "${System.currentTimeMillis()}_${uri.lastPathSegment}"
        val storageRef = storage.reference.child("trips/$tripId/$path/$fileName")
        storageRef.putFile(uri).await()
        return storageRef.downloadUrl.await().toString()
    }

    suspend fun uploadDocument(tripId: String, uri: Uri, title: String, category: String) {
        val fileName = "${System.currentTimeMillis()}_${uri.lastPathSegment}"
        val downloadUrl = uploadFile(tripId, uri, "documents")

        val document = TripDocument(
            title = title,
            category = category,
            fileUrl = downloadUrl,
            fileName = fileName
        )

        addDocument(tripId, document)
    }
}
