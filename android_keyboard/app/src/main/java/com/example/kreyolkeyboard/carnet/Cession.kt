package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Céder une carte de la main à la main, par deux codes QR, **définitivement**.
 *
 * Celui qui reçoit commence : il affiche une **demande**, qui porte un jeton
 * tiré au hasard sur son téléphone. Le donneur la scanne ; la carte quitte
 * aussitôt son carnet, et il affiche une **remise** liée à ce jeton. Le
 * receveur la scanne et la carte entre dans le sien. Rien ne passe par
 * Internet.
 *
 * L'ordre est tout l'enjeu. La version 1 (35.0.x) faisait l'inverse : la carte
 * entrait chez le receveur avant de quitter le donneur, qui devait encore
 * scanner une confirmation. Un donneur qui partait avant gardait sa carte, et
 * une même offre scannée par trois téléphones en donnait trois. Ici la carte
 * part d'abord, et la remise ne s'ouvre que sur le téléphone qui a fait la
 * demande. Le prix est inverse : une remise jamais scannée est une carte
 * perdue, pas doublée. C'est pourquoi le donneur la garde, hors de son carnet,
 * jusqu'à ce qu'il dise qu'elle est arrivée ([remisesEnAttente]). Voir
 * `CESSION-CARTES.md` à la racine du dépôt.
 *
 * La partie texte ([demande], [lireDemande], [remise], [lireRemise],
 * [estAncienneVersion]) n'a pas de `Context`, pour être testée sur la JVM.
 */
object Cession {

    /**
     * Propre à ce clavier : le code d'une carte luxembourgeoise ne doit pas
     * se lire ici comme une carte kréyòl, ni l'inverse. Le format est celui de
     * LuxKeyb (v23.0.0, repris de LuxKeyb 36.0.0), préfixe à part.
     */
    private const val PREFIXE = "KREYOLKEYB"
    private const val VERSION = "2"

    /** Une demande attend une semaine : assez pour une remise faite plus tard dans la journée. */
    const val VIE_DEMANDE_S = 7 * 24 * 3600L

    /** Une remise jamais confirmée reste trente jours sous « À remettre », puis s'oublie. */
    const val VIE_REMISE_S = 30 * 24 * 3600L

    data class Remise(val jeton: String, val forme: String, val nombre: Int?, val depuis: Long = 0L)

    private val alea = SecureRandom()

    fun nouveauJeton(): String = buildString {
        repeat(8) { append("%02x".format(alea.nextInt(256))) }
    }

    // ---------------------------------------------------------- les textes

    fun demande(jeton: String): String = listOf(PREFIXE, "D", VERSION, jeton).joinToString(":")

    /** Le jeton d'un code de demande, ou `null`. */
    fun lireDemande(texte: String): String? {
        val parts = texte.trim().split(":")
        if (parts.size != 4 || parts[0] != PREFIXE || parts[1] != "D" || parts[2] != VERSION) return null
        return parts[3].takeIf { estJeton(it) }
    }

    /** Le texte du code de remise. La forme vient en dernier : rien ne l'empêche de contenir « : ». */
    fun remise(r: Remise): String =
        listOf(PREFIXE, "C", VERSION, r.jeton, r.nombre?.toString() ?: "", r.forme).joinToString(":")

    /** Une remise lue sur un code, ou `null` si ce n'en est pas une ou si elle est mal formée. */
    fun lireRemise(texte: String): Remise? {
        val parts = texte.trim().split(":", limit = 6)
        if (parts.size != 6 || parts[0] != PREFIXE || parts[1] != "C" || parts[2] != VERSION) return null
        val jeton = parts[3].takeIf { estJeton(it) } ?: return null
        val nombre = if (parts[4].isEmpty()) null else parts[4].toIntOrNull() ?: return null
        val forme = parts[5].takeIf { it.isNotBlank() && it.length <= 64 } ?: return null
        return Remise(jeton, forme, nombre)
    }

    /**
     * Vrai pour un code de la version 1 : l'autre téléphone a une version qui
     * échange encore dans l'ancien ordre, et il faut le lui dire plutôt que
     * « ce n'est pas une carte ».
     */
    fun estAncienneVersion(texte: String): Boolean {
        val t = texte.trim()
        return t.startsWith("$PREFIXE:O:1:") || t.startsWith("$PREFIXE:R:1:")
    }

    private fun estJeton(s: String) = s.length == 16 && s.all { it in '0'..'9' || it in 'a'..'f' }

    // ---------------------------------------------------------- le receveur

    private const val PREFS = "carnet_cession"
    private const val CLE_DEMANDES = "demandes"
    private const val CLE_RECUS = "recus"
    private const val CLE_REMISES = "remises"
    /** Assez pour qu'un même code ne s'importe pas deux fois, sans grossir sans fin. */
    private const val MEMOIRE_RECUS = 200
    private const val MEMOIRE_DEMANDES = 20

