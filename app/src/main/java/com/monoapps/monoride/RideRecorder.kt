package com.monoapps.monoride

import kotlinx.coroutines.flow.MutableStateFlow

/** In-process bridge between the recording service and the UI. */
object RideRecorder {
    data class State(
        val recording: Boolean = false,
        val startedAt: Long = 0L,
        val distanceM: Double = 0.0,
        val points: Int = 0,
        val lastLat: Double? = null,
        val lastLon: Double? = null,
    )

    val state = MutableStateFlow(State())

    /** Bumped when a ride file is finalized, so coverage gets recomputed. */
    val finishedRides = MutableStateFlow(0)
}
