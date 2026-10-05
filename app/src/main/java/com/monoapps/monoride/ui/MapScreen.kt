package com.monoapps.monoride.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Path
import android.location.LocationManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monoapps.monoride.RideRecorder
import com.monoapps.monoride.RideService
import com.monoapps.monoride.RideViewModel
import com.monoapps.monoride.data.Streets
import com.monoapps.monoride.data.Way
import com.mudita.mmd.components.text.TextMMD
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun MapScreen(
    viewModel: RideViewModel,
    onOpenRides: () -> Unit,
    onOpenData: () -> Unit,
) {
    val context = LocalContext.current
    val streets by viewModel.streets.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val ridden by viewModel.ridden.collectAsState()
    val rec by RideRecorder.state.collectAsState()

    var centerX by remember { mutableFloatStateOf(0f) }
    var centerY by remember { mutableFloatStateOf(0f) }
    var mpp by remember { mutableFloatStateOf(0f) } // meters per pixel; 0 = uninitialized

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) RideService.start(context)
        else Toast.makeText(context, "Location permission needed to record", Toast.LENGTH_LONG).show()
    }

    fun startRide() {
        val granted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            RideService.start(context)
        } else {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    fun centerOnLocation() {
        val s = streets ?: return
        val lat: Double?
        val lon: Double?
        if (rec.lastLat != null) {
            lat = rec.lastLat; lon = rec.lastLon
        } else {
            val known = try {
                (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
                    .getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } catch (e: SecurityException) {
                null
            }
            lat = known?.latitude; lon = known?.longitude
        }
        if (lat == null || lon == null) {
            Toast.makeText(context, "No location fix yet", Toast.LENGTH_SHORT).show()
            return
        }
        centerX = s.projection.x(lon)
        centerY = s.projection.y(lat)
        if (mpp > 2f) mpp = 2f
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppBar(
            title = {
                Column {
                    TextMMD("MonoRide", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    val sub = stats?.let {
                        "%.1f%% · %.0f of %.0f km".format(Locale.US, it.percent, it.riddenKm, it.totalKm)
                    } ?: "No street data"
                    TextMMD(sub, fontSize = 12.sp)
                }
            },
            actions = {
                IconButton(onClick = onOpenRides) {
                    Icon(Icons.AutoMirrored.Outlined.List, contentDescription = "Rides")
                }
                IconButton(onClick = onOpenData) {
                    Icon(Icons.Outlined.Download, contentDescription = "Street data")
                }
            },
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val s = streets
            if (s == null) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TextMMD("No street data yet.", fontSize = 16.sp)
                    TextMMD(
                        "Download your city's streets to see the map and your progress. Rides are recorded either way.",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    )
                    BlackButton(text = "Download streets", onClick = onOpenData)
                }
            } else {
                StreetMap(
                    streets = s,
                    ridden = ridden,
                    centerX = centerX, centerY = centerY, mpp = mpp,
                    markerLat = rec.lastLat, markerLon = rec.lastLon,
                    onInit = { cx, cy, m -> centerX = cx; centerY = cy; mpp = m },
                    onPan = { dx, dy -> centerX += dx; centerY += dy },
                )
            }

            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MapButton(Icons.Outlined.Add, "Zoom in") { if (mpp > 0f) mpp = (mpp / 2f).coerceAtLeast(0.5f) }
                MapButton(Icons.Outlined.Remove, "Zoom out") { if (mpp > 0f) mpp = (mpp * 2f).coerceAtMost(256f) }
                MapButton(Icons.Outlined.MyLocation, "My location") { centerOnLocation() }
            }
        }

        BottomBar(
            recording = rec.recording,
            startedAt = rec.startedAt,
            distanceM = rec.distanceM,
            onStart = { startRide() },
            onStop = { RideService.stop(context) },
        )
    }
}

