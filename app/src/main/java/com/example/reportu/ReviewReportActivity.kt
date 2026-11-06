package com.example.reportu

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.reportu.databinding.ActivityReviewReportBinding

/** "Revisar reporte": mantenimiento cambia el estado y deja un comentario opcional. */
class ReviewReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReviewReportBinding
    private val store by lazy { Store.get(this) }
    private val statuses = Status.values().toList()
    private var report: Report? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val id = intent.getStringExtra(EXTRA_ID)
        val loaded = id?.let { store.getReport(it) }
        if (loaded == null || store.currentUser()?.role != Role.MAINTENANCE) {
            finish()
            return
        }
        report = loaded

        binding = ActivityReviewReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemInsets()

        binding.topBar.tvTopTitle.setText(R.string.review_title)
        binding.topBar.btnBack.setOnClickListener { finish() }

        binding.tvFolio.text = loaded.folio
        binding.tvStatus.bindStatus(loaded.status)
        binding.tvCategory.setText(loaded.category.label)
        binding.tvLocation.text = loaded.location
        binding.tvDescription.text = loaded.description

        val owner = store.findUser(loaded.ownerEmail)
        binding.tvReporter.text = if (owner != null) {
            getString(R.string.reporter_format, owner.name, getString(owner.role.label))
        } else {
            loaded.ownerEmail
        }

        val photo = loaded.photoPath?.let { Images.load(it, 1280) }
        binding.ivPhoto.isVisible = photo != null
        binding.photoPlaceholder.isVisible = photo == null
        if (photo != null) binding.ivPhoto.setImageBitmap(photo)

        binding.dropStatus.options = statuses.map { getString(it.label) }
        if (savedInstanceState == null) binding.dropStatus.selectedIndex = statuses.indexOf(loaded.status)

        binding.btnSave.setOnClickListener { save() }
    }

    private fun save() {
        val current = report ?: return
        val index = binding.dropStatus.selectedIndex
        if (index < 0) {
            binding.dropStatus.showError(getString(R.string.error_status_required))
            return
        }
        val newStatus = statuses[index]
        val comment = binding.inputComment.text

        if (newStatus == current.status && comment.isBlank()) {
            binding.root.snack(getString(R.string.review_no_changes))
            return
        }
        binding.root.hideKeyboard()
        store.review(current.id, newStatus, comment)
        Toast.makeText(this, R.string.review_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val EXTRA_ID = "report_id"
    }
}
