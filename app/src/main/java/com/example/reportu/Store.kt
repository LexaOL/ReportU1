package com.example.reportu

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

/** Errores posibles al autenticar. */
enum class AuthError {
    EMAIL_TAKEN,
    STUDENT_ID_TAKEN,
    INVALID_CREDENTIALS
}

/**
 * Almacenamiento local de la app (usuarios, sesión, reportes y notificaciones leídas).
 * Las contraseñas se guardan con sal + SHA-256, nunca en texto plano.
 *
 * Como no hay servidor, los reportes de todas las cuentas viven en el mismo dispositivo;
 * por eso el personal de mantenimiento ve los reportes creados por los estudiantes.
 */
class Store private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        seedMaintenanceAccount()
    }

    // ------------------------------------------------------------------ Sesión

    fun currentUser(): User? {
        val email = prefs.getString(KEY_SESSION, null) ?: return null
        return findUser(email)
    }

    fun findUser(email: String): User? = readUsers().firstOrNull { it.email == email }

    fun logout() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    // ------------------------------------------------------------------ Cuentas

    /** Crea la cuenta e inicia sesión. Devuelve un error o null si todo salió bien. */
    fun register(name: String, studentId: String, email: String, password: String, role: Role): AuthError? {
        val users = readUsers()
        val mail = normalizeEmail(email)
        val code = studentId.trim().uppercase()
        if (users.any { it.email == mail }) return AuthError.EMAIL_TAKEN
        if (users.any { it.studentId == code }) return AuthError.STUDENT_ID_TAKEN

        val salt = newSalt()
        users.add(
            User(name.trim(), code, mail, role, salt, hash(password, salt), System.currentTimeMillis())
        )
        writeUsers(users)
        prefs.edit().putString(KEY_SESSION, mail).apply()
        return null
    }

    fun login(email: String, password: String): AuthError? {
        val user = readUsers().firstOrNull { it.email == normalizeEmail(email) }
            ?: return AuthError.INVALID_CREDENTIALS
        val ok = MessageDigest.isEqual(
            hash(password, user.salt).toByteArray(Charsets.UTF_8),
            user.passwordHash.toByteArray(Charsets.UTF_8)
        )
        if (!ok) return AuthError.INVALID_CREDENTIALS
        prefs.edit().putString(KEY_SESSION, user.email).apply()
        return null
    }

    /** Cuenta de demostración para probar el panel de mantenimiento. */
    private fun seedMaintenanceAccount() {
        val users = readUsers()
        if (users.any { it.email == DEMO_MAINTENANCE_EMAIL }) return
        val salt = newSalt()
        users.add(
            User(
                name = "Carlos Ramírez",
                studentId = "MNT-001",
                email = DEMO_MAINTENANCE_EMAIL,
                role = Role.MAINTENANCE,
                salt = salt,
                passwordHash = hash(DEMO_MAINTENANCE_PASSWORD, salt),
                createdAt = System.currentTimeMillis()
            )
        )
        writeUsers(users)
    }

    // ------------------------------------------------------------------ Reportes

    /** Reportes de un usuario, del más reciente al más antiguo. */
    fun reportsFor(email: String): List<Report> =
        readReports().filter { it.ownerEmail == email }.sortedByDescending { it.createdAt }

    /** Todos los reportes (vista de mantenimiento). */
    fun allReports(): List<Report> = readReports().sortedByDescending { it.createdAt }

    fun getReport(id: String): Report? = readReports().firstOrNull { it.id == id }

    fun createReport(
        owner: User,
        category: Category,
        location: String,
        description: String,
        photoPath: String?
    ): Report {
        val now = System.currentTimeMillis()
        val report = Report(
            id = nextFolio(),
            ownerEmail = owner.email,
            title = deriveTitle(description),
            category = category,
            location = location.trim(),
            description = description.trim(),
            status = Status.PENDING,
            photoPath = photoPath,
            createdAt = now,
            updates = listOf(ReportUpdate(now, "", Status.PENDING))
        )
        val all = readReports()
        all.add(report)
        writeReports(all)
        return report
    }

    /**
     * Mantenimiento actualiza el estado y/o deja un comentario.
     * Si el estado no cambia pero hay comentario, se registra solo el comentario.
     */
    fun review(id: String, newStatus: Status, comment: String): Report? {
        val all = readReports()
        val index = all.indexOfFirst { it.id == id }
        if (index < 0) return null
        val report = all[index]
        val text = comment.trim()

        val update = when {
            newStatus != report.status -> ReportUpdate(System.currentTimeMillis(), text, newStatus)
            text.isNotEmpty() -> ReportUpdate(System.currentTimeMillis(), text, null)
            else -> return report
        }
        val updated = report.copy(status = newStatus, updates = report.updates + update)
        all[index] = updated
        writeReports(all)
        return updated
    }

    private fun nextFolio(): String {
        val next = prefs.getInt(KEY_COUNTER, FIRST_FOLIO - 1) + 1
        prefs.edit().putInt(KEY_COUNTER, next).apply()
        return String.format(Locale.ROOT, "%04d", next)
    }

    /** Título corto a partir de la descripción: primera oración, máx. ~45 caracteres. */
    private fun deriveTitle(description: String): String {
        val firstLine = description.trim().lineSequence().firstOrNull().orEmpty().trim()
        val sentence = firstLine.split(Regex("(?<=[.!?])\\s")).first().trimEnd('.', '!', '?', ' ')
        val text = if (sentence.length <= MAX_TITLE) {
            sentence
        } else {
            val cut = sentence.take(MAX_TITLE)
            cut.substringBeforeLast(' ', cut).trimEnd(',', ';', ':', ' ') + "…"
        }
        return text.replaceFirstChar { it.uppercase() }
    }

    // ------------------------------------------------------------------ Notificaciones

    /** Avisos del usuario: se generan a partir de los cambios en sus reportes. */
    fun notificationsFor(email: String): List<AppNotification> {
        val lastRead = prefs.getLong(KEY_READ_PREFIX + email, 0L)
        val result = mutableListOf<AppNotification>()
        reportsFor(email).forEach { report ->
            report.updates.forEachIndexed { index, update ->
                val text = when {
                    index == 0 -> appContext.getString(R.string.notif_received, report.folio)
                    update.status == Status.IN_PROGRESS ->
                        appContext.getString(R.string.notif_progress, report.folio)
                    update.status == Status.RESOLVED ->
                        appContext.getString(R.string.notif_resolved, report.folio)
                    update.status == Status.PENDING ->
                        appContext.getString(R.string.notif_pending, report.folio)
                    else -> appContext.getString(R.string.notif_comment, report.folio)
                }
                result.add(
                    AppNotification(
                        reportId = report.id,
                        text = text,
                        timestamp = update.timestamp,
                        unread = index != 0 && update.timestamp > lastRead
                    )
                )
            }
        }
        return result.sortedByDescending { it.timestamp }
    }

    fun hasUnreadNotifications(email: String): Boolean =
        notificationsFor(email).any { it.unread }

    fun markNotificationsRead(email: String) {
        prefs.edit().putLong(KEY_READ_PREFIX + email, System.currentTimeMillis()).apply()
    }

    // ------------------------------------------------------------------ Seguridad

    private fun normalizeEmail(email: String) = email.trim().lowercase()

    private fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun hash(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((salt + password).toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    // ------------------------------------------------------------------ JSON

    private fun readUsers(): MutableList<User> {
        val array = JSONArray(prefs.getString(KEY_USERS, "[]"))
        val list = mutableListOf<User>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(
                User(
                    name = o.getString("name"),
                    studentId = o.getString("studentId"),
                    email = o.getString("email"),
                    role = parseEnum(o.optString("role"), Role.STUDENT),
                    salt = o.getString("salt"),
                    passwordHash = o.getString("hash"),
                    createdAt = o.getLong("createdAt")
                )
            )
        }
        return list
    }

    private fun writeUsers(users: List<User>) {
        val array = JSONArray()
        users.forEach { u ->
            array.put(
                JSONObject()
                    .put("name", u.name)
                    .put("studentId", u.studentId)
                    .put("email", u.email)
                    .put("role", u.role.name)
                    .put("salt", u.salt)
                    .put("hash", u.passwordHash)
                    .put("createdAt", u.createdAt)
            )
        }
        prefs.edit().putString(KEY_USERS, array.toString()).apply()
    }

    private fun readReports(): MutableList<Report> {
        val array = JSONArray(prefs.getString(KEY_REPORTS, "[]"))
        val list = mutableListOf<Report>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val updatesJson = o.optJSONArray("updates") ?: JSONArray()
            val updates = mutableListOf<ReportUpdate>()
            for (j in 0 until updatesJson.length()) {
                val u = updatesJson.getJSONObject(j)
                updates.add(
                    ReportUpdate(
                        timestamp = u.getLong("ts"),
                        message = u.optString("message"),
                        status = if (u.isNull("status")) null else parseEnum(u.getString("status"), Status.PENDING)
                    )
                )
            }
            list.add(
                Report(
                    id = o.getString("id"),
                    ownerEmail = o.getString("owner"),
                    title = o.getString("title"),
                    category = parseEnum(o.optString("category"), Category.OTHER),
                    location = o.getString("location"),
                    description = o.getString("description"),
                    status = parseEnum(o.optString("status"), Status.PENDING),
                    photoPath = if (o.isNull("photo")) null else o.getString("photo"),
                    createdAt = o.getLong("createdAt"),
                    updates = updates
                )
            )
        }
        return list
    }

    private fun writeReports(reports: List<Report>) {
        val array = JSONArray()
        reports.forEach { r ->
            val updates = JSONArray()
            r.updates.forEach { u ->
                updates.put(
                    JSONObject()
                        .put("ts", u.timestamp)
                        .put("message", u.message)
                        .put("status", u.status?.name ?: JSONObject.NULL)
                )
            }
            array.put(
                JSONObject()
                    .put("id", r.id)
                    .put("owner", r.ownerEmail)
                    .put("title", r.title)
                    .put("category", r.category.name)
                    .put("location", r.location)
                    .put("description", r.description)
                    .put("status", r.status.name)
                    .put("photo", r.photoPath ?: JSONObject.NULL)
                    .put("createdAt", r.createdAt)
                    .put("updates", updates)
            )
        }
        prefs.edit().putString(KEY_REPORTS, array.toString()).apply()
    }

    private inline fun <reified T : Enum<T>> parseEnum(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback

    companion object {
        const val DEMO_MAINTENANCE_EMAIL = "mantenimiento@universidad.edu"
        const val DEMO_MAINTENANCE_PASSWORD = "Mantenimiento123"

        private const val PREFS = "reportu_store_v2"
        private const val KEY_USERS = "users"
        private const val KEY_REPORTS = "reports"
        private const val KEY_SESSION = "session_email"
        private const val KEY_COUNTER = "folio_counter"
        private const val KEY_READ_PREFIX = "notif_read_"
        private const val MAX_TITLE = 45
        private const val FIRST_FOLIO = 42   // el primer reporte es #0042, como en el diseño

        @Volatile
        private var instance: Store? = null

        fun get(context: Context): Store =
            instance ?: synchronized(this) {
                instance ?: Store(context).also { instance = it }
            }
    }
}
