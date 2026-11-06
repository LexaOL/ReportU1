package com.example.reportu

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Resources
import android.graphics.Rect
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** dp → px */
val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density).roundToInt()

/** Formato de fechas y saludos en español. */
object Fmt {
    private val locale: Locale = Locale.forLanguageTag("es-MX")

    private fun format(pattern: String, ts: Long): String =
        SimpleDateFormat(pattern, locale).format(Date(ts))

    /** "MIÉRCOLES, 28 DE OCTUBRE" */
    fun dateLong(ts: Long): String =
        format("EEEE, d 'de' MMMM", ts).uppercase(locale)

    /** "14 may · 10:24" */
    fun step(ts: Long): String =
        format("d MMM · HH:mm", ts).replace(".", "").lowercase(locale)

    /** "Hoy, 10:24" · "Ayer, 17:42" · "12 may, 16:40" */
    fun history(ts: Long): String {
        val time = format("HH:mm", ts)
        return when (dayDiff(ts)) {
            0 -> "Hoy, $time"
            1 -> "Ayer, $time"
            else -> format("d MMM, HH:mm", ts).replace(".", "").lowercase(locale)
        }
    }

    /** "Hace 12 min" para lo reciente; después igual que [history]. */
    fun notification(ts: Long): String {
        val minutes = (System.currentTimeMillis() - ts) / 60_000
        return when {
            minutes < 1 -> "Ahora"
            minutes < 60 -> "Hace $minutes min"
            else -> history(ts)
        }
    }

    fun greeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Buenos días"
            hour < 19 -> "Buenas tardes"
            else -> "Buenas noches"
        }
    }

    fun isToday(ts: Long): Boolean = dayDiff(ts) == 0

    /** Días de calendario entre [ts] y hoy (0 = hoy, 1 = ayer...). */
    private fun dayDiff(ts: Long): Int {
        fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
            timeInMillis = time
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return ((startOfDay(System.currentTimeMillis()) - startOfDay(ts)) / 86_400_000L).toInt()
    }
}

/**
 * Aplica los insets de las barras del sistema (y opcionalmente del teclado) como padding,
 * respetando el padding original de la vista. Necesario con enableEdgeToEdge().
 */
fun View.applySystemInsets(top: Boolean = true, bottom: Boolean = true, ime: Boolean = true) {
    val left0 = paddingLeft
    val top0 = paddingTop
    val right0 = paddingRight
    val bottom0 = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
        val bottomInset = if (bottom) max(bars.bottom, if (ime) keyboard.bottom else 0) else 0
        v.setPadding(
            left0 + bars.left,
            top0 + if (top) bars.top else 0,
            right0 + bars.right,
            bottom0 + bottomInset
        )
        insets
    }
}

/** Muestra el estado como una "píldora" (Pendiente / En proceso / Solucionado). */
fun TextView.bindStatus(status: Status) {
    setText(status.label)
    setBackgroundResource(status.pill)
    setTextColor(ContextCompat.getColor(context, status.textColor))
}

fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(windowToken, 0)
}

fun View.snack(message: String) {
    Snackbar.make(this, message, Snackbar.LENGTH_LONG).show()
}

/** Desplaza el ScrollView hasta que [target] quede visible. */
fun ScrollView.scrollToChild(target: View) {
    val rect = Rect()
    target.getDrawingRect(rect)
    offsetDescendantRectToMyCoords(target, rect)
    smoothScrollTo(0, max(0, rect.top - 24.dp))
}

/**
 * Muestra el diálogo pero deja que [onPositive] decida cuándo cerrarlo
 * (útil para validar formularios sin que el diálogo se cierre solo).
 * El builder debe tener un botón positivo definido.
 */
fun MaterialAlertDialogBuilder.showManual(onPositive: (AlertDialog) -> Unit): AlertDialog {
    val dialog = create()
    dialog.show()
    dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { onPositive(dialog) }
    return dialog
}

/** Abre la pantalla que le corresponde al usuario según su rol y limpia el historial. */
fun Activity.openHomeFor(user: User) {
    val target = if (user.role == Role.MAINTENANCE) MaintenanceActivity::class.java
    else HomeActivity::class.java
    startActivity(
        Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
    finish()
}

/** Pregunta y cierra la sesión actual. */
fun Activity.confirmLogout(store: Store) {
    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.logout_title)
        .setMessage(R.string.logout_message)
        .setNegativeButton(R.string.action_cancel, null)
        .setPositiveButton(R.string.logout_confirm) { _, _ ->
            store.logout()
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
        }
        .show()
}
