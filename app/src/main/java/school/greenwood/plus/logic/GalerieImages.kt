package school.greenwood.plus.logic

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * La galerie d'images (issue #109) : les règles du visualiseur plein écran,
 * mises à part pour être testées sans Android ni Compose.
 *
 * « La liste » : la couverture en tête quand elle existe, l'ordre du serveur
 * ensuite, sans blanc ni doublon (on garde la première occurrence).
 * « Le texte » : la position « 2 / 5 » et la description accessible
 * « Image 2 sur 5 — Titre », au singulier quand il n'y a qu'une image.
 * « La géométrie » : l'échelle bornée entre 1 et 8, l'image posée dans le
 * cadre sans rognure, et le décalage borné aux bords propres — une image
 * agrandie ne se laisse jamais tirer hors d'elle-même, et le retour à
 * l'échelle 1 remet le décalage à zéro.
 */
object GalerieImages {

    /**
     * Les URLs à défiler : la couverture en tête (si elle n'est pas blanche),
     * puis la galerie dans l'ordre du serveur. Espaces retirés, blancs
     * ignorés, doublons écartés — on garde la première occurrence.
     */
    fun liste(couverture: String?, galerie: List<String>): List<String> {
        val vues = LinkedHashSet<String>()
        val première = couverture?.trim()
        if (première != null && première.isNotEmpty()) {
            vues.add(première)
        }
        galerie.forEach { brute ->
            val url = brute.trim()
            if (url.isNotEmpty()) {
                vues.add(url)
            }
        }
        return vues.toList()
    }

    /** L'index de départ : la vignette touchée, sinon la première image. */
    fun indexDépart(liste: List<String>, touchée: String?): Int {
        if (touchée == null || touchée.isBlank()) return 0
        return liste.indexOf(touchée).coerceAtLeast(0)
    }

    /** « 2 / 5 » — rien à afficher quand il n'y a qu'une image à compter. */
    fun libelléPosition(index: Int, total: Int): String? {
        if (total <= 1) return null
        return "${index.coerceIn(0, total - 1) + 1} / $total"
    }

    /**
     * La description lue par TalkBack : « Image » seul, « Image 2 sur 5 »
     * au pluriel, et le titre derrière un tiret quand il y en a un.
     */
    fun descriptionImage(index: Int, total: Int, titre: String? = null): String {
        val base = if (total <= 1) {
            "Image"
        } else {
            "Image ${index.coerceIn(0, total - 1) + 1} sur $total"
        }
        val fin = titre?.trim()?.takeIf { it.isNotEmpty() } ?: return base
        return "$base — $fin"
    }

    /** L'échelle du pincement, bornée à [1f, 8f] — une valeur inutilisable repart à 1. */
    fun zoomBorné(zoom: Float): Float {
        if (!zoom.isFinite()) return 1f
        return zoom.coerceIn(1f, 8f)
    }

    /**
     * L'image telle qu'elle tient dans le cadre (letterbox) : mise à l'échelle
     * entière, jamais rognée. Si les dimensions ne veulent rien dire, on
     * considère que l'image remplit le cadre.
     */
    fun tailleAffichée(image: IntSize, cadre: IntSize): IntSize {
        val largeur = image.width
        val hauteur = image.height
        val largeurCadre = cadre.width
        val hauteurCadre = cadre.height
        if (largeur <= 0 || hauteur <= 0 || largeurCadre <= 0 || hauteurCadre <= 0) {
            return cadre
        }
        val échelle = min(
            largeurCadre.toFloat() / largeur,
            hauteurCadre.toFloat() / hauteur,
        )
        return IntSize(
            (largeur * échelle).roundToInt(),
            (hauteur * échelle).roundToInt(),
        )
    }

    /**
     * Le décalage admissible : au plus la moitié de ce qui dépasse du cadre,
     * jamais au-delà — ainsi l'image agrandie ne sort jamais de ses bords.
     * À l'échelle 1 (image qui tient déjà dans le cadre), le décalage est
     * ramené à zéro.
     */
    fun décalageBorné(décalage: Offset, zoom: Float, affiché: IntSize, cadre: IntSize): Offset {
        if (cadre.width <= 0 || cadre.height <= 0) return Offset.Zero
        val maxX = max(0f, (affiché.width * zoom - cadre.width) / 2f)
        val maxY = max(0f, (affiché.height * zoom - cadre.height) / 2f)
        val x = décalage.x.coerceIn(-maxX, maxX)
        val y = décalage.y.coerceIn(-maxY, maxY)
        // Un zéro négatif ne vaudrait pas « Offset.Zero » au bit près :
        // on le ramène à zéro positif.
        return Offset(if (x == 0f) 0f else x, if (y == 0f) 0f else y)
    }
}
