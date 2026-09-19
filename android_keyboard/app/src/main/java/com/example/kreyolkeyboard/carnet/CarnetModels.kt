package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.Color
import android.util.Log
import com.example.kreyolkeyboard.TranslationDictionary
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Les cinq jeux, du point de vue du carnet.
 *
 * Une carte porte **le ou les jeux où elle a été gagnée**, et c'est la seule
 * chose que le carnet sait de sa provenance. L'identifiant court est ce qui
 * part dans les préférences : le nom d'énumération peut changer, le stockage
 * non.
 *
 * Les couleurs et les emoji reprennent, à l'unité près, ceux des cartes du
 * menu `Jé` : un joueur doit reconnaître d'où vient une carte avant d'avoir lu
 * son étiquette.
 */
enum class JeuCarte(
    val id: String,
    val nom: String,
    val emoji: String,
    val couleur: Int
) {
    MOTS_MELES("mm", "Mots Mêlés", "🎲", 0xFF9C27B0.toInt()),
    MOTS_MELANGES("mx", "Mots Mélangés", "🔤", 0xFF1976D2.toInt()),
    MO_AN_KARENAJ("mk", "Mo an Karénaj", "🟩", 0xFF4CAF50.toInt()),
    FRAZ_A_TWOU("ft", "Fraz a twou", "📝", 0xFFFF8C00.toInt()),
    MOKWARE("mw", "Mokwaré", "🧩", 0xFFC2185B.toInt());

    companion object {
        private val PAR_ID = values().associateBy { it.id }
        fun parId(id: String): JeuCarte? = PAR_ID[id]
    }
}

/**
 * Sanblé, le carnet : les mots gagnés dans les jeux, et de quoi en faire des
 * cartes à collectionner.
 *
 * Le nom est attesté — `sanblé`, « collectionner, rassembler », dans la table
 * de gloses livrée. Le carnet est repris du Lëtzebuergesch Clavier, qui
 * partage cette base de code, où il répare une récompense qui disparaît avec
 * la grille : un mot dont on vient d'apprendre le sens est lu une fois, puis
 * il s'en va avec la partie. Les cinq jeux enseignaient tout autant et ne
 * gardaient rien.
 *
 * Quatre choix qui portent le reste :
 *
 * - **Il ne fait que grandir, et il survit à l'application.** Une collection
 *   qui repart de zéro à chaque partie n'est pas une collection. Le stockage
 *   est un `SharedPreferences`, domaine que `backup_rules.xml` et
 *   `data_extraction_rules.xml` incluent tous les deux : le carnet est donc
 *   sauvegardé dans le nuage et transféré d'un téléphone à l'autre. Ce sont
 *   des mots de dictionnaire, tirés d'un ensemble fermé et livré — rien de
 *   personnel n'y entre, contrairement au fichier d'usage de la gamification,
 *   qui vit dans `filesDir` et reste délibérément hors sauvegarde. C'est la
 *   même frontière que les emojis récents : des identifiants d'un jeu livré,
 *   jamais du texte libre.
 * - **La rareté est la fréquence, pas une invention.** `creole_dict.json` est
 *   trié par fréquence décroissante : le rang d'une forme *est* sa rareté,
 *   sans qu'il faille fabriquer la moindre statistique. Poser des « points de
 *   vie » sur une vraie langue apprendrait quelque chose de faux ; un rang de
 *   fréquence est vérifiable et se trouve être exactement la mécanique qu'on
 *   cherchait.
 * - **Les rangs sont lus à l'ouverture du carnet, jamais pendant la partie.**
 *   Et par un balayage du texte brut plutôt qu'un `JSONArray` : l'actif fait
 *   5 296 paires, dont l'arbre complet ferait plus de dix mille objets pour les
 *   quelques dizaines de rangs dont le carnet a besoin. Le dictionnaire créole
 *   est vingt fois plus petit que le luxembourgeois, donc le piège est vingt
 *   fois moins cher ici — mais la lecture reste la même, et elle est déjà
 *   écrite.
 * - **Un mot gagné dans deux jeux reste une carte.** Ce sont les mêmes mots :
 *   en faire deux cartes doublerait la collection sans rien lui apprendre. Le
 *   deuxième jeu s'ajoute à la carte, et le compteur de rencontres monte.
 *
 * Ce que le kréyòl change par rapport au portage : il n'y a pas de jeu de
 * nombres (Zuelwuert), donc pas de carte dont la rareté se lise sur autre
 * chose qu'un rang, et le champ `v` du stockage luxembourgeois n'existe pas
 * ici. Le carnet n'a pas non plus d'ancien domaine à reprendre : il naît
 * commun aux cinq jeux.
 */
