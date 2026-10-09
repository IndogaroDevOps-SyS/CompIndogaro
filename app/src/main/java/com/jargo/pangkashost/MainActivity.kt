package com.jargo.pangkashost

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0D0E15)
                ) {
                    DashboardScreen(
                        onStartService = { mode ->
                            if (checkStoragePermission()) {
                                val intent = Intent(this, PangkasService::class.java).apply {
                                    putExtra("MODE", mode)
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    startForegroundService(intent)
                                } else {
                                    startService(intent)
                                }
                            } else {
                                requestStoragePermission()
                            }
                        }
                    )
                }
            }
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }
    }
}

@Composable
fun DashboardScreen(onStartService: (String) -> Unit) {
    var selectedMode by remember { mutableStateOf("pangkas") }
    var isRunning by remember { mutableStateOf(false) }

    // Lottie Animation Loading
    val composition by rememberLottieComposition(LottieCompositionSpec.Url("https://assets9.lottiefiles.com/packages/lf20_qq89p8nh.json"))
    val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "PANGKAS ENGINE DAEMON",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64FFDA)
        )

        // Animation Container
        Card(
            modifier = Modifier.fillMaxWidth().height(160.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2230))
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (isRunning) {
                    LottieAnimation(
                        composition = composition,
                        progress = { progress },
                        modifier = Modifier.size(140.dp)
                    )
                } else {
                    Text("Engine Idle / Ready", color = Color.Gray, fontSize = 14.sp)
                }
            }
        }

        // Mode Selection Container
        Text("PILIH MODE EKSEKUSI", fontSize = 14.sp, color = Color.LightGray)
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedMode == "pangkas",
                onClick = { selectedMode = "pangkas" },
                label = { Text("Pangkas (H.265)") }
            )
            FilterChip(
                selected = selectedMode == "denoise",
                onClick = { selectedMode = "denoise" },
                label = { Text("Penjernihan") }
            )
            FilterChip(
                selected = selectedMode == "dual",
                onClick = { selectedMode = "dual" },
                label = { Text("Pangkas + Jernih") }
            )
        }

        // Container Deep Scanner & Duplicate Detector
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171A26))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("DEEP SCAN & GO DUPLICATE DETECTOR", fontWeight = FontWeight.Bold, color = Color.White)
                Text("Pindai folder tersembunyi & kalkulasi hash video duplikat via Go Engine.", fontSize = 12.sp, color = Color.Gray)
                
                Button(
                    onClick = { /* Scan Trigger */ },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29B6F6))
                ) {
                    Text("Mulai Scan Internal & Duplikat", color = Color.Black)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action Trigger Button
        Button(
            onClick = {
                isRunning = !isRunning
                if (isRunning) onStartService(selectedMode)
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) Color(0xFFFF5252) else Color(0xFF00E676)
            )
        ) {
            Text(
                if (isRunning) "STOP DAEMON ENGINE" else "JALANKAN PROSES",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}
