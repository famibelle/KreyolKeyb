package com.example.kreyolkeyboard

import android.content.Context
import android.util.Log

/**
 * Les emojis récemment employés, en tête du panneau emoji (v15.0.0).
 *
 * Le panneau expose environ 1 900 emojis répartis en neuf catégories, alors
 * qu'un utilisateur donné en emploie une poignée : sans cette liste, chaque
 * envoi recommençait par la même descente dans la grille.
 *
 * ### Ce qui est conservé, et pourquoi c'est acceptable
 *
 * La v10.6.0 a retiré le dictionnaire personnel de la 10.5.0 au motif qu'un
 * clavier qui conserve des mots tapés par l'utilisateur, si encadré soit-il,
 * reste un clavier qui conserve ce qu'on écrit. Cette liste-ci est d'une autre
 * nature, et la distinction est ce qui la rend défendable :
 *
 * - elle ne retient que des identifiants pris dans un ensemble fermé et public,
 *   les emojis livrés dans `emoji_data.json`, jamais du texte libre ;
 * - elle est bornée à [CAPACITE] entrées, sans horodatage ni compteur, donc
 *   elle ne dit ni quand ni combien de fois ;
 * - c'est le statut qu'ont déjà les compteurs d'usage que la gamification écrit
 *   dans `filesDir`, et elle obéit à la même exclusion : rien n'est enregistré
 *   depuis un champ sensible (voir [setEnregistrementAutorise]).
 *
 * Elle ne quitte pas l'appareil, l'application n'ayant aucune permission
 * réseau, et le bouton « Vider les emojis récents » l'efface depuis les
 * réglages du clavier.
 */
object EmojiRecents {

    private const val TAG = "EmojiRecents"
    private const val PREFS_NAME = "kreyol_emoji_prefs"
    private const val KEY_RECENTS = "recents"

    /**
     * Nombre d'emojis conservés.
     *
     * Trente, soit très exactement la page visible du panneau : dix colonnes
     * (`EmojiPickerView.GRID_COLUMNS`) sur trois rangées (`VISIBLE_ROWS`). Au
     * delà, la catégorie « Récents » se mettrait à défiler, ce qui lui ferait
     * perdre la seule chose qu'on lui demande, tout montrer d'un coup d'oeil.
     */
    const val CAPACITE = 30

    /**
     * Séparateur des entrées dans la préférence.
     *
     * L'unité de séparation d'ASCII (U+001F) et non une virgule ou une espace :
     * un emoji est une suite de points de code qui peut contenir des jointeurs
     * de largeur nulle et des sélecteurs de variante, mais jamais un caractère
     * de contrôle. C'est la seule classe de caractères dont on soit certain
     * qu'elle n'apparaîtra pas dans une valeur.
     */
    private const val SEPARATEUR = "\u001F"

    /**
     * Faux dans un champ dont le contenu ne doit rien laisser derrière lui.
     *
     * Le service le remet à jour à chaque prise de focus, comme
     * [KeyFeedback.refresh] : un objet et non un paramètre, parce que la vue qui
     * enregistre (le panneau emoji) est construite loin du service et n'a aucun
     * moyen de connaître le champ de saisie courant.
     */
    @Volatile
    private var enregistrementAutorise = true

    fun setEnregistrementAutorise(autorise: Boolean) {
        enregistrementAutorise = autorise
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Les emojis récents, du plus récent au plus ancien. */
    fun lire(context: Context): List<String> {
        val brut = prefs(context).getString(KEY_RECENTS, null) ?: return emptyList()
        return brut.split(SEPARATEUR).filter { it.isNotEmpty() }.take(CAPACITE)
    }

    /** Note l'emploi d'un emoji, sauf dans un champ sensible. */
    fun enregistrer(context: Context, emoji: String) {
        if (!enregistrementAutorise) {
            Log.d(TAG, "Champ sensible: emoji non retenu")
            return
        }
        if (emoji.isEmpty() || emoji.contains(SEPARATEUR)) return

        val misAJour = fusionner(lire(context), emoji)
        prefs(context).edit()
            .putString(KEY_RECENTS, misAJour.joinToString(SEPARATEUR))
            .apply()
    }

    /** Efface la liste. Appelé depuis les réglages du clavier. */
    fun vider(context: Context) {
        prefs(context).edit().remove(KEY_RECENTS).apply()
        Log.d(TAG, "Emojis récents effacés")
    }

    /**
     * La liste mise à jour par l'emploi de [emoji].
     *
     * `internal` (et non private) pour être testable en JVM sans SharedPreferences.
     *
     * L'emoji passe en tête et son occurrence précédente disparaît : sans cette
     * déduplication, réemployer le même emoji trente fois de suite chasserait
     * toute la liste au profit d'une seule valeur répétée.
     */
    internal fun fusionner(existants: List<String>, emoji: String): List<String> =
        (listOf(emoji) + existants.filter { it != emoji }).take(CAPACITE)
}