object Carnet {

    /**
     * La couleur du carnet.
     *
     * Il en fallait une qui n'appartienne à aucun des cinq jeux, pour que la
     * collection ne se lise pas comme celle de l'un d'eux.
     */
    const val COULEUR = 0xFF5E35B1.toInt()

    private const val PREFS = "kreyol_carnet_prefs"
    private const val CLE_CARTES = "cartes"
    private const val ASSET_DICO = "creole_dict.json"
    private const val TAG = "Carnet"

    /** Ordre de capture. La liste ne perd jamais d'entrée. */
    private var cartes: MutableList<CarteMot> = ArrayList()
    private var parForme: MutableMap<String, Int> = HashMap()
    private var charge = false

    /** Rangs de fréquence des seules formes du carnet, lus à la demande. */
    private var rangs: Map<String, Int> = emptyMap()
    private var rangsPour: Int = -1

    @Synchronized
    fun charger(context: Context) {
        if (charge) return
        charge = true
        try {
            val brut = prefs(context).getString(CLE_CARTES, null) ?: return
            val tableau = JSONArray(brut)
            for (i in 0 until tableau.length()) {
                val o = tableau.getJSONObject(i)
                val forme = o.optString("m")
                if (forme.isEmpty()) continue
                parForme[forme] = cartes.size
                cartes.add(
                    CarteMot(
                        forme = forme,
                        premiereFois = o.optLong("d"),
                        rencontres = o.optInt("n", 1),
                        numero = cartes.size + 1,
                        jeux = lireJeux(o.optString("g")),
                        boite = o.optInt("b", 0),
                        jourEcheance = o.optInt("j", Sonje.JAMAIS_PLANIFIEE)
                    )
                )
            }
            Log.d(TAG, "${cartes.size} cartes chargées")
        } catch (e: Exception) {
            // Un carnet illisible ne doit pas empêcher de jouer : on repart
            // d'un carnet vide, que la première capture réécrira.
            Log.e(TAG, "Carnet illisible: ${e.message}", e)
            cartes = ArrayList()
            parForme = HashMap()
        }
    }

    /**
     * Les jeux d'une carte stockée.
     *
     * Une provenance vide ou inconnue retombe sur Mokwaré : c'est le jeu qui a
     * amené le carnet, et une carte doit toujours pouvoir dire d'où elle
     * vient.
     */
    private fun lireJeux(brut: String): Set<JeuCarte> {
        if (brut.isEmpty()) return setOf(JeuCarte.MOKWARE)
        val jeux = brut.split(',').mapNotNull { JeuCarte.parId(it) }
        return if (jeux.isEmpty()) setOf(JeuCarte.MOKWARE) else LinkedHashSet(jeux)
    }

