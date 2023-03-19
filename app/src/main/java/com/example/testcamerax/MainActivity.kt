package com.example.testcamerax

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder

import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.testcamerax.databinding.ActivityMainBinding
import com.google.android.gms.maps.model.LatLng
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*

@ExperimentalGetImage
class MainActivity : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private val PERMISSION_CODE = 1


    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var imageCapture: ImageCapture
    private lateinit var binding: ActivityMainBinding
    private lateinit var recognizer: TextRecognizer
    private lateinit var api: ApiService

    val locationRequest = LocationRequest.create()
        .setInterval(10000) // 10 seconds
        .setFastestInterval(5000) // 5 seconds
        .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (!arePermissionsGranted()) {
            requestPermissions()
        } else {
            startCamera()
        }

    }


    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        // Set up the camera capture button

        binding.btnCapture.setOnClickListener {
            // Create a file to save the captured image
            val outputDirectory = getOutputDirectory()
            val photoFile = File(outputDirectory, "${System.currentTimeMillis()}.jpg")

            // Take a picture using the image capture use case
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        // Process the captured image to recognize text
                        processImage(image, photoFile)
                        getLocation()
                        super.onCaptureSuccess(image)
                    }
                })
        }

        // Set up the camera preview
        cameraProviderFuture.addListener({
            // Initialize the camera provider and image capture use case
            cameraProvider = cameraProviderFuture.get()
            imageCapture = ImageCapture.Builder()
                .setTargetRotation(binding.viewFinder.display.rotation)
                .build()

            // Set up the camera preview use case
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            // Select the back camera as the default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind any previously bound use cases
                cameraProvider.unbindAll()

                // Bind the preview and image capture use cases to the camera
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Unable to bind camera use cases", exc)
            }
        }, ContextCompat.getMainExecutor(this))

    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val requestCode = PERMISSION_CODE

        val cameraPermission = ContextCompat.checkSelfPermission(this, permissions[0])
        val locationPermission = ContextCompat.checkSelfPermission(this, permissions[1])

        val permissionList = ArrayList<String>()

        if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(permissions[0])
        }

        if (locationPermission != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(permissions[1])
        }

        if (permissionList.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionList.toTypedArray(),
                requestCode
            )
        }
    }

    private fun processImage(image: ImageProxy, photoFile: File) {
        // Create an InputImage from the captured image
        val mediaImage = image.image ?: return
        val imageRotation = image.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, imageRotation)

        // Pass the image to the text recognizer and extract the recognized text
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val recognizedText = visionText.text

                // Display the recognized text in a TextView or save it to a file
                binding.textView.text = recognizedText
                photoFile.writeText(recognizedText)

                // Close the image to free up resources
                image.close()
            }
            .addOnFailureListener { e ->
                // Handle the error
                Log.e(TAG, "Text recognition failed: $e")
                image.close()
            }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }


    // Check if the CAMERA permission is granted
    private fun isLocationPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun arePermissionsGranted(): Boolean {
        return isLocationPermissionGranted() && isCameraPermissionGranted()
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start the camera
                startCamera()
            } else {
                // Permission denied, show a message or handle the error
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }


    @SuppressLint("MissingPermission")
    private fun getLocation() {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        api = retrofit.create(ApiService::class.java)
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

    }

    private var locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation
            val origin = "${location.latitude},${location.longitude}"
            val destination = "Plaza Indonesia Jakarta"
            val units = "metric"
            val mode = "driving"
            val apiKey = ""
            val geocoder = Geocoder(this@MainActivity, Locale.getDefault())
            val addresses: MutableList<Address>? = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            val cityName = addresses?.get(0)?.locality // This will give you the city name
            Toast.makeText(
                this@MainActivity,
                "HORE",
                Toast.LENGTH_SHORT
            ).show()
            // Call the Distance Matrix API and handle the response
            val call: Call<DistanceMatrixResponse> =
                api.getDistanceMatrix(origin, destination, units, mode, apiKey)
            call.enqueue(object :
                Callback<DistanceMatrixResponse> {
                override fun onResponse(
                    call: Call<DistanceMatrixResponse>,
                    response: Response<DistanceMatrixResponse>
                ) {
                    if (response.isSuccessful) {
                        // Handle the successful response
                        val distance =
                            response.body()?.rows?.get(0)?.elements?.get(0)?.distance?.text
                        val duration =
                            response.body()?.rows?.get(0)?.elements?.get(0)?.duration?.text

                        binding.textViewLocation.text ="My current location is $cityName:, which is located  $distance away from Plaza Indonesia Jakarta.  The estimated travel duration by car is  $duration."
                        // Update the UI with the distance and duration values
                    } else {
                        // Handle the unsuccessful response
                        Toast.makeText(
                            this@MainActivity,
                            "Error: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<DistanceMatrixResponse>, t: Throwable) {
                    // Handle the failure
                    Toast.makeText(this@MainActivity, "Error: ${t.message}", Toast.LENGTH_SHORT)
                        .show()
                }
            })
        }
    }
}