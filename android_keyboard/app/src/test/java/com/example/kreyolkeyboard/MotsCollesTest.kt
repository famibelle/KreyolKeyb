package com.example.kreyolkeyboard

import android.content.ContextWrapper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Deux mots tapés sans espace se recollent ; un mot du dictionnaire, non
 * (v23.0.0, repris de LuxKeyb 37.2.0).
 *
 * Le banc de correction donnait 0 % sur les mots collés, la barre restant vide
 * une fois sur quatre ; 71,5 % désormais en tête.
 */
class MotsCollesTest {

    private val moteur: SuggestionEngine by lazy {
        SuggestionEngine(ContextWrapper(null)).also { m ->
            m.ouvrirActif = { File("src/main/assets", it).inputStream() }
            runBlocking { m.chargerKreyol() }
        }
    }

    @Test
    fun deuxMotsCollesSeSeparent() {
        assertEquals("pou ka", moteur.rangeeKreyol("pouka").first())
        assertEquals("sé moun", moteur.rangeeKreyol("sémoun").first())
        assertEquals("ou pa", moteur.rangeeKreyol("oupa").first())
        assertEquals("kout tèt", moteur.rangeeKreyol("kouttèt").first())
    }

    @Test
    fun unMotDuDictionnaireNeSeCoupePas() {
        for (mot in listOf("annou", "granmoun", "lanmè", "konsa", "kité")) {
            val barre = moteur.rangeeKreyol(mot)
            assertTrue("$mot : $barre", barre.none { ' ' in it })
        }
    }

    @Test
    fun unePaireAuTraitDUnionNeSeCoupePasEnDeux() {
        // Le dictionnaire écrit « kay-la » : la correction le propose, une
        // espace à la place serait une faute.
        val barre = moteur.rangeeKreyol("kayla")
        assertTrue("kayla : $barre", barre.none { ' ' in it })
        assertTrue("kayla : $barre", "kay-la" in barre)
    }
}
