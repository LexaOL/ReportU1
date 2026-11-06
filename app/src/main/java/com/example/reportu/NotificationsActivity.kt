package com.example.reportu

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.reportu.databinding.ActivityNotificationsBinding

/**
 * Notificaciones del usuario: se generan cuando se recibe un reporte, cambia su estado
 * o mantenimiento deja un comentario. Las nuevas aparecen con punto y se marcan como
 * leídas al salir de la pantalla.
 */
class NotificationsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationsBinding
    private val store by lazy { Store.get(this) }
    private lateinit var adapter: NotificationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityNotificationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemInsets()

        binding.topBar.tvTopTitle.setText(R.string.title_notifications)
        binding.topBar.btnBack.setOnClickListener { finish() }

        adapter = NotificationAdapter { item ->
            startActivity(
                Intent(this, ReportDetailActivity::class.java)
                    .putExtra(ReportDetailActivity.EXTRA_ID, item.reportId)
            )
        }
        binding.rvNotifications.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        val user = store.currentUser() ?: return
        val items = store.notificationsFor(user.email)
        adapter.submitList(items)
        binding.rvNotifications.isVisible = items.isNotEmpty()
        binding.tvEmpty.isVisible = items.isEmpty()
    }

    // Al salir, todo lo mostrado pasa a "leído".
    override fun onPause() {
        super.onPause()
        store.currentUser()?.let { store.markNotificationsRead(it.email) }
    }
}
