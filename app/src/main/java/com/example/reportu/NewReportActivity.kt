package com.example.reportu

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.location.LocationManagerCompat
import androidx.core.view.isVisible
import com.example.reportu.databinding.ActivityNewReportBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.util.Locale

/** Formulario "Nuevo reporte": tipo, descripción, foto y ubicación (texto, GPS o QR). */
class NewReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewReportBinding
    private val store by lazy { Store.get(this) }
    private val categories = Category.values().toList()

    private var photoPath: String? = null
    private var cameraUri: Uri? = null
    private var submitted = false

    // ---- Selectores de resultado ----

    private val pickPhoto =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) handlePhoto(uri)
        }

    private val takePhoto =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = cameraUri
            if (success && uri != null) handlePhoto(uri)
        }

    private val requestCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) openCamera() else toast(R.string.error_camera_permission)
        }

    private val requestLocation =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.any { it }) fetchLocation() else toast(R.string.error_location_permission)
        }

    private val scanQr =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.getStringExtra(ScanQrActivity.EXTRA_LOCATION)?.let {
                    binding.inputLocation.text = it
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityNewReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemInsets()

        binding.topBar.tvTopTitle.setText(R.string.new_report_title)
        binding.topBar.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        savedInstanceState?.let {
            photoPath = it.getString(STATE_PHOTO)
            cameraUri = it.getString(STATE_CAMERA_URI)?.let { s -> Uri.parse(s) }
        }

        binding.dropCategory.options = categories.map { getString(it.label) }
        updatePhotoPreview()

        binding.boxAddPhoto.setOnClickListener { showPhotoSourcePicker() }
        binding.btnRemovePhoto.setOnClickListener { removePhoto() }
        binding.btnGps.setOnClickListener { onGpsClick() }
        binding.btnQr.setOnClickListener {
            binding.root.hideKeyboard()
            scanQr.launch(Intent(this, ScanQrActivity::class.java))
        }
        binding.btnSubmit.setOnClickListener { submit() }

        // Si el usuario ya escribió algo, pedimos confirmación antes de descartar.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasChanges()) {
                    MaterialAlertDialogBuilder(this@NewReportActivity)
                        .setTitle(R.string.discard_title)
                        .setMessage(R.string.discard_message)
                        .setNegativeButton(R.string.discard_keep, null)
                        .setPositiveButton(R.string.discard_confirm) { _, _ -> finish() }
                        .show()
                } else {
                    finish()
                }
            }
        })
    }

    // ------------------------------------------------------------------ Foto

    private fun showPhotoSourcePicker() {
        val options = arrayOf(getString(R.string.photo_camera), getString(R.string.photo_gallery))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.photo_dialog_title)
            .setItems(options) { _, which ->
                if (which == 0) launchCamera() else launchGallery()
            }
            .show()
    }

    private fun launchGallery() {
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun launchCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            openCamera()
        } else {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCamera() {
        try {
            val dir = File(cacheDir, "camera").apply { mkdirs() }
            val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            cameraUri = uri
            takePhoto.launch(uri)
        } catch (e: Exception) {
            toast(R.string.error_camera)
        }
    }

    /** Reduce y guarda la foto elegida en segundo plano. */
    private fun handlePhoto(uri: Uri) {
        binding.btnSubmit.isEnabled = false
        binding.btnSubmit.alpha = 0.5f
        Images.saveAsync(this, uri) { path ->
            // La captura temporal de la cámara ya no hace falta.
            File(cacheDir, "camera").listFiles()?.forEach { it.delete() }

            if (isDestroyed) {
                path?.let { File(it).delete() }
                return@saveAsync
            }
            binding.btnSubmit.isEnabled = true
            binding.btnSubmit.alpha = 1f
            if (path == null) {
                toast(R.string.error_photo)
            } else {
                photoPath?.let { File(it).delete() }
                photoPath = path
                updatePhotoPreview()
            }
        }
    }

    private fun removePhoto() {
        photoPath?.let { File(it).delete() }
        photoPath = null
        updatePhotoPreview()
    }

    private fun updatePhotoPreview() {
        val bitmap = photoPath?.let { Images.load(it, 1024) }
        if (bitmap != null) {
            binding.ivPreview.setImageBitmap(bitmap)
            binding.boxPhotoPreview.isVisible = true
            binding.boxAddPhoto.isVisible = false
        } else {
            photoPath = null
            binding.boxPhotoPreview.isVisible = false
            binding.boxAddPhoto.isVisible = true
        }
    }

    // ------------------------------------------------------------------ GPS

    private fun onGpsClick() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            fetchLocation()
        } else {
            requestLocation.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull {
                try {
                    manager.isProviderEnabled(it)
                } catch (e: Exception) {
                    false
                }
            }
        if (provider == null) {
            toast(R.string.error_gps_disabled)
            return
        }

        setGpsBusy(true)
        try {
            LocationManagerCompat.getCurrentLocation(
                manager,
                provider,
                null,
                ContextCompat.getMainExecutor(this)
            ) { location: Location? ->
                if (isDestroyed) return@getCurrentLocation
                setGpsBusy(false)
                val result = location ?: lastKnownLocation(manager)
                if (result != null) applyLocation(result) else toast(R.string.error_gps_unavailable)
            }
        } catch (e: SecurityException) {
            setGpsBusy(false)
            toast(R.string.error_location_permission)
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(manager: LocationManager): Location? = try {
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { manager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
    } catch (e: Exception) {
        null
    }

    private fun applyLocation(location: Location) {
        binding.inputLocation.text = getString(
            R.string.gps_location_format,
            String.format(Locale.US, "%.5f", location.latitude),
            String.format(Locale.US, "%.5f", location.longitude)
        )
    }

    private fun setGpsBusy(busy: Boolean) {
        binding.btnGps.isEnabled = !busy
        binding.btnGps.setText(if (busy) R.string.btn_gps_busy else R.string.btn_gps)
    }

    // ------------------------------------------------------------------ Envío

    private fun submit() {
        val description = binding.inputDescription.text.trim()
        val location = binding.inputLocation.text.trim()
        val categoryIndex = binding.dropCategory.selectedIndex
        var firstInvalid: View? = null

        fun fail(view: View) {
            if (firstInvalid == null) firstInvalid = view
        }

        if (categoryIndex < 0) {
            binding.dropCategory.showError(getString(R.string.error_category_required))
            fail(binding.dropCategory)
        }
        if (description.length < MIN_DESCRIPTION) {
            binding.inputDescription.showError(getString(R.string.error_description_short, MIN_DESCRIPTION))
            fail(binding.inputDescription)
        }
        if (location.length < 3) {
            binding.inputLocation.showError(getString(R.string.error_location_required))
            fail(binding.inputLocation)
        }

        firstInvalid?.let {
            binding.scroll.scrollToChild(it)
            return
        }

        val user = store.currentUser() ?: return
        val report = store.createReport(
            owner = user,
            category = categories[categoryIndex],
            location = location,
            description = description,
            photoPath = photoPath
        )
        submitted = true
        binding.root.hideKeyboard()
        Toast.makeText(this, getString(R.string.report_sent, report.folio), Toast.LENGTH_LONG).show()
        startActivity(
            Intent(this, ReportDetailActivity::class.java)
                .putExtra(ReportDetailActivity.EXTRA_ID, report.id)
        )
        finish()
    }

    private fun hasChanges(): Boolean =
        binding.dropCategory.selectedIndex >= 0 ||
            binding.inputDescription.text.isNotBlank() ||
            binding.inputLocation.text.isNotBlank() ||
            photoPath != null

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    // ------------------------------------------------------------------ Estado

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PHOTO, photoPath)
        outState.putString(STATE_CAMERA_URI, cameraUri?.toString())
    }

    override fun onDestroy() {
        super.onDestroy()
        // Si se abandona el formulario, borramos la foto que no llegó a guardarse en un reporte.
        if (isFinishing && !submitted) photoPath?.let { File(it).delete() }
    }

    private companion object {
        const val MIN_DESCRIPTION = 10
        const val STATE_PHOTO = "photo"
        const val STATE_CAMERA_URI = "camera_uri"
    }
}
