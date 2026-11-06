package com.example.reportu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/** Guarda fotos de reportes en el almacenamiento interno y las carga con caché. */
object Images {

    private const val MAX_SIDE = 1280
    private const val JPEG_QUALITY = 85

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val cache = object : LruCache<String, Bitmap>(
        ((Runtime.getRuntime().maxMemory() / 1024L) / 16L).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
    }

    /**
     * Reduce, corrige la rotación y guarda la imagen en segundo plano.
     * [onDone] se ejecuta en el hilo principal con la ruta del archivo (o null si falló).
     */
    fun saveAsync(context: Context, uri: Uri, onDone: (String?) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            val path = try {
                save(app, uri)
            } catch (e: Exception) {
                null
            } catch (e: OutOfMemoryError) {
                null
            }
            mainHandler.post { onDone(path) }
        }
    }

    private fun save(context: Context, uri: Uri): String? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE)
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }
        val scale = min(1f, MAX_SIDE.toFloat() / max(decoded.width, decoded.height))
        if (scale < 1f) matrix.postScale(scale, scale)

        val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }

        if (result !== decoded) decoded.recycle()
        result.recycle()
        return file.absolutePath
    }

    /** Carga una imagen ya guardada, reducida a ~[reqSize] px por lado. */
    fun load(path: String, reqSize: Int): Bitmap? {
        val key = "$path@$reqSize"
        cache.get(key)?.let { return it }

        val file = File(path)
        if (!file.exists()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, reqSize)
        }
        val bitmap = BitmapFactory.decodeFile(path, options) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    private fun sampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= target) sample *= 2
        return sample
    }
}
