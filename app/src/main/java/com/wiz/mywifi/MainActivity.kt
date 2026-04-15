package com.wiz.mywifi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.wiz.mywifi.ui.theme.MyWifiTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyWifiTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("Wi-Fi Monitor") }
                        )
                    }
                ) { innerPadding ->
                    WifiStatusScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun WifiStatusScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isConnected by remember { mutableStateOf(false) }
    var ssid by remember { mutableStateOf("Desconocido") }
    var bssid by remember { mutableStateOf("N/A") }
    var speed by remember { mutableStateOf("0 Mbps") }
    var freq by remember { mutableStateOf("0 MHz") }
    var scanResults by remember { mutableStateOf<List<ScanResult>>(emptyList()) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasLocationPermission = isGranted
    }

    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun startScan() {
        if (hasLocationPermission) {
            wifiManager.startScan()
        }
    }

    fun updateWifiInfo() {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (isWifi) {
            isConnected = true
            @Suppress("DEPRECATION")
            val wifiInfo: WifiInfo? = wifiManager.connectionInfo

            if (wifiInfo != null) {
                ssid = wifiInfo.ssid.replace("\"", "")
                bssid = wifiInfo.bssid ?: "N/A"
                speed = "${wifiInfo.linkSpeed} Mbps"
                freq = "${wifiInfo.frequency} MHz"
            }
        } else {
            isConnected = false
            ssid = "Desconectado"
        }
    }

    DisposableEffect(Unit) {
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { updateWifiInfo() }
            override fun onLost(network: Network) { updateWifiInfo() }
            override fun onCapabilitiesChanged(network: Network, cap: NetworkCapabilities) { updateWifiInfo() }
        }

        val wifiScanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                if (success) {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        scanResults = wifiManager.scanResults
                    }
                }
            }
        }

        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(wifiScanReceiver, intentFilter)

        connectivityManager.registerNetworkCallback(networkRequest, callback)
        updateWifiInfo()
        startScan()

        onDispose {
            connectivityManager.unregisterNetworkCallback(callback)
            context.unregisterReceiver(wifiScanReceiver)
        }
    }

    val mainColor = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = mainColor
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = ssid,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = if (isConnected) "Conectado" else "Desconectado",
            style = MaterialTheme.typography.bodyMedium,
            color = mainColor
        )

        Spacer(modifier = Modifier.height(48.dp))

        if (isConnected) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SimpleInfoRow(icon = Icons.Default.Info, label = "BSSID", value = bssid)
                SimpleInfoRow(icon = Icons.Default.Refresh, label = "Velocidad", value = speed)
                SimpleInfoRow(icon = Icons.Default.LocationOn, label = "Frecuencia", value = freq)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Redes cercanas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { startScan() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Escanear")
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(scanResults) { result ->
                WifiScanItem(result)
            }
        }

        if (!hasLocationPermission) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Permiso de ubicación necesario",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Se requiere para mostrar el nombre de la red.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(
                        onClick = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("CONCEDER")
                    }
                }
            }
        }
    }
}

@Composable
fun WifiScanItem(scanResult: ScanResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = if (scanResult.SSID.isEmpty()) "Red oculta" else scanResult.SSID,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Intensidad: ${scanResult.level} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SimpleInfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
