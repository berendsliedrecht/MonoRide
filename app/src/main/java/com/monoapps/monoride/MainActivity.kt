package com.monoapps.monoride

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monoapps.monoride.ui.DataScreen
import com.monoapps.monoride.ui.MapScreen
import com.monoapps.monoride.ui.RidesScreen
import com.mudita.mmd.ThemeMMD

enum class Screen { Map, Rides, Data }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThemeMMD {
                RideApp()
            }
        }
    }
}

@Composable
fun RideApp(viewModel: RideViewModel = viewModel()) {
    var screen by remember { mutableStateOf(Screen.Map) }

    // Physical back returns to the map instead of closing the app.
    BackHandler(enabled = screen != Screen.Map) { screen = Screen.Map }

    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        when (screen) {
            Screen.Map -> MapScreen(
                viewModel = viewModel,
                onOpenRides = { screen = Screen.Rides },
                onOpenData = { screen = Screen.Data },
            )
            Screen.Rides -> RidesScreen(viewModel = viewModel, onBack = { screen = Screen.Map })
            Screen.Data -> DataScreen(viewModel = viewModel, onBack = { screen = Screen.Map })
        }
    }
}
