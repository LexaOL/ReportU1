package com.example.reportu

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.os.BundleCompat
import androidx.core.view.isVisible

/**
 * Campo desplegable (como el selector "Rol" o "Tipo de problema" del diseño):
 * muestra el valor elegido o un texto de ayuda y abre una lista al tocarlo.
 *
 * Atributos XML: dropLabel, dropHint, dropCompact.
 */
class DropdownField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val labelView: TextView
    private val valueView: TextView
    private val errorView: TextView
    private val box: View

    private var hasError = false

    /** Opciones disponibles. */
    var options: List<String> = emptyList()
        set(value) {
            field = value
            if (selectedIndex >= value.size) selectedIndex = -1
            refresh()
        }

    /** Posición elegida (-1 = ninguna). */
    var selectedIndex: Int = -1
        set(value) {
            field = value
            refresh()
        }

    val selectedValue: String?
        get() = options.getOrNull(selectedIndex)

    var hint: CharSequence? = null
        set(value) {
            field = value
            refresh()
        }

    /** Se llama cuando el usuario elige una opción. */
    var onSelected: ((Int) -> Unit)? = null

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_dropdown_field, this, true)
        labelView = findViewById(R.id.dfLabel)
        valueView = findViewById(R.id.dfValue)
        errorView = findViewById(R.id.dfError)
        box = findViewById(R.id.dfBox)

        context.withStyledAttributes(attrs, R.styleable.DropdownField) {
            val label = getString(R.styleable.DropdownField_dropLabel)
            labelView.text = label
            labelView.isVisible = !label.isNullOrEmpty()
            hint = getString(R.styleable.DropdownField_dropHint)
            if (getBoolean(R.styleable.DropdownField_dropCompact, false)) {
                box.layoutParams = box.layoutParams.apply { height = 44.dp }
                valueView.textSize = 12f
                box.setPaddingRelative(12.dp, 0, 8.dp, 0)
            }
        }

        box.setOnClickListener { showPopup() }
        refresh()
    }

    fun showError(message: String?) {
        hasError = message != null
        errorView.text = message
        errorView.isVisible = message != null
        box.setBackgroundResource(
            if (message != null) R.drawable.bg_dropdown_error else R.drawable.bg_dropdown
        )
    }

    private fun refresh() {
        val value = selectedValue
        if (value != null) {
            valueView.text = value
            valueView.setTextColor(ContextCompat.getColor(context, R.color.ink))
        } else {
            valueView.text = hint
            valueView.setTextColor(ContextCompat.getColor(context, R.color.hint))
        }
    }

    private fun showPopup() {
        if (options.isEmpty()) return
        box.hideKeyboard()
        val popup = ListPopupWindow(context)
        popup.anchorView = box
        popup.width = box.width
        popup.isModal = true
        popup.setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.bg_popup))
        popup.setAdapter(ArrayAdapter(context, R.layout.item_dropdown, options))
        popup.setOnItemClickListener { _, _, position, _ ->
            selectedIndex = position
            if (hasError) showError(null)
            onSelected?.invoke(position)
            popup.dismiss()
        }
        popup.show()
    }

    // Igual que LabeledInput: varias instancias comparten ids internos.

    override fun dispatchSaveInstanceState(container: SparseArray<Parcelable>) {
        dispatchFreezeSelfOnly(container)
    }

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>) {
        dispatchThawSelfOnly(container)
    }

    override fun onSaveInstanceState(): Parcelable {
        return Bundle().apply {
            putParcelable(STATE_SUPER, super.onSaveInstanceState())
            putInt(STATE_INDEX, selectedIndex)
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            super.onRestoreInstanceState(
                BundleCompat.getParcelable(state, STATE_SUPER, Parcelable::class.java)
            )
            selectedIndex = state.getInt(STATE_INDEX, -1)
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    private companion object {
        const val STATE_SUPER = "super"
        const val STATE_INDEX = "index"
    }
}
