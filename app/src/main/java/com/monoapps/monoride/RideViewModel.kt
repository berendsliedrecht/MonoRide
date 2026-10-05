package com.monoapps.monoride

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.monoapps.monoride.data.CoverageStore
import com.monoapps.monoride.data.Gpx
import com.monoapps.monoride.data.Matcher
import com.monoapps.monoride.data.Overpass
import com.monoapps.monoride.data.StreetStore
import com.monoapps.monoride.data.Streets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.io.File
import java.io.OutputStreamWriter

class RideViewModel(app: Application) : AndroidViewModel(app) {

    data class Stats(val riddenKm: Double, val totalKm: Double, val percent: Double)
    data class RideInfo(val file: File, val distanceKm: Double)

    private val filesDir = app.filesDir
    private val prefs = app.getSharedPreferences("monoride", Context.MODE_PRIVATE)
    private val coverage = CoverageStore(CoverageStore.file(filesDir))
    private val lock = Any()

    val streets = MutableStateFlow<Streets?>(null)
    val stats = MutableStateFlow<Stats?>(null)
    val ridden = MutableStateFlow<Set<Long>>(emptySet())
    val rides = MutableStateFlow<List<RideInfo>>(emptyList())
    val downloadStatus = MutableStateFlow<String?>(null)

    var city: String
        get() = prefs.getString("city", "Rotterdam") ?: "Rotterdam"
        set(value) = prefs.edit().putString("city", value).apply()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            streets.value = StreetStore.load(StreetStore.file(filesDir))
            synchronized(lock) { coverage.load() }
            processNewRides()
            refreshRides()
        }
        viewModelScope.launch(Dispatchers.IO) {
            RideRecorder.finishedRides.drop(1).collect {
                processNewRides()
                refreshRides()
            }
        }
    }

    /** Matches any ride file not yet merged into coverage, then publishes new state. */
    private fun processNewRides() {
        val s = streets.value ?: return publish()
        synchronized(lock) {
            var changed = false
            for (ride in Gpx.listRides(filesDir)) {
                if (ride.name in coverage.processed) continue
                val points = Gpx.readPoints(ride)
                if (points.isNotEmpty()) {
                    val px = FloatArray(points.size)
                    val py = FloatArray(points.size)
                    for (i in points.indices) {
                        px[i] = s.projection.x(points[i][1])
                        py[i] = s.projection.y(points[i][0])
                    }
                    coverage.ridden.addAll(Matcher.match(s.ways, px, py))
                }
                coverage.processed.add(ride.name)
                changed = true
            }
            if (changed) coverage.save()
        }
        publish()
    }

    private fun publish() {
        val s = streets.value
        val keys: Set<Long>
        synchronized(lock) { keys = HashSet(coverage.ridden) }
        ridden.value = keys
        if (s == null) {
            stats.value = null
            return
        }
        var riddenM = 0.0
        for (key in keys) {
            val way = s.byId[key ushr 16] ?: continue
            riddenM += way.pieceLen.toDouble()
        }
        val totalM = s.totalLength
        stats.value = Stats(
            riddenKm = riddenM / 1000,
            totalKm = totalM / 1000,
            percent = if (totalM > 0) riddenM / totalM * 100 else 0.0,
        )
    }

    private fun refreshRides() {
        rides.value = Gpx.listRides(filesDir).map { file ->
            RideInfo(file, Gpx.trackDistanceMeters(Gpx.readPoints(file)) / 1000)
        }
    }

    /** Clears coverage and replays every ride. Used after deletes and re-downloads. */
    fun recompute() {
        viewModelScope.launch(Dispatchers.IO) {
            synchronized(lock) {
                coverage.ridden.clear()
                coverage.processed.clear()
                coverage.save()
            }
            processNewRides()
        }
    }

    fun deleteRide(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            file.delete()
            synchronized(lock) {
                coverage.ridden.clear()
                coverage.processed.clear()
                coverage.save()
            }
            processNewRides()
            refreshRides()
        }
    }

    fun downloadStreets(cityName: String) {
        if (downloadStatus.value != null) return
        city = cityName
        downloadStatus.value = "Starting…"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val count = Overpass.download(cityName, StreetStore.file(filesDir)) {
                    downloadStatus.value = it
                }
                downloadStatus.value = "Loading $count streets…"
                streets.value = StreetStore.load(StreetStore.file(filesDir))
                synchronized(lock) {
                    coverage.ridden.clear()
                    coverage.processed.clear()
                    coverage.save()
                }
                processNewRides()
                downloadStatus.value = null
            } catch (e: Exception) {
                downloadStatus.value = "Failed: ${e.message}"
            }
        }
    }

    fun clearDownloadError() {
        if (downloadStatus.value?.startsWith("Failed") == true) downloadStatus.value = null
    }

    fun exportMerged(uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)!!.use { out ->
                    OutputStreamWriter(out).use { w ->
                        Gpx.writeMerged(Gpx.listRides(filesDir).sortedBy { it.name }, w)
                    }
                }
            }.isSuccess
            onDone(ok)
        }
    }
}
