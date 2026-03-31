package com.example.journeybox.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.journeybox.data.model.*
import com.example.journeybox.data.repository.FirebaseRepository
import com.example.journeybox.ui.viewmodel.TripViewModel
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TripDetailScreen(navController: NavController, tripId: String, viewModel: TripViewModel = viewModel()) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Itinerary", "Documents", "Expenses")
    val itinerary by viewModel.itinerary.collectAsState()
    val documents by viewModel.documents.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val trips by viewModel.trips.collectAsState()
    val currentUserId = viewModel.currentUserId

    val context = LocalContext.current
    val repository = remember { FirebaseRepository() }
    val friends by repository.getFriends().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val currentTrip = trips.find { it.id == tripId }

    var showAddActivityDialog by remember { mutableStateOf(false) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showCollaboratorsDialog by remember { mutableStateOf(false) }
    var showScanResultDialog by remember { mutableStateOf<ScanResultData?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var showEditDescriptionDialog by remember { mutableStateOf(false) }

    val billScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isScanning = true
            scope.launch {
                scanBillWithGemini(context, it) { description, category, amount ->
                    isScanning = false
                    showScanResultDialog = ScanResultData(it, description, amount, category)
                }
            }
        }
    }

    LaunchedEffect(tripId) {
        viewModel.loadTripDetails(tripId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentTrip?.name ?: "Trip Details", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showCollaboratorsDialog = true }) {
                        Icon(Icons.Default.Person, contentDescription = "Collaborators", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        },
        floatingActionButton = {
            if (selectedTab == 1 || selectedTab == 3) {
                FloatingActionButton(
                    onClick = {
                        if (selectedTab == 1) showAddActivityDialog = true
                        else if (selectedTab == 3) showAddExpenseDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.secondary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Header Image and Trip Info
            Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                val imageUrl = currentTrip?.destination?.let { dest ->
                    when {
                        dest.contains("Goa", ignoreCase = true) -> "https://images.unsplash.com/photo-1512343879784-a960bf40e7f2?auto=format&fit=crop&w=800&q=80"
                        dest.contains("Manali", ignoreCase = true) -> "https://images.unsplash.com/photo-1626621341517-bbf3d9990a23?auto=format&fit=crop&w=800&q=80"
                        else -> "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80"
                    }
                } ?: "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?auto=format&fit=crop&w=800&q=80"

                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
                Column(modifier = Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(currentTrip?.name ?: "Loading...", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)

                    if (currentTrip != null) {
                        val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
                        val dateRange = "${dateFormat.format(Date(currentTrip.startDate))} - ${dateFormat.format(Date(currentTrip.endDate))}"
                        Text(dateRange, color = Color.White, fontSize = 16.sp)
                    }
                }
            }

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 16.dp,
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { 
                            Text(
                                text = title,
                                maxLines = 1,
                                softWrap = false
                            ) 
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> TripOverview(currentTrip, viewModel, scope, context, onEditDescription = { showEditDescriptionDialog = true })
                1 -> ItinerarySection(itinerary, currentUserId, tripId, repository)
                2 -> DocumentsSection(documents, onUploadClick = { billScannerLauncher.launch("image/*") })
                3 -> ExpensesSection(
                    expenses = expenses,
                    currentUserId = currentUserId,
                    tripId = tripId,
                    viewModel = viewModel,
                    onDeleteExpense = { expenseId ->
                        viewModel.deleteExpense(tripId, expenseId)
                    },
                    repository = repository
                )
            }
        }
    }

    if (showEditDescriptionDialog && currentTrip != null) {
        EditDescriptionDialog(
            currentDescription = currentTrip.description,
            onDismiss = { showEditDescriptionDialog = false },
            onSave = { newDesc ->
                viewModel.updateTripDescription(tripId, newDesc)
                showEditDescriptionDialog = false
            }
        )
    }

    if (isScanning) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("AI Scanning Bill") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Analyzing the receipt...")
                }
            },
            confirmButton = {}
        )
    }

    showScanResultDialog?.let { result ->
        var editedDescription by remember { mutableStateOf(result.description) }
        var editedAmount by remember { mutableStateOf(result.amount.toString()) }
        var selectedCategory by remember { mutableStateOf(result.category) }
        val categories = listOf("Food", "Travel", "Shopping", "Stay", "Other")

        AlertDialog(
            onDismissRequest = { showScanResultDialog = null },
            title = { Text("Verify Scanned Details") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editedDescription,
                        onValueChange = { editedDescription = it },
                        label = { Text("Shop/Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editedAmount,
                        onValueChange = { editedAmount = it },
                        label = { Text("Amount (₹)") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Category", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addScannedBill(
                        tripId = tripId,
                        description = editedDescription,
                        amount = editedAmount.toDoubleOrNull() ?: 0.0,
                        category = selectedCategory
                    )
                    showScanResultDialog = null
                }) {
                    Text("Approve & Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showScanResultDialog = null }) {
                    Text("Discard")
                }
            }
        )
    }

    if (showAddActivityDialog && currentTrip != null) {
        AddActivityDialog(
            trip = currentTrip,
            onDismiss = { showAddActivityDialog = false },
            onConfirm = { title, loc, notes, time ->
                viewModel.addActivity(tripId, TripActivity(
                    title = title, 
                    location = loc, 
                    notes = notes, 
                    time = time,
                    addedBy = currentUserId ?: ""
                ))
                showAddActivityDialog = false
            }
        )
    }

    if (showAddExpenseDialog) {
        AddExpenseDialog(
            onDismiss = { showAddExpenseDialog = false },
            onConfirm = { desc, amount, cat ->
                viewModel.addExpense(tripId, Expense(description = desc, amount = amount, category = cat, date = System.currentTimeMillis()))
                showAddExpenseDialog = false
            }
        )
    }

    if (showCollaboratorsDialog && currentTrip != null) {
        CollaboratorsDialog(
            tripId = tripId,
            ownerId = currentTrip.ownerId,
            currentUserId = currentUserId ?: "",
            currentCollaborators = currentTrip.collaborators,
            friends = friends,
            onDismiss = { showCollaboratorsDialog = false },
            onAddCollaborator = { friend ->
                scope.launch {
                    repository.addCollaborator(tripId, friend.uid)
                }
            },
            onRemoveCollaborator = { memberUid ->
                scope.launch {
                    repository.removeCollaborator(tripId, memberUid)
                }
            },
            repository = repository
        )
    }
}

