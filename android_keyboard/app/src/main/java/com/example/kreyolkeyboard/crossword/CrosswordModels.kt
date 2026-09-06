package com.example.kreyolkeyboard.crossword

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Modèles de données pour le jeu « Mokwaré » : une grille de mots croisés
 * numérotée, ses définitions en français, et les lettres que le joueur écrit.
 *
 * L'actif `creole_crossword.json` est produit par
 * `Dictionnaires/generate_crossword.py`, qui construit et valide les grilles
 * hors du téléphone. Rien n'est placé ici : ce fichier lit, numérote, et tient
 * le compte de ce qui a été écrit.
 *
 * C'est le seul jeu où le joueur **produit** l'orthographe. Les quatre autres
 * la lui montrent, grille de Mots Mêlés, lettres de Mots Mélangés,
 * propositions de Mo an Karénaj et de Fraz a twou. Ici la case est vide,
 * accents compris : c'est le but.
 *
 * Repris du Lëtzebuergesch Clavier, qui partage cette base de code, moins deux
 * choses que le kréyòl ne connaît pas : la majuscule des substantifs (le
 * dictionnaire livré est entièrement en minuscules) et la liste de mots
 * écartés (non portée, voir CLAUDE.md). La grille en capitales n'efface donc
 * ici qu'un accent, que le pavé permet de réécrire.
 */

enum class CrosswordDifficulty(val level: Int, val label: String) {
    FACILE(1, "Facile"),
    NORMALE(2, "Normal"),
    DIFFICILE(3, "Difficile");

    companion object {
        fun fromLevel(level: Int): CrosswordDifficulty =
            values().firstOrNull { it.level == level } ?: NORMALE
    }
}

/**
 * Un mot posé dans la grille.
 *
 * [answer] est ce qu'il faut écrire, en capitales et **accents compris** :
 * « KRÉYÒL », jamais « KREYOL ». [canonical] est la forme telle que le
 * dictionnaire l'écrit (« kréyòl ») : elle est rappelée quand le mot est
 * trouvé, avec sa définition, comme mémo de vocabulaire.
 */
data class CrosswordWord(
    val answer: String,
    val canonical: String,
    val clue: String,
    val row: Int,
    val col: Int,
    val across: Boolean
) {
    val length: Int get() = answer.length

    fun rowAt(index: Int): Int = if (across) row else row + index
    fun colAt(index: Int): Int = if (across) col + index else col

    /** Rang de la case dans le mot, ou -1 si le mot ne passe pas par là. */
    fun indexOf(r: Int, c: Int): Int {
        if (across) {
            if (r != row || c < col || c >= col + length) return -1
            return c - col
        }
        if (c != col || r < row || r >= row + length) return -1
        return r - row
    }
}

/**
 * Une grille : ses dimensions, ses mots, et la numérotation qui les relie aux
 * définitions.
 *
 * La numérotation est **calculée ici et non livrée** : c'est la règle des mots
 * croisés depuis toujours, on numérote de gauche à droite et de haut en bas
 * les cases qui commencent un mot, et deux mots qui partent de la même case
 * portent le même numéro. La déduire coûte une boucle ; la transporter aurait
 * ajouté un champ à vérifier dans l'actif.
 */
class CrosswordGrid(
    val width: Int,
    val height: Int,
    val difficulty: CrosswordDifficulty,
    val words: List<CrosswordWord>
) {
    private fun cle(r: Int, c: Int) = r * width + c

    /** Numéro affiché dans le coin de chaque case qui commence un mot. */
    val numerosParCase: Map<Int, Int> = run {
        val departs = words
            .map { cle(it.row, it.col) }
            .distinct()
            .sorted()
        departs.withIndex().associate { (rang, case) -> case to rang + 1 }
    }

    /** Numéro de chaque mot, dans l'ordre de [words]. */
    val numeros: List<Int> = words.map { numerosParCase[cle(it.row, it.col)] ?: 0 }

    /** Lettre attendue dans une case, ou null si la case est noire. */
    private val solution: Map<Int, Char> = HashMap<Int, Char>().apply {
        for (mot in words) {
            for (i in 0 until mot.length) {
                put(cle(mot.rowAt(i), mot.colAt(i)), mot.answer[i])
            }
        }
    }

    fun solutionAt(r: Int, c: Int): Char? = solution[cle(r, c)]

    fun estCaseJouable(r: Int, c: Int): Boolean = solution.containsKey(cle(r, c))

    /** Les mots qui passent par une case, horizontal d'abord. */
    fun motsSur(r: Int, c: Int): List<Int> =
        words.indices.filter { words[it].indexOf(r, c) >= 0 }
            .sortedByDescending { words[it].across }

    /** Les mots dans l'ordre où les listes de définitions les présentent. */
    fun definitions(across: Boolean): List<Int> =
        words.indices.filter { words[it].across == across }
            .sortedBy { numeros[it] }
}

