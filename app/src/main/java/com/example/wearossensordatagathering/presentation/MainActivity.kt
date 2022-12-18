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
import android.os.IBinder
import android.util.Log
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
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.roundToLong


class MainActivity : ComponentActivity(), SensorEventListener {

    private var gattServiceConn: GattServiceConn? = null
    private lateinit var sensorManager: SensorManager

    private val _sensorState = MutableLiveData<Boolean>(false)
    private val sensorState: LiveData<Boolean> = _sensorState

    private var ppSensor: Sensor? = null
    private val _ppState = MutableLiveData<Float>()
    private val ppState: LiveData<Float> = _ppState

    private val _userState = MutableLiveData<Int>(1)
    private val userState: LiveData<Int> = _userState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp(sensorState, ppState, userState, nextUser = ::nextUser)
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
                Manifest.permission.BODY_SENSORS
            ), 0
        )

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        ppSensor = sensorManager.getDefaultSensor(65547, true)

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

        sensorManager.unregisterListener(this)
        stopService(Intent(this, GattService::class.java))

        Log.i("Miel", "ondestroy was called")
    }

    override fun onSensorChanged(event: SensorEvent?) {

        if (event?.sensor?.type == 65547) {
            val pp = event.values[0]
            _ppState.value = pp
        }

    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        return
    }

    private fun looper() {

        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {

                Log.i("Miel","Entering of timer 1")


                _sensorState.postValue(true)

                startForegroundService(Intent(this@MainActivity, GattService::class.java))
                sensorManager.registerListener(
                    this@MainActivity, ppSensor, SensorManager.SENSOR_DELAY_NORMAL
                )

                Timer().schedule((2.5 * 60 * 1000).roundToLong()) {

                    Log.i("Miel","Entering of timer 2")
                    stopService(Intent(this@MainActivity, GattService::class.java))
                    sensorManager.unregisterListener(this@MainActivity)

                    _sensorState.postValue(false)
                }
            }
        }, 0, 30 * 60 * 1000)

    }

    private fun nextUser() {
        val currentUser: Int? = _userState.getValue()
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
    ppValue: LiveData<Float>,
    userId: LiveData<Int>,
    nextUser: () -> Unit
) {
    WearOSSensorDataGatheringTheme {

        val sensorValue by sensorValue.observeAsState()
        val ppValue by ppValue.observeAsState()
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

            Text(text = "Last pp value: $ppValue")

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