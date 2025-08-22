package com.example.multiagent

import android.content.Context
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.io.FileInputStream
import java.io.IOException
import org.tensorflow.lite.Interpreter

object TFLiteModelLoader {

    @Throws(IOException::class)
    fun loadModelFile(context: Context, modelFileName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun createInterpreter(context: Context, modelFileName: String = "model.tflite"): Interpreter {
        return try {
            val modelBuffer = loadModelFile(context, modelFileName)
            Interpreter(modelBuffer)
        } catch (e: IOException) {
            throw RuntimeException("Error loading TFLite model", e)
        }
    }
}