/**
 * L'état d'une partie : ce que le joueur a écrit, et où il en est.
 *
 * Séparé du fragment pour être vérifiable sans Android, c'est ici que se
 * décide ce qui compte comme juste, et une case qui se valide par erreur
 * apprendrait une faute.
 */
class CrosswordSession(val grid: CrosswordGrid) {

    private val lettres = HashMap<Int, Char>()
    private fun cle(r: Int, c: Int) = r * grid.width + c

    /** Mot sélectionné, et case courante à l'intérieur de ce mot. */
    var motSelectionne: Int = grid.words.indices.firstOrNull() ?: -1
        private set
    var caseSelectionnee: Int = 0
        private set

    fun lettreAt(r: Int, c: Int): Char? = lettres[cle(r, c)]

    /**
     * Sélectionne la case touchée. Toucher à nouveau une case déjà
     * sélectionnée fait passer à l'autre mot qui la traverse : c'est le geste
     * habituel des mots croisés sur écran, et il évite un bouton « sens ».
     */
    fun selectionner(r: Int, c: Int) {
        val candidats = grid.motsSur(r, c)
        if (candidats.isEmpty()) return
        val dejaLa = grid.words.getOrNull(motSelectionne)?.indexOf(r, c) ?: -1
        val choisi = if (dejaLa >= 0 && candidats.size > 1) {
            candidats.first { it != motSelectionne }
        } else {
            candidats.firstOrNull { it == motSelectionne } ?: candidats.first()
        }
        motSelectionne = choisi
        caseSelectionnee = grid.words[choisi].indexOf(r, c)
    }

    /** Sélectionne un mot entier, sur sa première case vide. */
    fun selectionnerMot(index: Int) {
        if (index !in grid.words.indices) return
        motSelectionne = index
        val mot = grid.words[index]
        caseSelectionnee = (0 until mot.length)
            .firstOrNull { lettreAt(mot.rowAt(it), mot.colAt(it)) == null } ?: 0
    }

    /**
     * Écrit une lettre dans la case courante et avance.
     *
     * L'avance saute les cases déjà remplies **du même mot** : quand un mot
     * vertical en a déjà donné deux lettres, s'arrêter dessus obligerait à
     * réécrire ce qui est acquis.
     */
    fun ecrire(lettre: Char) {
        val mot = grid.words.getOrNull(motSelectionne) ?: return
        if (caseSelectionnee !in 0 until mot.length) return
        lettres[cle(mot.rowAt(caseSelectionnee), mot.colAt(caseSelectionnee))] = lettre
        var suivante = caseSelectionnee + 1
        while (suivante < mot.length &&
            lettreAt(mot.rowAt(suivante), mot.colAt(suivante)) != null
        ) suivante++
        caseSelectionnee = if (suivante < mot.length) suivante else mot.length - 1
    }

    /**
     * Efface. La case courante si elle porte une lettre, sinon la précédente :
     * l'effacement recule, comme partout ailleurs.
     */
    fun effacer() {
        val mot = grid.words.getOrNull(motSelectionne) ?: return
        val courante = cle(mot.rowAt(caseSelectionnee), mot.colAt(caseSelectionnee))
        if (lettres.remove(courante) == null && caseSelectionnee > 0) {
            caseSelectionnee--
            lettres.remove(cle(mot.rowAt(caseSelectionnee), mot.colAt(caseSelectionnee)))
        }
    }

    fun motRempli(index: Int): Boolean {
        val mot = grid.words[index]
        return (0 until mot.length).all { lettreAt(mot.rowAt(it), mot.colAt(it)) != null }
    }

    fun motJuste(index: Int): Boolean {
        val mot = grid.words[index]
        return (0 until mot.length).all {
            lettreAt(mot.rowAt(it), mot.colAt(it)) == mot.answer[it]
        }
    }

    /** Vrai si la case porte une lettre fausse, une fois son mot rempli. */
    fun caseFausse(r: Int, c: Int): Boolean {
        val lettre = lettreAt(r, c) ?: return false
        return lettre != grid.solutionAt(r, c)
    }

    fun motsJustes(): Int = grid.words.indices.count { motJuste(it) }

    fun termine(): Boolean = motsJustes() == grid.words.size

    /** Remplit tout : sert au bouton « Montrer la solution ». */
    fun reveler() {
        for (mot in grid.words) {
            for (i in 0 until mot.length) {
                lettres[cle(mot.rowAt(i), mot.colAt(i))] = mot.answer[i]
            }
        }
    }
}

object CrosswordData {

    private const val ASSET = "creole_crossword.json"
    private const val TAG = "CrosswordData"

