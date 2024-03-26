package com.google.mediapipe.examples.gesturerecognizer.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.mediapipe.examples.gesturerecognizer.SharedViewModel
import com.google.mediapipe.examples.gesturerecognizer.ml.MyModel
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class BluetoothServer(private val context: Context, private val sharedViewModel: SharedViewModel) {
    private val bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var serverSocket: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null

    private fun postImage(imageBitmap: Bitmap) {
        sharedViewModel.postImage(imageBitmap)
    }

    fun startServer(uuid: UUID, serviceName: String) {

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            // You can use the API that requires the permission.
            Log.d("BluetoothSocket", "Retrieving server socket");
            serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(serviceName, uuid)
            Log.d("BluetoothSocket", "Server socket retrieved " + serverSocket.toString());
        }

        // Start listening for connection in a new thread
        Thread {
            // Keep listening until exception occurs or a socket is returned
            while (true) {
                try {
                    Log.d("BluetoothSocket", "Connecting...");
                    socket = serverSocket?.accept()
                    Log.d("BluetoothSocket", "socket is " + socket.toString());
                } catch (e: IOException) {
                    break
                }
                // If a connection was accepted
                if (socket != null) {
                    // Manage the connected socket
                    Log.d("BluetoothSocket", "Socket connected: ${socket!!.isConnected}")
                    manageConnectedSocket(socket!!)
//                    serverSocket?.close()
//                    break
                }
            }
        }.start()
    }

    private fun classifyImage(resizedBitmap: Bitmap): Pair<String, Float>{
        val model = MyModel.newInstance(context)
        Log.d("BluetoothSocket", "creating input for reference")
        // Creates inputs for reference.
        val byteBuffer = ByteBuffer.allocateDirect(4 * 64 * 64 * 3) // float size is 4 bytes
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(64 * 64)
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.width, 0, 0, resizedBitmap.width, resizedBitmap.height)

        for (value in intValues) {
            val r = (value shr 16) and 0xFF
            val g = (value shr 8) and 0xFF
            val b = value and 0xFF

            byteBuffer.putFloat(r.toFloat())
            byteBuffer.putFloat(g.toFloat())
            byteBuffer.putFloat(b.toFloat())
        }

        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 64, 64, 3), DataType.FLOAT32)
        inputFeature0.loadBuffer(byteBuffer)
        Log.d("BluetoothSocket", "Generating output from model")
        // Runs model inference and gets result.
        val outputs = model.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer
        Log.d("BluetoothSocket", "Output generated")
        val confidences = outputFeature0.floatArray
        // Find the index of the class with the biggest confidence.
        var maxPos = 0
        var maxConfidence = 0f
        val classes = arrayOf(
            "A",
            "B",
            "C",
            "D",
            "E",
            "F",
            "G",
            "H",
            "I",
            "J",
            "K",
            "L",
            "M",
            "N",
            "O",
            "P",
            "Q",
            "R",
            "S",
            "T",
            "U",
            "V",
            "W",
            "X",
            "Y",
            "Z",
            "del",
            "nothing",
            "space",
        )
        for (i in confidences.indices) {
//            Log.d("BluetoothSocket", "Confidence for ${classes[i]} is ${confidences[i]}")
            if (confidences[i] > maxConfidence) {
                maxConfidence = confidences[i]
                maxPos = i
            }
        }
        Log.d("BluetoothSocket", "confidences size: ${confidences.size}")
        Log.d("BluetoothSocket", "MaxPos is: $maxPos")

        val predictedLabel = classes[maxPos]

        // Releases model resources if no longer used.
