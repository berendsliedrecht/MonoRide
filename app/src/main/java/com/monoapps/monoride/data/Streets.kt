package com.monoapps.monoride.data

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Streets are cut into pieces of roughly this length; a piece is the unit of coverage. */
const val PIECE_LEN = 25f

/** A street from OSM, projected to local meters. */
class Way(val id: Long, val name: String, val xs: FloatArray, val ys: FloatArray) {
    var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
    var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE

    /** Cumulative length in meters at each vertex. */
    val cum = FloatArray(xs.size)

    init {
        for (i in xs.indices) {
            minX = min(minX, xs[i]); maxX = max(maxX, xs[i])
            minY = min(minY, ys[i]); maxY = max(maxY, ys[i])
            if (i > 0) {
                val dx = xs[i] - xs[i - 1]
                val dy = ys[i] - ys[i - 1]
                cum[i] = cum[i - 1] + kotlin.math.sqrt(dx * dx + dy * dy)
            }
        }
    }

    val length: Float get() = cum[cum.size - 1]
    val pieceCount: Int = max(1, ceil(length / PIECE_LEN).toDouble().toInt())
    val pieceLen: Float = length / pieceCount

    /** Stable coverage key for piece [i]: OSM way id in the high bits, piece index low. */
    fun pieceKey(i: Int): Long = (id shl 16) or i.toLong()

    /** Point at distance [d] along the way, written into [out] as x,y. */
    fun pointAt(d: Float, out: FloatArray) {
        if (length <= 0f) { out[0] = xs[0]; out[1] = ys[0]; return }
        val dd = d.coerceIn(0f, length)
        var i = 1
        while (i < cum.size && cum[i] < dd) i++
        if (i >= cum.size) { out[0] = xs.last(); out[1] = ys.last(); return }
        val seg = cum[i] - cum[i - 1]
        val t = if (seg <= 0f) 0f else (dd - cum[i - 1]) / seg
        out[0] = xs[i - 1] + (xs[i] - xs[i - 1]) * t
        out[1] = ys[i - 1] + (ys[i] - ys[i - 1]) * t
    }
}

class Streets(
    val city: String,
    val ways: List<Way>,
    val projection: Projection,
) {
    val byId: Map<Long, Way> = ways.associateBy { it.id }
    val totalLength: Double = ways.sumOf { it.length.toDouble() }
    var minX = 0f; var maxX = 0f; var minY = 0f; var maxY = 0f

    init {
        if (ways.isNotEmpty()) {
            minX = ways.minOf { it.minX }; maxX = ways.maxOf { it.maxX }
            minY = ways.minOf { it.minY }; maxY = ways.maxOf { it.maxY }
        }
    }
}

/**
 * Binary storage for the street network:
 * magic, version, wayCount, city, then per way: id, name, pointCount, lat/lon pairs as 1e7 ints.
 */
object StreetStore {
    private const val MAGIC = 0x4D525354 // "MRST"
    private const val VERSION = 1

    fun file(dir: File) = File(dir, "streets.bin")

    /** Raw way as read from disk or Overpass, before projection. */
    class RawWay(val id: Long, val name: String, val latE7: IntArray, val lonE7: IntArray)

    fun writeHeader(out: DataOutputStream, city: String) {
        out.writeInt(MAGIC)
        out.writeInt(VERSION)
        out.writeInt(0) // way count, patched afterwards
        out.writeUTF(city)
    }

    fun writeWay(out: DataOutputStream, way: RawWay) {
        out.writeLong(way.id)
        out.writeUTF(way.name)
        out.writeInt(way.latE7.size)
        for (i in way.latE7.indices) {
            out.writeInt(way.latE7[i])
            out.writeInt(way.lonE7[i])
        }
    }

    fun patchWayCount(file: File, count: Int) {
        RandomAccessFile(file, "rw").use {
            it.seek(8)
            it.writeInt(count)
        }
    }

    fun load(file: File): Streets? {
        if (!file.exists()) return null
        return try {
            DataInputStream(file.inputStream().buffered()).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION) return null
                val count = input.readInt()
                val city = input.readUTF()
                val raw = ArrayList<RawWay>(count)
                var minLat = Int.MAX_VALUE; var maxLat = Int.MIN_VALUE
                var minLon = Int.MAX_VALUE; var maxLon = Int.MIN_VALUE
                repeat(count) {
                    val id = input.readLong()
                    val name = input.readUTF()
                    val n = input.readInt()
                    val lats = IntArray(n); val lons = IntArray(n)
                    for (i in 0 until n) {
                        lats[i] = input.readInt(); lons[i] = input.readInt()
                        minLat = min(minLat, lats[i]); maxLat = max(maxLat, lats[i])
                        minLon = min(minLon, lons[i]); maxLon = max(maxLon, lons[i])
                    }
                    raw.add(RawWay(id, name, lats, lons))
                }
                if (raw.isEmpty()) return null
                val proj = Projection((minLat + maxLat) / 2 / 1e7, (minLon + maxLon) / 2 / 1e7)
                val ways = raw.map { w ->
                    val xs = FloatArray(w.latE7.size); val ys = FloatArray(w.latE7.size)
                    for (i in xs.indices) {
                        xs[i] = proj.x(w.lonE7[i] / 1e7)
                        ys[i] = proj.y(w.latE7[i] / 1e7)
                    }
                    Way(w.id, w.name, xs, ys)
                }
                Streets(city, ways, proj)
            }
        } catch (e: Exception) {
            null
        }
    }
}