    /**
     * Les lettres du pavé de saisie, dans l'ordre où il les range.
     *
     * **C'est la disposition du clavier de l'application, pas l'alphabet.** Le
     * joueur est un locuteur qui tape sur cet AZERTY tous les jours : ses
     * doigts savent déjà où sont les lettres, alors qu'un pavé alphabétique
     * l'oblige à une recherche visuelle à chaque touche. Les rangées 1 et 2
     * reprennent donc `KeyboardLayoutManager.createAlphabeticLayout()` à
     * l'identique, soit vingt lettres sur vingt-six et l'essentiel de la
     * frappe, et la rangée 3 garde l'ordre `w x c v b n`.
     *
     * Deux différences avec le clavier, et elles sont voulues :
     *
     * - **Les trois accents sont des touches visibles.** Sur le clavier, `é` et
     *   `è` sont en rangée 4 autour de la barre d'espace, et `ò` n'est pas une
     *   touche du tout : il est en appui long sur `o` (v10.11.3, qui l'a retiré
     *   de la rangée la plus frappée). Ici la case est vide et produire
     *   l'accent *est* le jeu : cacher `ò` sous un appui long apprendrait
     *   exactement la faute que Mokwaré existe pour corriger. Rien ne se perd
     *   côté mémoire musculaire, puisque `ò` n'a pas de position à retenir et
     *   que `é`/`è` sont ancrés à une barre d'espace que le jeu n'a pas.
     * - **Pas de ⇧, pas d'apostrophe, pas de barre d'espace.** La grille est en
     *   capitales et une case porte une lettre. Les trois rangées font donc dix
     *   touches chacune, accents et effacement compris, et sont de largeur
     *   égale : c'est le résultat que visait la v10.11.3 pour le clavier.
     *
     * `generate_crossword.py` ne retient que les mots qui s'écrivent avec ces
     * vingt-neuf lettres.
     */
    val LETTRES: List<String> = (
        "AZERTYUIOP QSDFGHJKLM WXCVBNÉÈÒ"
        ).split(" ")

    private var cachedGrids: List<CrosswordGrid>? = null
    private var cachedAttribution: String? = null

    /**
     * Charge et met en cache les grilles livrées.
     *
     * Aucun repli codé en dur, comme pour Fraz a twou : une grille écrite à la
     * main serait jouable, donc un actif manquant passerait inaperçu.
     */
    fun loadGrids(context: Context): List<CrosswordGrid> {
        cachedGrids?.let { return it }

        val grilles = try {
            val contenu = BufferedReader(
                InputStreamReader(context.assets.open(ASSET))
            ).use { it.readText() }

            val racine = JSONObject(contenu)
            val credits = racine.optJSONArray("attribution")
            cachedAttribution = if (credits == null) "" else
                (0 until credits.length()).joinToString("\n") { credits.getString(it) }

            val tableau = racine.getJSONArray("grilles")
            val liste = ArrayList<CrosswordGrid>(tableau.length())
            for (i in 0 until tableau.length()) {
                val objet = tableau.getJSONObject(i)
                val motsJson = objet.getJSONArray("mots")
                val mots = ArrayList<CrosswordWord>(motsJson.length())
                for (j in 0 until motsJson.length()) {
                    val motJson = motsJson.getJSONObject(j)
                    mots.add(
                        CrosswordWord(
                            answer = motJson.getString("m"),
                            canonical = motJson.getString("f"),
                            clue = motJson.getString("g"),
                            row = motJson.getInt("r"),
                            col = motJson.getInt("c"),
                            across = motJson.getString("d") == "H"
                        )
                    )
                }
                if (mots.isEmpty()) continue

                liste.add(
                    CrosswordGrid(
                        width = objet.getInt("w"),
                        height = objet.getInt("h"),
                        difficulty = CrosswordDifficulty.fromLevel(objet.optInt("l", 2)),
                        words = mots
                    )
                )
            }
            liste
        } catch (e: Exception) {
            Log.e(TAG, "Actif $ASSET illisible: ${e.message}", e)
            emptyList()
        }

        cachedGrids = grilles
        Log.d(TAG, "${grilles.size} grilles chargées")
        return grilles
    }

    /** Une grille au hasard, de la difficulté demandée, ou null s'il n'y en a pas. */
    fun newGrid(context: Context, difficulty: CrosswordDifficulty): CrosswordGrid? {
        val disponibles = loadGrids(context).filter { it.difficulty == difficulty }
        return if (disponibles.isEmpty()) null else disponibles.random()
    }

    fun countByDifficulty(context: Context): Map<CrosswordDifficulty, Int> =
        loadGrids(context).groupingBy { it.difficulty }.eachCount()

    /**
     * Crédits, tels qu'ils voyagent dans l'actif.
     *
     * Les définitions viennent de Kreyolopedia et du Wiktionnaire français
     * (CC BY-SA 4.0, dont la citation est une obligation de licence), les mots
     * et leurs fréquences du corpus PawolKreyol (Apache-2.0). Le jeu affiche ce
     * texte ; ne pas le retirer de l'écran.
     */
    fun attribution(context: Context): String {
        loadGrids(context)
        return cachedAttribution ?: ""
    }
}
