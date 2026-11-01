package com.example.gpstracker

import android.content.Context
import android.location.Location
import com.google.android.gms.maps.model.LatLng
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.util.Locale

data class TrackPoint(
    val lat: Double,
    val lng: Double,
    val time: Long,
    val speed: Float,
    val accuracy: Float,
    val segmentId: Long = 0L
) {
    val latLng: LatLng get() = LatLng(lat, lng)

    fun toLocation(): Location = Location("history").apply {
        latitude = lat
        longitude = lng
        time = this@TrackPoint.time
        speed = this@TrackPoint.speed
        accuracy = this@TrackPoint.accuracy
    }

    fun toCsv(): String = String.format(
        Locale.US,
        "%.7f,%.7f,%d,%.2f,%.2f,%d",
        lat, lng, time, speed, accuracy, segmentId
    )

    companion object {
        fun fromCsv(line: String): TrackPoint? {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("lat")) {
                return null
            }
            val parts = trimmed.split(",")
            if (parts.size < 5) return null
            return try {
                TrackPoint(
                    lat = parts[0].toDouble(),
                    lng = parts[1].toDouble(),
                    time = parts[2].toLong(),
                    speed = parts[3].toFloat(),
                    accuracy = parts[4].toFloat(),
                    segmentId = if (parts.size >= 6) parts[5].toLong() else 0L
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

class LocationHistoryStore(context: Context) {

    private val file = File(context.filesDir, "location_history.csv")

    @Synchronized
    fun append(point: TrackPoint) {
        try {
            val exists = file.exists()
            PrintWriter(FileWriter(file, true)).use { writer ->
                if (!exists || file.length() == 0L) {
                    writer.println("lat,lng,time,speed,accuracy,segmentId")
                }
                writer.println(point.toCsv())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun loadAll(): List<TrackPoint> {
        if (!file.exists()) return emptyList()
        return try {
            file.readLines().mapNotNull { TrackPoint.fromCsv(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun clear(): Boolean {
        return try {
            if (file.exists()) file.delete() else true
        } catch (e: Exception) {
            false
        }
    }
}
