package com.example.kreyolkeyboard

/**
 * Filtre de Bloom — la structure qui répond à « ce mot existe-t-il ? ».
 *
 * C'est la seule question que pose le correcteur orthographique, et
 * l'asymétrie des erreurs d'un filtre de Bloom tombe du bon côté :
 *
 * - il ne peut **jamais** rejeter une forme qu'on y a mise, donc il ne peut
 *   pas faire souligner un mot correct — le défaut qu'un correcteur existe
 *   pour éviter ;
 * - il accepte à tort environ 1 % des chaînes absentes, donc il laisse passer
 *   une faute de frappe de temps en temps, ce qui est sans conséquence.
 *
 * En échange, 150 000 formes tiennent dans ~170 Ko au lieu de plusieurs Mo de
 * chaînes et de nœuds de table de hachage — dans un processus de saisie
 * qu'Android compresse en swap dès qu'il passe en arrière-plan, et qui doit
 * tout recharger au retour.
 *
 * Le hachage est **écrit deux fois**, ici et dans `Dictionnaires/bloom.py`.
 * Une divergence ferait souligner toute une langue d'un coup sans rien casser
 * d'autre : chaque actif porteur d'un filtre est donc accompagné d'un test qui
 * rejoue le filtre livré sur les formes livrées.
 */
object BloomFilter {

    private const val FNV_OFFSET = -3750763034362895579L // 0xCBF29CE484222325
    private const val FNV_PRIME = 0x100000001B3L

    /**
     * FNV-1a 64 bits. [prefixe] ajoute l'octet 0x00 devant les données pour
     * obtenir le second hachage indépendant de la construction de
     * Kirsch-Mitzenmacher.
     */
    fun fnv1a(donnees: ByteArray, prefixe: Boolean): Long {
        var h = FNV_OFFSET
        if (prefixe) h = (h xor 0L) * FNV_PRIME
        for (octet in donnees) {
            h = (h xor (octet.toLong() and 0xFF)) * FNV_PRIME
        }
        return h
    }

    /**
     * Le mot est-il dans le filtre ?
     *
     * Le masque 63 bits, plutôt qu'un modulo non signé, existe parce que
     * `Long.remainderUnsigned` n'arrive qu'à l'API 24, sous le minSdk 21 du
     * projet — le Python fait la même opération. Et le second hachage est
     * forcé impair : sans cela, les k indices d'un mot partagent leur parité
     * et ne couvrent que la moitié du filtre (3,42 % de faux positifs mesurés
     * au lieu de 1,03 %, à taille égale).
     *
     * [mot] doit arriver dans la forme sous laquelle le filtre a été
     * construit : minuscules pour le français, minuscules sans diacritiques
     * pour le luxembourgeois.
     */
    fun contient(mot: String, bloom: ByteArray, bits: Long, hachages: Int): Boolean {
        if (bits <= 0 || hachages <= 0 || bloom.isEmpty()) return false
        val donnees = mot.toByteArray(Charsets.UTF_8)
        val h1 = fnv1a(donnees, false)
        val h2 = fnv1a(donnees, true) or 1L
        for (i in 0 until hachages) {
            val indice = ((h1 + i * h2) and Long.MAX_VALUE) % bits
            val octet = (indice ushr 3).toInt()
            if (bloom[octet].toInt() and (1 shl (indice and 7L).toInt()) == 0) {
                return false
            }
        }
        return true
    }

    /**
     * Base64 décodé à la main : `java.util.Base64` demande l'API 26 et
     * `android.util.Base64` n'existe pas sur la JVM des tests, alors que le
     * minSdk du projet est 21 et que les filtres doivent être vérifiables hors
     * appareil.
     */
    fun decoderBase64(texte: String): ByteArray {
        val table = IntArray(128) { -1 }
        val alphabet =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        for (i in alphabet.indices) table[alphabet[i].code] = i
        val utiles = texte.count { it.code < 128 && table[it.code] >= 0 }
        val sortie = ByteArray(utiles * 3 / 4)
        var tampon = 0
        var bits = 0
        var pos = 0
        for (c in texte) {
            val v = if (c.code < 128) table[c.code] else -1
            if (v < 0) continue
            tampon = (tampon shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                sortie[pos++] = ((tampon ushr bits) and 0xFF).toByte()
            }
        }
        return sortie
    }
}
