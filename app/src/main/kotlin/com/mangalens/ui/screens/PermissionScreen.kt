package com.mangalens.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(
    isOverlayGranted: Boolean,
    isCaptureGranted: Boolean,
    geminiApiKey: String,
    onRequestOverlay: () -> Unit,
    onRequestCapture: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onBack: () -> Unit
) {
    var apiKeyInput by remember { mutableStateOf(geminiApiKey) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var apiKeySaved by remember { mutableStateOf(geminiApiKey.isNotBlank()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Setup MangaLens",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Grant permissions and optionally add a Gemini API key for high-quality translations.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Overlay permission
            PermissionCard(
                title = "Display Over Other Apps",
                description = "Allows translations to float over your manga reader.",
                isGranted = isOverlayGranted,
                onClick = onRequestOverlay
            )

            // Screen capture permission
            PermissionCard(
                title = "Screen Recording",
                description = "Required to see the Japanese text on screen.",
                isGranted = isCaptureGranted,
                onClick = onRequestCapture
            )

            // Gemini API key card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (apiKeySaved)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (apiKeySaved) Icons.Default.CheckCircle
                            else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (apiKeySaved) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                "Gemini API Key",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (apiKeySaved) "High-quality AI translations active"
                                else "Optional — enables Gemini Flash translations",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyInput = it
                            apiKeySaved = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Paste your API key here") },
                        singleLine = true,
                        visualTransformation = if (apiKeyVisible)
                            VisualTransformation.None
                        else
                            PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                Text(if (apiKeyVisible) "Hide" else "Show", fontSize = 11.sp)
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    )

                    Text(
                        text = "Get a free key at aistudio.google.com → Get API key",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            onSaveApiKey(apiKeyInput)
                            apiKeySaved = apiKeyInput.isNotBlank()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        enabled = apiKeyInput.isNotBlank()
                    ) {
                        Text(if (apiKeySaved) "✓ Saved" else "Save API Key",
                            fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isOverlayGranted && isCaptureGranted) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("DONE", fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    "Please grant all permissions to continue",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isGranted) Color(0xFF4CAF50) else Color(0xFFFF9800),
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!isGranted) {
                Button(
                    onClick = onClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("GRANT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text("ENABLED", color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
        }
    }
}