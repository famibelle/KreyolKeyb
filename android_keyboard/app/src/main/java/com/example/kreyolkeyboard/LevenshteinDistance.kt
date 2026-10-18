package com.example.kreyolkeyboard

import android.util.Log

/**
 * Utility class for calculating Levenshtein distance and finding spell corrections
 * 
 * The Levenshtein distance measures the minimum number of single-character edits
 * (insertions, deletions, or substitutions) required to change one word into another.
 * Two adjacent letters swapped also count as one edit (optimal string alignment):
 * « tojuou » is one keystroke slip away from « toujou », not two.
 * 
 * This is used for spell correction when users make typos or mix up letters.
 * 
 * Example:
 * - "bonjo" → "bonjou" (distance = 1, missing 'u')
 * - "kreyol" → "kréyòl" (distance = 2, missing accents)
 * - "mesli" → "mèsi" (distance = 1, extra 'l')
 */
object LevenshteinDistance {
    
    private const val TAG = "LevenshteinDistance"
    
    /**
     * Calculates the Levenshtein distance between two strings
     * using dynamic programming for optimal performance.
     * 
     * @param s1 First string
     * @param s2 Second string
     * @return The minimum number of edits needed to transform s1 into s2
     */
    fun calculate(s1: String, s2: String): Int =
        calculateBounded(s1, s2, Int.MAX_VALUE)

    /**
     * Distance de Levenshtein, abandonnée dès qu'elle dépasse [maxDistance].
     *
     * Trois choses la séparent d'une implémentation naïve, et elles comptent
     * parce que cette fonction est appelée pour chaque mot du dictionnaire à
     * chaque frappe qui déclenche le repli de correction :
     *
     * - **deux lignes au lieu d'une matrice.** L'ancienne version allouait
     *   `Array(len1 + 1) { IntArray(len2 + 1) }`, soit une douzaine de tableaux
     *   par mot comparé et des centaines de milliers d'allocations par frappe ;
     * - **la casse repliée une fois par chaîne** et non à chaque cellule.
     *   `lowercaseChar()` était appelé len1 × len2 fois par comparaison ;
     * - **l'abandon anticipé** : si toute une ligne dépasse déjà [maxDistance],
     *   aucune suite ne peut redescendre, et la réponse ne sera de toute façon
     *   pas retenue.
     *
     * Renvoie une valeur strictement supérieure à [maxDistance] quand la
     * distance réelle l'est, sans garantir laquelle : l'appelant ne compare
     * qu'au seuil.
     *
     * **Deux lettres voisines inversées coûtent une modification, pas deux**
     * (variante « optimal string alignment » de Damerau). C'est la faute de
     * qui tape vite, et Levenshtein seul la comptait double : la bonne forme
     * passait derrière toute forme à une lettre près. Sur le banc de
     * correction, une inversion ne donnait le bon mot en tête que 19,8 % du
     * temps, contre 79,7 % désormais. Il faut pour cela la ligne d'avant la
     * précédente, d'où une troisième ligne. L'abandon anticipé reste juste :
     * une inversion part de la ligne `i - 2`, dont chaque cellule majore déjà
     * d'une unité une cellule de la ligne `i - 1`.
     */
    fun calculateBounded(s1: String, s2: String, maxDistance: Int): Int {
        val len1 = s1.length
        val len2 = s2.length

        if (len1 == 0) return len2
        if (len2 == 0) return len1
        // La différence de longueur est un minorant de la distance : inutile
        // de dérouler la programmation dynamique pour s'en apercevoir.
        if (kotlin.math.abs(len1 - len2) > maxDistance) return maxDistance + 1

        val a = CharArray(len1) { s1[it].lowercaseChar() }
        val b = CharArray(len2) { s2[it].lowercaseChar() }

        var avantPrecedente = IntArray(len2 + 1)
        var precedente = IntArray(len2 + 1) { it }
        var courante = IntArray(len2 + 1)

        for (i in 1..len1) {
            courante[0] = i
            var minimumLigne = courante[0]
            for (j in 1..len2) {
                val cout = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(
                    precedente[j] + 1,          // suppression
                    courante[j - 1] + 1,        // insertion
                    precedente[j - 1] + cout    // substitution
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    v = minOf(v, avantPrecedente[j - 2] + 1)   // inversion
                }
                courante[j] = v
                if (v < minimumLigne) minimumLigne = v
            }
            if (minimumLigne > maxDistance) return maxDistance + 1
            val echange = avantPrecedente
            avantPrecedente = precedente
            precedente = courante
            courante = echange
        }

        return precedente[len2]
    }
    
