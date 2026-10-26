package school.greenwood.plus.documents

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

internal object WordcraftIntents {
    fun preview(context: Context, uri: Uri, mime: String, knownName: String? = null): Intent? {
        if (!WordcraftSettings.enabled(context) || uri.scheme != "content") return null
        val name = knownName ?: runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        val extension = WordcraftPolicy.extension(name, mime) ?: return null
        return Intent(context, WordcraftActivity::class.java)
            .setDataAndType(uri, mime)
            .putExtra("extension", extension)
            .putExtra("title", name?.take(120) ?: "Document")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun external(uri: Uri, mime: String): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
