package com.hardtekpt.crux.data.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Pictures attached to walls: a photo of the wall or a gym map with it marked. Each is
 * copied into app storage, scaled down to at most [MAX_SIDE] px and saved as JPEG, so it
 * survives the original being deleted and stays small enough to back up. The database keeps
 * only the file name.
 */
@Singleton
class AreaImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    fun file(name: String): File = File(dir, name)

    /** Copies a picked or captured image in; returns its file name. */
    suspend fun importFrom(uri: Uri): String = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        // ImageDecoder applies the camera's EXIF rotation for us.
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        save(bitmap)
    }

    /** Restores an image from a backup; returns its new file name, or null if it isn't one. */
    suspend fun importBytes(bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
        val name = newName()
        file(name).writeBytes(bytes)
        name
    }

    suspend fun readBytes(name: String): ByteArray? = withContext(Dispatchers.IO) {
        file(name).takeIf { it.exists() }?.readBytes()
    }

    suspend fun delete(name: String?) {
        if (name == null) return
        withContext(Dispatchers.IO) { file(name).delete() }
    }

    /** Where the camera writes a new photo before it is imported. */
    fun newCaptureUri(): Uri {
        val capture = File(File(context.cacheDir, CAPTURE_DIR).apply { mkdirs() }, "capture.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.images", capture)
    }

    private fun save(bitmap: Bitmap): String {
        val name = newName()
        file(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        bitmap.recycle()
        return name
    }

    private fun newName() = "${UUID.randomUUID()}.jpg"

    companion object {
        const val DIR = "area_images"
        const val CAPTURE_DIR = "camera"
        const val MAX_SIDE = 2048
        const val QUALITY = 85
    }
}
