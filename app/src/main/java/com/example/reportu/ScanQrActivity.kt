package com.example.reportu

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.example.reportu.databinding.ActivityScanQrBinding
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Lee el código QR de un salón con la cámara. El QR debe contener el texto
 * "Edificio|Salón|Campus" (el campus es opcional), por ejemplo: Edificio B|Salón 204|Campus Central.
 * Devuelve la ubicación como "Edificio B · Salón 204" a la pantalla de Nuevo reporte.
 */
class ScanQrActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanQrBinding
    private lateinit var cameraExecutor: ExecutorService
    private val scanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        )
    }

    private var cameraStarted = false
    private var detectedLocation: String? = null

    private val requestCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else showNoPermission()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        binding = ActivityScanQrBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraExecutor = Executors.newSingleThreadExecutor()

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.topBarDark.updateLayoutParams<ViewGroup.LayoutParams> { height = 64.dp + bars.top }
            binding.topBarDark.setPadding(bars.left, bars.top, bars.right, 0)
            binding.bottomSheet.setPadding(24.dp + bars.left, 24.dp, 24.dp + bars.right, 24.dp + bars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnContinue.setOnClickListener {
            detectedLocation?.let {
                setResult(RESULT_OK, Intent().putExtra(EXTRA_LOCATION, it))
                finish()
            }
        }
        binding.cameraArea.setOnClickListener {
            // Si el permiso fue denegado, llevamos al usuario a Ajustes para activarlo.
            if (!hasCameraPermission()) openAppSettings()
        }
        setDetected(null, null)
    }

    override fun onStart() {
        super.onStart()
        if (hasCameraPermission()) {
            if (!cameraStarted) startCamera()
        } else if (!cameraStarted) {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
        )
    }

    // ------------------------------------------------------------------ Cámara

    private fun startCamera() {
        cameraStarted = true
        binding.ivQrPlaceholder.isVisible = false
        binding.tvScanTitle.setText(R.string.scan_title)
        binding.tvScanHint.setText(R.string.scan_hint)

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(binding.previewView.surfaceProvider)

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(cameraExecutor) { proxy -> analyze(proxy) }

                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                cameraStarted = false
                showCameraError()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null) {
            proxy.close()
            return
        }
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { codes ->
                val raw = codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                if (raw != null) onQrDetected(raw)
            }
            .addOnCompleteListener { proxy.close() }
    }

    // ------------------------------------------------------------------ Resultado

    private fun onQrDetected(raw: String) {
        val parts = raw.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return
        val location = parts.take(2).joinToString(" · ").take(80)
        if (location == detectedLocation) return
        setDetected(location, parts.getOrNull(2))
    }

    private fun setDetected(location: String?, campus: String?) {
        detectedLocation = location
        val found = location != null
        binding.tvDetectedLocation.text = location ?: getString(R.string.scan_not_detected)
        binding.tvDetectedCampus.text = campus ?: if (found) "" else getString(R.string.scan_not_detected_hint)
        binding.ivDetectedCheck.visibility = if (found) android.view.View.VISIBLE else android.view.View.INVISIBLE
        binding.btnContinue.isEnabled = found
        binding.btnContinue.alpha = if (found) 1f else 0.4f
    }

    private fun showNoPermission() {
        binding.ivQrPlaceholder.isVisible = true
        binding.tvScanTitle.setText(R.string.scan_no_permission_title)
        binding.tvScanHint.setText(R.string.scan_no_permission_hint)
    }

    private fun showCameraError() {
        binding.ivQrPlaceholder.isVisible = true
        binding.tvScanTitle.setText(R.string.scan_error_title)
        binding.tvScanHint.setText(R.string.scan_error_hint)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        scanner.close()
    }

    companion object {
        const val EXTRA_LOCATION = "location"
    }
}