    /**
     * Enregistre une rencontre. Retourne vrai si la carte est neuve.
     *
     * Une forme déjà là n'est pas dupliquée : son compteur monte, le jeu
     * s'ajoute à sa provenance, et c'est ce qui permet de dire « nouveau » ou
     * « revu » au bilan de fin de partie.
     *
     * **Un mot sans glose ne devient pas une carte**, et le refus est ici
     * plutôt que dans chacun des cinq jeux, pour la raison qui a déjà fait de
     * `TranslationDictionary.estProposable` un point de passage unique.
     *
     * La règle n'est pas théorique. Quatre jeux tirent déjà dans les seuls
     * mots glosés depuis la 12.0.0, mais Fraz a twou ne tire pas : ses
     * réponses sortent du corpus, et **156 de ses 240 réponses, soit 65 %,
     * n'ont aucune glose**. Sans ce filtre, deux cartes sur trois gagnées à ce
     * jeu porteraient « sens non répertorié » à la place de ce qu'elles sont
     * censées apprendre, et la révision les retournerait pour ne rien montrer.
     * C'est le même raisonnement que le filtre de tirage des jeux : une carte
     * qui ne peut pas dire ce que son mot veut dire n'apprend que l'orthographe.
     *
     * Le prix est connu et assumé : Fraz a twou donne trois fois moins de
     * cartes que ce qu'il fait trouver. Élargir la table de gloses est ce qui
     * le lèvera, pas un assouplissement ici.
     */
    @Synchronized
    fun ajouter(context: Context, forme: String, jeu: JeuCarte): Boolean {
        charger(context)
        if (forme.isBlank()) return false
        if (!TranslationDictionary.estProposable(context, forme)) return false
        val existant = parForme[forme]
        if (existant != null) {
            val c = cartes[existant]
            cartes[existant] = c.copy(
                rencontres = c.rencontres + 1,
                jeux = if (jeu in c.jeux) c.jeux
                else LinkedHashSet(c.jeux).apply { add(jeu) }
            )
            enregistrer(context)
            return false
        }
        parForme[forme] = cartes.size
        cartes.add(
            CarteMot(
                forme = forme,
                premiereFois = System.currentTimeMillis(),
                rencontres = 1,
                numero = cartes.size + 1,
                jeux = setOf(jeu)
            )
        )
        enregistrer(context)
        return true
    }

    @Synchronized
    fun cartes(context: Context): List<CarteMot> {
        charger(context)
        return ArrayList(cartes)
    }

    @Synchronized
    fun taille(context: Context): Int {
        charger(context)
        return cartes.size
    }

    @Synchronized
    fun contient(context: Context, forme: String): Boolean {
        charger(context)
        return forme in parForme
    }

    /** Les jeux dont au moins une carte est au carnet, dans l'ordre du menu. */
    @Synchronized
    fun jeuxRepresentes(context: Context): List<JeuCarte> {
        charger(context)
        val vus = cartes.flatMapTo(HashSet()) { it.jeux }
        return JeuCarte.values().filter { it in vus }
    }

    /**
     * Donne une échéance aux cartes qu'aucune session n'a encore planifiées,
     * en les étalant sur les jours suivants.
     *
     * Appelée à l'ouverture de la révision, donc aussi bien pour la reprise
     * d'un carnet existant que pour les cartes gagnées depuis la dernière
     * session : dans les deux cas le problème est le même, un paquet de cartes
     * qui deviendraient toutes dues le même jour. Voir
     * [Sonje.echeanceDEtalement].
     *
     * Retourne le nombre de cartes planifiées, zéro si rien n'a bougé (le cas
     * courant, qui n'écrit alors pas les préférences).
     */
    @Synchronized
    fun planifier(context: Context, aujourdHui: Int = Sonje.aujourdHui()): Int {
        charger(context)
        var position = 0
        cartes.forEachIndexed { i, c ->
            if (c.jourEcheance == Sonje.JAMAIS_PLANIFIEE) {
                cartes[i] = c.copy(
                    jourEcheance = Sonje.echeanceDEtalement(aujourdHui, position)
                )
                position++
            }
        }
        if (position > 0) enregistrer(context)
        return position
    }

    /** La file de la prochaine session : au plus [Sonje.PLAFOND_SESSION] cartes. */
    @Synchronized
    fun file(context: Context, aujourdHui: Int = Sonje.aujourdHui()): List<CarteMot> {
        charger(context)
        return Sonje.file(cartes, aujourdHui)
    }

    /**
     * Combien de cartes la prochaine session proposerait.
     *
     * C'est la taille de la file, donc un nombre **plafonné** : la bannière
     * annonce ce que la session contient, jamais l'arriéré. Se lit dans les
     * préférences seules, sans toucher aux actifs, ce que la bannière exige.
     */
    @Synchronized
    fun aRevoir(context: Context, aujourdHui: Int = Sonje.aujourdHui()): Int =
        file(context, aujourdHui).size

