package com.example.journeybox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.journeybox.ui.screens.*
import com.example.journeybox.ui.theme.JourneyBoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JourneyBoxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    NavHost(navController = navController, startDestination = "splash") {
                        composable("splash") { SplashScreen(navController) }
                        composable("login") { LoginScreen(navController) }
                        composable("signup") { SignupScreen(navController) }
                        composable(
                            route = "dashboard?openAddTrip={openAddTrip}",
                            arguments = listOf(
                                navArgument("openAddTrip") {
                                    type = NavType.BoolType
                                    defaultValue = false
                                }
                            )
                        ) { backStackEntry ->
                            val openAddTrip = backStackEntry.arguments?.getBoolean("openAddTrip") ?: false
                            MainDashboard(navController, openAddTrip = openAddTrip)
                        }
                        composable("profile") { ProfileScreen(navController) }
                        composable("tripDetail/{tripId}") { backStackEntry ->
                            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
                            TripDetailScreen(navController, tripId)
                        }
                        composable("sos") { SOSScreen(navController) }
                    }
                }
            }
        }
    }
}
