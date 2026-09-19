package school.greenwood.plus.util

import android.media.AudioAttributes
import android.media.MediaPlayer

/*
 * Lecteur des messages vocaux. L'URL signée du serveur est jouée en flux :
 * elle expire après 15–20 minutes, mais elle est toujours fraîche au moment
 * du GET `messages` qui l'a apportée (docs/BOTI-API.md). La forme du champ
 * `audio` n'a jamais été observée non-nulle en production — tout échec de
 * préparation reste silencieux (état Fini), le lecteur ne s'affiche que
 * quand une pièce audio existe.
 */

class LecteurAudio(
    private val url: String,
    private val onChanged: () -> Unit = {},
) {

    enum class État { Préparation, Prêt, Lecture, Pause, Fini }

    var état: État = État.Préparation
        private set
    var duréeMs: Int = 0
        private set

    private var player: MediaPlayer? = null

    init {
        val p = MediaPlayer()
        player = p
        p.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build(),
        )
        p.setOnPreparedListener {
            état = État.Prêt
            duréeMs = it.duration
            onChanged()
        }
        p.setOnCompletionListener {
            état = État.Fini
            onChanged()
        }
        p.setOnErrorListener { _, _, _ ->
            état = État.Fini
            onChanged()
            true
        }
        runCatching {
            p.setDataSource(url)
            p.prepareAsync()
        }.onFailure {
            état = État.Fini
            onChanged()
        }
    }

    val enLecture: Boolean get() = état == État.Lecture

    fun position(): Int = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)

    fun basculer() {
        val p = player ?: return
        when (état) {
            État.Lecture -> {
                runCatching { p.pause() }
                état = État.Pause
            }
            État.Pause, État.Fini -> {
                if (état == État.Fini) runCatching { p.seekTo(0) }
                runCatching { p.start() }
                état = État.Lecture
            }
            else -> return
        }
        onChanged()
    }

    fun libérer() {
        player?.let { p ->
            runCatching { p.stop() }
            p.release()
        }
        player = null
        état = État.Fini
    }
}