package com.example.reportu

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.reportu.databinding.FragmentHistoryBinding

/** Pestaña Historial: todos los reportes del usuario con filtros por estado. */
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: HistoryAdapter
    private var allReports: List<Report> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = HistoryAdapter(showTime = true) { report ->
            startActivity(
                Intent(requireContext(), ReportDetailActivity::class.java)
                    .putExtra(ReportDetailActivity.EXTRA_ID, report.id)
            )
        }
        binding.rvHistory.adapter = adapter
        binding.chipFilters.setOnCheckedStateChangeListener { _, _ -> applyFilter() }
    }

    override fun onResume() {
        super.onResume()
        val store = Store.get(requireContext())
        val user = store.currentUser() ?: return
        allReports = store.reportsFor(user.email)
        applyFilter()
    }

    private fun applyFilter() {
        if (_binding == null) return

        val status = when (binding.chipFilters.checkedChipId) {
            R.id.chipPending -> Status.PENDING
            R.id.chipProgress -> Status.IN_PROGRESS
            R.id.chipResolved -> Status.RESOLVED
            else -> null
        }
        val filtered = if (status == null) allReports else allReports.filter { it.status == status }
        adapter.submitList(filtered)

        binding.tvCount.text = resources.getQuantityString(
            R.plurals.reports_count, filtered.size, filtered.size
        )

        val empty = filtered.isEmpty()
        binding.emptyState.isVisible = empty
        binding.rvHistory.isVisible = !empty
        if (empty) {
            if (allReports.isEmpty()) {
                binding.tvEmptyTitle.setText(R.string.empty_history_title)
                binding.tvEmptyText.setText(R.string.empty_history_text)
            } else {
                binding.tvEmptyTitle.setText(R.string.empty_filter_title)
                binding.tvEmptyText.setText(R.string.empty_filter_text)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