    /**
     * Distance bornée pour le balayage du dictionnaire : même résultat que
     * [calculateBounded] (exact jusqu'à [maxDistance], `maxDistance + 1`
     * au-delà), sans rien allouer par mot.
     *
     * Le repli compare la saisie à des dizaines de milliers de formes par
     * frappe. [calculateBounded] allouait quatre tableaux à chacune : sur un
     * téléphone modeste, ce sont ces centaines de milliers d'allocations qui
     * coûtaient, plus que le calcul. Ici :
     *
     * - la saisie est repliée en minuscules **une fois** par recherche
     *   ([entree]) et les deux lignes de travail sont fournies par l'appelant ;
     * - seule la **bande diagonale** de largeur `2 × maxDistance + 1` est
     *   calculée (Ukkonen) : hors d'elle, toute cellule dépasse déjà le seuil.
     *   Pour un seuil de 2, cinq cellules par ligne au lieu de toute la ligne.
     *
     * Les inversions de lettres voisines y coûtent un, comme dans
     * [calculateBounded] : d'où la troisième ligne, [avantPrecedente]. La bande
     * de la ligne `i - 2` couvre exactement les cellules `j - 2` lues.
     */
    internal fun distanceBornee(
        entree: CharArray,
        mot: String,
        maxDistance: Int,
        precedente: IntArray,
        courante: IntArray,
        avantPrecedente: IntArray
    ): Int {
        val n = entree.size
        val m = mot.length
        val horsSeuil = maxDistance + 1
        if (kotlin.math.abs(n - m) > maxDistance) return horsSeuil
        if (m == 0) return n
        if (n == 0) return m

        var avant = avantPrecedente
        var prec = precedente
        var cour = courante
        for (j in 0..n) prec[j] = if (j <= maxDistance) j else horsSeuil

        var cAvant = ' '
        for (i in 1..m) {
            val c = mot[i - 1].lowercaseChar()
            val debut = maxOf(1, i - maxDistance)
            val fin = minOf(n, i + maxDistance)
            var minimumLigne = horsSeuil
            if (debut == 1) {
                cour[0] = if (i <= maxDistance) i else horsSeuil
                minimumLigne = cour[0]
            } else {
                cour[debut - 1] = horsSeuil
            }
            for (j in debut..fin) {
                var v = prec[j - 1] + (if (entree[j - 1] == c) 0 else 1)
                val suppression = prec[j] + 1
                if (suppression < v) v = suppression
                val insertion = cour[j - 1] + 1
                if (insertion < v) v = insertion
                if (i > 1 && j > 1 && entree[j - 1] == cAvant && entree[j - 2] == c) {
                    val inversion = avant[j - 2] + 1
                    if (inversion < v) v = inversion
                }
                if (v > horsSeuil) v = horsSeuil
                cour[j] = v
                if (v < minimumLigne) minimumLigne = v
            }
            if (fin < n) cour[fin + 1] = horsSeuil
            if (minimumLigne > maxDistance) return horsSeuil
            val echange = avant
            avant = prec
            prec = cour
            cour = echange
            cAvant = c
        }
        return prec[n]
    }

    /**
     * Distance d'abord, puis la fréquence multipliée par [multiplicateur]
     * (5 pour une faute de touche voisine, voir [VoisinsClavier]). Le moteur
     * applique le même multiplicateur dans son score : sans lui ici, la coupe
     * à [maxResults] pouvait écarter la bonne correction avant même que le
     * moteur ne la voie.
     */
    private fun classer(
        matches: List<Triple<String, Int, Int>>,
        maxResults: Int,
        multiplicateur: (String) -> Int
    ): List<Triple<String, Int, Int>> =
        matches
            .map { it to it.second.toLong() * multiplicateur(it.first) }
            .sortedWith(
                compareBy<Pair<Triple<String, Int, Int>, Long>> { it.first.third }
                    .thenByDescending { it.second }
            )
            .take(maxResults)
            .map { it.first }

    /** Une forme sur [PAS_ANNULATION] seulement interroge l'annulation. */
    private const val PAS_ANNULATION = 4096

    /**
     * Calculates Levenshtein distance with accent normalization.
     * This combines spell correction with accent-tolerant matching.
     * 
     * @param s1 First string
     * @param s2 Second string
     * @param normalizer Function to normalize accents (optional)
     * @return The minimum number of edits needed (ignoring accent differences)
     */
    fun calculateNormalized(
        s1: String, 
        s2: String,
        normalizer: (String) -> String = { it }
    ): Int {
        val normalized1 = normalizer(s1)
        val normalized2 = normalizer(s2)
        return calculate(normalized1, normalized2)
    }
    
