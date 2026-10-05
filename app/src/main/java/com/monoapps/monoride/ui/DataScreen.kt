package com.monoapps.monoride.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monoapps.monoride.RideViewModel
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import java.util.Locale

@Composable
fun DataScreen(viewModel: RideViewModel, onBack: () -> Unit) {
    val streets by viewModel.streets.collectAsState()
    val status by viewModel.downloadStatus.collectAsState()
    var city by remember { mutableStateOf(viewModel.city) }
    val busy = status != null && status?.startsWith("Failed") != true

    Column(modifier = Modifier.fillMaxSize()) {
        AppBar(
            title = { TextMMD("Street data", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            },
        )

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(16.dp))
            TextMMD(
                "MonoRide needs your city's streets once, from OpenStreetMap. " +
                    "This is the only time the app uses the internet.",
                fontSize = 14.sp,
            )

            Spacer(modifier = Modifier.height(20.dp))
            TextMMD("City (municipality name)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            TextFieldMMD(
                value = city,
                onValueChange = { city = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { TextMMD("e.g. Rotterdam") },
            )

            Spacer(modifier = Modifier.height(16.dp))
            BlackButton(
                text = if (busy) "Downloading…" else "Download streets",
                onClick = {
                    viewModel.clearDownloadError()
                    viewModel.downloadStreets(city.trim())
                },
                enabled = !busy && city.isNotBlank(),
            )

            status?.let {
                Spacer(modifier = Modifier.height(12.dp))
                TextMMD(it, fontSize = 14.sp)
            }

            streets?.let { s ->
                Spacer(modifier = Modifier.height(28.dp))
                TextMMD("Current data", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                TextMMD(
                    "%s: %d streets, %.0f km".format(Locale.US, s.city, s.ways.size, s.totalLength / 1000),
                    fontSize = 16.sp,
                )

                Spacer(modifier = Modifier.height(28.dp))
                OutlinedButtonMMD(
                    onClick = { viewModel.recompute() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                ) {
                    TextMMD("Recompute coverage from rides", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextMMD(
                    "Replays all saved rides against the street data. Use after re-downloading.",
                    fontSize = 12.sp,
                )
            }
        }
    }
}
