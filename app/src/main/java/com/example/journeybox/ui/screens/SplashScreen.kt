package com.example.journeybox.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.journeybox.R
import com.example.journeybox.ui.viewmodel.AuthState
import com.example.journeybox.ui.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavController, viewModel: AuthViewModel = viewModel()) {
    val authState by viewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        delay(2000)
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            navController.navigate("login") {
                popUpTo("splash") { inclusive = true }
            }
        } else {
            // If user is logged in, wait for AuthViewModel to determine if profile is complete
            when (authState) {
                is AuthState.Success -> {
                    navController.navigate("dashboard") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
                is AuthState.ProfileIncomplete -> {
                    navController.navigate("login") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
                is AuthState.Error -> {
                    navController.navigate("login") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
                else -> { /* Wait for state to settle from Idle/Loading */ }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.ic_journeybox_logo),
                contentDescription = "JourneyBox Logo",
                modifier = Modifier.size(150.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "JOURNEYBOX",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = "Smart Travel Companion App",
                fontSize = 16.sp,
                color = Color.Gray
            )
        }
    }
}
