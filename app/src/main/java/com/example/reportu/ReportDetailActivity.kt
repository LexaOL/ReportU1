package com.example.reportu

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.reportu.databinding.ActivityReportDetailBinding
import com.example.reportu.databinding.ViewStepBinding

/** Detalle del reporte para quien lo creó: datos, foto y avance (Pendiente → Solucionado). */
class ReportDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportDetailBinding
    private val store by lazy { Store.get(this) }
    private var report: Report? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityReportDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemInsets()

        binding.topBar.tvTopTitle.setText(R.string.detail_title)
        binding.topBar.btnBack.setOnClickListener { finish() }
        binding.btnBackToHistory.setOnClickListener { finish() }
        binding.ivPhoto.setOnClickListener { showFullPhoto() }
    }

    // Se recarga por si el estado cambió mientras la pantalla estaba atrás.
    override fun onResume() {
        super.onResume()
        val id = intent.getStringExtra(EXTRA_ID)
        val loaded = id?.let { store.getReport(it) }
        val user = store.currentUser()
        if (loaded == null || user == null || loaded.ownerEmail != user.email) {
            finish()
            return
        }
        report = loaded
        bind(loaded)
    }

    private fun bind(r: Report) {
        binding.tvFolio.text = r.folio
        binding.tvStatus.bindStatus(r.status)
        binding.tvCategory.setText(r.category.label)
        binding.tvLocation.text = r.location
        binding.tvDescription.text = r.description

        val photo = r.photoPath?.let { Images.load(it, 1280) }
        binding.ivPhoto.isVisible = photo != null
        binding.photoPlaceholder.isVisible = photo == null
        if (photo != null) binding.ivPhoto.setImageBitmap(photo)

        bindSteps(r)
        bindNotes(r)
    }

    /** Tres pasos: ✓ completados, número en el actual, círculo vacío en los pendientes. */
    private fun bindSteps(r: Report) {
        val steps = listOf<Pair<ViewStepBinding, Status>>(
            binding.step1 to Status.PENDING,
            binding.step2 to Status.IN_PROGRESS,
            binding.step3 to Status.RESOLVED
        )
        val currentIndex = Status.values().indexOf(r.status)

        steps.forEachIndexed { index, (step, status) ->
            step.stepLineLeft.visibility = if (index == 0) View.INVISIBLE else View.VISIBLE
            step.stepLineRight.visibility = if (index == steps.lastIndex) View.INVISIBLE else View.VISIBLE
            step.stepLabel.setText(status.label)

            val reached = index <= currentIndex
            val done = index < currentIndex || (reached && status == Status.RESOLVED)
            if (reached) {
                step.stepCircle.setBackgroundResource(R.drawable.bg_circle_black)
                step.stepCircle.setTextColor(ContextCompat.getColor(this, R.color.white))
                step.stepCircle.text = if (done) "✓" else (index + 1).toString()
                val time = r.updates.lastOrNull { it.status == status }?.timestamp
                step.stepTime.text = if (time != null) Fmt.step(time) else "—"
            } else {
                step.stepCircle.setBackgroundResource(R.drawable.bg_circle_outline_light)
                step.stepCircle.setTextColor(ContextCompat.getColor(this, R.color.muted))
                step.stepCircle.text = (index + 1).toString()
                step.stepTime.setText(R.string.step_pending)
            }
        }
    }

    /** Comentarios que dejó mantenimiento (la sección solo aparece si hay alguno). */
    private fun bindNotes(r: Report) {
        val notes = r.updates.drop(1).filter { it.message.isNotBlank() }.sortedByDescending { it.timestamp }
        binding.notesSection.isVisible = notes.isNotEmpty()
        binding.llNotes.removeAllViews()
        notes.forEach { note ->
            binding.llNotes.addView(TextView(this).apply {
                text = getString(R.string.note_format, Fmt.step(note.timestamp), note.message)
                setTextColor(ContextCompat.getColor(context, R.color.ink))
                textSize = 12f
                setLineSpacing(3.dp.toFloat(), 1f)
                setPadding(0, 0, 0, 12.dp)
            })
        }
    }

    private fun showFullPhoto() {
        val path = report?.photoPath ?: return
        val bitmap = Images.load(path, 2048) ?: return
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val image = ImageView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = getString(R.string.cd_report_photo)
            setImageBitmap(bitmap)
            setOnClickListener { dialog.dismiss() }
        }
        dialog.setContentView(image)
        dialog.window?.setBackgroundDrawable(ColorDrawable(0xFF000000.toInt()))
        dialog.show()
    }

    companion object {
        const val EXTRA_ID = "report_id"
    }
}
