package com.example.reportu

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.reportu.databinding.FragmentHomeBinding

/** Pestaña Inicio: saludo, tarjeta "Nuevo reporte" y reportes recientes. */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: RecentAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = RecentAdapter { report ->
            startActivity(
                Intent(requireContext(), ReportDetailActivity::class.java)
                    .putExtra(ReportDetailActivity.EXTRA_ID, report.id)
            )
        }
        binding.rvRecent.adapter = adapter

        binding.cardNewReport.setOnClickListener {
            (requireActivity() as HomeActivity).openNewReport()
        }
        binding.tvSeeAll.setOnClickListener {
            (requireActivity() as HomeActivity).showTab(HomeActivity.TAB_HISTORY)
        }
    }

    // Se recarga al volver de crear o ver un reporte.
    override fun onResume() {
        super.onResume()
        val store = Store.get(requireContext())
        val user = store.currentUser() ?: return
        val reports = store.reportsFor(user.email)

        binding.tvDate.text = Fmt.dateLong(System.currentTimeMillis())
        binding.tvGreeting.text = getString(R.string.home_greeting, user.name.substringBefore(' '))

        adapter.submitList(reports.take(RECENT_COUNT))
        binding.rvRecent.isVisible = reports.isNotEmpty()
        binding.emptyState.isVisible = reports.isEmpty()
        binding.tvSeeAll.isVisible = reports.isNotEmpty()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val RECENT_COUNT = 3
    }
}