    /**
     * Enregistre le résultat d'une carte révisée.
     *
     * [reussi] à faux ramène la carte en boîte 0 ; la session, elle, se charge
     * de la repasser avant la fin, sans quoi on quitterait sur un échec jamais
     * rejoué.
     */
    @Synchronized
    fun noter(
        context: Context,
        forme: String,
        verdict: Verdict,
        aujourdHui: Int = Sonje.aujourdHui()
    ) {
        charger(context)
        val index = parForme[forme] ?: return
        val c = cartes[index]
        val boite = Sonje.apresVerdict(c.boite, verdict)
        cartes[index] = c.copy(
            boite = boite,
            jourEcheance = Sonje.echeance(aujourdHui, boite)
        )
        enregistrer(context)
    }

    /**
     * Rareté d'une carte, lue sur le rang de fréquence de sa forme.
     *
     * Les rangs sont chargés à la première demande et remis en cache tant que
     * le carnet ne grandit pas — ouvrir le carnet ne relit donc l'actif qu'une
     * fois.
     */
    @Synchronized
    fun rarete(context: Context, carte: CarteMot): Rarete {
        assurerRangs(context)
        return Rarete.pourRang(rangs[carte.forme])
    }

    @Synchronized
    fun rang(context: Context, forme: String): Int? {
        assurerRangs(context)
        return rangs[forme]
    }

