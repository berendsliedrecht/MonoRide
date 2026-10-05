package com.monoapps.monoride

import com.monoapps.monoride.data.CoverageStore
import com.monoapps.monoride.data.Gpx
import com.monoapps.monoride.data.Matcher
import com.monoapps.monoride.data.Way
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.StringWriter

private fun straightWay(id: Long = 1L, lengthM: Float = 100f, y: Float = 0f) = Way(
    id = id,
    name = "Test street",
    xs = floatArrayOf(0f, lengthM),
    ys = floatArrayOf(y, y),
)

class WayTest {
    @Test
    fun `pieces split a 100m way into four 25m pieces`() {
        val way = straightWay()
        assertEquals(4, way.pieceCount)
        assertEquals(25f, way.pieceLen, 0.001f)
        assertEquals(100f, way.length, 0.001f)
    }

    @Test
    fun `pointAt interpolates along the way`() {
        val way = straightWay()
        val out = FloatArray(2)
        way.pointAt(50f, out)
        assertEquals(50f, out[0], 0.001f)
        assertEquals(0f, out[1], 0.001f)
        way.pointAt(200f, out) // clamped to the end
        assertEquals(100f, out[0], 0.001f)
    }

    @Test
    fun `piece keys embed the way id`() {
        val way = straightWay(id = 42L)
        assertEquals((42L shl 16) or 3L, way.pieceKey(3))
    }
}

class MatcherTest {
    @Test
    fun `track along a street marks all its pieces`() {
        val way = straightWay()
        // Points every 10m, 5m north of the street: well within MATCH_DIST.
        val px = FloatArray(11) { it * 10f }
        val py = FloatArray(11) { 5f }
        val ridden = Matcher.match(listOf(way), px, py)
        assertEquals(way.pieceCount, ridden.size)
    }

    @Test
    fun `distant street stays unridden`() {
        val near = straightWay(id = 1L, y = 0f)
        val far = straightWay(id = 2L, y = 500f)
        val px = FloatArray(11) { it * 10f }
        val py = FloatArray(11) { 0f }
        val ridden = Matcher.match(listOf(near, far), px, py)
        assertTrue(ridden.all { (it ushr 16) == 1L })
        assertEquals(near.pieceCount, ridden.size)
    }

    @Test
    fun `crossing a street only marks the pieces near the crossing`() {
        // Track runs north-south at x=50, crossing an east-west 200m street.
        val way = straightWay(lengthM = 200f)
        val px = FloatArray(21) { 50f }
        val py = FloatArray(21) { (it - 10) * 10f }
        val ridden = Matcher.match(listOf(way), px, py)
        assertTrue(ridden.isNotEmpty())
        assertTrue(ridden.size < way.pieceCount / 2)
    }

    @Test
    fun `empty track matches nothing`() {
        assertTrue(Matcher.match(listOf(straightWay()), FloatArray(0), FloatArray(0)).isEmpty())
    }
}

class GpxTest {
    @Test
    fun `written track reads back`() {
        val file = File.createTempFile("ride", ".gpx")
        val writer = Gpx.TrackWriter(file, "test")
        writer.point(51.9225, 4.47917, 2.0, 1_700_000_000_000)
        writer.point(51.9230, 4.4800, null, 1_700_000_010_000)
        writer.close()

        val points = Gpx.readPoints(file)
        assertEquals(2, points.size)
        assertEquals(51.9225, points[0][0], 1e-6)
        assertEquals(4.47917, points[0][1], 1e-6)
        assertTrue(Gpx.trackDistanceMeters(points) > 50)
        file.delete()
    }

    @Test
    fun `truncated file without footer still reads`() {
        val file = File.createTempFile("ride", ".gpx")
        val writer = Gpx.TrackWriter(file, "test")
        writer.point(51.9225, 4.47917, null, 1_700_000_000_000)
        // No close(): simulates a crash mid-ride. Points are flushed per fix.
        assertEquals(1, Gpx.readPoints(file).size)
        file.delete()
    }

    @Test
    fun `merged export contains one track per ride`() {
        val a = File.createTempFile("ride-a", ".gpx")
        val b = File.createTempFile("ride-b", ".gpx")
        for (f in listOf(a, b)) {
            val w = Gpx.TrackWriter(f, f.name)
            w.point(51.9, 4.4, null, 1_700_000_000_000)
            w.close()
        }
        val out = StringWriter()
        Gpx.writeMerged(listOf(a, b), out)
        val gpx = out.toString()
        assertEquals(2, Regex("<trk>").findAll(gpx).count())
        assertEquals(2, Regex("<trkpt").findAll(gpx).count())
        assertTrue(gpx.trimEnd().endsWith("</gpx>"))
        a.delete(); b.delete()
    }
}

class CoverageStoreTest {
    @Test
    fun `save and load roundtrip`() {
        val file = File.createTempFile("coverage", ".bin")
        val store = CoverageStore(file)
        store.ridden.addAll(listOf(1L, 2L, (42L shl 16) or 3L))
        store.processed.add("ride-20260930-101500.gpx")
        store.save()

        val loaded = CoverageStore(file)
        loaded.load()
        assertEquals(store.ridden, loaded.ridden)
        assertEquals(store.processed, loaded.processed)
        file.delete()
    }

    @Test
    fun `missing file loads empty`() {
        val store = CoverageStore(File("/tmp/does-not-exist-monoride.bin"))
        store.load()
        assertTrue(store.ridden.isEmpty())
        assertFalse(store.processed.contains("anything"))
    }
}
