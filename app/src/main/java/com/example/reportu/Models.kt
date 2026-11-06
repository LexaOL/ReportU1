package com.example.reportu

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/** Tipo de cuenta. Mantenimiento ve el panel de reportes; los demás reportan problemas. */
enum class Role(@StringRes val label: Int) {
    STUDENT(R.string.role_student),
    TEACHER(R.string.role_teacher),
    STAFF(R.string.role_staff),
    MAINTENANCE(R.string.role_maintenance)
}

/** Estado de un reporte: Pendiente → En proceso → Solucionado. */
enum class Status(
    @StringRes val label: Int,
    @DrawableRes val pill: Int,
    @ColorRes val textColor: Int,
    @DrawableRes val icon: Int
) {
    PENDING(R.string.status_pending, R.drawable.bg_pill_pending, R.color.ink, R.drawable.ic_clock),
    IN_PROGRESS(R.string.status_progress, R.drawable.bg_pill_progress, R.color.ink, R.drawable.ic_visibility),
    RESOLVED(R.string.status_resolved, R.drawable.bg_pill_resolved, R.color.white, R.drawable.ic_wrench)
}

enum class Category(@StringRes val label: Int) {
    PROJECTOR(R.string.cat_projector),
    LIGHTING(R.string.cat_lighting),
    FURNITURE(R.string.cat_furniture),
    AIR_CONDITIONING(R.string.cat_air),
    PLUMBING(R.string.cat_plumbing),
    ELECTRICITY(R.string.cat_electricity),
    NETWORK(R.string.cat_network),
    CLEANING(R.string.cat_cleaning),
    SECURITY(R.string.cat_security),
    OTHER(R.string.cat_other)
}

data class User(
    val name: String,
    val studentId: String,
    val email: String,
    val role: Role,
    val salt: String,
    val passwordHash: String,
    val createdAt: Long
)

/** Una entrada del historial de un reporte (creación, cambio de estado o comentario). */
data class ReportUpdate(
    val timestamp: Long,
    val message: String,
    val status: Status?
)

data class Report(
    val id: String,
    val ownerEmail: String,
    val title: String,
    val category: Category,
    val location: String,
    val description: String,
    val status: Status,
    val photoPath: String?,
    val createdAt: Long,
    val updates: List<ReportUpdate>
) {
    /** "#0042" */
    val folio: String get() = "#$id"

    /** Edificio: lo que va antes del "·" en "Edificio B · Salón 204". */
    val building: String get() = location.substringBefore('·').trim()
}

data class AppNotification(
    val reportId: String,
    val text: String,
    val timestamp: Long,
    val unread: Boolean
)
