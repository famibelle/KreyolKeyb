package com.example.kreyolkeyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kreyolkeyboard.carnet.CarteCarnet
import com.example.kreyolkeyboard.carnet.CarteMot
import com.example.kreyolkeyboard.carnet.JeuCarte
import com.example.kreyolkeyboard.carnet.Lieu
import com.example.kreyolkeyboard.carnet.PartageCarte
import com.example.kreyolkeyboard.carnet.Rarete
import org.json.JSONArray
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Calendar

/**
 * Planche de contrôle des cartes entières, telles que l'image de partage les
 * fige (le théâtre de papier de la carte ouverte) : pas un test, un outil de
 * relecture, comme [PlancheLieuxTest].
 */
@RunWith(AndroidJUnit4::class)
class PlancheCartesTest {

    /** Des mots dessinés aux quatre raretés, un mot sans dessin, au carême puis à l'hivernage. */
    private val mots = listOf("dlo", "kaz", "pyébwa", "avyon", "loraj", "myèl", "rèstoran", "téléfòn", "kréyòl")

    private fun date(mois: Int): Long = Calendar.getInstance().apply {
        set(2026, mois, 15, 12, 0, 0)
    }.timeInMillis

    @Test
    fun cartes() {
        val instr = InstrumentationRegistry.getInstrumentation()
        val ctx = instr.targetContext
        // Les rangs ne sont relevés que pour les cartes du carnet : ici, à la main.
        val dico = JSONArray(ctx.assets.open("creole_dict.json").bufferedReader().readText())
        val rangs = HashMap<String, Int>()
        for (i in 0 until dico.length()) rangs[dico.getJSONArray(i).getString(0)] = i + 1
        val cases = ArrayList<Pair<String, Bitmap>>()
        for ((saison, mois) in listOf("carême" to Calendar.MARCH, "hivernage" to Calendar.AUGUST)) {
            mots.forEachIndexed { i, mot ->
                val carte = CarteMot(mot, date(mois), 1, i + 1, setOf(JeuCarte.MOKWARE))
                instr.runOnMainSync {
                    val contenu = CarteCarnet.contenu(ctx, carte)
                        .copy(rang = rangs[mot], rarete = Rarete.pourRang(rangs[mot]))
                    cases += "$mot · ${contenu.rarete.name} · ${Lieu.du(mot).nom} · $saison" to PartageCarte.dessiner(ctx, contenu)
                }
            }
        }
        // Deux planches, une par saison, quatre cartes par rangée, réduites de moitié.
        cases.chunked(mots.size).forEachIndexed { n, morceau ->
            val l = morceau[0].second.width / 2
            val h = morceau[0].second.height / 2
            val colonnes = 3
            val lignes = (morceau.size + colonnes - 1) / colonnes
            val out = Bitmap.createBitmap(colonnes * (l + 8), lignes * (h + 34), Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            c.drawColor(Color.WHITE)
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { textSize = 22f; color = Color.BLACK }
            morceau.forEachIndexed { i, (titre, b) ->
                val x = (i % colonnes) * (l + 8f)
                val y = (i / colonnes) * (h + 34f)
                c.drawText(titre, x + 4f, y + 24f, p)
                c.drawBitmap(Bitmap.createScaledBitmap(b, l, h, true), x, y + 32f, p)
            }
            File(ctx.getExternalFilesDir(null), "planche_cartes_${n + 1}.png").outputStream().use {
                out.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
