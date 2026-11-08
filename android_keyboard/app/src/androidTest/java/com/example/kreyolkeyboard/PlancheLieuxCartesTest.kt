package com.example.kreyolkeyboard

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kreyolkeyboard.carnet.CarteCarnet
import com.example.kreyolkeyboard.carnet.CarteMot
import com.example.kreyolkeyboard.carnet.JeuCarte
import com.example.kreyolkeyboard.carnet.Lieu
import com.example.kreyolkeyboard.carnet.PartageCarte
import com.example.kreyolkeyboard.carnet.Rarete
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Calendar

/**
 * Une carte commune par lieu, au carême, telle que l'image de partage la
 * fige : pour relire les trente lieux un par un avec le propriétaire. Pas un
 * test, un outil de relecture.
 */
@RunWith(AndroidJUnit4::class)
class PlancheLieuxCartesTest {

    /** Le mot courant le plus fréquent de chaque lieu, dans l'ordre de [Lieu]. */
    private val mots = listOf(
        "jou", "solèy", "lanné", "menm", "nou", "rasin", "doubout", "pran", "adan", "kontan",
        "tou", "douvan", "pyé", "kréyòl", "bèl", "dlo", "men", "gadé", "san", "a-y",
        "krab", "moun", "fanm", "bouch", "biten", "péyi", "zòt", "osi", "gran", "manjé"
    )

    @Test
    fun cartes() {
        val instr = InstrumentationRegistry.getInstrumentation()
        val ctx = instr.targetContext
        val dico = JSONArray(ctx.assets.open("creole_dict.json").bufferedReader().readText())
        val rangs = HashMap<String, Int>()
        for (i in 0 until dico.length()) rangs[dico.getJSONArray(i).getString(0)] = i + 1
        val date = Calendar.getInstance().apply { set(2026, Calendar.MARCH, 15, 12, 0, 0) }.timeInMillis
        // `-Pandroid.testInstrumentationRunnerArguments.lieux=0,5` pour n'en rendre que quelques-uns.
        val choisis = InstrumentationRegistry.getArguments().getString("lieux")?.split(',')?.map { it.trim().toInt() }?.toSet()
        mots.forEachIndexed { i, mot ->
            if (choisis != null && i !in choisis) return@forEachIndexed
            assertEquals(Lieu.values()[i], Lieu.du(mot))
            instr.runOnMainSync {
                val contenu = CarteCarnet.contenu(ctx, CarteMot(mot, date, 1, 1, setOf(JeuCarte.MOKWARE)))
                    .copy(rang = rangs[mot], rarete = Rarete.pourRang(rangs[mot]))
                val b = PartageCarte.dessiner(ctx, contenu)
                File(ctx.getExternalFilesDir(null), "carte_lieu_%02d.png".format(i)).outputStream().use {
                    b.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }
}
