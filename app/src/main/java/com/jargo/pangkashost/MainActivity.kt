package com.jargo.pangkashost

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.regex.Pattern

class MainActivity : ComponentActivity() {

    private val logList = mutableStateListOf(
        "[SYSTEM] Daemon Host Engine Initialized.",
        "[SYSTEM] Target SDK: 29 (Full Storage Access Active).",
        "[NATIVE] Real-time Process Stream Ready."
    )

    var worker1Progress by mutableStateOf(0)
    var worker1Efficiency by mutableStateOf(0)
    var worker1File by mutableStateOf("Idle")
    var scanResultJson by mutableStateOf<String?>(null)

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val rawMsg = intent?.getStringExtra("LOG_MESSAGE") ?: return
            val msg = sanitizeAnsi(rawMsg)

            if (msg.isBlank()) return

            if (logList.sumOf { it.length } > 128000) {
                logList.removeAt(0)
            }
            logList.add(msg)

            // Parse Stream Stats untuk Speedometer/Gauge & Log Go
            if (msg.contains("WORKER_1") || msg.contains("[W1]")) {
                try {
                    val payload = if (msg.contains("WORKER_1] ")) msg.substringAfter("WORKER_1] ") else msg
                    val parts = payload.split("|")
                    for (part in parts) {
                        if (part.startsWith("PROGRESS:")) worker1Progress = part.substringAfter(":").trim().toIntOrNull() ?: worker1Progress
                        if (part.startsWith("EFFICIENCY:")) worker1Efficiency = part.substringAfter(":").trim().toIntOrNull() ?: worker1Efficiency
                        if (part.startsWith("FILE:")) worker1File = part.substringAfter(":").trim()
                    }
                } catch (_: Exception) {}
            }

            if (msg.contains("RESULT_JSON")) {
                scanResultJson = msg.substringAfter("RESULT_JSON] ")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate()

        val filter = IntentFilter("com.jargo.pangkashost.LOG_EVENT")
        // Menggunakan ContextCompat agar kompatibel dari API lama hingga Android 13+ (Tiramisu)
        ContextCompat.registerReceiver(
            this,
            logReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0A0C10)
                ) {
                    DashboardScreen(
                        logs = logList,
                        worker1Progress = worker1Progress,
                        worker1Efficiency = worker1Efficiency,
                        worker1File = worker1File,
                        scanResultJson = scanResultJson,
                        onDismissScanDialog = { scanResultJson = null },
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

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(logReceiver)
        } catch (_: Exception) {}
    }

    private fun sanitizeAnsi(input: String): String {
        val ansiRegex = Pattern.compile("\u001B\\[[;\\d]*[A-Za-zKk]")
        return ansiRegex.matcher(input).replaceAll("").trim()
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
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    logs: List<String>,
    worker1Progress: Int,
    worker1Efficiency: Int,
    worker1File: String,
    scanResultJson: String?,
    onDismissScanDialog: () -> Unit,
    onStartService: (String) -> Unit
) {
    var selectedMode by remember { mutableStateOf("pangkas") }
    var isRunning by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showLogBottomSheet by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
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
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color(0xFF1E2230))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Terminal Log System", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.List, contentDescription = null, tint = Color(0xFF64FFDA)) },
                            onClick = {
                                showMenu = false
                                showLogBottomSheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Pengaturan Engine", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF64FFDA)) },
                            onClick = {
                                showMenu = false
                                showSettingsDialog = true
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10141E))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WorkerGauge(
                        workerTitle = "WORKER 1 (COMPRESS)",
                        progressPercent = if (isRunning) worker1Progress else 0,
                        efficiencyPercent = if (isRunning) worker1Efficiency else 0,
                        activeFileName = if (isRunning) worker1File else "Idle"
                    )
                    WorkerGauge(
                        workerTitle = "WORKER 2 (DENOISE/SCAN)",
                        progressPercent = 0,
                        efficiencyPercent = 0,
                        activeFileName = "Idle"
                    )
                }
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
                    Text("Pindai folder tersembunyi & kalkulasi SHA-256 hash video duplikat via Go Engine.", fontSize = 12.sp, color = Color(0xFF888A99))
                    Button(
                        onClick = { onStartService("scan") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Mulai Scan Internal & Duplikat", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    isRunning = !isRunning
                    if (isRunning) {
                        onStartService(selectedMode)
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

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (scanResultJson != null) {
            val json = try { JSONObject(scanResultJson) } catch (_: Exception) { null }
            val totalFiles = json?.optInt("total_files") ?: 0
            val dupCount = json?.optInt("duplicate_count") ?: 0
            val savedBytes = json?.optLong("saved_bytes") ?: 0L
            val savedMB = savedBytes / (1024 * 1024)

            AlertDialog(
                onDismissRequest = onDismissScanDialog,
                icon = {
                    Icon(
                        if (dupCount > 0) Icons.Default.Info else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (dupCount > 0) Color(0xFF00B0FF) else Color(0xFF00E676)
                    )
                },
                title = {
                    Text(if (dupCount > 0) "Hasil Pemindaian Scan" else "Sistem Bersih (All Clean)")
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Total Video Di-scan: $totalFiles file")
                        Text("Video Duplikat Ditemukan: $dupCount file")
                        Text("Potensi Hemat Storage: $savedMB MB")
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDismissScanDialog) {
                        Text("OK", color = Color(0xFF64FFDA))
                    }
                },
                containerColor = Color(0xFF141824)
            )
        }

        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Pengaturan Engine", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("CRF Threshold: 28 (H.265 Standard)", color = Color.Gray, fontSize = 12.sp)
                        Text("Max Threads: 4 Core", color = Color.Gray, fontSize = 12.sp)
                        Text("Scan Hidden Folder: Aktif", color = Color.Gray, fontSize = 12.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("Simpan", color = Color(0xFF64FFDA))
                    }
                },
                containerColor = Color(0xFF141824)
            )
        }

        if (showLogBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showLogBottomSheet = false },
                containerColor = Color(0xFF0D0E15)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "SYSTEM TERMINAL LOGS (REALTIME STREAM)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF64FFDA),
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
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
                                    color = when {
                                        log.contains("ERROR") || log.contains("CRITICAL") -> Color(0xFFFF5252)
                                        log.contains("SUCCESS") -> Color(0xFF00E676)
                                        log.contains("DUPLICATE") || log.contains("RESULT_JSON") -> Color(0xFF00B0FF)
                                        else -> Color(0xFF64FFDA)
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun WorkerGauge(
    workerTitle: String,
    progressPercent: Int,
    efficiencyPercent: Int,
    activeFileName: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(workerTitle, fontSize = 10.sp, color = Color(0xFF888A99), fontWeight = FontWeight.Bold)
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progressPercent / 100f },
                modifier = Modifier.size(64.dp),
                color = Color(0xFF64FFDA),
                trackColor = Color(0xFF1E2230),
                strokeWidth = 6.dp
            )
            Text("$progressPercent%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Text("Efficiency: $efficiencyPercent%", fontSize = 10.sp, color = Color(0xFF64FFDA))
        Text(activeFileName, fontSize = 9.sp, color = Color(0xFF888A99), maxLines = 1)
    }
}
