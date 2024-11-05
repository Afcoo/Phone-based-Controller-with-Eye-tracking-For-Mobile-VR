package com.example.controller

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.controller.ui.theme.ControllerTheme
import java.io.InputStream
import java.io.OutputStream
import java.lang.Exception
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var rotationVector: Sensor? = null

    private var socket = Socket()
    private lateinit var outStream: OutputStream
    private lateinit var inStream: InputStream

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ControllerTheme() {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting(this)
                }
            }
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.apply {
            rotationVector = getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        }

        println("getting list of sensors")
        val deviceSensors: List<Sensor> = sensorManager.getSensorList(Sensor.TYPE_ALL)
        deviceSensors.forEach { sensor ->
            println(
                "name: ${sensor.name}, type: ${sensor.stringType}, power: ${sensor.power}, " +
                        "resolution: ${sensor.resolution}, range: ${sensor.maximumRange}, " +
                        "vendor: ${sensor.vendor}, version: ${sensor.version}"
            )
        }
    }

    override fun onResume() {
        super.onResume()

        sensorManager.apply {
            registerListener(this@MainActivity, rotationVector, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()

        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> getRotationVector(event)
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        println("AccuracyChanged")
    }

    private fun getRotationVector(event: SensorEvent) {
        var data = event.values.clone()

        var x = data[0]
        var y = data[1]
        var z = data[2]
        var w = data[3]

        sendDataToSocket("quaternion", String.format("%f,%f,%f,%f", x, y, z, w))
    }

    public fun connectSocket(
        hostname: String,
        port: Int,
        timeout: Int = 10000,
        message: MutableState<String>? = null
    ) {
        thread(start = true) {
            try {
                if (this.isSocketConnected()) {
                    throw Exception("Socket is already connected")
                }
                this.socket = Socket()
                this.socket.connect(InetSocketAddress(hostname, port), timeout)

                this.outStream = socket.getOutputStream()
                this.inStream = socket.getInputStream()

                if (message != null) {
                    message.value = "Connected to ${hostname}:${port}"
                }
            } catch (e: Exception) {
                println(e)
                println(e.message)

                if (message != null) {
                    message.value = e.message.toString()
                }
            }
        }
    }

    public fun closeSocket(exceptionMessage: MutableState<String>? = null) {
        thread(start = true) {
            try {
                this.socket.close()

                if (exceptionMessage != null) {
                    exceptionMessage.value = "Disconnected"
                }
            } catch (e: Exception) {
                println(e)
                println(e.message)

                if (exceptionMessage != null) {
                    exceptionMessage.value = e.message.toString()
                }
            }
        }
    }

    public fun sendDataToSocket(type: String, message: String = "") {
        thread(start = true) {
            if (this.isSocketConnected()) {
                try {
                    outStream.write(
                        "${type},${message}\n".toByteArray()
                    )
                } catch (e: Exception) {
                    println(e)
                    println(e.message)
                }
            }
        }
    }

    public fun isSocketConnected(): Boolean {
        return (this.socket.isConnected && !this.socket.isClosed)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Greeting(mainActivity: MainActivity) {
    val message = remember { mutableStateOf("") }

    val hostname = remember { mutableStateOf("192.168.137.1") }
    val port = remember { mutableStateOf("3207") }

    Column(
        modifier = Modifier.background(Color.Black)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            OutlinedTextField(
                modifier = Modifier.width(180.dp),
                label = { Text("IP Address") },
                value = hostname.value,
                onValueChange = { hostname.value = it },
                placeholder = { Text(text = "IP Address") },
                colors = TextFieldDefaults.textFieldColors(
                    textColor = Color.White,
                    containerColor = Color.Black
                )

            )
            Spacer(modifier = Modifier.width(10.dp))
            OutlinedTextField(
                modifier = Modifier.width(80.dp),
                label = { Text("Port") },
                value = port.value,
                onValueChange = { port.value = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = { Text(text = "Port Number") },
                colors = TextFieldDefaults.textFieldColors(
                    textColor = Color.White,
                    containerColor = Color.Black,

                    )
            )
        }

        Row(
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Button(
                onClick = {
                    mainActivity.connectSocket(
                        hostname.value,
                        port.value.toInt(),
                        message = message
                    )
                },
            ) {
                Text("Connect")
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = {
                    mainActivity.closeSocket(exceptionMessage = message)
                },
            ) {
                Text("Disconnect")
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = {
                    mainActivity.sendDataToSocket("correction", "")
                },
            ) {
                Text("Correction")
            }
        }


        Box(
            modifier = Modifier
                .height(130.dp)
                .fillMaxWidth()
                .padding(all = 5.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(10.dp)
                ),
        ) {
            Text(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(all = 5.dp),
                text = message.value,
                color = Color.White
            )
        }

        Box(
            modifier = Modifier
                .padding(all = 5.dp)
                .fillMaxSize()
                .clip(shape = RoundedCornerShape(10.dp))
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(10.dp)
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            println("tap start")
                            mainActivity.sendDataToSocket("tap_start")

                            awaitRelease()

                            println("tap end")
                            mainActivity.sendDataToSocket("tap_end")
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            println("drag start")
                        },
                        onDrag = { change: PointerInputChange, dragAmount: Offset ->
                            println("x: ${dragAmount.x}, y: ${dragAmount.y}")
                            mainActivity.sendDataToSocket("drag", "${dragAmount.x},${dragAmount.y}")
                        },
                        onDragCancel = {
                            println("drag cancel")
                        },
                        onDragEnd = {
                            println("drag end")
                            mainActivity.sendDataToSocket("tap_end")
                        }
                    )
                }
        )
    }

}