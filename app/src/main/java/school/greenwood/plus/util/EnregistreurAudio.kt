package school.greenwood.plus.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/*
 * Enregistreur des messages vocaux — MediaRecorder vers un m4a dans le cache.
 * Le bundle officiel nomme l'envoi `audio_<epoch>.<type>` ; le nom est posé au
 * moment de l'envoi (MessagesRepository), ici on garde un fichier neutre.
 * Tout échec (micro occupé, permission retirée en vol) reste silencieux et
 * rend null : le composeur ne doit jamais bloquer un message texte.
 */

class EnregistreurAudio(private val context: Context) {

    enum class État { Inactif, Enregistrement }

    var état: État = État.Inactif
        private set
    private var recorder: MediaRecorder? = null
    private var cible: File? = null

    /** Démarre l'enregistrement. Retourne false si le micro est refusé. */
    fun démarrer(): Boolean {
        if (état == État.Enregistrement) return true
        val fichier = File(context.cacheDir, "voix_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(96000)
            r.setOutputFile(fichier.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            cible = fichier
            état = État.Enregistrement
            true
        } catch (err: Exception) {
            runCatching { r.release() }
            fichier.delete()
            false
        }
    }

    /** Arrête et conserve le fichier. Null si rien d'exploitable. */
    fun arrêter(): File? {
        val r = recorder ?: return null
        recorder = null
        état = État.Inactif
        val fichier = cible
        cible = null
        return try {
            r.stop()
            r.release()
            fichier?.takeIf { it.exists() && it.length() > 0 }
        } catch (err: Exception) {
            runCatching { r.release() }
            fichier?.delete()
            null
        }
    }

    /** Annule : arrête et jette le fichier. */
    fun annuler() {
        recorder?.let { r ->
            runCatching { r.stop() }
            runCatching { r.release() }
        }
        recorder = null
        état = État.Inactif
        cible?.delete()
        cible = null
    }
}
