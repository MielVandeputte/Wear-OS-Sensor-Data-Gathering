package com.example.wearossensordatagathering.presentation

import android.content.Context
import android.content.Context.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Environment
import androidx.lifecycle.LiveData
import java.io.File
import java.io.FileWriter

class LoggableSensor(
    private val sensorType: Int,
    private val typeName: String,
    wakeUp: Boolean,
    context: Context
) : SensorEventListener {

    private var sensorManager: SensorManager
    private var sensor: Sensor

    private val cache: MutableList<SensorRecord> = mutableListOf()

    init {
        sensorManager = context.getSystemService(SENSOR_SERVICE) as SensorManager
        sensor = sensorManager.getDefaultSensor(sensorType, wakeUp)
    }

    fun startListening() {
        sensorManager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_FASTEST)
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == sensorType) {
            val v = event.values[0]
            cache.add(SensorRecord(System.currentTimeMillis(), v))
        }
    }

    fun flushToFile(userId: Int) {
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "$typeName.csv"
        )
        val fileWriter = FileWriter(file)
        for (record in cache) {
            fileWriter.append(record.timestamp.toString() + ", " + record.value.toString() + ", " + userId + "\n")
        }
        fileWriter.close()
        cache.clear()

    }

    override fun onAccuracyChanged(sensor: Sensor?, p1: Int) {
        return
    }
}