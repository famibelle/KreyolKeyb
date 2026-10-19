package com.example.kreyolkeyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kreyolkeyboard.carnet.Fete
import com.example.kreyolkeyboard.carnet.Heure
import com.example.kreyolkeyboard.carnet.Lieu
import com.example.kreyolkeyboard.carnet.Paysages
import com.example.kreyolkeyboard.carnet.Rarete
import com.example.kreyolkeyboard.carnet.Saison
import com.example.kreyolkeyboard.carnet.Souvenir
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Planche de contrôle des lieux : pas un test, un outil de relecture. */
@RunWith(AndroidJUnit4::class)
class PlancheLieuxTest {
    private val L = 400
    private val H = 264

    private fun planche(nom: String, cases: List<Pair<String, Bitmap?>>, colonnes: Int) {
        val lignes = (cases.size + colonnes - 1) / colonnes
        val out = Bitmap.createBitmap(colonnes * (L + 8), lignes * (H + 30), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; color = Color.BLACK }
        cases.forEachIndexed { i, (titre, b) ->
            val x = (i % colonnes) * (L + 8f)
            val y = (i / colonnes) * (H + 30f)
            c.drawText(titre, x + 4f, y + 22f, p)
            b?.let { c.drawBitmap(it, x, y + 28f, null) }
        }
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        File(ctx.getExternalFilesDir(null), "$nom.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun lieux() {
        val cases = ArrayList<Pair<String, Bitmap?>>()
        for (lieu in Lieu.values()) for (r in Rarete.values())
            cases += "${lieu.nom} · ${r.name}" to Paysages.rendu(lieu, Heure.de(r, null), L, H)
        planche("planche_lieux_1", cases.take(32), 4)
        planche("planche_lieux_2", cases.drop(32), 4)
    }

    @Test
    fun souvenirs() {
        val s = listOf(
            Triple(Lieu.GRANDE_ANSE, Rarete.COMMUN, Souvenir(Saison.CAREME, null)),
            Triple(Lieu.LES_SAINTES, Rarete.PEU_COMMUN, Souvenir(Saison.HIVERNAGE, null)),
            Triple(Lieu.SOUFRIERE, Rarete.TRES_RARE, Souvenir(Saison.CAREME, Fete.NOUVEL_AN)),
            Triple(Lieu.PLACE_VICTOIRE, Rarete.PEU_COMMUN, Souvenir(Saison.CAREME, Fete.CARNAVAL)),
            Triple(Lieu.ROCHES_GRAVEES, Rarete.RARE, Souvenir(Saison.CAREME, Fete.VINGT_SEPT_MAI)),
            Triple(Lieu.MORNE_A_L_EAU, Rarete.TRES_RARE, Souvenir(Saison.HIVERNAGE, Fete.TOUSSAINT)),
            Triple(Lieu.MARIE_GALANTE, Rarete.RARE, Souvenir(Saison.HIVERNAGE, Fete.CHANTE_NWEL)),
            Triple(Lieu.FIGUIER_MAUDIT, Rarete.COMMUN, Souvenir(Saison.HIVERNAGE, null))
        )
        planche("planche_souvenirs", s.map { (l, r, sv) -> "${l.nom} · ${sv.nom}" to Paysages.rendu(l, Heure.de(r, null), L, H, sv) }, 4)
    }
}