    /** Remet le carnet à zéro. N'existe que pour les tests et le débogage. */
    @Synchronized
    fun vider(context: Context) {
        cartes = ArrayList()
        parForme = HashMap()
        rangs = emptyMap()
        rangsPour = -1
        charge = true
        prefs(context).edit().remove(CLE_CARTES).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun enregistrer(context: Context) {
        val tableau = JSONArray()
        cartes.forEach {
            val o = JSONObject()
                .put("m", it.forme)
                .put("d", it.premiereFois)
                .put("n", it.rencontres)
                .put("g", it.jeux.joinToString(",") { j -> j.id })
            // La révision n'écrit que ces deux entiers, et c'est ce qui tient
            // la promesse de [PreuveDeFrappe] : une échéance repoussée parce
            // que le mot a été écrit au clavier est indiscernable d'une
            // échéance repoussée par une carte réussie. Aucun compteur de
            // frappe n'entre dans un domaine sauvegardé.
            if (it.boite != 0) o.put("b", it.boite)
            if (it.jourEcheance != Sonje.JAMAIS_PLANIFIEE) o.put("j", it.jourEcheance)
            tableau.put(o)
        }
        prefs(context).edit().putString(CLE_CARTES, tableau.toString()).apply()
    }

    private fun assurerRangs(context: Context) {
        charger(context)
        if (rangsPour == cartes.size) return
        rangsPour = cartes.size
        rangs = if (cartes.isEmpty()) emptyMap()
        else lireRangs(context, cartes.mapTo(HashSet()) { it.forme })
    }

    /**
     * Le rang de chaque forme demandée dans `creole_dict.json`.
     *
     * L'actif est un tableau de paires `[["ka", 1800], ["an", 1388], …]` trié
     * par fréquence décroissante : le rang est la position, et il suffit de
     * compter les ouvertures de paire en relevant la chaîne de tête. On balaye
     * donc le texte une fois, sans construire le moindre objet JSON — voir la
     * note de classe. Les formes du dictionnaire ne contiennent ni guillemet
     * ni contre-oblique, la lecture est donc sûre telle quelle.
     */
    private fun lireRangs(context: Context, formes: Set<String>): Map<String, Int> {
        if (formes.isEmpty()) return emptyMap()
        return try {
            val texte = BufferedReader(
                InputStreamReader(context.assets.open(ASSET_DICO))
            ).use { it.readText() }

            val trouves = HashMap<String, Int>(formes.size)
            var i = 0
            var rang = 0
            val n = texte.length
            while (i < n && trouves.size < formes.size) {
                // Début d'une paire : le premier guillemet qui suit un '['.
                val crochet = texte.indexOf('[', i)
                if (crochet < 0) break
                val ouvre = texte.indexOf('"', crochet)
                if (ouvre < 0) break
                val ferme = texte.indexOf('"', ouvre + 1)
                if (ferme < 0) break
                val forme = texte.substring(ouvre + 1, ferme)
                if (forme in formes) trouves[forme] = rang
                rang++
                i = ferme + 1
            }
            trouves
        } catch (e: Exception) {
            // Sans rangs, tout est « commun » : le carnet reste consultable,
            // il perd seulement sa hiérarchie.
            Log.e(TAG, "Rangs illisibles: ${e.message}", e)
            emptyMap()
        }
    }
}

/**
 * Une carte du carnet.
 *
 * [numero] est l'ordre de capture, celui qu'affiche le pied de carte : la
 * collection se raconte dans l'ordre où on l'a faite, pas dans celui du
 * dictionnaire. [jeux] garde les jeux où la carte a été gagnée, dans l'ordre
 * où ils l'ont donnée : le premier est celui qui l'a fait entrer au carnet.
 */
data class CarteMot(
    val forme: String,
    val premiereFois: Long,
    val rencontres: Int,
    val numero: Int,
    val jeux: Set<JeuCarte> = setOf(JeuCarte.MOKWARE),
    val boite: Int = 0,
    val jourEcheance: Int = Sonje.JAMAIS_PLANIFIEE
) {
    /** Le jeu qui a fait entrer la carte au carnet. */
    val origine: JeuCarte get() = jeux.firstOrNull() ?: JeuCarte.MOKWARE

    /** La carte a franchi toutes les boîtes : elle ne revient plus. */
    val acquise: Boolean get() = boite >= Sonje.BOITE_ACQUISE
}

/**
 * Les quatre paliers de rareté, lus sur le rang de fréquence.
 *
 * Les seuils sont **mesurés sur le vivier créole, jamais transposés**. Le
 * dictionnaire luxembourgeois compte 38 442 formes et pose ses paliers à
 * 3 000 / 6 500 / 9 000 ; le dictionnaire kréyòl en compte 5 296, et y
 * recopier ces nombres rendrait toutes les cartes communes.
 *
 * Le vivier du carnet est ce que [Carnet.ajouter] accepte, c'est-à-dire les
 * **622 formes glosées** du dictionnaire (voir sa note : une carte muette
 * n'est pas une carte). Sur ce vivier, 800 / 2 000 / 3 500 donnent
 * 42 / 28 / 20 / 10 % — la courbe d'un jeu de cartes, où le commun domine et
 * où la dernière catégorie se mérite. Le carnet luxembourgeois obtient
 * 37 / 34 / 20 / 8 % avec les siens.
 *
 * Deux conséquences mesurées, à connaître avant de toucher aux seuils :
 *
 * - **Fraz a twou ne donne jamais de carte rare, et c'est structurel.** Ses
 *   réponses sont choisies dans une bande de fréquence (5 ≤ freq ≤ 150), donc
 *   aucune ne dépasse le rang 1 800 : de ses 138 réponses encartables, 97 %
 *   sont communes et le reste peu commun. Le jeu qui fait le plus travailler
 *   la langue est celui qui donne les cartes les plus modestes.
 * - **Mokwaré est le pourvoyeur de rares** (49 / 27 / 15 / 9 % sur ses 391
 *   formes), parce que ses grilles tirent dans tout ce qui est glosé et
 *   écrivable au pavé.
 *
 * Un rang inconnu (forme absente du dictionnaire de fréquences) est traité
 * comme le plus rare : c'est la lecture juste, une forme que le corpus ne
 * connaît pas est plus rare que tout ce qu'il connaît.
 */
enum class Rarete(val libelle: String, val symbole: String, val couleur: Int) {
    COMMUN("Commun", "●", 0xFF78909C.toInt()),
    PEU_COMMUN("Peu commun", "◆", 0xFF43A047.toInt()),
    RARE("Rare", "★", 0xFF1E88E5.toInt()),
    TRES_RARE("Très rare", "✦", 0xFF8E24AA.toInt());

