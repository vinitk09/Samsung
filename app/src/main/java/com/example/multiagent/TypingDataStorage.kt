package com.example.multiagent

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class TypingDataStorage(private val context: Context) {
    private val tag = "TypingDataStorage"
    private val fileName = "typing_events.json"
    private val gson = Gson()
    private val maxEvents = 1000

    fun saveTypingEvent(event: TypingEvent) {
        try {
            val allEvents = loadAllTypingEvents().toMutableList()
            allEvents.add(event)
            val eventsToSave = if (allEvents.size > maxEvents) {
                allEvents.takeLast(maxEvents)
            } else {
                allEvents
            }
            val jsonString = gson.toJson(eventsToSave)
            val file = File(context.filesDir, fileName)
            file.writeText(jsonString)
        } catch (e: Exception) {
            Log.e(tag, "Error saving typing event: ${e.message}")
        }
    }

    fun loadAllTypingEvents(): List<TypingEvent> {
        return try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) return emptyList()
            val jsonString = file.readText()
            val type = object : TypeToken<List<TypingEvent>>() {}.type
            gson.fromJson(jsonString, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(tag, "Error loading typing events: ${e.message}")
            emptyList()
        }
    }
}