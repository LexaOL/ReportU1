package com.example.reportu

import android.os.Bundle
import android.util.Patterns
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.reportu.databinding.ActivityRegisterBinding

/** Pantalla para crear una cuenta nueva (estudiante, docente o personal administrativo). */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val store by lazy { Store.get(this) }

    /** Roles que se pueden elegir al registrarse (mantenimiento no se auto-registra). */
    private val roles = listOf(Role.STUDENT, Role.TEACHER, Role.STAFF)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemInsets()

        binding.topBar.tvTopTitle.setText(R.string.register_top_title)
        binding.topBar.btnBack.setOnClickListener { finish() }

        binding.dropRole.options = roles.map { getString(it.label) }
        if (savedInstanceState == null) binding.dropRole.selectedIndex = 0

        binding.inputPassword.editText.imeOptions = EditorInfo.IME_ACTION_DONE
        binding.inputPassword.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptRegister()
                true
            } else {
                false
            }
        }

        binding.btnRegister.setOnClickListener { attemptRegister() }
        binding.tvLoginLink.setOnClickListener { finish() }
    }

    private fun attemptRegister() {
        val name = binding.inputName.text.trim()
        val studentId = binding.inputStudentId.text.trim()
        val email = binding.inputEmail.text.trim()
        val password = binding.inputPassword.text
        var firstInvalid: LabeledInput? = null

        fun fail(field: LabeledInput, message: String) {
            field.showError(message)
            if (firstInvalid == null) firstInvalid = field
        }

        if (name.length < 3) fail(binding.inputName, getString(R.string.error_name_short))
        if (!STUDENT_ID_REGEX.matches(studentId)) {
            fail(binding.inputStudentId, getString(R.string.error_student_id_invalid))
        }
        if (email.isEmpty()) {
            fail(binding.inputEmail, getString(R.string.error_email_required))
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            fail(binding.inputEmail, getString(R.string.error_email_invalid))
        }
        if (password.length < MIN_PASSWORD) {
            fail(binding.inputPassword, getString(R.string.error_password_short, MIN_PASSWORD))
        }

        firstInvalid?.let {
            binding.scroll.scrollToChild(it)
            it.editText.requestFocus()
            return
        }

        val role = roles.getOrElse(binding.dropRole.selectedIndex) { Role.STUDENT }
        binding.root.hideKeyboard()
        when (store.register(name, studentId, email, password, role)) {
            null -> {
                Toast.makeText(this, R.string.register_success, Toast.LENGTH_SHORT).show()
                store.currentUser()?.let { openHomeFor(it) }
            }
            AuthError.EMAIL_TAKEN -> {
                binding.inputEmail.showError(getString(R.string.error_email_taken))
                binding.inputEmail.editText.requestFocus()
            }
            AuthError.STUDENT_ID_TAKEN -> {
                binding.inputStudentId.showError(getString(R.string.error_student_id_taken))
                binding.inputStudentId.editText.requestFocus()
            }
            else -> binding.root.snack(getString(R.string.error_generic))
        }
    }

    private companion object {
        const val MIN_PASSWORD = 8
        val STUDENT_ID_REGEX = Regex("^[A-Za-z0-9]{4,15}$")
    }
}
