package com.example.kreyolkeyboard

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kreyolkeyboard.carnet.CarteCarnet
import com.example.kreyolkeyboard.carnet.CarteMot
import com.example.kreyolkeyboard.carnet.JeuCarte
import com.example.kreyolkeyboard.carnet.PartageCarte
import com.example.kreyolkeyboard.carnet.Paysages
import com.example.kreyolkeyboard.carnet.Rarete
import org.json.JSONArray
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Calendar

/** Les propositions de Chutes du Carbet en papier découpé, sur une commune et une très rare : un outil de relecture. */
@RunWith(AndroidJUnit4::class)
class PlancheCarbetTest {

    @Test
    fun propositions() {
        val instr = InstrumentationRegistry.getInstrumentation()
        val ctx = instr.targetContext
        val dico = JSONArray(ctx.assets.open("creole_dict.json").bufferedReader().readText())
        val rangs = HashMap<String, Int>()
        for (i in 0 until dico.length()) rangs[dico.getJSONArray(i).getString(0)] = i + 1
        val date = Calendar.getInstance().apply { set(2026, Calendar.MARCH, 15, 12, 0, 0) }.timeInMillis
        for (v in 0..2) {
            Paysages.CARBET = v
            Paysages.viderCache()
            val cartes = ArrayList<Bitmap>()
            for (mot in listOf("dan", "rèstoran")) {
                instr.runOnMainSync {
                    val contenu = CarteCarnet.contenu(ctx, CarteMot(mot, date, 1, 1, setOf(JeuCarte.MOKWARE)))
                        .copy(rang = rangs[mot], rarete = Rarete.pourRang(rangs[mot]))
                    cartes += PartageCarte.dessiner(ctx, contenu)
                }
            }
            cartes.forEachIndexed { i, b ->
                File(ctx.getExternalFilesDir(null), "carte_carbet_${"ABC"[v]}_$i.png").outputStream().use {
                    b.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        Paysages.CARBET = 0
    }
}