    /**
     * Une nouvelle demande, notée comme en attente. Les jetons vivent dans
     * leur propre fichier de préférences, hors sauvegarde ; ils ne disent rien
     * d'autre qu'un hasard tiré sur ce téléphone.
     */
    fun nouvelleDemande(context: Context, maintenant: Long = secondes()): String {
        val jeton = nouveauJeton()
        val gardees = (demandesValides(lire(context, CLE_DEMANDES), maintenant) + (jeton to maintenant))
            .takeLast(MEMOIRE_DEMANDES)
        ecrire(context, CLE_DEMANDES, gardees.joinToString(",") { (j, t) -> "$j@$t" })
        return jeton
    }

    /** Vrai si [jeton] est une demande de ce téléphone qui attend encore sa remise. */
    fun demandeEnAttente(context: Context, jeton: String, maintenant: Long = secondes()): Boolean =
        demandesValides(lire(context, CLE_DEMANDES), maintenant).any { it.first == jeton }

    /**
     * Note la remise [jeton] comme reçue : sa demande est close et le même code
     * ne s'importera plus.
     */
    fun noterRecue(context: Context, jeton: String, maintenant: Long = secondes()) {
        val demandes = demandesValides(lire(context, CLE_DEMANDES), maintenant).filter { it.first != jeton }
        ecrire(context, CLE_DEMANDES, demandes.joinToString(",") { (j, t) -> "$j@$t" })
        val recus = lire(context, CLE_RECUS).split(",").filter { it.isNotEmpty() }
        ecrire(context, CLE_RECUS, (recus + jeton).takeLast(MEMOIRE_RECUS).joinToString(","))
    }

    fun dejaRecue(context: Context, jeton: String): Boolean = jeton in lire(context, CLE_RECUS).split(",")

    /** Les demandes stockées sous forme `jeton@seconde`, sans les périmées. */
    internal fun demandesValides(brut: String, maintenant: Long): List<Pair<String, Long>> =
        brut.split(",").mapNotNull { e ->
            val (j, t) = e.split("@").takeIf { it.size == 2 } ?: return@mapNotNull null
            val depuis = t.toLongOrNull() ?: return@mapNotNull null
            if (!estJeton(j) || maintenant - depuis > VIE_DEMANDE_S) null else j to depuis
        }

    // ---------------------------------------------------------- le donneur

    /**
     * Les cartes cédées dont le donneur n'a pas encore dit qu'elles étaient
     * arrivées, les plus anciennes d'abord. Elles ne sont plus dans son carnet ;
     * il garde seulement de quoi remontrer leur code.
     */
    fun remisesEnAttente(context: Context, maintenant: Long = secondes()): List<Remise> =
        remisesValides(lire(context, CLE_REMISES), maintenant)

    fun ajouterRemise(context: Context, r: Remise) {
        val toutes = remisesEnAttente(context, r.depuis).filter { it.jeton != r.jeton } + r
        ecrire(context, CLE_REMISES, encoderRemises(toutes))
    }

    fun oublierRemise(context: Context, jeton: String) {
        ecrire(context, CLE_REMISES, encoderRemises(remisesEnAttente(context).filter { it.jeton != jeton }))
    }

    internal fun encoderRemises(remises: List<Remise>): String = JSONArray().apply {
        remises.forEach { r ->
            put(JSONObject().put("j", r.jeton).put("m", r.forme).put("t", r.depuis).apply {
                r.nombre?.let { put("v", it) }
            })
        }
    }.toString()

    internal fun remisesValides(brut: String, maintenant: Long): List<Remise> = try {
        val t = JSONArray(brut.ifEmpty { "[]" })
        (0 until t.length()).mapNotNull { i ->
            val o = t.getJSONObject(i)
            val r = Remise(
                o.optString("j"), o.optString("m"),
                if (o.has("v")) o.optInt("v") else null, o.optLong("t")
            )
            r.takeIf { estJeton(it.jeton) && it.forme.isNotEmpty() && maintenant - it.depuis <= VIE_REMISE_S }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun secondes() = System.currentTimeMillis() / 1000

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun lire(context: Context, cle: String) = prefs(context).getString(cle, "").orEmpty()
    private fun ecrire(context: Context, cle: String, valeur: String) {
        // commit et non apply : la carte vient de quitter le carnet, sa remise
        // doit être sur le disque avant qu'on montre quoi que ce soit.
        prefs(context).edit().putString(cle, valeur).commit()
    }

    // ---------------------------------------------------------- le dessin

    /** Le code QR de [texte], en noir sur blanc, [cote] pixels de côté. */
    fun codeQR(texte: String, cote: Int): Bitmap {
        val matrice = QRCodeWriter().encode(
            texte, BarcodeFormat.QR_CODE, cote, cote,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
        )
        val pixels = IntArray(matrice.width * matrice.height) { i ->
            if (matrice.get(i % matrice.width, i / matrice.width)) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, matrice.width, matrice.height, Bitmap.Config.ARGB_8888)
    }
}