@Composable
private fun BottomBar(
    recording: Boolean,
    startedAt: Long,
    distanceM: Double,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (recording) {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(startedAt) {
                while (true) {
                    now = System.currentTimeMillis()
                    delay(1000)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextMMD(elapsed(now - startedAt), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                TextMMD("%.1f km".format(Locale.US, distanceM / 1000), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            BlackButton(text = "Stop ride", onClick = onStop)
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            BlackButton(text = "Start ride", onClick = onStart)
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun MapButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(Color.White)
            .border(2.dp, Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.Black)
    }
}

@Composable
private fun StreetMap(
    streets: Streets,
    ridden: Set<Long>,
    centerX: Float, centerY: Float, mpp: Float,
    markerLat: Double?, markerLon: Double?,
    onInit: (Float, Float, Float) -> Unit,
    onPan: (Float, Float) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        LaunchedEffect(streets) {
            if (mpp == 0f) {
                onInit(
                    (streets.minX + streets.maxX) / 2,
                    (streets.minY + streets.maxY) / 2,
                    ((streets.maxX - streets.minX) / widthPx).coerceIn(0.5f, 256f),
                )
            }
        }

        val thinPaint = remember {
            Paint().apply {
                color = android.graphics.Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 1f
                isAntiAlias = false
            }
        }
        val thickPaint = remember {
            Paint().apply {
                color = android.graphics.Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 5f
                strokeCap = Paint.Cap.ROUND
                isAntiAlias = false
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                // Street lines extend past the viewport; without this they paint over the top bar.
                .clipToBounds()
                .pointerInput(mpp) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        onPan(-drag.x * mpp, -drag.y * mpp)
                    }
                },
        ) {
            if (mpp <= 0f) return@Canvas
            val w = size.width
            val h = size.height
            val halfW = w / 2 * mpp
            val halfH = h / 2 * mpp
            val vMinX = centerX - halfW - 30f
            val vMaxX = centerX + halfW + 30f
            val vMinY = centerY - halfH - 30f
            val vMaxY = centerY + halfH + 30f

            val thin = Path()
            val thick = Path()
            val tmp = FloatArray(2)

            for (way in streets.ways) {
                if (way.maxX < vMinX || way.minX > vMaxX || way.maxY < vMinY || way.minY > vMaxY) continue

                var lastSx = 0f
                var lastSy = 0f
                for (i in way.xs.indices) {
                    val sx = (way.xs[i] - centerX) / mpp + w / 2
                    val sy = (way.ys[i] - centerY) / mpp + h / 2
                    if (i == 0) {
                        thin.moveTo(sx, sy)
                        lastSx = sx; lastSy = sy
                    } else {
                        // Decimate sub-pixel vertices when zoomed out.
                        val last = i == way.xs.size - 1
                        if (last || kotlin.math.abs(sx - lastSx) > 1.5f || kotlin.math.abs(sy - lastSy) > 1.5f) {
                            thin.lineTo(sx, sy)
                            lastSx = sx; lastSy = sy
                        }
                    }
                }

                if (ridden.isNotEmpty()) {
                    var i = 0
                    while (i < way.pieceCount) {
                        if (way.pieceKey(i) in ridden) {
                            var j = i
                            while (j + 1 < way.pieceCount && way.pieceKey(j + 1) in ridden) j++
                            appendRun(thick, way, i * way.pieceLen, (j + 1) * way.pieceLen, centerX, centerY, mpp, w, h, tmp)
                            i = j + 1
                        } else {
                            i++
                        }
                    }
                }
            }

            drawIntoCanvas { c ->
                c.nativeCanvas.drawPath(thin, thinPaint)
                c.nativeCanvas.drawPath(thick, thickPaint)
            }

            if (markerLat != null && markerLon != null) {
                val mx = (streets.projection.x(markerLon) - centerX) / mpp + w / 2
                val my = (streets.projection.y(markerLat) - centerY) / mpp + h / 2
                drawCircle(Color.White, radius = 9f, center = Offset(mx, my))
                drawCircle(Color.Black, radius = 9f, center = Offset(mx, my), style = Stroke(width = 4f))
                drawCircle(Color.Black, radius = 3f, center = Offset(mx, my))
            }
        }
    }
}

/** Appends the sub-polyline of [way] between distances [startD] and [endD] to [path]. */
private fun appendRun(
    path: Path, way: Way, startD: Float, endD: Float,
    centerX: Float, centerY: Float, mpp: Float, w: Float, h: Float, tmp: FloatArray,
) {
    fun sx(x: Float) = (x - centerX) / mpp + w / 2
    fun sy(y: Float) = (y - centerY) / mpp + h / 2

    way.pointAt(startD, tmp)
    path.moveTo(sx(tmp[0]), sy(tmp[1]))
    for (i in 1 until way.cum.size) {
        if (way.cum[i] > startD && way.cum[i] < endD) path.lineTo(sx(way.xs[i]), sy(way.ys[i]))
    }
    way.pointAt(endD, tmp)
    path.lineTo(sx(tmp[0]), sy(tmp[1]))
}
