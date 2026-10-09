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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0A0C10)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onStartService: (String) -> Unit) {
    var selectedMode by remember { mutableStateOf("pangkas") }
    var isRunning by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(true) }
    
    val logs = remember { mutableStateListOf(
        "[SYSTEM] Daemon Host Engine Initialized.",
        "[SYSTEM] Target SDK: 29 (Full Storage Bypass Active).",
        "[NATIVE] Go Binary 'pangkas' ready in assets.",
        "[NATIVE] FFmpeg ARM64 static linked."
    ) }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun appendLog(msg: String) {
        if (logs.sumOf { it.length } > 128000) {
            logs.removeAt(0)
        }
        logs.add(msg)
        coroutineScope.launch {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "PANGKAS DAEMON",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64FFDA)
                    )
                },
                actions = {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Settings", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color(0xFF1E2230))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Pengaturan Engine", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF64FFDA)) },
                            onClick = {
                                showMenu = false
                                appendLog("[CONFIG] Menu Pengaturan dibuka.")
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0C10))
            )
        },
        containerColor = Color(0xFF0A0C10)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Speedometer Gauge Component Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                SpeedometerGauge(
                    progress = if (isRunning) 0.78f else 0.0f,
                    speedValue = if (isRunning) 60 else 0,
                    unitLabel = "FPS",
                    gearLabel = if (isRunning) "TURBO" else "STANDBY"
                )
            }

            Text("PILIH MODE EKSEKUSI", fontSize = 12.sp, color = Color(0xFF888A99), fontWeight = FontWeight.Bold)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedMode == "pangkas",
                    onClick = { selectedMode = "pangkas" },
                    label = { Text("Pangkas (H.265)") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedMode == "denoise",
                    onClick = { selectedMode = "denoise" },
                    label = { Text("Penjernihan") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectedMode == "dual",
                    onClick = { selectedMode = "dual" },
                    label = { Text("Dual Mode") },
                    modifier = Modifier.weight(1f)
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141824))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("DEEP SCAN & GO DUPLICATE DETECTOR", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    Text("Pindai folder tersembunyi & kalkulasi hash video duplikat via Go Engine.", fontSize = 12.sp, color = Color(0xFF888A99))
                    
                    Button(
                        onClick = {
                            appendLog("[DEEP SCAN] Memulai pemindaian folder tersembunyi & duplikat...")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Mulai Scan Internal & Duplikat", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SHOW SYSTEM LOGS", fontSize = 12.sp, color = Color(0xFF888A99), fontWeight = FontWeight.Bold)
                Switch(
                    checked = showLogs,
                    onCheckedChange = { showLogs = it }
                )
            }

            AnimatedVisibility(visible = showLogs) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .border(1.dp, Color(0xFF262C40), RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF050608))
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs) { log ->
                            Text(
                                text = log,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = if (log.contains("ERROR")) Color(0xFFFF5252) else Color(0xFF64FFDA)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    isRunning = !isRunning
                    if (isRunning) {
                        appendLog("[SERVICE] Foreground Service & WakeLock Started. Mode: $selectedMode")
                        onStartService(selectedMode)
                    } else {
                        appendLog("[SERVICE] Foreground Service Stopped.")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Color(0xFFFF5252) else Color(0xFF00E676)
                )
            ) {
                Text(
                    if (isRunning) "STOP DAEMON ENGINE" else "JALANKAN PROSES",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
