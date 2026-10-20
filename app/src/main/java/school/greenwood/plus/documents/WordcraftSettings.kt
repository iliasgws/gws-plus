package school.greenwood.plus.documents

import android.content.Context

/** Nonsecret, device-local opt-in. Synchronous read keeps attachment Intent creation nonblocking. */
internal object WordcraftSettings {
    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences("experimental_document_viewer", Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean("wordcraft_beta", false)
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("wordcraft_beta", enabled).apply()
    }
}
