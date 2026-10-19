package com.example.kreyolkeyboard.carnet

import android.content.Context

/**
 * La carte offerte à la fin de l'installation : « kréyòl » (v23.0.0, repris de
 * LuxKeyb 26.3.0).
 *
 * C'est une vraie carte du carnet, qui entre dans la révision comme les autres.
 * Côté luxembourgeois, la carte « Moien » doit corriger son sens (le LOD la
 * glose « matin ») ; ici le mot choisi est glosé tel quel (« créole »,
 * Wiktionnaire) et porte des phrases du corpus, si bien que la carte se lit
 * comme toutes les autres. Le bonjour (`bonjou`) n'a pas pu être retenu : il
 * n'a pas de glose, et le carnet refuse une carte sans sens.
 *
 * Sa rareté reste celle de son rang de fréquence : la carte n'a pas à se faire
 * passer pour ce qu'elle n'est pas.
 */
object CarteAccueil {

    const val FORME = "kréyòl"

    /** Verse la carte au carnet. `true` seulement si elle n'y était pas encore. */
    fun offrir(context: Context): Boolean =
        Carnet.ajouter(context, FORME, JeuCarte.ACCUEIL)

    /**
     * Le contenu de [carte], tel quel : la carte offerte n'a rien à corriger.
     * Gardé pour que les écrans partagés avec LuxKeyb l'appellent au même
     * endroit.
     */
    @Suppress("UNUSED_PARAMETER")
    fun corriger(context: Context, contenu: ContenuCarte): ContenuCarte = contenu
}
