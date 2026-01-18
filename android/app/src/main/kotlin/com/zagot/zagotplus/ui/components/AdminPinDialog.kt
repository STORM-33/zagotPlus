package com.zagot.zagotplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.zagot.zagotplus.Config
import com.zagot.zagotplus.data.preferences.AuthPreferences

/**
 * Dialog for verifying admin password before protected actions.
 * Uses the same visual style as the main PIN screen.
 */
@Composable
fun AdminPinDialog(
    authPreferences: AuthPreferences,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val expectedLength = Config.ADMIN_PIN_LENGTH
    
    // Auto-submit when PIN reaches expected length
    LaunchedEffect(currentPin) {
        if (currentPin.length == expectedLength) {
            if (authPreferences.verifyAdminPin(currentPin)) {
                onSuccess()
            } else {
                errorMessage = "Неправильний пароль адміністратора"
                currentPin = ""
            }
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Введіть пароль адміністратора",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                // PIN dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    repeat(expectedLength) { index ->
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index < currentPin.length) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    }
                                )
                        )
                    }
                }
                
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                
                // Compact keypad
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (row in 0..2) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            for (col in 1..3) {
                                val digit = row * 3 + col
                                CompactKeypadButton(
                                    text = digit.toString(),
                                    onClick = {
                                        if (currentPin.length < expectedLength) {
                                            currentPin += digit.toString()
                                            errorMessage = null
                                        }
                                    }
                                )
                            }
                        }
                    }
                    
                    // Last row: empty, 0, backspace
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Spacer(modifier = Modifier.size(56.dp))
                        
                        CompactKeypadButton(
                            text = "0",
                            onClick = {
                                if (currentPin.length < expectedLength) {
                                    currentPin += "0"
                                    errorMessage = null
                                }
                            }
                        )
                        
                        IconButton(
                            onClick = {
                                if (currentPin.isNotEmpty()) {
                                    currentPin = currentPin.dropLast(1)
                                    errorMessage = null
                                }
                            },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Видалити",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Скасувати")
                }
            }
        }
    }
}

@Composable
private fun CompactKeypadButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .bouncyClick(enabled = true, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
