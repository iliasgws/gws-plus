package school.greenwood.plus.data.session

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException

/*
 * Chiffrement au repos des credentials persistés (issue #138) : le jeton
 * Boti, le jeton communautaire et la clé IA sont scellés avec une clé AES-256
 * GCM non exportable d'Android Keystore. Le format est versionné
 * (« enc:v1: ») pour permettre une migration future sans ambiguïté, et le
 * DataStore qui les porte est exclu des sauvegardes (issue #137).
 *
 * Aucun algorithme maison : uniquement la primitive standard du système
 * (AES/GCM/NoPadding, IV aléatoire de 12 octets généré par Keystore à
 * chaque chiffrement — jamais réutilisé).
 */

/** Longueur de l'IV GCM préfixé au chiffré. */
internal const val IV_OCTETS = 12

/**
 * Format persisté : « enc:v1: » + base64(IV(12) || chiffré+tag).
 * Pur JVM (java.util.Base64, minSdk 26) — testable sans Android.
 */
internal object FormatSecrets {
    const val PRÉFIXE = "enc:v1:"

    fun estChiffré(valeur: String): Boolean = valeur.startsWith(PRÉFIXE)

    fun emballer(iv: ByteArray, chiffré: ByteArray): String =
        PRÉFIXE + Base64.getEncoder().encodeToString(iv + chiffré)

    /** Sépare IV et chiffré ; null si le payload est illisible ou tronqué. */
    fun déballer(valeur: String): Pair<ByteArray, ByteArray>? {
        val brut = runCatching {
            Base64.getDecoder().decode(valeur.removePrefix(PRÉFIXE))
        }.getOrNull() ?: return null
        if (brut.size <= IV_OCTETS) return null
        return brut.copyOfRange(0, IV_OCTETS) to brut.copyOfRange(IV_OCTETS, brut.size)
    }
}

/**
 * Chiffreur symétrique : [chiffrer] renvoie IV||chiffré, [déchiffrer] reçoit
 * le même format. Le chiffré Keystore est le seul utilisé en production ;
 * les tests JVM substituent une implémentation factice.
 */
internal interface Chiffreur {
    fun chiffrer(clair: ByteArray): ByteArray
    fun déchiffrer(payload: ByteArray): ByteArray
}

/** Implémentation Keystore (instrumentée) — Android only. */
internal object ChiffreurKeystore : Chiffreur {
    // Double-checked : la création est sérialisée, les lectures courantes non.
    private val verrou = Any()

    fun clé(): SecretKey {
        val magasin = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (magasin.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        synchronized(verrou) {
            // Un autre thread (ou une autre instance) a pu la créer entre-temps.
            (magasin.getKey(ALIAS, null) as? SecretKey)?.let { return it }
            val générateur =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            générateur.init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    // L'IV est généré par Keystore à chaque opération : jamais réutilisé.
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            return générateur.generateKey()
        }
    }

    override fun chiffrer(clair: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, clé())
        return cipher.iv + cipher.doFinal(clair)
    }

    override fun déchiffrer(payload: ByteArray): ByteArray {
        require(payload.size > IV_OCTETS) { "payload trop court" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            clé(),
            GCMParameterSpec(128, payload.copyOfRange(0, IV_OCTETS)),
        )
        return cipher.doFinal(payload.copyOfRange(IV_OCTETS, payload.size))
    }

    private const val ALIAS = "gws_plus_session_v1"
}

/**
 * Scelle/ouvre les secrets du stock de session. [chiffreur] est injectable
 * pour les tests JVM ; la production utilise [ChiffreurKeystore].
 */
internal class SessionSecrets(
    private val chiffreur: Chiffreur = ChiffreurKeystore,
) {

    fun estChiffré(valeur: String): Boolean = FormatSecrets.estChiffré(valeur)

    fun seal(clair: String): String {
        if (clair.isEmpty()) return ""
        val payload = chiffreur.chiffrer(clair.toByteArray(Charsets.UTF_8))
        require(payload.size > IV_OCTETS) { "chiffreur sans IV" }
        return FormatSecrets.emballer(
            payload.copyOfRange(0, IV_OCTETS),
            payload.copyOfRange(IV_OCTETS, payload.size),
        )
    }

    /**
     * Ouvre un secret ; une valeur non chiffrée (release antérieure) est
     * renvoyée telle quelle pour migration. Une valeur chiffrée illisible
     * (clé Keystore invalidée, fichier corrompu) devient "" : la session
     * redemande une connexion plutôt que de s'effondrer — jamais de crash,
     * jamais de secret dans une exception.
     */
    fun open(valeur: String): String {
        if (valeur.isEmpty()) return ""
        if (!FormatSecrets.estChiffré(valeur)) return valeur
        val (iv, chiffré) = FormatSecrets.déballer(valeur) ?: return ""
        return try {
            String(chiffreur.déchiffrer(iv + chiffré), Charsets.UTF_8)
        } catch (annulation: CancellationException) {
            throw annulation
        } catch (_: Exception) {
            "" // Clé Keystore perdue/corrompue : reconnexion exigée, en douceur.
        }
    }
}

/**
 * Décision de migration d'une valeur persistée (pur JVM, testée sans
 * Android) : un secret non chiffré écrit par une release antérieure est
 * scellé ; le reste (vide, déjà chiffré) reste tel quel.
 */
internal fun valeurÀMigrer(valeur: String?, sceller: (String) -> String): String? =
    valeur
        ?.takeIf { it.isNotEmpty() && !FormatSecrets.estChiffré(it) }
        ?.let(sceller)
