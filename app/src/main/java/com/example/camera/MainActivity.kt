package com.example.camera


import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.camera.ui.theme.CameraTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission())
    { isGranted ->
        if (isGranted) {
            Log.d("Camera", "Permission granted")
        } else {
            Log.d("Camera", "Permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            CameraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CameraScreen()
                }
            }
        }
    }
}

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { ImageCapture.Builder().build() }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Camera Preview
        CameraPreview(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            imageCapture = imageCapture,
            lifecycleOwner = lifecycleOwner
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Capture Button
        Button(
            onClick = {
                takePhoto(
                    context = context,
                    imageCapture = imageCapture,
                    executor = executor,
                    onImageCaptured = { uri ->
                        capturedImageUri = uri
                    },
                    onError = { Log.e("Camera", "Photo capture failed: ${it.message}", it) }
                )
            },
            modifier = Modifier.padding(16.dp)
        ) {
            Text("Capture Frame")
        }

        // Show the last captured image URI
        capturedImageUri?.let {
            Text(
                text = "Image saved: ${it.path}",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    imageCapture: ImageCapture,
    lifecycleOwner: LifecycleOwner
) {
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        factory = { ctx ->
            val previewView = androidx.camera.view.PreviewView(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                // Set up the preview use case
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // Choose the camera (back camera in this case)
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    // Unbind all use cases before rebinding
                    cameraProvider.unbindAll()

                    // Bind use cases to camera
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                } catch (ex: Exception) {
                    Log.e("CameraPreview", "Use case binding failed", ex)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: Executor,
    onImageCaptured: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    // Create a storage location for the image
    val photoFile = File(
        getOutputDirectory(context),
        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis()) + ".jpg"
    )

    // Create output options object which contains file + metadata
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    // Set up image capture listener
    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                onImageCaptured(savedUri)
            }

            override fun onError(exception: ImageCaptureException) {
                onError(exception)
            }
        }
    )
}

private fun getOutputDirectory(context: Context): File {
    val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
        File(it, "CameraFrameCapture").apply { mkdirs() }
    }
    return if (mediaDir != null && mediaDir.exists()) mediaDir else context.filesDir
}