//        model.close()
        Log.d("BluetoothSocket", "Predicted Label: $predictedLabel, with confidence: $maxConfidence")
        return Pair(predictedLabel, maxConfidence)
    }

    fun sendBackResult(label: String, precision: Float) {
        val outputStream = socket?.outputStream
        val message = "Result: $label, Confidence: $precision"

        Log.d("BluetoothSocket", message)

        // Convert the message into bytes using the proper character encoding
        val messageBytes = message.toByteArray(Charsets.UTF_8)
        // Write the message bytes to the output stream
        outputStream?.write(messageBytes)
        // It's good practice to flush the stream to ensure all data is sent
        outputStream?.flush()
        // You might also want to implement some protocol to signal the end of a message or stream
        outputStream?.write('\n'.code)
        Log.d("BluetoothSocket", "Finish writing result back.")
    }

    private fun saveImageToFile(context: Context, imageData: ByteArray, filename: String) {
        // Use getExternalFilesDir on context to get a file directory that does not require runtime permissions
        val imageFile = File(context.filesDir, filename)
        try {
            FileOutputStream(imageFile).use { fos ->
                fos.write(imageData)
            }
            Log.d("BluetoothSocket", "Image saved to ${imageFile.absolutePath}")
        } catch (e: IOException) {
            Log.e("BluetoothSocket", "Error saving image to file", e)
        }
    }

    private fun readImageFromSocket(socket: BluetoothSocket): Bitmap ? {

        val inputStream = socket.inputStream
        val buffer = ByteArrayOutputStream()
        var bytesRead: Int
        val data = ByteArray(8192)
        Log.d("BluetoothSocket", "Start reading image from socket")

        try {
            while (true) {
                bytesRead = inputStream.read(data)
                if (bytesRead == -1) {
                    // End of stream reached unexpectedly
                    Log.d("BluetoothSocket", "End of stream reached unexpectedly")
                    break
                }

                // Write read bytes to the buffer
                buffer.write(data, 0, bytesRead)

                // Convert buffer to string to check for "END"
                val receivedString = buffer.toString("UTF-8")
                if (receivedString.endsWith("END")) {
                    Log.d("BluetoothSocket", "END sequence reached, total bytes read: ${buffer.size()}")
                    break
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }

        val imageData = buffer.toByteArray()

        val filename = "image_${System.currentTimeMillis()}.jpeg"
        saveImageToFile(context, imageData, filename)

        Log.d("BluetoothSocket", "Finish reading image from socket, size: ${imageData.size}")

        Log.d("BluetoothSocket", "Transform into image bitmap from byte array")
        val originalBitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size).copy(Bitmap.Config.ARGB_8888, true)

        // Create a Matrix for rotation
        val matrix = Matrix()
        matrix.postRotate(180f) // Rotate 180 degrees

        // Create a new bitmap from the original using the matrix
        val rotatedBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)

        return rotatedBitmap
//        return Bitmap.createScaledBitmap(imageBitmap, 64, 64, true)
    }

    // Define the manageConnectedSocket method to manage the BluetoothSocket
    private fun manageConnectedSocket(socket: BluetoothSocket) {
        try {
            // Get the output stream from the socket once at the beginning
            val outputStream = socket.outputStream

            // Continue reading, processing, and responding in a loop
            while (true) {
                Log.d("BluetoothSocket", "Reading image from socket")
                val imageBitmap = readImageFromSocket(socket)
                // Break the loop if socket is disconnected or an exit condition is met
                if (imageBitmap == null) {
                    Log.d("BluetoothSocket", "No image received or socket disconnected, exiting loop")
                    break
                }
                Log.d("BluetoothSocket", "Posting image to SharedViewModel")
                postImage(imageBitmap)

//                Log.d("BluetoothSocket", "Classifying image")
//                val result = classifyImage(imageBitmap)
//                val message = "Result: ${result.first}, Confidence: ${result.second}"
//
//                Log.d("BluetoothSocket", message)
//
//                // Convert the message into bytes using the proper character encoding
//                val messageBytes = message.toByteArray(Charsets.UTF_8)
//                // Write the message bytes to the output stream
//                outputStream.write(messageBytes)
//                // It's good practice to flush the stream to ensure all data is sent
//                outputStream.flush()
//                // You might also want to implement some protocol to signal the end of a message or stream
//                outputStream.write('\n'.code)
//                Log.d("BluetoothSocket", "Finish writing result back.")
            }
        } catch (e: IOException) {
            Log.e("BluetoothSocket", "Error managing the socket", e)
        }
//        finally {
//            try {
//                socket.close()
//            } catch (e: IOException) {
//                Log.e("BluetoothSocket", "Error closing the socket", e)
//            }
//        }
    }

    fun stopServer() {
        try {
            serverSocket?.close()
            Log.d("BluetoothSocket", "stopping server")
        } catch (e: IOException) {
            // Handle exception
        }
    }
}