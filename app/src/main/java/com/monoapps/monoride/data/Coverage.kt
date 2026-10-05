package com.monoapps.monoride.data

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.floor

/** A street piece counts as ridden when its midpoint is within this many meters of a track point. */
const val MATCH_DIST = 20f

/**
 * The merged, binary ridden/not-ridden state: a set of piece keys plus the list of
 * ride files already merged in. No per-ride or ride-count data is kept.
 */
class CoverageStore(private val file: File) {
    val ridden = HashSet<Long>()
    val processed = HashSet<String>()

    companion object {
        private const val MAGIC = 0x4D524356 // "MRCV"
        private const val VERSION = 1
        fun file(dir: File) = File(dir, "coverage.bin")
    }

    fun load() {
        ridden.clear(); processed.clear()
        if (!file.exists()) return
        try {
            DataInputStream(file.inputStream().buffered()).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION) return
                repeat(input.readInt()) { processed.add(input.readUTF()) }
                repeat(input.readInt()) { ridden.add(input.readLong()) }
            }
        } catch (e: Exception) {
            ridden.clear(); processed.clear()
        }
    }

    fun save() {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        DataOutputStream(tmp.outputStream().buffered()).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeInt(processed.size)
            processed.forEach { out.writeUTF(it) }
            out.writeInt(ridden.size)
            ridden.forEach { out.writeLong(it) }
        }
        tmp.renameTo(file)
    }
}

/** Matches a recorded track against the street network. Pure geometry, no IO. */
object Matcher {
    private const val CELL = 25f

    private fun cellKey(cx: Int, cy: Int): Long = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)

    /**
     * Returns the piece keys covered by a track given as projected x/y meter arrays.
     */
    fun match(ways: List<Way>, px: FloatArray, py: FloatArray): HashSet<Long> {
        val result = HashSet<Long>()
        if (px.isEmpty()) return result

        // Spatial hash of track points, cell size >= MATCH_DIST so 3x3 lookup suffices.
        val grid = HashMap<Long, MutableList<Int>>()
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (i in px.indices) {
            val cx = floor(px[i] / CELL).toInt()
            val cy = floor(py[i] / CELL).toInt()
            grid.getOrPut(cellKey(cx, cy)) { ArrayList() }.add(i)
            if (px[i] < minX) minX = px[i]; if (px[i] > maxX) maxX = px[i]
            if (py[i] < minY) minY = py[i]; if (py[i] > maxY) maxY = py[i]
        }
        minX -= MATCH_DIST; maxX += MATCH_DIST; minY -= MATCH_DIST; maxY += MATCH_DIST

        val mid = FloatArray(2)
        for (way in ways) {
            if (way.maxX < minX || way.minX > maxX || way.maxY < minY || way.minY > maxY) continue
            for (piece in 0 until way.pieceCount) {
                way.pointAt((piece + 0.5f) * way.pieceLen, mid)
                if (nearTrack(mid[0], mid[1], grid, px, py)) result.add(way.pieceKey(piece))
            }
        }
        return result
    }

    private fun nearTrack(
        x: Float, y: Float,
        grid: HashMap<Long, MutableList<Int>>,
        px: FloatArray, py: FloatArray,
    ): Boolean {
        val cx = floor(x / CELL).toInt()
        val cy = floor(y / CELL).toInt()
        val d2 = MATCH_DIST * MATCH_DIST
        for (dx in -1..1) for (dy in -1..1) {
            val points = grid[cellKey(cx + dx, cy + dy)] ?: continue
            for (i in points) {
                val ddx = px[i] - x; val ddy = py[i] - y
                if (ddx * ddx + ddy * ddy <= d2) return true
            }
        }
        return false
    }
}
