package com.example.wearossensordatagathering.presentation

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.wearossensordatagathering.presentation.theme.WearOSSensorDataGatheringTheme
import java.io.File
import java.io.FileWriter
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.roundToLong

class MainActivity : ComponentActivity() {

    private var gattServiceConn: GattServiceConn? = null

    private lateinit var sensorList: List<LoggableSensor>

    private val _sensorState = MutableLiveData(false)
    private val sensorState: LiveData<Boolean> = _sensorState

    private val _userState = MutableLiveData(1)
    private val userState: LiveData<Int> = _userState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp(sensorState, userState, nextUser = ::nextUser)
        }

        requestPermissions(
            this, arrayOf(
                // Protection level: normal
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.FOREGROUND_SERVICE,
                // Protection level: dangerous
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BODY_SENSORS,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ), 0
        )

        sensorList = listOf(
            LoggableSensor(65547,"ppSensor",true,this),
            LoggableSensor(31,"heartBeatSensor",true,this),
            LoggableSensor(21,"heartRateSensor",true,this),
            LoggableSensor(5,"lightSensor",true,this),
            LoggableSensor(65544,"ppgGainSensor",true,this),
            LoggableSensor(65541,"ppgSensor",true,this),
            LoggableSensor(17,"sigMotionSensor",true,this),
            LoggableSensor(4,"gyroscopeSensor",true,this),
            LoggableSensor(9,"gravitySensor",true,this),
            LoggableSensor(10,"linAccelerationSensor",true,this),
        )

        looper()

    }

    override fun onStart() {
        super.onStart()

        val latestGattServiceConn = GattServiceConn()
        if (bindService(Intent(this, GattService::class.java), latestGattServiceConn, 0)) {
            gattServiceConn = latestGattServiceConn
        }
        gattServiceConn?.binding?.setMyCharacteristicValue("test")
    }

    override fun onStop() {
        super.onStop()

        if (gattServiceConn != null) {
            unbindService(gattServiceConn!!)
            gattServiceConn = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        stopService(Intent(this, GattService::class.java))
        sensorList.forEach{ s -> s.stopListening() }

    }

    private fun looper() {

        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {

                _sensorState.postValue(true)

                startForegroundService(Intent(this@MainActivity, GattService::class.java))
                sensorList.forEach{s -> s.startListening()}

                Timer().schedule((0.5 * 60 * 1000).roundToLong()) {

                    stopService(Intent(this@MainActivity, GattService::class.java))
                    sensorList.forEach{s -> s.stopListening()}

                    _sensorState.postValue(false)

                    if(_userState.value != null){
                        sensorList.forEach{s -> s.flushToFile(_userState.value!!)}
                    }
                }
            }
        }, 0, 30 * 60 * 1000)

    }

    private fun nextUser() {
        val currentUser: Int? = _userState.value
        if (currentUser != null) {
            _userState.value = currentUser + 1
        }
    }

    private class GattServiceConn : ServiceConnection {
        var binding: DeviceAPI? = null

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binding = service as? DeviceAPI
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binding = null
        }
    }
}

@Composable
fun WearApp(
    sensorValue: LiveData<Boolean>,
    userId: LiveData<Int>,
    nextUser: () -> Unit
) {
    WearOSSensorDataGatheringTheme {

        val sensorValue by sensorValue.observeAsState()
        val userId by userId.observeAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(text = "Current user: $userId")
            Text(text = "Sensors on: $sensorValue")

            Button(onClick = { nextUser() }) {
                Text(
                    text = "Next user",
                    style = TextStyle(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )

            }
        }
    }
}