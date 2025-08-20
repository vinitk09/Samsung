package com.example.multiagent

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream

class TouchDataStorage(private val context: Context) {
    private val tag = "TouchDataStorage"
    private val fileName = "touch_events.json"
    private val gson = Gson()

    fun saveTouchEvent(event: TouchEvent) {
        try {
            // Read existing events
            val existingEvents = loadAllTouchEvents().toMutableList()

            // Add new event
            existingEvents.add(event)

            // Keep only the last 1000 events to prevent storage issues
            val eventsToSave = if (existingEvents.size > 1000) {
                existingEvents.takeLast(1000)
            } else {
                existingEvents
            }

            // Convert to JSON
            val jsonString = gson.toJson(eventsToSave)

            // Save to file
            val file = File(context.filesDir, fileName)
            FileOutputStream(file).use { output ->
                output.write(jsonString.toByteArray())
            }

            Log.d(tag, "Saved touch event. Total events: ${eventsToSave.size}")
        } catch (e: Exception) {
            Log.e(tag, "Error saving touch event: ${e.message}")
        }
    }

    fun loadAllTouchEvents(): List<TouchEvent> {
        return try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) {
                return emptyList()
            }

            FileInputStream(file).use { input ->
                val jsonString = input.bufferedReader().use { it.readText() }
                val type = object : TypeToken<List<TouchEvent>>() {}.type
                gson.fromJson(jsonString, type) ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error loading touch events: ${e.message}")
            emptyList()
        }
    }

    fun clearAllData() {
        try {
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                file.delete()
            }
            Log.d(tag, "Cleared all touch data")
        } catch (e: Exception) {
            Log.e(tag, "Error clearing data: ${e.message}")
        }
    }
}