    /**
     * Finds the closest matching words from a dictionary using Levenshtein distance.
     * 
     * This method:
     * 1. Pre-filters by word length (performance optimization)
     * 2. Calculates distance for remaining candidates
     * 3. Filters by maximum allowed distance
     * 4. Sorts by distance (closest first), then by frequency
     * 
     * @param input The user's typed word (potentially misspelled)
     * @param dictionary List of (word, frequency) pairs
     * @param maxDistance Maximum allowed Levenshtein distance (default: 2)
     * @param maxResults Maximum number of suggestions to return (default: 5)
     * @param lengthTolerance Maximum length difference to consider (default: 2)
     * @return List of (word, frequency, distance) triples sorted by relevance
     */
    fun findClosestMatches(
        input: String,
        dictionary: List<Pair<String, Int>>,
        maxDistance: Int = 2,
        maxResults: Int = 5,
        lengthTolerance: Int = 2,
        estAnnule: () -> Boolean = { false },
        multiplicateur: (String) -> Int = { 1 }
    ): List<Triple<String, Int, Int>> {
        
        if (input.isEmpty()) return emptyList()
        
        val inputLength = input.length
        val entree = CharArray(inputLength) { input[it].lowercaseChar() }
        val precedente = IntArray(inputLength + 1)
        val courante = IntArray(inputLength + 1)
        val avantPrecedente = IntArray(inputLength + 1)
        
        // Un seul parcours, sans liste intermédiaire : le filtre par longueur
        // puis le calcul de distance allouaient chacun une copie du
        // dictionnaire, et ce chemin est celui de la correction
        // orthographique, appelé dès qu'aucun préfixe ne correspond.
        val matches = ArrayList<Triple<String, Int, Int>>()
        var examined = 0
        for ((indice, entreeDico) in dictionary.withIndex()) {
            if (indice % PAS_ANNULATION == 0 && estAnnule()) return emptyList()
            val (word, freq) = entreeDico
            if (kotlin.math.abs(word.length - inputLength) > lengthTolerance) continue
            examined++
            val distance = distanceBornee(entree, word, maxDistance, precedente, courante, avantPrecedente)
            if (distance <= maxDistance) matches.add(Triple(word, freq, distance))
        }
        
        Log.d(TAG, "Spell check '$input': $examined/${dictionary.size} candidates after length filter")
        
        val ranked = classer(matches, maxResults, multiplicateur)

        if (ranked.isNotEmpty()) {
            Log.d(TAG, "✓ Found ${ranked.size} corrections for '$input': ${ranked.take(3).map { it.first }}")
        } else {
            Log.d(TAG, "✗ No corrections found for '$input' (within distance $maxDistance)")
        }
        
        return ranked
    }
    
    /**
     * Finds closest matches with accent normalization.
     * Combines spell correction with accent-tolerant matching.
     * 
     * @param input The user's typed word
     * @param dictionary List of (word, frequency) pairs
     * @param normalizer Function to normalize accents
     * @param maxDistance Maximum allowed distance
     * @param maxResults Maximum number of suggestions
     * @return List of (word, frequency, distance) triples sorted by relevance
     */
    fun findClosestMatchesNormalized(
        input: String,
        dictionary: List<Pair<String, Int>>,
        normalizedWords: List<String>,
        normalizer: (String) -> String,
        maxDistance: Int = 2,
        maxResults: Int = 5,
        estAnnule: () -> Boolean = { false },
        multiplicateur: (String) -> Int = { 1 }
    ): List<Triple<String, Int, Int>> {
        
        if (input.isEmpty()) return emptyList()
        
        val normalizedInput = normalizer(input)
        val inputLength = normalizedInput.length
        val entree = CharArray(inputLength) { normalizedInput[it].lowercaseChar() }
        val precedente = IntArray(inputLength + 1)
        val courante = IntArray(inputLength + 1)
        val avantPrecedente = IntArray(inputLength + 1)

        // `normalizedWords` est la liste précalculée au chargement du moteur,
        // alignée indice à indice avec `dictionary`. Sans elle, cette boucle
        // appelait `normalizer(word)` sur **chacune** des formes, avant même le
        // filtre de longueur : le repli reconstruisait le dictionnaire
        // normalisé à chaque frappe. Sur le clavier luxembourgeois issu de
        // celui-ci (38 442 formes), c'était l'essentiel des 670 à 1 180 ms
        // mesurés sur un Galaxy A21s, bien avant le coût de la distance
        // elle-même.
        //
        // Le filtre de longueur passe donc en premier, sur un entier, et rien
        // n'est alloué pour les mots qu'il écarte — l'écrasante majorité.
        val matches = ArrayList<Triple<String, Int, Int>>()
        val n = minOf(dictionary.size, normalizedWords.size)
        for (i in 0 until n) {
            // Une frappe plus récente a rendu cette recherche inutile : on la
            // laisse tomber au lieu de lui disputer le processeur.
            if (i % PAS_ANNULATION == 0 && estAnnule()) return emptyList()
            val normalizedWord = normalizedWords[i]
            if (kotlin.math.abs(normalizedWord.length - inputLength) > maxDistance) continue
            val distance = distanceBornee(entree, normalizedWord, maxDistance, precedente, courante, avantPrecedente)
            if (distance <= maxDistance) {
                val (word, freq) = dictionary[i]
                matches.add(Triple(word, freq, distance))
            }
        }

        val ranked = classer(matches, maxResults, multiplicateur)

        Log.d(TAG, "Normalized spell check '$input': ${ranked.size} matches found")
        
        return ranked
    }
    
}