@Composable
fun TripOverview(
    trip: Trip?, 
    viewModel: TripViewModel, 
    scope: kotlinx.coroutines.CoroutineScope, 
    context: Context,
    onEditDescription: () -> Unit
) {
    if (trip == null) return
    
    var isGenerating by remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Details", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OverviewItem(Icons.Default.Place, "Start Point", trip.startPoint)
                    OverviewItem(Icons.Default.LocationOn, "Destination", trip.destination)
                    OverviewItem(Icons.Default.DateRange, "Start Time", dateFormat.format(Date(trip.startDate)))
                    OverviewItem(Icons.Default.DateRange, "End Time", dateFormat.format(Date(trip.endDate)))
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Description", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        IconButton(onClick = onEditDescription, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Description", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    
                    if (trip.description.isNotEmpty()) {
                        Text(trip.description, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                    } else {
                        Text("No description provided.", fontSize = 14.sp, color = Color.LightGray, modifier = Modifier.padding(top = 4.dp))
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            isGenerating = true
                            scope.launch {
                                viewModel.clearItinerary(trip.id)
                                generateItineraryWithGemini(context, trip, viewModel.currentUserId ?: "") { activities ->
                                    activities.forEach { viewModel.addActivity(trip.id, it) }
                                }
                                isGenerating = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isGenerating,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate Itinerary")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditDescriptionDialog(currentDescription: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var description by remember { mutableStateOf(currentDescription) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Trip Description") },
        text = {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                minLines = 5
            )
        },
        confirmButton = {
            Button(onClick = { onSave(description) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun OverviewItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, fontSize = 12.sp, color = Color.Gray)
            Text(value.ifEmpty { "Not set" }, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

data class ScanResultData(val uri: Uri, val description: String, val amount: Double, val category: String)

private suspend fun generateItineraryWithGemini(
    context: Context,
    trip: Trip,
    userId: String,
    onResult: (List<TripActivity>) -> Unit
) {
    try {
        val generativeModel = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = "YOUR_API_KEY"
        )

        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val startStr = dateFormat.format(Date(trip.startDate))
        val endStr = dateFormat.format(Date(trip.endDate))

        val prompt = """
            Create a detailed, step-by-step travel itinerary for '${trip.name}'.
            Start Point: ${trip.startPoint}
            Destination: ${trip.destination}
            Trip Start: $startStr
            Trip End: $endStr
            Description: ${trip.description}
            
            The plan must include:
            1. Departure from ${trip.startPoint}.
            2. Arrival at ${trip.destination}.
            3. Checking into accommodation and freshening up.
            4. Detailed sightseeing, meals, and specific activities for each day.
            5. Realistic time slots for each activity.
            
            Return ONLY a valid JSON array of objects. Do not include markdown formatting or extra text.
            Structure:
            [
              {
                "title": "Activity Name",
                "location": "Location Name",
                "notes": "Detailed description or tips",
                "time": 1735123456000,
                "day": 1
              }
            ]
            'time' is a Long timestamp in milliseconds. Ensure times are between ${trip.startDate} and ${trip.endDate}.
        """.trimIndent()

        val response = generativeModel.generateContent(prompt)
        val textResponse = response.text

        if (!textResponse.isNullOrBlank()) {
            val jsonText = textResponse.replace("```json", "").replace("```", "").trim()
            val jsonArray = JSONArray(jsonText)
            val activities = mutableListOf<TripActivity>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                activities.add(TripActivity(
                    title = obj.getString("title"),
                    location = obj.getString("location"),
                    notes = obj.getString("notes"),
                    time = obj.getLong("time"),
                    day = obj.optInt("day", 1),
                    addedBy = userId
                ))
            }
            onResult(activities)
        }
    } catch (e: Exception) {
        Log.e("TripDetailScreen", "Gemini planning failed: ${e.message}")
    }
}

private suspend fun scanBillWithGemini(context: Context, uri: Uri, onResult: (String, String, Double) -> Unit) {
    try {
        Log.d("TripDetailScreen", "Starting robust scan for URI: $uri")
        val originalBitmap = withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= 28) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            } catch (e: Exception) {
                Log.e("TripDetailScreen", "Bitmap loading failed: ${e.message}")
                null
            }
        }

        if (originalBitmap == null) {
            onResult("Error: Could not load image", "Other", 0.0)
            return
        }

        // Scale down to prevent API payload limits (max dimension 1024px)
        val maxDimension = 1024
        val (width, height) = if (originalBitmap.width > originalBitmap.height) {
            val ratio = originalBitmap.width.toFloat() / originalBitmap.height
            maxDimension to (maxDimension / ratio).toInt()
        } else {
            val ratio = originalBitmap.height.toFloat() / originalBitmap.width
            (maxDimension / ratio).toInt() to maxDimension
        }
        val bitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true)
        Log.d("TripDetailScreen", "Image scaled to ${bitmap.width}x${bitmap.height}")

        val generativeModel = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = "YOUR_API_KEY"
        )

        val inputContent = content {
            image(bitmap)
            text("Analyze this receipt. Extract: 1. Shop Name, 2. Category (map to exactly one of: Food, Travel, Shopping, Stay, Other), 3. Total Amount in INR (if it is in other currency, convert it to INR. Return ONLY the whole number value, no decimal points). Return ONLY a JSON object: {\"description\": \"ShopName\", \"category\": \"CategoryName\", \"amount\": 123.45}. If not found, use 'Unknown', 'Other' or 0.0.")
        }

        Log.d("TripDetailScreen", "Requesting Gemini...")
        val response = generativeModel.generateContent(inputContent)
        val textResponse = response.text
        Log.d("TripDetailScreen", "AI Raw Response: $textResponse")

        if (textResponse.isNullOrBlank()) {
            onResult("Error: AI returned no text", "Other", 0.0)
            return
        }

        val jsonText = textResponse.replace("```json", "").replace("```", "").trim()
        val json = JSONObject(jsonText)
        val desc = json.optString("description", "Unknown")
        val cat = json.optString("category", "Other")
        val amt = json.optDouble("amount", 0.0)

        onResult(desc, cat, amt)
    } catch (e: Exception) {
        Log.e("TripDetailScreen", "Scan failed with exception: ${e.message}", e)
        onResult("Error: ${e.localizedMessage ?: "Unknown Error"}", "Other", 0.0)
    }
}

