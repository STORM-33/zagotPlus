package com.zagot.zagotplus.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zagot.zagotplus.R

@Composable
fun PinScreen(
    onAuthenticated: () -> Unit,
    viewModel: PinViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isAuthenticated) {
        if (uiState.isAuthenticated) {
            onAuthenticated()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = when (uiState.mode) {
                    PinMode.SET_PIN -> stringResource(R.string.pin_set_title)
                    PinMode.CONFIRM_PIN -> stringResource(R.string.pin_confirm_title)
                    PinMode.VERIFY_PIN -> stringResource(R.string.pin_verify_title)
                },
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            PinDots(
                pinLength = uiState.currentPin.length,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            uiState.errorMessage?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            PinKeypad(
                onDigitClick = viewModel::onDigitPressed,
                onBackspaceClick = viewModel::onBackspacePressed,
                enabled = !uiState.isLockedOut
            )
        }
    }
}

@Composable
private fun PinDots(
    pinLength: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < pinLength) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
            )
        }
    }
}

@Composable
private fun PinKeypad(
    onDigitClick: (Int) -> Unit,
    onBackspaceClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Rows 1-3: digits 1-9
        for (row in 0..2) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                for (col in 1..3) {
                    val digit = row * 3 + col
                    KeypadButton(
                        text = digit.toString(),
                        onClick = { onDigitClick(digit) },
                        enabled = enabled
                    )
                }
            }
        }

        // Row 4: empty, 0, backspace
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            KeypadButton(
                text = "",
                onClick = { },
                enabled = false,
                modifier = Modifier.size(88.dp)
            )
            
            KeypadButton(
                text = "0",
                onClick = { onDigitClick(0) },
                enabled = enabled
            )
            
            IconButton(
                onClick = onBackspaceClick,
                enabled = enabled,
                modifier = Modifier.size(88.dp)
            ) {
                Icon(
                    Icons.Default.Backspace,
                    contentDescription = stringResource(R.string.pin_backspace),
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(88.dp)
            .clip(CircleShape)
            .background(
                if (enabled && text.isNotEmpty()) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
            .clickable(enabled = enabled && text.isNotEmpty()) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (text.isNotEmpty()) {
            Text(
                text = text,
                style = MaterialTheme.typography.displaySmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
        }
    }
}
