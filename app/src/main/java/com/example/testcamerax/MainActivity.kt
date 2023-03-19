package com.example.testcamerax

import android.Manifest
import android.content.ContentValues.TAG
import android.content.Context
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.testcamerax.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


@ExperimentalGetImage class MainActivity : AppCompatActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private val CAMERA_PERMISSION_CODE = 1


    private lateinit var binding:ActivityMainBinding
    private lateinit var imageAnalyzer: ImageAnalysis.Analyzer
    private lateinit var recognizer: TextRecognizer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        initButton()
        // Request camera permission if necessary
        if (!isCameraPermissionGranted()) {
            requestCameraPermission()
        } else {
            startCamera()
        }
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun initButton(){
        binding.btnCapture.setOnClickListener {
            // Create a file to save the captured image
            val outputDirectory = getOutputDirectory()
            val photoFile = File(outputDirectory, "${System.currentTimeMillis()}.jpg")

            // Create an ImageCapture use case to capture the image
            val imageCapture = ImageCapture.Builder()
                .setTargetRotation(binding.viewFinder.display.rotation)
                .build()


            imageCapture.takePicture(
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        // Process the captured image to recognize text
                        processImage(image, photoFile)
                        super.onCaptureSuccess(image)
                    }
                })
        }
    }

    private fun startCamera(){
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }


            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview)

            } catch(exc: Exception) {
                Log.e("TEST", "Use case binding failed", exc)
            }
        },ContextCompat.getMainExecutor(this))
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
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }


    // Check if the CAMERA permission is granted
    private fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Request permission to use the camera
    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_CODE
        )
    }

    // Handle the result of the permission request
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start the camera
                startCamera()
            } else {
                // Permission denied, show a message or handle the error
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }



    @ExperimentalGetImage private inner class YourImageAnalyzer : ImageAnalysis.Analyzer {

        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        Log.d("TAG",visionText.text)
                        val resultText = visionText.text
                        runOnUiThread {  binding.textView.text = resultText}
                    }
                    .addOnFailureListener{e->
                    }
            }
        }
    }
}