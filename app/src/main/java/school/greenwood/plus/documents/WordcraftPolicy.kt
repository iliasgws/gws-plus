package school.greenwood.plus.documents

/** Formats imported by the pinned WordCraft build, not a universal Office viewer. */
internal object WordcraftPolicy {
    const val maxBytes = 10 * 1024 * 1024
    val extensions = setOf("docx", "doc", "odt", "rtf", "txt", "md", "html", "htm", "tex")
    private val mimes = mapOf(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx",
        "application/msword" to "doc",
        "application/vnd.oasis.opendocument.text" to "odt",
        "application/rtf" to "rtf", "text/rtf" to "rtf",
        "text/plain" to "txt", "text/markdown" to "md", "text/html" to "html",
        "application/x-tex" to "tex",
    )

    fun extension(name: String?, mime: String?): String? {
        val ext = name?.substringAfterLast('.', "")?.lowercase()
        // A known unsupported extension must not be disguised by a generic MIME.
        if (!ext.isNullOrEmpty()) return ext.takeIf { it in extensions }
        return mimes[mime?.substringBefore(';')?.lowercase()?.trim()]
    }
}
