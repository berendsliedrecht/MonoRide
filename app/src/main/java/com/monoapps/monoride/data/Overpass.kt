package com.monoapps.monoride.data

import android.util.JsonReader
import android.util.JsonToken
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * One-time download of a city's rideable street network from the Overpass API
 * (https://overpass-api.de). Streams the JSON response straight into the binary
 * street file so the full response is never held in memory.
 */
object Overpass {
    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"

    // Streets you can legally bike on: no motorways, no foot-only paths, and no
    // roads where a parallel cycle path must be used instead.
    private const val HIGHWAY_TYPES =
        "primary|secondary|tertiary|unclassified|residential|living_street|cycleway"

    private fun query(city: String) = """
        [out:json][timeout:300];
        area["name"="$city"]["boundary"="administrative"]["admin_level"="8"]->.a;
        way["highway"~"^($HIGHWAY_TYPES)$"]
           ["access"!~"^(private|no)$"]
           ["bicycle"!~"^(no|use_sidepath)$"]
           (area.a);
        out geom;
    """.trimIndent()

    /**
     * Downloads streets for [city] into [target]. Reports progress via [onStatus].
     * Returns the number of streets written. Throws on network or parse failure;
     * [target] is only replaced on success.
     */
    fun download(city: String, target: File, onStatus: (String) -> Unit): Int {
        onStatus("Contacting OpenStreetMap…")
        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        // Overpass rejects anonymous clients with HTTP 406; identify per their fair-use policy.
        conn.setRequestProperty(
            "User-Agent",
            "MonoRide/0.1 (https://github.com/berendsliedrecht/MonoRide)",
        )
        conn.connectTimeout = 30_000
        conn.readTimeout = 300_000
        conn.outputStream.use {
            it.write("data=${URLEncoder.encode(query(city), "UTF-8")}".toByteArray())
        }
        if (conn.responseCode != 200) {
            throw RuntimeException("Overpass returned HTTP ${conn.responseCode}")
        }

        val tmp = File(target.parentFile, "${target.name}.tmp")
        var count = 0
        try {
            DataOutputStream(tmp.outputStream().buffered()).use { out ->
                StreetStore.writeHeader(out, city)
                JsonReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                    reader.beginObject()
                    while (reader.hasNext()) {
                        if (reader.nextName() == "elements") {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                readElement(reader)?.let { way ->
                                    StreetStore.writeWay(out, way)
                                    count++
                                    if (count % 1000 == 0) onStatus("Downloading… $count streets")
                                }
                            }
                            reader.endArray()
                        } else {
                            reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
            }
            if (count == 0) {
                tmp.delete()
                throw RuntimeException("No streets found for \"$city\"")
            }
            StreetStore.patchWayCount(tmp, count)
            if (!tmp.renameTo(target)) throw RuntimeException("Could not save street data")
        } catch (e: Exception) {
            tmp.delete()
            throw e
        } finally {
            conn.disconnect()
        }
        return count
    }

    private fun readElement(reader: JsonReader): StreetStore.RawWay? {
        var id = 0L
        var name = ""
        val lats = ArrayList<Int>()
        val lons = ArrayList<Int>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextLong()
                "geometry" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        if (reader.peek() == JsonToken.NULL) { reader.skipValue(); continue }
                        var lat = 0.0; var lon = 0.0
                        reader.beginObject()
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "lat" -> lat = reader.nextDouble()
                                "lon" -> lon = reader.nextDouble()
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        lats.add((lat * 1e7).toInt())
                        lons.add((lon * 1e7).toInt())
                    }
                    reader.endArray()
                }
                "tags" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        if (reader.nextName() == "name") name = reader.nextString()
                        else reader.skipValue()
                    }
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        if (lats.size < 2) return null
        return StreetStore.RawWay(id, name, lats.toIntArray(), lons.toIntArray())
    }
}