@Composable
fun CollaboratorsDialog(
    tripId: String,
    ownerId: String,
    currentUserId: String,
    currentCollaborators: List<String>,
    friends: List<User>,
    onDismiss: () -> Unit,
    onAddCollaborator: (User) -> Unit,
    onRemoveCollaborator: (String) -> Unit,
    repository: FirebaseRepository
) {
    var showAddFriendSection by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (showAddFriendSection) "Add Friend to Trip" else "Trip Collaborators") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!showAddFriendSection) {
                    // List current collaborators
                    Text("People in this trip:", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val collaboratorsData = remember { mutableStateListOf<User>() }
                    LaunchedEffect(currentCollaborators) {
                        collaboratorsData.clear()
                        currentCollaborators.forEach { uid ->
                            // Use collect to get the latest profile
                            launch {
                                repository.getUserProfile(uid).collect { user ->
                                    if (user != null) {
                                        val index = collaboratorsData.indexOfFirst { it.uid == user.uid }
                                        if (index != -1) {
                                            collaboratorsData[index] = user
                                        } else {
                                            collaboratorsData.add(user)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                        items(collaboratorsData) { user ->
                            var showMemberMenu by remember { mutableStateOf(false) }
                            val isAdmin = user.uid == ownerId
                            
                            ListItem(
                                headlineContent = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(user.displayName)
                                        if (isAdmin) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    "Admin",
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                },
                                supportingContent = { Text("@${user.username}") },
                                leadingContent = {
                                    if (user.profilePictureUrl.isNotEmpty()) {
                                        AsyncImage(
                                            model = user.profilePictureUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp).clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Default.Person, null, modifier = Modifier.size(32.dp))
                                    }
                                },
                                trailingContent = {
                                    // Only show 3-dots if user is NOT admin and current user is Admin
                                    if (!isAdmin && currentUserId == ownerId) {
                                        Box {
                                            IconButton(onClick = { showMemberMenu = true }) {
                                                Icon(Icons.Default.MoreVert, contentDescription = "Member options")
                                            }
                                            DropdownMenu(
                                                expanded = showMemberMenu,
                                                onDismissRequest = { showMemberMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Remove Member", color = Color.Red) },
                                                    onClick = {
                                                        showMemberMenu = false
                                                        onRemoveCollaborator(user.uid)
                                                    },
                                                    leadingIcon = { Icon(Icons.Default.PersonRemove, null, tint = Color.Red) }
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    if (currentUserId == ownerId) {
                        Button(
                            onClick = { showAddFriendSection = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PersonAdd, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add More Friends")
                        }
                    }
                } else {
                    // List available friends to add
                    val availableFriends = friends.filter { it.uid !in currentCollaborators }
                    if (availableFriends.isEmpty()) {
                        Text("All your friends are already in this trip!")
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                            items(availableFriends) { friend ->
                                ListItem(
                                    headlineContent = { Text(friend.displayName) },
                                    supportingContent = { Text("@${friend.username}") },
                                    modifier = Modifier.clickable { onAddCollaborator(friend) },
                                    trailingContent = {
                                        Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    leadingContent = {
                                        if (friend.profilePictureUrl.isNotEmpty()) {
                                            AsyncImage(
                                                model = friend.profilePictureUrl,
                                                contentDescription = null,
                                                modifier = Modifier.size(32.dp).clip(CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(Icons.Default.Person, null, modifier = Modifier.size(32.dp))
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { showAddFriendSection = false }) {
                        Text("Back to list")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun ItinerarySection(
    activities: List<TripActivity>,
    currentUserId: String?,
    tripId: String,
    repository: FirebaseRepository
) {
    val scope = rememberCoroutineScope()
    if (activities.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No activities planned yet.")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
        ) {
            items(activities) { activity ->
                val isPast = activity.time < System.currentTimeMillis()
                val contentColor = if (isPast) Color.Gray.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                val indicatorColor = if (isPast) Color.Gray.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary

                Row(modifier = Modifier.padding(vertical = 8.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(12.dp).background(indicatorColor, CircleShape))
                        Box(modifier = Modifier.width(2.dp).height(60.dp).background(if (isPast) Color.LightGray.copy(alpha = 0.5f) else Color.LightGray))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val timeStr = SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()).format(Date(activity.time))
                        Text(timeStr, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = indicatorColor)
                        Text(activity.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = contentColor)
                        Text(activity.location, fontSize = 12.sp, color = contentColor.copy(alpha = 0.8f))
                        if (activity.notes.isNotEmpty()) {
                            Text(activity.notes, fontSize = 12.sp, color = contentColor.copy(alpha = 0.7f))
                        }
                    }
                    
                    // Delete option
                    if (!isPast && (activity.addedBy == currentUserId)) {
                        IconButton(onClick = {
                            scope.launch {
                                repository.deleteActivity(tripId, activity.id)
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentsSection(documents: List<TripDocument>, onUploadClick: () -> Unit) {
    Column {
        Button(
            onClick = onUploadClick,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan Bill/Invoice")
        }
        
        if (documents.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No scanned documents yet.", color = Color.Gray)
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(documents) { doc ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (doc.isScannedBill) "Bill" else doc.category, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(doc.title, fontWeight = FontWeight.Bold)
                            Text(doc.details, fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpensesSection(
    expenses: List<Expense>, 
    currentUserId: String?, 
    tripId: String, 
    viewModel: TripViewModel, 
    onDeleteExpense: (String) -> Unit,
    repository: FirebaseRepository
) {
    val totalSpent = expenses.sumOf { it.amount }
    val trips by viewModel.trips.collectAsState()
    val currentTrip = trips.find { it.id == tripId }
    val collaboratorCount = currentTrip?.collaborators?.size ?: 1
    val costPerPerson = totalSpent / collaboratorCount

    // Ordered Categories
    val categoriesOrder = listOf("Food", "Travel", "Shopping", "Stay", "Other")
    
    // Custom colors for Donut Chart
    val othersColor = android.graphics.Color.parseColor("#9E9E9E") // Gray for Others
    val materialColors = ColorTemplate.MATERIAL_COLORS.toMutableList()
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2E2E))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Total Spent: ₹$totalSpent",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    AndroidView(
                        modifier = Modifier.size(180.dp),
                        factory = { context ->
                            PieChart(context).apply {
                                description.isEnabled = false
                                setHoleColor(android.graphics.Color.TRANSPARENT)
                                setCenterTextColor(android.graphics.Color.WHITE)
                                legend.isEnabled = false
                            }
                        },
                        update = { pieChart ->
                            val groupedExpenses = expenses.groupBy { it.category }
                            
                            // Reorder entries according to categoriesOrder
                            val entries = categoriesOrder.mapNotNull { cat ->
                                groupedExpenses[cat]?.let { list ->
                                    PieEntry(list.sumOf { it.amount }.toFloat(), cat)
                                }
                            }
                            
                            val chartColors = entries.map { entry ->
                                if (entry.label.equals("Other", ignoreCase = true)) {
                                    othersColor
                                } else {
                                    val originalIndex = categoriesOrder.indexOf(entry.label)
                                    materialColors[originalIndex % materialColors.size]
                                }
                            }

                            val dataSet = PieDataSet(entries, "").apply {
                                colors = chartColors
                                valueTextColor = android.graphics.Color.WHITE
                                valueTextSize = 12f
                            }
                            pieChart.data = PieData(dataSet)
                            pieChart.invalidate()
                        }
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Legend Column
                    Column(
                        modifier = Modifier.wrapContentHeight(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val categoryTotals = expenses.groupBy { it.category }
                            .mapValues { it.value.sumOf { exp -> exp.amount } }
                        
                        // Display legends in specific order
                        categoriesOrder.forEach { cat ->
                            if (categoryTotals.containsKey(cat)) {
                                val color = if (cat.equals("Other", ignoreCase = true)) {
                                    othersColor
                                } else {
                                    val originalIndex = categoriesOrder.indexOf(cat)
                                    materialColors[originalIndex % materialColors.size]
                                }
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(Color(color))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$cat: ₹${categoryTotals[cat]}",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "Cost per person: ₹${costPerPerson.toLong()}",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            items(expenses) { expense ->
                val authorProfile by repository.getUserProfile(expense.addedBy).collectAsState(initial = null)
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(expense.description, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(expense.category, fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "added by ${authorProfile?.displayName ?: "..."}",
                                fontSize = 12.sp, 
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("₹${expense.amount}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        
                        if (expense.addedBy == currentUserId) {
                            IconButton(onClick = { onDeleteExpense(expense.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Expense", tint = Color.Red)
                            }
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddActivityDialog(trip: Trip, onDismiss: () -> Unit, onConfirm: (String, String, String, Long) -> Unit) {
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val datePickerState = rememberDatePickerState()
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDateTime by remember { mutableLongStateOf(System.currentTimeMillis().coerceIn(trip.startDate, trip.endDate)) }

    var showTimePicker by remember { mutableStateOf(false) }
    val calendar = Calendar.getInstance().apply { timeInMillis = selectedDateTime }
    val timePickerState = rememberTimePickerState(
        initialHour = calendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = calendar.get(Calendar.MINUTE)
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dateMillis ->
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = selectedDateTime
                        val hour = cal.get(Calendar.HOUR_OF_DAY)
                        val minute = cal.get(Calendar.MINUTE)
                        
                        val newCal = Calendar.getInstance()
                        newCal.timeInMillis = dateMillis
                        newCal.set(Calendar.HOUR_OF_DAY, hour)
                        newCal.set(Calendar.MINUTE, minute)
                        
                        selectedDateTime = newCal.timeInMillis
                    }
                    showDatePicker = false
                }) { Text("OK") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Select Time", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 20.dp))
                    TimePicker(state = timePickerState)
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                        TextButton(onClick = {
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = selectedDateTime
                            cal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            cal.set(Calendar.MINUTE, timePickerState.minute)
                            selectedDateTime = cal.timeInMillis
                            showTimePicker = false
                        }) { Text("OK") }
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Activity") },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Activity Schedule", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                        Text(SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(selectedDateTime)), fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                        Text(SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(selectedDateTime)), fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                val now = System.currentTimeMillis()
                if (selectedDateTime < now) {
                    Toast.makeText(context, "Cannot add activities in the past", Toast.LENGTH_SHORT).show()
                } else if (selectedDateTime < trip.startDate || selectedDateTime > trip.endDate) {
                    Toast.makeText(context, "Activity must be within trip dates", Toast.LENGTH_SHORT).show()
                } else {
                    onConfirm(title, location, notes, selectedDateTime) 
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(onDismiss: () -> Unit, onConfirm: (String, Double, String) -> Unit) {
    var desc by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food") }
    val categories = listOf("Food", "Travel", "Shopping", "Stay", "Other")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Expense") },
        text = {
            Column {
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Category", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(desc, amount.toDoubleOrNull() ?: 0.0, category) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
