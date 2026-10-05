package com.monoapps.monoride.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monoapps.monoride.RideViewModel
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun RidesScreen(viewModel: RideViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val rides by viewModel.rides.collectAsState()
    var toDelete by remember { mutableStateOf<File?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml"),
    ) { uri ->
        if (uri != null) viewModel.exportMerged(uri) { ok ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    if (ok) "Exported all rides" else "Export failed",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppBar(
            title = { TextMMD("Rides", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (rides.isNotEmpty()) {
                    IconButton(onClick = { exportLauncher.launch("monoride-all.gpx") }) {
                        Icon(Icons.Outlined.Share, contentDescription = "Export all as one GPX")
                    }
                }
            },
        )

        if (rides.isEmpty()) {
            TextMMD(
                "No rides yet. Start one from the map screen.",
                fontSize = 16.sp,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyColumnMMD(modifier = Modifier.fillMaxSize()) {
                items(rides.size) { i ->
                    val ride = rides[i]
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
                            TextMMD(rideTitle(ride.file), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            TextMMD("%.1f km".format(Locale.US, ride.distanceKm), fontSize = 14.sp)
                        }
                        IconButton(onClick = { toDelete = ride.file }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete ride")
                        }
                    }
                    HorizontalDividerMMD()
                }
            }
        }
    }

    toDelete?.let { file ->
        ConfirmDialog(
            message = "Delete ${rideTitle(file)}? Its streets are removed from your coverage.",
            confirmText = "Delete",
            onConfirm = {
                viewModel.deleteRide(file)
                toDelete = null
            },
            onDismiss = { toDelete = null },
        )
    }
}

private fun rideTitle(file: File): String {
    // ride-yyyyMMdd-HHmmss.gpx -> readable date
    val raw = file.name.removePrefix("ride-").removeSuffix(".gpx")
    return try {
        val date = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).parse(raw)!!
        SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault()).format(date)
    } catch (e: Exception) {
        file.name
    }
}
