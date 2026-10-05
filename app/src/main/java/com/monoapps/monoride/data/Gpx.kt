package com.monoapps.monoride.data

import java.io.BufferedWriter
import java.io.File
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Standard GPX 1.1 files, one per ride, so any other app can read them. */
object Gpx {
    const val DIR = "rides"

    private val trkpt = Regex("""<trkpt lat="(-?[0-9.]+)" lon="(-?[0-9.]+)"""")

    fun ridesDir(filesDir: File): File = File(filesDir, DIR).apply { mkdirs() }

    fun newRideFile(filesDir: File, startedAt: Long): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(startedAt))
        return File(ridesDir(filesDir), "ride-$stamp.gpx")
    }

    fun listRides(filesDir: File): List<File> =
        ridesDir(filesDir).listFiles { f -> f.name.endsWith(".gpx") }?.sortedByDescending { it.name }
            ?: emptyList()

    /**
     * Reads track points as lat/lon pairs. Line-based and tolerant of a missing
     * footer, so a ride cut short by a crash still loads.
     */
    fun readPoints(file: File): List<DoubleArray> {
        val points = ArrayList<DoubleArray>()
        try {
            file.forEachLine { line ->
                trkpt.find(line)?.let { m ->
                    points.add(doubleArrayOf(m.groupValues[1].toDouble(), m.groupValues[2].toDouble()))
                }
            }
        } catch (e: Exception) {
            // Return whatever was read.
        }
        return points
    }

    fun trackDistanceMeters(points: List<DoubleArray>): Double {
        var d = 0.0
        for (i in 1 until points.size) {
            d += distanceMeters(points[i - 1][0], points[i - 1][1], points[i][0], points[i][1])
        }
        return d
    }

    /** Merges all rides into one GPX file with one track per ride. */
    fun writeMerged(rides: List<File>, out: Writer) {
        out.write(XML_HEADER)
        out.write(GPX_OPEN)
        for (ride in rides) {
            out.write("  <trk><name>${ride.name.removeSuffix(".gpx")}</name><trkseg>\n")
            ride.forEachLine { line ->
                if (trkpt.containsMatchIn(line)) out.write("$line\n")
            }
            out.write("  </trkseg></trk>\n")
        }
        out.write("</gpx>\n")
    }

    private const val XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
    private const val GPX_OPEN =
        "<gpx version=\"1.1\" creator=\"MonoRide\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n"

    /**
     * Incremental GPX writer: header on open, one trkpt per line, flushed per point
     * so a crash loses at most the last fix.
     */
    class TrackWriter(file: File, name: String) {
        private val out: BufferedWriter = file.bufferedWriter()
        private val time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        init {
            out.write(XML_HEADER)
            out.write(GPX_OPEN)
            out.write("  <trk><name>$name</name><trkseg>\n")
            out.flush()
        }

        fun point(lat: Double, lon: Double, eleMeters: Double?, timeMs: Long) {
            val ele = if (eleMeters != null) "<ele>${"%.1f".format(Locale.US, eleMeters)}</ele>" else ""
            out.write(
                "    <trkpt lat=\"${"%.7f".format(Locale.US, lat)}\" lon=\"${"%.7f".format(Locale.US, lon)}\">" +
                    "$ele<time>${time.format(Date(timeMs))}</time></trkpt>\n"
            )
            out.flush()
        }

        fun close() {
            out.write("  </trkseg></trk>\n</gpx>\n")
            out.close()
        }
    }
}
