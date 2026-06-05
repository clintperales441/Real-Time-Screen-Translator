package com.mangalens.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangalens.feature.ocr.domain.DetectedText
import com.mangalens.feature.translator.domain.Translation
import com.mangalens.feature.overlay.domain.OverlayItem
import com.mangalens.feature.history.ui.HistoryState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isCaptureGranted: Boolean,
    isCapturing: Boolean,
    isOcrProcessing: Boolean,
    ocrResults: List<DetectedText>,
    isTranslating: Boolean,
    translation: Translation?,
    isOverlayVisible: Boolean,
    overlayItems: List<OverlayItem>,
    historyState: HistoryState,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onRunOcr: () -> Unit,
    onRunTranslation: () -> Unit,
    onShowOverlay: () -> Unit,
    onHideOverlay: () -> Unit,
    onLoadHistory: () -> Unit,
    onOpenPermissions: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MangaLens", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenPermissions) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatusRow("Service Status", if (isCapturing) "ACTIVE" else "INACTIVE", if (isCapturing) Color(0xFF4CAF50) else Color(0xFFF44336))
                    StatusRow("Permission", if (isCaptureGranted) "GRANTED" else "REQUIRED", if (isCaptureGranted) Color(0xFF4CAF50) else Color(0xFFFF9800))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Main Action Button
            if (!isCapturing) {
                Button(
                    onClick = onStartCapture,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    enabled = isCaptureGranted
                ) {
                    Text("START TRANSLATION", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onStopCapture,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                ) {
                    Text("STOP TRANSLATION", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Stats Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Live Statistics", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Detected Blocks: ${ocrResults.size}")
                    Text("Last Translation: ${translation?.translatedText ?: "None"}")
                    Text("Active Overlays: ${overlayItems.size}")
                }
            }
        }
    }
}

@Composable
fun StatusRow(label: String, status: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontWeight = FontWeight.Medium)
        Surface(
            color = color,
            shape = CircleShape
        ) {
            Text(
                text = status,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
