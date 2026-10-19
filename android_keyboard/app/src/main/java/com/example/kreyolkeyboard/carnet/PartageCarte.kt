package com.example.kreyolkeyboard.carnet

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import android.view.View
import androidx.core.content.FileProvider
import com.example.kreyolkeyboard.R
import java.io.File
import java.io.FileOutputStream

/**
 * Partager une carte : la carte entière en image, et le lien de l'application.
 *
 * C'est la seule chose du carnet qui sorte de l'appareil, et elle n'en sort
 * que sur un geste du joueur. L'image ne porte que des données de
 * dictionnaire : le mot, sa glose (Kreyolopedia ou Wiktionnaire, CC BY-SA 4.0,
 * dont la source est sur la carte), la phrase du corpus et son crédit, le
 * numéro de la carte. Rien de ce qui a été tapé. Repris de LuxKeyb 35.0.0
 * (v23.0.0).
 *
 * Le format est celui de la carte de niveau, 1080 × 1350 : le portrait que
 * WhatsApp et les réseaux affichent sans recadrer. Le partage lui-même reprend
 * sa recette, `ClipData` comprise : sans elle, sous Android 14, l'application
 * cible reçoit un `SecurityException` et l'image ne s'attache pas (voir
 * `SettingsActivity.shareLevelCard`). La recette est recopiée ici plutôt
 * qu'extraite de `SettingsActivity`, fichier partagé avec KreyolKeyb : le
 * restructurer coûterait à chaque fusion.
 */
object PartageCarte {

    private const val TAG = "PartageCarte"
    private const val LARGEUR = 1080
    private const val HAUTEUR = 1350
    /** La largeur de la carte dans l'image ; sa hauteur suit son rapport 300 × 440. */
    private const val LARGEUR_CARTE = 780

    fun partager(context: Context, contenu: ContenuCarte) {
        try {
            val image = dessiner(context, contenu)
            val dossier = File(context.cacheDir, "images").apply { mkdirs() }
            // Un seul fichier, réécrit : le cache n'accumule pas une image par carte.
            val fichier = File(dossier, "carte_lux.png")
            FileOutputStream(fichier).use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fichier)

            val message = context.getString(
                R.string.carte_partage_message,
                contenu.carte.forme,
                contenu.glose.ifEmpty { "…" },
                context.packageName
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, message)
                clipData = ClipData.newUri(context.contentResolver, fichier.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.carte_partager_titre))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Partage impossible: ${e.message}", e)
        }
    }

    /**
     * L'image : la carte posée sur le fond du carnet, le nom de l'application
     * en pied. La carte est mesurée à sa largeur finale, et non réduite d'une
     * vue d'écran : ses textes sont en unités de carte, ils restent nets.
     */
    fun dessiner(context: Context, contenu: ContenuCarte): Bitmap {
        val image = Bitmap.createBitmap(LARGEUR, HAUTEUR, Bitmap.Config.ARGB_8888)
        val c = Canvas(image)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        p.shader = LinearGradient(
            0f, 0f, 0f, HAUTEUR.toFloat(),
            eclaircir(Carnet.COULEUR, 0.12f), assombrir(Carnet.COULEUR, 0.45f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, LARGEUR.toFloat(), HAUTEUR.toFloat(), p)
        p.shader = null

        val carte = CarteCarnet.complete(context, contenu)
        carte.measure(
            View.MeasureSpec.makeMeasureSpec(LARGEUR_CARTE, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        carte.layout(0, 0, carte.measuredWidth, carte.measuredHeight)
        val haut = 50f
        c.save()
        c.translate((LARGEUR - carte.measuredWidth) / 2f, haut)
        carte.draw(c)
        c.restore()

        val pied = haut + carte.measuredHeight + (HAUTEUR - haut - carte.measuredHeight) / 2f
        p.color = Color.WHITE
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = 46f
        c.drawText("Klavyé Kréyòl Karukéra", LARGEUR / 2f, pied, p)
        p.typeface = Typeface.DEFAULT
        p.textSize = 30f
        p.color = 0xCCFFFFFF.toInt()
        c.drawText(context.getString(R.string.carte_partage_pied), LARGEUR / 2f, pied + 46f, p)
        return image
    }
}
