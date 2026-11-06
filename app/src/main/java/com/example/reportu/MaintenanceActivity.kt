package com.example.reportu

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.view.isVisible
import com.example.reportu.databinding.ActivityMaintenanceBinding

/** Panel del personal de mantenimiento: resumen, filtros y reportes asignados. */
class MaintenanceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMaintenanceBinding
    private val store by lazy { Store.get(this) }
    private lateinit var adapter: HistoryAdapter

    private var allReports: List<Report> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val user = store.currentUser()
        if (user == null || user.role != Role.MAINTENANCE) {
            // Esta pantalla es solo para mantenimiento.
            if (user != null) openHomeFor(user) else startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
            return
        }

        binding = ActivityMaintenanceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemInsets()

        binding.tvGreeting.text = getString(
            R.string.maintenance_greeting,
            Fmt.greeting(),
            user.name.substringBefore(' ')
        )
        binding.tvLogout.setOnClickListener { confirmLogout(store) }

        adapter = HistoryAdapter(showTime = false) { report ->
            startActivity(
                Intent(this, ReviewReportActivity::class.java)
                    .putExtra(ReviewReportActivity.EXTRA_ID, report.id)
            )
        }
        binding.rvReports.adapter = adapter

        binding.dropStatus.options = listOf(
            getString(R.string.filter_all_statuses),
            getString(Status.PENDING.label),
            getString(Status.IN_PROGRESS.label),
            getString(Status.RESOLVED.label)
        )
        binding.dropStatus.selectedIndex = 0
        binding.dropBuilding.options = listOf(getString(R.string.filter_all_buildings))
        binding.dropBuilding.selectedIndex = 0
        binding.dropStatus.onSelected = { applyFilters() }
        binding.dropBuilding.onSelected = { applyFilters() }
    }

    override fun onResume() {
        super.onResume()
        if (!::binding.isInitialized) return
        allReports = store.allReports()

        // Edificios que existen en los reportes (se conserva el elegido si sigue existiendo).
        val previous = binding.dropBuilding.selectedValue
        val buildings = allReports.map { it.building }.filter { it.isNotEmpty() }.distinct().sorted()
        val options = listOf(getString(R.string.filter_all_buildings)) + buildings
        binding.dropBuilding.options = options
        binding.dropBuilding.selectedIndex = options.indexOf(previous).coerceAtLeast(0)

        binding.tvStatPending.text = allReports.count { it.status == Status.PENDING }.toString()
        binding.tvStatProgress.text = allReports.count { it.status == Status.IN_PROGRESS }.toString()
        binding.tvStatToday.text = allReports.count { Fmt.isToday(it.createdAt) }.toString()
        applyFilters()
    }

    private fun applyFilters() {
        val status = when (binding.dropStatus.selectedIndex) {
            1 -> Status.PENDING
            2 -> Status.IN_PROGRESS
            3 -> Status.RESOLVED
            else -> null
        }
        val building = if (binding.dropBuilding.selectedIndex > 0) binding.dropBuilding.selectedValue else null

        val filtered = allReports.filter { report ->
            (status == null || report.status == status) &&
                (building == null || report.building == building)
        }
        adapter.submitList(filtered)
        binding.rvReports.isVisible = filtered.isNotEmpty()
        binding.tvEmpty.isVisible = filtered.isEmpty()

        val active = filtered.count { it.status != Status.RESOLVED }
        binding.tvActive.text = buildSpannedString {
            bold { append(active.toString()) }
            append(" ")
            append(getString(R.string.maintenance_active))
        }
    }
}
