package com.example.kreyolkeyboard.chassecroise

import android.content.Context
import android.util.Log
import com.example.kreyolkeyboard.crossword.CrosswordDifficulty
import com.example.kreyolkeyboard.crossword.CrosswordGrid
import com.example.kreyolkeyboard.crossword.CrosswordWord
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Modèles de données pour le jeu « Mo an plas » : une grille vide, la liste des
 * mots à y caser, et aucune définition.
 *
 * L'actif `creole_chassecroise.json` est produit par
 * `Dictionnaires/generate_chassecroise.py`, qui construit les grilles hors du
 * téléphone **et vérifie que chacune n'a qu'une solution**. Rien n'est placé
 * ici : ce fichier lit, et tient le compte de ce que le joueur a posé.
 *
 * La grille, le mot et la difficulté sont ceux de Mokwaré — mêmes classes,
 * même schéma d'actif — parce que c'est la même géométrie et qu'en dupliquer
 * une deuxième version ferait deux fois le travail à chaque correction. Seule
 * la partie diffère, et elle diffère complètement : là-bas on écrit des
 * lettres, ici on pose des mots entiers. C'est aussi pourquoi ce jeu n'a pas
 * de pavé de saisie, le seul de Mokwaré qui ne se retrouve pas ici.
 *
 * Ce que le jeu apporte et qu'aucun autre n'apporte : il est **jouable sans
 * connaître un mot de kréyòl**. Les cinq autres supposent une compréhension
 * préalable, ne serait-ce que pour lire la définition. Ici la déduction est
 * géométrique, et le sens arrive après — voir [verrouille].
 */

/**
 * L'état d'une partie : quel mot est posé dans quel emplacement.
 *
 * Séparé du fragment pour être vérifiable sans Android, comme
 * `CrosswordSession` : c'est ici que se décide ce qui compte comme juste, et
 * un emplacement qui se valide par erreur apprendrait une faute.
 *
 * Un « emplacement » est un `CrosswordWord` de la grille pris pour sa seule
 * géométrie — position, sens, longueur. Le mot qu'il porte dans l'actif est la
 * solution, que le joueur ne voit pas avant de l'avoir trouvée.
 */
class ChasseCroiseSession(val grid: CrosswordGrid, melangeur: (List<Int>) -> List<Int>) {

    /** Emplacement → rang du mot posé dedans, dans [grid].words. */
    private val places = HashMap<Int, Int>()

    /**
     * Emplacements gagnés. **Cet ensemble ne fait que grandir.**
     *
     * Un mot gagné est un mot dont les croisements ont prouvé la place : le
     * jeu a versé sa glose et l'a inscrit dans la liste des sens acquis. Deux
     * conséquences, et ce sont elles qui justifient de mémoriser plutôt que de
     * recalculer :
     *
     * - il ne se retire plus (voir [retirer]) ;
     * - il reste gagné même si un mot voisin, lui, s'en va. Sans mémoire,
     *   [croisementsCouverts] redeviendrait faux et le sens déjà lu
     *   disparaîtrait de la liste — le joueur verrait sa récompense reprise
     *   pour un geste qui ne le concernait pas.
     */
    private val gagnes = HashSet<Int>()

    /** Les mots proposés au joueur, dans un ordre qui ne dit rien. */
    val liste: List<Int> = melangeur(grid.words.indices.toList())

    /** Mot choisi dans la liste, ou -1. */
    var motChoisi: Int = -1
        private set

    /** Emplacements où un mot est posé. */
    val occupes: Set<Int> get() = places.keys

    fun motDe(emplacement: Int): Int? = places[emplacement]

    fun estPose(mot: Int): Boolean = places.containsValue(mot)

    fun choisir(mot: Int) {
        motChoisi = if (mot == motChoisi || estPose(mot)) -1 else mot
    }

    fun deselectionner() {
        motChoisi = -1
    }

    /**
     * Le mot choisi peut-il aller là ?
     *
     * Trois conditions : l'emplacement est libre, les longueurs coïncident, et
     * les lettres s'accordent avec ce qui est déjà posé aux croisements.
     *
     * La troisième est ce qui remplace un bouton « vérifier ». Un
     * chassé-croisé se joue au crayon : on ne peut pas écrire deux lettres
     * différentes dans une case, donc un mot incompatible ne se pose pas — le
     * refus n'est pas une correction, c'est la géométrie. Ce qui reste faux
     * après cela est un mot compatible mais mal placé, et celui-là ne se
     * signale qu'une fois tous ses croisements posés (voir [fautif]) : le
     * dénoncer plus tôt reviendrait à dicter la solution.
     */
    fun peutPoser(emplacement: Int, mot: Int): Boolean {
        if (emplacement in places) return false
        val creux = grid.words.getOrNull(emplacement) ?: return false
        val candidat = grid.words.getOrNull(mot) ?: return false
        if (candidat.answer.length != creux.length) return false
        for (i in 0 until creux.length) {
            val posee = lettreAt(creux.rowAt(i), creux.colAt(i)) ?: continue
            if (posee != candidat.answer[i]) return false
        }
        return true
    }