    /**
     * Le symbole, répété autant de fois que le palier est haut.
     *
     * La couleur seule ne suffit pas à séparer les paliers : le vert de
     * *Peu commun* et le bleu-gris de *Commun* se confondent en deutéranopie,
     * et une vignette de 160 dp ne laisse pas la place à un libellé. Compter
     * des symboles, en revanche, se fait sans couleur — c'est la convention
     * de tous les jeux de cartes, et elle ne coûte que trois caractères.
     */
    val insigne: String get() = symbole.repeat(ordinal + 1)

    /**
     * Le palier a-t-il droit aux marques réservées aux cartes rares ?
     *
     * Un seul endroit décide, parce que ces marques — le coin, le double
     * filet, le halo, l'éclat — doivent toutes apparaître au même palier :
     * une carte qui gagne le coin mais pas le halo se lit comme un bug.
     */
    val distinguee: Boolean get() = this == RARE || this == TRES_RARE

    companion object {
        const val SEUIL_COMMUN = 800
        const val SEUIL_PEU_COMMUN = 2000
        const val SEUIL_RARE = 3500

        /**
         * L'intensité de la gemme d'un mot, de 0 (fréquent) à 1 (rare).
         *
         * Le palier est discret, quatre valeurs, alors que le rang est
         * continu : le plus haut palier va du 3 500ᵉ mot aux hors-corpus, et
         * seul le petit numéro du bas de carte distingue le 3 500ᵉ du 5 000ᵉ.
         * La gemme comble ce trou sans rien inventer : la teinte reste celle
         * du champ du mot, et seuls la saturation et l'éclat suivent le rang.
         *
         * La courbe est **monotone à travers les paliers** : un mot à 3 499
         * n'est jamais plus vif qu'un mot à 3 500, sinon la gemme
         * contredirait le métal. Elle est affine par morceaux, avec un nœud à
         * chaque seuil, donc un palier occupe toujours la même plage de
         * vivacité. Le dernier nœud est la taille du dictionnaire livré :
         * au-delà, et hors corpus, la gemme est à son maximum.
         */
        fun intensitePourRang(rang: Int?): Float {
            if (rang == null) return 1f
            for (i in 1 until NOEUDS_RANG.size) {
                if (rang < NOEUDS_RANG[i]) {
                    val t = (rang - NOEUDS_RANG[i - 1]).toFloat() /
                            (NOEUDS_RANG[i] - NOEUDS_RANG[i - 1])
                    return NOEUDS_INTENSITE[i - 1] +
                            t * (NOEUDS_INTENSITE[i] - NOEUDS_INTENSITE[i - 1])
                }
            }
            return 1f
        }

        private val NOEUDS_RANG =
            intArrayOf(0, SEUIL_COMMUN, SEUIL_PEU_COMMUN, SEUIL_RARE, 5_300)
        private val NOEUDS_INTENSITE = floatArrayOf(0f, 0.30f, 0.55f, 0.75f, 1f)

        fun pourRang(rang: Int?): Rarete = when {
            rang == null -> TRES_RARE
            rang < SEUIL_COMMUN -> COMMUN
            rang < SEUIL_PEU_COMMUN -> PEU_COMMUN
            rang < SEUIL_RARE -> RARE
            else -> TRES_RARE
        }
    }
}

/**
 * Une couleur ramenée vers le blanc.
 *
 * Les dégradés du carnet — le dos d'une carte, le cadre d'une très rare, le
 * halo qui la précède — se fabriquent tous à partir d'une seule couleur, celle
 * du jeu ou celle du palier. Les deux fonctions ci-dessous sont ce qui en tire
 * une famille : la même teinte, une fois levée, une fois posée.
 */
internal fun eclaircir(couleur: Int, part: Float): Int = Color.rgb(
    (Color.red(couleur) + (255 - Color.red(couleur)) * part).toInt(),
    (Color.green(couleur) + (255 - Color.green(couleur)) * part).toInt(),
    (Color.blue(couleur) + (255 - Color.blue(couleur)) * part).toInt()
)

/** Une couleur ramenée vers le noir. Voir [eclaircir]. */
internal fun assombrir(couleur: Int, part: Float): Int = Color.rgb(
    (Color.red(couleur) * (1 - part)).toInt(),
    (Color.green(couleur) * (1 - part)).toInt(),
    (Color.blue(couleur) * (1 - part)).toInt()
)
