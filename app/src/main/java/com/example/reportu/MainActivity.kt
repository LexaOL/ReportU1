package com.example.reportu

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.example.reportu.databinding.ActivityMainBinding
import com.example.reportu.databinding.DialogMaintenanceLoginBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.max

/** Pantalla de inicio de sesión (estudiantes y docentes) con acceso para mantenimiento. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val store by lazy { Store.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Si ya hay una sesión iniciada, saltamos directo a la pantalla que le toca.
        store.currentUser()?.let {
            openHomeFor(it)
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupInsets()

        binding.inputPassword.editText.imeOptions = EditorInfo.IME_ACTION_DONE
        binding.inputPassword.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else {
                false
            }
        }

        binding.btnLogin.setOnClickListener { attemptLogin() }
        binding.tvRegisterLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        binding.btnMaintenance.setOnClickListener { showMaintenanceDialog() }
        binding.tvForgotPassword.setOnClickListener {
            binding.root.snack(getString(R.string.forgot_password_info))
        }
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, 0, bars.right, max(bars.bottom, keyboard.bottom))
            // El gris del encabezado llega hasta arriba: empujamos el logo bajo la barra de estado.
            binding.ivLogoIcon.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = 32.dp + bars.top
            }
            insets
        }
    }

    private fun attemptLogin() {
        val email = binding.inputEmail.text.trim()
        val password = binding.inputPassword.text
        var valid = true

        if (email.isEmpty()) {
            binding.inputEmail.showError(getString(R.string.error_email_required))
            valid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.inputEmail.showError(getString(R.string.error_email_invalid))
            valid = false
        }
        if (password.isEmpty()) {
            binding.inputPassword.showError(getString(R.string.error_password_required))
            valid = false
        }
        if (!valid) return

        binding.root.hideKeyboard()
        val user = if (store.login(email, password) == null) store.currentUser() else null
        if (user != null) {
            openHomeFor(user)
        } else {
            binding.inputPassword.showError(getString(R.string.error_invalid_credentials))
        }
    }

    /** "Acceso para mantenimiento": solo deja pasar cuentas con rol de mantenimiento. */
    private fun showMaintenanceDialog() {
        val d = DialogMaintenanceLoginBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.maintenance_login_title)
            .setMessage(R.string.maintenance_login_message)
            .setView(d.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.btn_login, null)
            .showManual { dialog ->
                val email = d.inputMaintEmail.text.trim()
                val password = d.inputMaintPassword.text
                var valid = true

                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    d.inputMaintEmail.showError(getString(R.string.error_email_invalid))
                    valid = false
                }
                if (password.isEmpty()) {
                    d.inputMaintPassword.showError(getString(R.string.error_password_required))
                    valid = false
                }
                if (!valid) return@showManual

                val isMaintenance = store.findUser(email.lowercase())?.role == Role.MAINTENANCE
                if (!isMaintenance) {
                    d.inputMaintEmail.showError(getString(R.string.error_not_maintenance))
                    return@showManual
                }
                if (store.login(email, password) != null) {
                    d.inputMaintPassword.showError(getString(R.string.error_invalid_credentials))
                    return@showManual
                }
                dialog.dismiss()
                store.currentUser()?.let { openHomeFor(it) }
            }
    }
}
