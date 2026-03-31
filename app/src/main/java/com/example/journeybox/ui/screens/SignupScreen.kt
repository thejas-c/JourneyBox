package com.example.journeybox.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.journeybox.ui.viewmodel.AuthState
import com.example.journeybox.ui.viewmodel.AuthViewModel

@Composable
fun SignupScreen(navController: NavController, viewModel: AuthViewModel = viewModel()) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    val authState by viewModel.authState.collectAsState()
    val usernameAvailable by viewModel.usernameAvailable.collectAsState()

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            navController.navigate("dashboard") {
                popUpTo("signup") { inclusive = true }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (val state = authState) {
            is AuthState.WaitingForVerification -> {
                EmailVerificationView(
                    email = state.email,
                    onResendClick = { viewModel.resendVerificationEmail() },
                    onBackToSignup = { viewModel.resetState() }
                )
            }
            else -> {
                SignupFormView(
                    displayName = displayName,
                    onDisplayNameChange = { displayName = it },
                    username = username,
                    onUsernameChange = {
                        username = it.filter { char -> char.isLetterOrDigit() || char == '_' }
                        viewModel.checkUsername(username)
                    },
                    usernameAvailable = usernameAvailable,
                    email = email,
                    onEmailChange = { email = it },
                    password = password,
                    onPasswordChange = { password = it },
                    authState = authState,
                    onSignupClick = {
                        viewModel.signup(email, password, username, displayName)
                    },
                    onLoginClick = { navController.navigate("login") }
                )
            }
        }

        if (authState is AuthState.Error) {
            Text(
                text = (authState as AuthState.Error).message,
                color = Color.Red,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
fun SignupFormView(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    usernameAvailable: Boolean?,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    authState: AuthState,
    onSignupClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    val isMinLength = password.length >= 8
    val hasUppercase = password.any { it.isUpperCase() }
    val hasLowercase = password.any { it.isLowerCase() }
    val hasDigit = password.any { it.isDigit() }
    val hasSpecial = password.any { !it.isLetterOrDigit() }
    val isPasswordValid = isMinLength && hasUppercase && hasLowercase && hasDigit && hasSpecial

    Text(
        text = "Create Account",
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.secondary
    )

    Spacer(modifier = Modifier.height(32.dp))

    OutlinedTextField(
        value = displayName,
        onValueChange = onDisplayNameChange,
        label = { Text("Full Name") },
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text("Username") },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            if (username.length >= 3) {
                when (usernameAvailable) {
                    true -> Icon(Icons.Default.CheckCircle, "Available", tint = Color.Green)
                    false -> Icon(Icons.Default.Warning, "Taken", tint = Color.Red)
                    else -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        },
        supportingText = {
            if (usernameAvailable == false) Text("Username is already taken", color = Color.Red)
        }
    )

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text("Email") },
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("Password") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        isError = password.isNotEmpty() && !isPasswordValid
    )

    if (password.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            PasswordRequirementItem("Minimum 8 characters", isMinLength)
            PasswordRequirementItem("At least 1 uppercase letter", hasUppercase)
            PasswordRequirementItem("At least 1 lowercase letter", hasLowercase)
            PasswordRequirementItem("At least 1 numeric character", hasDigit)
            PasswordRequirementItem("At least 1 special character", hasSpecial)
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    if (authState is AuthState.Loading) {
        CircularProgressIndicator()
    } else {
        Button(
            onClick = onSignupClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = usernameAvailable == true && email.isNotEmpty() && isPasswordValid,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("SIGN UP")
        }

        TextButton(onClick = onLoginClick) {
            Text("Already have an account? Login")
        }
    }
}

@Composable
fun PasswordRequirementItem(text: String, isMet: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = if (isMet) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            tint = if (isMet) Color.Green else Color.Red,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (isMet) Color.Green else Color.Red
        )
    }
}

@Composable
fun EmailVerificationView(
    email: String,
    onResendClick: () -> Unit,
    onBackToSignup: () -> Unit
) {
    Icon(
        imageVector = Icons.Default.Email,
        contentDescription = null,
        modifier = Modifier.size(100.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    
    Spacer(modifier = Modifier.height(24.dp))
    
    Text(
        text = "Verify your email",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.secondary
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    Text(
        text = "We've sent a real verification link to:\n$email\n\nPlease check your inbox (and spam folder) and click the link to activate your account.",
        fontSize = 16.sp,
        color = Color.Gray,
        textAlign = TextAlign.Center,
        lineHeight = 22.sp
    )

    Spacer(modifier = Modifier.height(32.dp))

    CircularProgressIndicator(modifier = Modifier.size(32.dp))
    Text(
        text = "Waiting for verification...",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )

    Spacer(modifier = Modifier.height(32.dp))

    Button(
        onClick = onResendClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Resend Verification Email")
    }

    TextButton(onClick = onBackToSignup) {
        Text("Back to Signup")
    }
}
