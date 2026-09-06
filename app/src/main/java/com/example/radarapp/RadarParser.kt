package com.example.radarapp

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

class RadarParser(private val context: Context) {
    
    fun parseRadars(): List<Radar> {
        val radars = mutableListOf<Radar>()
        
        try {
            val inputStream = context.resources.openRawResource(R.raw.radars)
            val reader = BufferedReader(InputStreamReader(inputStream))
            
            // Skip header
            reader.readLine()
            
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                try {
                    val parts = line!!.split(";")
                    if (parts.size >= 15) {
                        val radar = Radar(
                            id = parts[0].trim(),
                            latitude = parts[3].trim().replace(",", ".").toDoubleOrNull() ?: 0.0,
                            longitude = parts[4].trim().replace(",", ".").toDoubleOrNull() ?: 0.0,
                            type = parts[9].trim(),
                            vitesse = parts[14].trim().toIntOrNull() ?: 0,
                            route = parts[11].trim()
                        )
                        if (radar.latitude != 0.0 && radar.longitude != 0.0) {
                            radars.add(radar)
                        }
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return radars
    }
}
