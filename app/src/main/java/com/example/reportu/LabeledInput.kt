package com.example.reportu

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.text.InputFilter
import android.text.InputType
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.AttributeSet
import android.util.SparseArray
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.withStyledAttributes
import androidx.core.os.BundleCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged

/**
 * Campo de texto con etiqueta arriba y mensaje de error abajo.
 * Soporta contraseña (con botón para mostrar/ocultar), texto multilínea y contador de caracteres.
 *
 * Atributos XML: inputLabel, inputHint, inputKind, inputMaxLength.
 */
class LabeledInput @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    enum class Kind { TEXT, EMAIL, PASSWORD, MULTILINE, NAME, CODE }

    private val labelView: TextView
    private val counterView: TextView
    private val errorView: TextView
    private val toggle: ImageButton
    val editText: EditText

    private var kind = Kind.TEXT
    private var maxLength = 0
    private var passwordVisible = false
    private var hasError = false
    private var showToggle = false
    private var helperText: CharSequence? = null

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_labeled_input, this, true)
        labelView = findViewById(R.id.liLabel)
        counterView = findViewById(R.id.liCounter)
        errorView = findViewById(R.id.liError)
        toggle = findViewById(R.id.liToggle)
        editText = findViewById(R.id.liEdit)

        toggle.setOnClickListener {
            passwordVisible = !passwordVisible
            applyPasswordVisibility()
        }
        editText.doAfterTextChanged {
            updateCounter()
            if (hasError) showError(null)
        }

        context.withStyledAttributes(attrs, R.styleable.LabeledInput) {
            label = getString(R.styleable.LabeledInput_inputLabel)
            hint = getString(R.styleable.LabeledInput_inputHint)
            showToggle = getBoolean(R.styleable.LabeledInput_inputToggle, false)
            helper = getString(R.styleable.LabeledInput_inputHelper)
            setKind(Kind.values()[getInt(R.styleable.LabeledInput_inputKind, 0)])
            setMaxLength(getInt(R.styleable.LabeledInput_inputMaxLength, 0))
        }
    }

    // ------------------------------------------------------------------ API pública

    var label: CharSequence?
        get() = labelView.text
        set(value) {
            labelView.text = value
            labelView.isVisible = !value.isNullOrEmpty()
        }

    var hint: CharSequence?
        get() = editText.hint
        set(value) {
            editText.hint = value
        }

    /** Texto auxiliar a la derecha de la etiqueta (por ejemplo "Opcional"). */
    var helper: CharSequence?
        get() = helperText
        set(value) {
            helperText = value
            updateCounter()
        }

    var text: String
        get() = editText.text?.toString().orEmpty()
        set(value) {
            editText.setText(value)
            editText.setSelection(editText.text?.length ?: 0)
        }

    fun setKind(newKind: Kind) {
        kind = newKind
        val type = when (newKind) {
            Kind.TEXT ->
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            Kind.EMAIL ->
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            Kind.PASSWORD ->
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            Kind.MULTILINE ->
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            Kind.NAME ->
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME or
                    InputType.TYPE_TEXT_FLAG_CAP_WORDS
            Kind.CODE ->
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        editText.inputType = type

        if (newKind == Kind.MULTILINE) {
            editText.minLines = 4
            editText.gravity = Gravity.TOP or Gravity.START
        } else {
            editText.minLines = 1
            editText.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (newKind) {
                Kind.EMAIL -> editText.setAutofillHints(View.AUTOFILL_HINT_EMAIL_ADDRESS)
                Kind.PASSWORD -> editText.setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
                Kind.NAME -> editText.setAutofillHints(View.AUTOFILL_HINT_NAME)
                else -> Unit
            }
        }

        toggle.isVisible = newKind == Kind.PASSWORD && showToggle
        editText.setPaddingRelative(
            16.dp,
            12.dp,
            if (newKind == Kind.PASSWORD && showToggle) 48.dp else 16.dp,
            12.dp
        )
        applyPasswordVisibility()
    }

    /** Limita la longitud y muestra un contador "n/máx" junto a la etiqueta. 0 = sin límite. */
    fun setMaxLength(max: Int) {
        maxLength = max
        editText.filters = if (max > 0) arrayOf(InputFilter.LengthFilter(max)) else emptyArray()
        updateCounter()
    }

    /** Muestra [message] bajo el campo y marca el borde en rojo; null limpia el error. */
    fun showError(message: String?) {
        hasError = message != null
        errorView.text = message
        errorView.isVisible = message != null
        editText.setBackgroundResource(
            if (message != null) R.drawable.bg_input_error else R.drawable.bg_input_outline
        )
    }

    // ------------------------------------------------------------------ Internos

    private fun updateCounter() {
        if (maxLength > 0) {
            counterView.text = context.getString(
                R.string.char_counter,
                editText.text?.length ?: 0,
                maxLength
            )
            counterView.isVisible = true
        } else {
            counterView.text = helperText
            counterView.isVisible = !helperText.isNullOrEmpty()
        }
    }

    private fun applyPasswordVisibility() {
        if (kind != Kind.PASSWORD) return
        // Al cambiar el inputType a contraseña Android fuerza una fuente monoespaciada.
        editText.typeface = Typeface.DEFAULT
        editText.transformationMethod =
            if (passwordVisible) HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
        editText.setSelection(editText.text?.length ?: 0)
        toggle.setImageResource(
            if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility
        )
        toggle.contentDescription = context.getString(
            if (passwordVisible) R.string.cd_hide_password else R.string.cd_show_password
        )
    }

    // Varias instancias comparten los mismos ids internos: guardamos el estado a mano
    // para que al rotar la pantalla cada campo conserve su propio texto.

    override fun dispatchSaveInstanceState(container: SparseArray<Parcelable>) {
        dispatchFreezeSelfOnly(container)
    }

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>) {
        dispatchThawSelfOnly(container)
    }

    override fun onSaveInstanceState(): Parcelable {
        return Bundle().apply {
            putParcelable(STATE_SUPER, super.onSaveInstanceState())
            putString(STATE_TEXT, text)
            putBoolean(STATE_VISIBLE, passwordVisible)
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            super.onRestoreInstanceState(
                BundleCompat.getParcelable(state, STATE_SUPER, Parcelable::class.java)
            )
            passwordVisible = state.getBoolean(STATE_VISIBLE, false)
            text = state.getString(STATE_TEXT).orEmpty()
            applyPasswordVisibility()
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    private companion object {
        const val STATE_SUPER = "super"
        const val STATE_TEXT = "text"
        const val STATE_VISIBLE = "visible"
    }
}
