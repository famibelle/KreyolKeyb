package com.example.kreyolkeyboard

import android.content.ContextWrapper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Une faute qui est aussi le début d'un autre mot se corrige quand même
 * (v23.0.0, repris de LuxKeyb 37.2.0).
 *
 * Avant, la correction ne se cherchait que si rien ne commençait par ce qui
 * était tapé : « rout » (pour « tout », le r touche le t) proposait
 * « routyé ». Le seuil est réglé sur le banc de
 * correction, voir [SuggestionEngine.ratioCorrectionSurPrefixe].
 */
class CorrectionSurPrefixeTest {

    private val moteur: SuggestionEngine by lazy {
        SuggestionEngine(ContextWrapper(null)).also { m ->
            m.ouvrirActif = { File("src/main/assets", it).inputStream() }
            runBlocking { m.chargerKreyol() }
        }
    }

    @Test
    fun uneFauteQuiCommenceUnMotSeCorrige() {
        assertEquals("tout", moteur.rangeeKreyol("rout").first())
        assertEquals("adan", moteur.rangeeKreyol("aran").first())
        assertEquals("pran", moteur.rangeeKreyol("oran").first())
    }

    @Test
    fun unMotJusteResteEnTete() {
        assertEquals("moun", moteur.rangeeKreyol("moun").first())
        assertEquals("toujou", moteur.rangeeKreyol("touj").first())
    }
}
