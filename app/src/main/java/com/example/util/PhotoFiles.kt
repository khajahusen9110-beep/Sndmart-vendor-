package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Saving and sharing a customer's photo (they may be a design the shop has to print or copy). */
object PhotoFiles {
    private val client by lazy { OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build() }

    private suspend fun download(url: String): ByteArray = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            r.body?.bytes() ?: error("empty photo")
        }
    }

    /** Puts the photo in the phone's gallery (Pictures / SndmartVendor). Android 10 and newer. */
    suspend fun saveToGallery(context: Context, url: String, baseName: String): Result<Unit> = runCatching {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "Please use Share on this phone" }
        val bytes = download(url)
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$baseName.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SndmartVendor")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("could not create the file")
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("could not write the file")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    }

    /** Opens the share sheet with the photo itself (WhatsApp, Bluetooth, printer apps ...). */
    suspend fun share(context: Context, url: String, baseName: String): Result<Unit> = runCatching {
        val bytes = download(url)
        val uri: Uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared").also { it.mkdirs() }
            dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 3600_000L) it.delete() }
            val f = File(dir, "$baseName.jpg")
            f.writeBytes(bytes)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share photo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
