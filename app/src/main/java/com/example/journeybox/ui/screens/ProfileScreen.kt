package com.example.journeybox.ui.screens

import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.journeybox.data.model.FriendRequest
import com.example.journeybox.data.model.User
import com.example.journeybox.data.repository.FirebaseRepository
import com.example.journeybox.ui.viewmodel.AuthViewModel
import com.example.journeybox.ui.viewmodel.TripViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel(),
    tripViewModel: TripViewModel = viewModel()
) {
    val repository = remember { FirebaseRepository() }
    val userProfile by repository.getUserProfile().collectAsState(initial = null)
    val incomingRequests by repository.getIncomingFriendRequests().collectAsState(initial = emptyList())
    val friends by repository.getFriends().collectAsState(initial = emptyList())
    val trips by tripViewModel.trips.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    isUploading = true
                    val downloadUrl = repository.uploadProfilePicture(it)
                    repository.updateProfile(userProfile?.displayName ?: "", downloadUrl)
                    snackbarHostState.showSnackbar("Profile picture updated!")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Upload failed: ${e.message}")
                } finally {
                    isUploading = false
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Profile", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Logout") },
                            onClick = {
                                showMenu = false
                                authViewModel.logout()
                                navController.navigate("login") { popUpTo(0) }
                            },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) }
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.primary) {
                NavigationBarItem(
                    selected = false,
                    onClick = { 
                        navController.navigate("dashboard") {
                            popUpTo("dashboard") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White) },
                    label = { Text("Home", color = Color.White) }
                )
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color.White) },
                    label = { Text("Profile", color = Color.White) }
                )
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(
                    onClick = { 
                        navController.navigate("dashboard?openAddTrip=true") {
                            popUpTo("dashboard") { inclusive = true }
                        }
                    },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                // Profile Header
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(enabled = !isUploading) { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (userProfile?.profilePictureUrl.isNullOrEmpty()) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(60.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        AsyncImage(
                            model = userProfile?.profilePictureUrl,
                            contentDescription = "Profile Picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    
                    if (isUploading) {
                        CircularProgressIndicator(color = Color.White)
                    } else {
                        // Overlay edit icon
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(16.dp).padding(bottom = 4.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Balancing spacer to keep the text perfectly centered
                    Spacer(modifier = Modifier.width(32.dp))

                    Text(
                        text = userProfile?.displayName ?: "Loading...",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { showEditDialog = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Name", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
                Text(
                    text = "@${userProfile?.username ?: ""}",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatCard("Trips", trips.size.toString())
                    StatCard("Friends", friends.size.toString())
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Friends", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showAddFriendDialog = true }) {
                        Icon(Icons.Default.Add, null)
                        Text("Add Friend")
                    }
                }
            }

            if (incomingRequests.isNotEmpty()) {
                item {
                    Text("Friend Requests", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                items(incomingRequests) { request ->
                    FriendRequestItem(request, 
                        onAccept = { scope.launch { repository.acceptFriendRequest(request) } },
                        onDecline = { scope.launch { repository.declineFriendRequest(request.id) } }
                    )
                }
            }

            if (friends.isEmpty()) {
                item {
                    Text("No friends yet", color = Color.Gray, modifier = Modifier.padding(16.dp))
                }
            } else {
                items(friends) { friend ->
                    FriendItem(
                        user = friend,
                        onRemove = { 
                            scope.launch { 
                                try {
                                    repository.removeFriend(friend.uid)
                                    snackbarHostState.showSnackbar("Friend removed")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Error: ${e.message}")
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showEditDialog && userProfile != null) {
        EditProfileDialog(
            user = userProfile!!,
            onDismiss = { showEditDialog = false },
            onConfirm = { name ->
                scope.launch {
                    try {
                        repository.updateProfile(name, userProfile!!.profilePictureUrl)
                        showEditDialog = false
                        snackbarHostState.showSnackbar("Profile updated!")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Update failed: ${e.message}")
                    }
                }
            }
        )
    }

    if (showAddFriendDialog) {
        AddFriendDialog(
            onDismiss = { showAddFriendDialog = false },
            onSearch = { username ->
                scope.launch {
                    try {
                        val targetUser = repository.getUserByUsername(username)
                        if (targetUser != null && targetUser.uid != userProfile?.uid) {
                            repository.sendFriendRequest(targetUser.uid, targetUser.username)
                            showAddFriendDialog = false
                            snackbarHostState.showSnackbar("Friend request sent!")
                        } else {
                            showAddFriendDialog = false
                            snackbarHostState.showSnackbar("User not found")
                        }
                    } catch (e: Exception) {
                        showAddFriendDialog = false
                        snackbarHostState.showSnackbar("Error: ${e.message}")
                    }
                }
            }
        )
    }
}

@Composable
fun StatCard(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun FriendRequestItem(request: FriendRequest, onAccept: () -> Unit, onDecline: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "@${request.fromUsername}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "sent you a request",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
            Row {
                IconButton(
                    onClick = onAccept,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Green.copy(alpha = 0.1f))
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Accept", tint = Color.Green)
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onDecline,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Red.copy(alpha = 0.1f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Decline", tint = Color.Red)
                }
            }
        }
    }
}

@Composable
fun FriendItem(user: User, onRemove: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(user.displayName) },
        supportingContent = { Text("@${user.username}") },
        leadingContent = {
            if (user.profilePictureUrl.isEmpty()) {
                Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(40.dp))
            } else {
                AsyncImage(
                    model = user.profilePictureUrl,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Friend options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Remove Friend", color = Color.Red) },
                        onClick = {
                            showMenu = false
                            onRemove()
                        },
                        leadingIcon = { Icon(Icons.Default.PersonRemove, null, tint = Color.Red) }
                    )
                }
            }
        }
    )
}

@Composable
fun EditProfileDialog(user: User, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(user.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile") },
        text = {
            Column {
                Text("Username: @${user.username} (Cannot be changed)", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display Name") })
            }
        },
        confirmButton = { Button(onClick = { onConfirm(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddFriendDialog(onDismiss: () -> Unit, onSearch: (String) -> Unit) {
    var username by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Friend") },
        text = {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Enter Username") },
                placeholder = { Text("e.g. wanderlust_john") }
            )
        },
        confirmButton = { Button(onClick = { onSearch(username) }) { Text("Send Request") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
