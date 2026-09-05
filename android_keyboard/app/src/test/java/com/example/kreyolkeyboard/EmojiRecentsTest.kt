package com.example.kreyolkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la liste des emojis récents (v15.0.0).
 *
 * Seule la fusion est testable en JVM, le reste demandant des
 * SharedPreferences. C'est aussi la partie où une erreur se voit tout de suite :
 * un emoji réemployé qui apparaîtrait deux fois, ou une liste qui grandirait
 * au delà de la page visible du panneau.
 */
class EmojiRecentsTest {

    @Test
    fun `un premier emoji ouvre la liste`() {
        assertEquals(listOf("🥭"), EmojiRecents.fusionner(emptyList(), "🥭"))
    }

    @Test
    fun `le dernier employe passe en tete`() {
        assertEquals(
            listOf("🌺", "🥭", "🎺"),
            EmojiRecents.fusionner(listOf("🥭", "🎺"), "🌺")
        )
    }

    @Test
    fun `reemployer un emoji le remonte sans le dupliquer`() {
        // Sans déduplication, la liste se remplirait d'un seul emoji répété
        val apres = EmojiRecents.fusionner(listOf("🥭", "🎺", "🌺"), "🌺")
        assertEquals(listOf("🌺", "🥭", "🎺"), apres)
        assertEquals(apres.size, apres.toSet().size)
    }

    @Test
    fun `la liste ne depasse jamais la page visible du panneau`() {
        // Dix colonnes sur trois rangées : au delà, « Récents » se mettrait à
        // défiler et perdrait sa raison d'être
        assertEquals(30, EmojiRecents.CAPACITE)

        var liste = emptyList<String>()
        for (i in 1..50) {
            liste = EmojiRecents.fusionner(liste, "e$i")
        }
        assertEquals(EmojiRecents.CAPACITE, liste.size)
    }

    @Test
    fun `le plus ancien sort quand la liste est pleine`() {
        var liste = (1..EmojiRecents.CAPACITE).map { "e$it" }.reversed()
        assertEquals("e1", liste.last())

        liste = EmojiRecents.fusionner(liste, "nouveau")
        assertEquals("nouveau", liste.first())
        assertTrue("l'emploi le plus ancien doit sortir", !liste.contains("e1"))
    }

    @Test
    fun `un emoji a ton de peau est une entree distincte de sa variante neutre`() {
        // Les tons de peau sont choisis par appui long et commités tels quels :
        // deux variantes du même concept sont deux valeurs différentes, et
        // c'est bien la variante employée que l'on doit retrouver
        val apres = EmojiRecents.fusionner(listOf("👍🏿"), "👍🏻")
        assertEquals(listOf("👍🏻", "👍🏿"), apres)
    }
}