    /** Pose le mot choisi. Retourne faux si le placement était refusé. */
    fun poser(emplacement: Int): Boolean {
        val mot = motChoisi
        if (mot < 0 || !peutPoser(emplacement, mot)) return false
        places[emplacement] = mot
        motChoisi = -1
        encaisser()
        return true
    }

    /**
     * Retire le mot d'un emplacement et le rend à la liste.
     *
     * **Un mot gagné ne se retire pas.** Ses croisements l'ont confronté, le
     * jeu a versé sa glose et l'a compté : le rendre à la liste rouvrirait un
     * emplacement dont le joueur connaît déjà la réponse, et obligerait la
     * liste des sens acquis à reprendre ce qu'elle a donné. Reste retirable
     * tout ce qui n'est pas prouvé — un mot posé qu'aucun croisement n'a
     * encore confronté, et un mot [fautif].
     */
    fun retirer(emplacement: Int): Boolean {
        if (emplacement in gagnes) return false
        return places.remove(emplacement) != null
    }

    /**
     * Enregistre les emplacements que le dernier coup vient de prouver.
     *
     * Appelé après chaque pose, et jamais après un retrait : [gagnes] ne
     * décroît pas.
     */
    private fun encaisser() {
        for (emplacement in grid.words.indices) {
            if (emplacement !in gagnes &&
                juste(emplacement) && croisementsCouverts(emplacement)
            ) {
                gagnes.add(emplacement)
            }
        }
    }

    /** Les emplacements libres où le mot choisi pourrait aller. */
    fun emplacementsPossibles(mot: Int): List<Int> =
        grid.words.indices.filter { peutPoser(it, mot) }

    /** La lettre visible dans une case, venue du mot qui la couvre. */
    fun lettreAt(r: Int, c: Int): Char? {
        for ((emplacement, mot) in places) {
            val rang = grid.words[emplacement].indexOf(r, c)
            if (rang >= 0) return grid.words[mot].answer[rang]
        }
        return null
    }

    /** Vrai si l'emplacement porte le mot que la solution y met. */
    fun juste(emplacement: Int): Boolean {
        val mot = places[emplacement] ?: return false
        return grid.words[mot].answer == grid.words[emplacement].answer
    }

    /**
     * Tous les croisements de cet emplacement sont-ils couverts par un autre
     * mot posé ?
     *
     * C'est la condition qui rend un placement irrévocable au sens du jeu :
     * tant qu'une case du mot reste seule, rien ne l'a encore confronté.
     */
    fun croisementsCouverts(emplacement: Int): Boolean {
        if (emplacement !in places) return false
        val creux = grid.words[emplacement]
        for (i in 0 until creux.length) {
            val r = creux.rowAt(i)
            val c = creux.colAt(i)
            val voisins = grid.motsSur(r, c).filter { it != emplacement }
            if (voisins.isEmpty()) continue
            if (voisins.none { it in places }) return false
        }
        return true
    }

    /**
     * Le mot est-il gagné, c'est-à-dire posé, juste, et confronté à tous ses
     * croisements ?
     *
     * C'est le moment où le jeu montre la glose française. La récompense se
     * mérite par déduction : la révéler dès le dépôt ferait résoudre la grille
     * par sondage — poser, regarder si la glose s'allume, retirer. Même
     * discipline que Kräizwuert, qui ne signale une lettre fausse qu'une fois
     * son mot entièrement écrit.
     *
     * La réponse est acquise : voir [gagnes]. Un mot verrouillé le reste
     * jusqu'à la fin de la partie, et ne se retire plus.
     */
    fun verrouille(emplacement: Int): Boolean = emplacement in gagnes

    /** Posé, confronté, et pourtant faux : le seul cas qu'on signale en rouge. */
    fun fautif(emplacement: Int): Boolean =
        emplacement in places && croisementsCouverts(emplacement) && !juste(emplacement)

    fun motsJustes(): Int = grid.words.indices.count { juste(it) }

    fun termine(): Boolean = motsJustes() == grid.words.size

    /** Pose tout : sert au bouton « Montrer la solution ». */
    fun reveler() {
        places.clear()
        grid.words.indices.forEach { places[it] = it }
        motChoisi = -1
        encaisser()
    }
}

object ChasseCroiseData {

    private const val ASSET = "creole_chassecroise.json"
    private const val TAG = "ChasseCroiseData"

    private var cachedGrids: List<CrosswordGrid>? = null
    private var cachedAttribution: String? = null

    /**
     * Charge et met en cache les grilles livrées.
     *
     * Aucun repli codé en dur, comme pour Mokwaré : une grille écrite à la main
     * serait jouable, donc un actif manquant passerait inaperçu.
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
                            // Ici le champ ne porte pas une définition mais la
                            // récompense : le sens n'est montré qu'une fois le
                            // mot verrouillé.
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
     * Les gloses viennent de Kreyolopedia et du Wiktionnaire français, toutes
     * deux sous CC BY-SA 4.0, dont la citation est une obligation de licence.
     * Le jeu affiche ce texte ; ne pas le retirer de l'écran.
     */
    fun attribution(context: Context): String {
        loadGrids(context)
        return cachedAttribution ?: ""
    }
}
