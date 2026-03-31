package com.example.journeybox.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.journeybox.data.model.Trip
import com.example.journeybox.data.model.User
import com.example.journeybox.data.repository.FirebaseRepository
import com.example.journeybox.ui.viewmodel.TripViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(
    navController: NavController, 
    viewModel: TripViewModel = viewModel(),
    openAddTrip: Boolean = false
) {
    val trips by viewModel.trips.collectAsState()
    var showAddTripDialog by remember { mutableStateOf(openAddTrip) }
    val repository = remember { FirebaseRepository() }
    val friends by repository.getFriends().collectAsState(initial = emptyList())
    val currentUserId = viewModel.currentUserId

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Trips", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                actions = {
                    IconButton(onClick = { /* TODO: Search */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.primary) {
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White) },
                    label = { Text("Home", color = Color.White) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { 
                        navController.navigate("profile") {
                            popUpTo("dashboard") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color.White) },
                    label = { Text("Profile", color = Color.White) }
                )
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(
                    onClick = { showAddTripDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Trip", tint = Color.White)
                }
                Spacer(modifier = Modifier.height(16.dp))
                FloatingActionButton(
                    onClick = { navController.navigate("sos") },
                    containerColor = Color.Red,
                    shape = CircleShape
                ) {
                    Text("SOS", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (trips.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No trips yet. Add your first journey!", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(trips) { trip ->
                        TripCard(
                            trip = trip,
                            currentUserId = currentUserId,
                            onClick = { navController.navigate("tripDetail/${trip.id}") },
                            onDelete = { viewModel.deleteTrip(trip.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddTripDialog) {
        AddTripDialog(
            friends = friends,
            onDismiss = { showAddTripDialog = false },
            onConfirm = { name, startPoint, dest, desc, start, end, selectedFriends ->
                viewModel.addTrip(Trip(
                    name = name,
                    startPoint = startPoint,
                    destination = dest,
                    description = desc,
                    startDate = start,
                    endDate = end,
                    collaborators = selectedFriends.map { it.uid }
                ))
                showAddTripDialog = false
            }
        )
    }
}

@Composable
fun TripCard(trip: Trip, currentUserId: String?, onClick: () -> Unit, onDelete: () -> Unit) {
    val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    val dateRange = "${dateFormat.format(Date(trip.startDate))} - ${dateFormat.format(Date(trip.endDate))}"
    var showMenu by remember { mutableStateOf(false) }
    
    val imageUrl = when {
        trip.destination.contains("Goa", ignoreCase = true) -> "https://images.unsplash.com/photo-1512343879784-a960bf40e7f2?auto=format&fit=crop&w=800&q=80"
        trip.destination.contains("Manali", ignoreCase = true) -> "https://images.unsplash.com/photo-1626621341517-bbf3d9990a23?auto=format&fit=crop&w=800&q=80"
        else -> "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            )

            // 3-dot menu for deletion
            if (trip.ownerId == currentUserId) {
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete Trip", color = Color.Red) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(trip.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(dateRange, color = Color.White, fontSize = 14.sp)
                if (trip.collaborators.size > 1) {
                    Text("${trip.collaborators.size} Members", color = Color.LightGray, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text("View Trip >", fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTripDialog(
    friends: List<User>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, Long, Long, List<User>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var startPoint by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    val context = LocalContext.current
    val datePickerState = rememberDatePickerState()
    var showDatePicker by remember { mutableStateOf(false) }
    var pickingStart by remember { mutableStateOf(true) }

    val calendar = Calendar.getInstance()
    var startDateTime by remember { mutableLongStateOf(calendar.timeInMillis) }
    var endDateTime by remember { mutableLongStateOf(calendar.timeInMillis + 86400000) }

    var showTimePicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour = calendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = calendar.get(Calendar.MINUTE)
    )

    val selectedFriends = remember { mutableStateListOf<User>() }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dateMillis ->
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = if (pickingStart) startDateTime else endDateTime
                        val hour = cal.get(Calendar.HOUR_OF_DAY)
                        val minute = cal.get(Calendar.MINUTE)
                        
                        val newCal = Calendar.getInstance()
                        newCal.timeInMillis = dateMillis
                        newCal.set(Calendar.HOUR_OF_DAY, hour)
                        newCal.set(Calendar.MINUTE, minute)
                        
                        if (pickingStart) startDateTime = newCal.timeInMillis else endDateTime = newCal.timeInMillis
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
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (pickingStart) "Select Start Time" else "Select End Time",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )
                    TimePicker(state = timePickerState)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                        TextButton(onClick = {
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = if (pickingStart) startDateTime else endDateTime
                            cal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            cal.set(Calendar.MINUTE, timePickerState.minute)
                            
                            if (pickingStart) startDateTime = cal.timeInMillis else endDateTime = cal.timeInMillis
                            showTimePicker = false
                        }) { Text("OK") }
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Trip") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Trip Name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = startPoint, onValueChange = { startPoint = it }, label = { Text("Start Point") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = destination, onValueChange = { destination = it }, label = { Text("Destination") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Trip Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Trip Duration", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Starts:", modifier = Modifier.weight(0.2f), fontSize = 14.sp)
                        OutlinedButton(
                            onClick = { pickingStart = true; showDatePicker = true }, 
                            modifier = Modifier.weight(0.4f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(
                                SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(startDateTime)), 
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedButton(
                            onClick = { pickingStart = true; showTimePicker = true }, 
                            modifier = Modifier.weight(0.4f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(
                                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(startDateTime)), 
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Ends:", modifier = Modifier.weight(0.2f), fontSize = 14.sp)
                        OutlinedButton(
                            onClick = { pickingStart = false; showDatePicker = true }, 
                            modifier = Modifier.weight(0.4f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(
                                SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(endDateTime)), 
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedButton(
                            onClick = { pickingStart = false; showTimePicker = true }, 
                            modifier = Modifier.weight(0.4f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(
                                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(endDateTime)), 
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Add Collaborators (Friends)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                if (friends.isEmpty()) {
                    item { Text("No friends to add", color = Color.Gray, fontSize = 12.sp) }
                } else {
                    items(friends) { friend ->
                        val isSelected = selectedFriends.contains(friend)
                        ListItem(
                            headlineContent = { Text(friend.displayName) },
                            supportingContent = { Text("@${friend.username}") },
                            trailingContent = {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        if (it) selectedFriends.add(friend) else selectedFriends.remove(friend)
                                    }
                                )
                            },
                            modifier = Modifier.clickable {
                                if (isSelected) selectedFriends.remove(friend) else selectedFriends.add(friend)
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                val now = System.currentTimeMillis()
                if (startDateTime < now) {
                    Toast.makeText(context, "Start time cannot be in the past", Toast.LENGTH_SHORT).show()
                } else if (endDateTime <= startDateTime) {
                    Toast.makeText(context, "End time must be after start time", Toast.LENGTH_SHORT).show()
                } else {
                    onConfirm(name, startPoint, destination, description, startDateTime, endDateTime, selectedFriends.toList()) 
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
