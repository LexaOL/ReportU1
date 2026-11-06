package com.example.reportu

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.reportu.databinding.ActivityHomeBinding

/** Contenedor del estudiante/docente: Inicio · Reportar · Historial. */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val store by lazy { Store.get(this) }
    private var currentTab = TAB_HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val user = store.currentUser()
        if (user == null || user.role == Role.MAINTENANCE) {
            // Sin sesión (o cuenta de mantenimiento): corresponde a otra pantalla.
            if (user != null) openHomeFor(user) else startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
            return
        }

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemInsets(top = true, bottom = true, ime = false)

        currentTab = savedInstanceState?.getInt(STATE_TAB, TAB_HOME) ?: TAB_HOME
        if (savedInstanceState == null) showTab(TAB_HOME)
        updateChrome()

        binding.navHome.setOnClickListener { showTab(TAB_HOME) }
        binding.navHistory.setOnClickListener { showTab(TAB_HISTORY) }
        binding.navReport.setOnClickListener { openNewReport() }
        binding.btnBell.setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }
        binding.tvLogout.setOnClickListener { confirmLogout(store) }

        // Atrás desde Historial regresa a Inicio antes de salir de la app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentTab != TAB_HOME) {
                    showTab(TAB_HOME)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) refreshBell()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TAB, currentTab)
    }

    fun openNewReport() {
        startActivity(Intent(this, NewReportActivity::class.java))
    }

    /** Cambia de pestaña (también lo usan los fragmentos, p. ej. "Ver todos"). */
    fun showTab(tab: Int) {
        currentTab = tab
        val fragment: Fragment = if (tab == TAB_HISTORY) HistoryFragment() else HomeFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        updateChrome()
    }

    /** Ajusta encabezado y barra inferior según la pestaña activa. */
    private fun updateChrome() {
        val history = currentTab == TAB_HISTORY
        binding.tvSection.isVisible = history
        binding.headerSpacer.isVisible = !history
        binding.bellContainer.isVisible = !history
        binding.tvLogout.isVisible = history

        tint(binding.navHomeIcon, binding.navHomeLabel, !history)
        tint(binding.navHistoryIcon, binding.navHistoryLabel, history)
        refreshBell()
    }

    private fun tint(icon: ImageView, label: TextView, active: Boolean) {
        val color = ContextCompat.getColor(this, if (active) R.color.ink else R.color.hint)
        icon.setColorFilter(color)
        label.setTextColor(color)
    }

    private fun refreshBell() {
        val user = store.currentUser() ?: return
        binding.bellDot.isVisible = store.hasUnreadNotifications(user.email)
    }

    companion object {
        const val TAB_HOME = 0
        const val TAB_HISTORY = 1
        private const val STATE_TAB = "tab"
    }
}
