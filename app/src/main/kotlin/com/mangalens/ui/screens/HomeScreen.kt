package com.mangalens.ui.screens

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
import com.mangalens.feature.history.ui.HistoryState
import com.mangalens.feature.ocr.domain.DetectedText
import com.mangalens.feature.overlay.domain.OverlayItem
import com.mangalens.feature.translator.domain.Translation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isCaptureGranted: Boolean,
    isCapturing: Boolean,
    isGeminiActive: Boolean,
    geminiEnabled: Boolean,
    onToggleGemini: (Boolean) -> Unit,
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
            // Status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusRow(
                        label = "Service",
                        status = if (isCapturing) "ACTIVE" else "INACTIVE",
                        color = if (isCapturing) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                    StatusRow(
                        label = "Permission",
                        status = if (isCaptureGranted) "GRANTED" else "REQUIRED",
                        color = if (isCaptureGranted) Color(0xFF4CAF50) else Color(0xFFFF9800)
                    )
                    StatusRow(
                        label = "Translation",
                        status = if (isGeminiActive && geminiEnabled) "GEMINI AI ✦"
                        else "ML KIT (offline)",
                        color = if (isGeminiActive && geminiEnabled) Color(0xFF7C4DFF)
                        else Color(0xFF9E9E9E)
                    )
                }
            }

            // Gemini toggle — only visible when API key is saved
            if (isGeminiActive) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF7C4DFF).copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Use Gemini AI",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFF7C4DFF)
                            )
                            Text(
                                if (geminiEnabled) "AI translation active"
                                else "ML Kit (offline) active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = geminiEnabled,
                            onCheckedChange = onToggleGemini,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF7C4DFF),
                                checkedTrackColor = Color(0xFF7C4DFF).copy(alpha = 0.4f)
                            )
                        )
                    }
                }
            }

            // Gemini setup prompt — only when no key saved and not capturing
            if (!isGeminiActive && !isCapturing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF7C4DFF).copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✦", fontSize = 20.sp, color = Color(0xFF7C4DFF))
                        Column {
                            Text(
                                "Add Gemini API key for accurate translations",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7C4DFF)
                            )
                            Text(
                                "Tap ⚙ Settings → paste your key → Save → restart capture",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Pushes buttons to the bottom — use fillMaxHeight fraction instead
            // of weight() which requires a weighted parent
            Spacer(modifier = Modifier.height(16.dp))

            if (!isCapturing) {
                Button(
                    onClick = onStartCapture,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isGeminiActive && geminiEnabled)
                            Color(0xFF7C4DFF) else Color(0xFF4CAF50)
                    ),
                    enabled = isCaptureGranted
                ) {
                    Text(
                        if (isGeminiActive && geminiEnabled) "START (Gemini AI)"
                        else "START TRANSLATION",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onStopCapture,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336)
                    )
                ) {
                    Text("STOP TRANSLATION", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Stats card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Live Statistics",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
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
        Surface(color = color, shape = CircleShape) {
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