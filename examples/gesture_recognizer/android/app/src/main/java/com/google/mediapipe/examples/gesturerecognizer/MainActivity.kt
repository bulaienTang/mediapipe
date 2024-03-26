/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.gesturerecognizer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.mediapipe.examples.gesturerecognizer.bluetooth.BluetoothServer
import com.google.mediapipe.examples.gesturerecognizer.databinding.ActivityMainBinding
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var activityMainBinding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var bluetoothServer: BluetoothServer

    companion object {
        private const val REQUEST_BLUETOOTH_CONNECT = 101
    }
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment
        val navController = navHostFragment.navController
        activityMainBinding.navigation.setupWithNavController(navController)
        activityMainBinding.navigation.setOnNavigationItemReselectedListener {
            // ignore the reselection
        }

        val sharedViewModel: SharedViewModel by viewModels()

        // request bluetooth connect permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.d("BluetoothSocket", "Requesting Bluetooth Connection Permission")
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_BLUETOOTH_CONNECT)
        } else {
            // Proceed with your Bluetooth operations since permission is already granted
            Log.d("BluetoothSocket", "Connection Permission Already Granted, starting bluetooth server")
            bluetoothServer = BluetoothServer(this, sharedViewModel)
            val uuid = UUID.fromString("b22ab232-47c3-499d-acb5-85dcb714dd32")
            bluetoothServer.startServer(uuid, "MyGestureAppService")
        }

        sharedViewModel.gestureRecognitionResult.observe(this) { result ->
            val (label, confidence) = result
            bluetoothServer.sendBackResult(label, confidence)
        }

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_BLUETOOTH_CONNECT -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission is granted.
                    val sharedViewModel: SharedViewModel by viewModels()
                    Log.d("BluetoothSocket", "Connection Permission Granted, starting bluetooth server")
                    bluetoothServer = BluetoothServer(this, sharedViewModel);
                    val uuid = UUID.fromString("b22ab232-47c3-499d-acb5-85dcb714dd32")
                    bluetoothServer.startServer(uuid, "MyGestureAppService")
                } else {
                    // Explain to the user that the feature is unavailable because the permission is denied.
                    Log.e("BluetoothSocket", "Error Requesting Connection Permission")
                }
                return
            }
        }
    }

    override fun onBackPressed() {
        finish()
        Log.d("BluetoothSocket", "back pressed, stopping app")
        bluetoothServer.stopServer()
    }
}
