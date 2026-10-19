package com.example.kreyolkeyboard.carnet

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Fait compiler au processeur graphique, pendant que rien ne bouge, ce que
 * dessinera la première carte.
 *
 * ## Le défaut
 *
 * Mesuré sur un Galaxy A21s (2026-10-18) : la première pochette après une
 * installation ou une mise à jour se fige 0,5 s (commune) à 0,7 s (très rare)
 * au moment exact où la carte se retourne. C'est la compilation des shaders
 * de la carte ornée, que le pilote garde ensuite en cache : effacer ce seul
 * cache, sans réinstaller, refait l'arrêt, et la pochette suivante est fluide.
 * Ce n'est donc pas le code (un profil de référence n'y changerait rien).
 * Une commune ne chauffe pas tout : après elle, une très rare s'arrête encore
 * 0,35 s. Juste après l'installation, la première pochette est la carte
 * d'accueil : c'était la première impression qui se figeait.
 *
 * ## Le remède
 *
 * Une fois par installation ou mise à jour, quand l'écran d'accueil est posé,
 * une carte de chaque palier, chacune d'une saison, est dessinée une fois,
 * en train de se retourner, avec les effets de la pochette, presque transparentes et sous le contenu de l'écran, puis
 * retirées. L'arrêt a lieu sur un écran
 * immobile, où il ne se voit pas.
 */
object ChauffeCartes {

    private const val PREFS = "carnet_chauffe"
    private const val CLE = "installation"

    /** Laisse l'écran d'accueil se poser avant de chauffer. */
    private const val DELAI_MS = 2500L

    /** Les images dessinées avant de retirer les cartes : une suffit, deux par prudence. */
    private const val IMAGES = 2

    fun planifier(activity: Activity) {
        if (Pochette.animationsReduites(activity)) return
        val marque = marqueInstallation(activity) ?: return
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong(CLE, -1L) == marque) return
        val racine = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        racine.postDelayed({
            if (activity.isFinishing || activity.isDestroyed || !racine.isAttachedToWindow) return@postDelayed
            try {
                chauffer(activity, racine) { prefs.edit().putLong(CLE, marque).apply() }
            } catch (e: Throwable) {
                // Rien de grave : la première pochette se figera, comme avant.
            }
        }, DELAI_MS)
    }

    private fun chauffer(activity: Activity, racine: ViewGroup, fait: () -> Unit) {
        val d = activity.resources.displayMetrics.density
        val largeur = (300 * d).toInt()
        val hauteur = (Ornement.HAUTEUR / Ornement.LARGEUR * largeur).toInt()
        val calque = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(largeur, hauteur)
            alpha = 0.01f
            isClickable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        // Un palier par saison : chaque saison a ses particules et son
        // habillage (neige, pétales, aigrettes ou lucioles, feuilles), donc
        // ses shaders. Sans saison, la très rare d'été s'arrêtait encore 0,28 s.
        val paliers = listOf(
            Rarete.COMMUN to "2026-01-15",
            Rarete.PEU_COMMUN to "2026-04-20",
            Rarete.RARE to "2026-10-15",
            Rarete.TRES_RARE to "2026-07-15"
        )
        var restantes = paliers.size
        paliers.forEach { (palier, jour) ->
            val carte = CarteCarnet.complete(activity, exemple(palier, jour))
            (carte as? CarteOrnee)?.avecAmbiance()?.attendreSaMatiere {
                restantes--
                if (restantes == 0) dessinerPuisRetirer(racine, calque, fait)
            } ?: run { restantes-- }
            // Comme dans la pochette : la face vit dans une couche matérielle.
            // Et elle se retourne : une couche dessinée en perspective a ses
            // propres shaders, sans lesquels la très rare s'arrêtait encore
            // 0,28 s en plein retournement.
            calque.addView(FrameLayout(activity).apply {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                cameraDistance = 12000f * d
                rotationY = -35f
                addView(carte, FrameLayout.LayoutParams(largeur, hauteur))
            }, FrameLayout.LayoutParams(largeur, hauteur))
        }
        // Et les effets de la pochette d'une très rare, saisis à mi-course,
        // pour qu'eux non plus ne compilent pas en pleine cérémonie.
        val plein = { v: View -> calque.addView(v, FrameLayout.LayoutParams(largeur, hauteur)) }
        plein(DosDeCarte(activity, JeuCarte.MO_AN_PLAS, Rarete.TRES_RARE).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            cameraDistance = 12000f * d
            rotationY = 35f
        })
        plein(HaloRarete(activity, Rarete.TRES_RARE))
        plein(NuitEtoilee(activity))
        plein(RayonsRarete(activity, Rarete.TRES_RARE).apply { avancement = 0.5f })
        plein(EclatCarte(activity, Rarete.TRES_RARE).apply { avancement = 0.5f })
        plein(FeuxArtifice(activity, Rarete.TRES_RARE, 1L).apply { avancement = 0.5f })
        plein(EclairCarte(activity, Rarete.TRES_RARE))
        // Sous le contenu : l'écran le recouvre, et le peu qui en paraîtrait
        // est à 1 % d'opacité.
        racine.addView(calque, 0)
        if (restantes == 0) dessinerPuisRetirer(racine, calque, fait)
    }

    private fun dessinerPuisRetirer(racine: ViewGroup, calque: View, fait: () -> Unit) {
        calque.invalidate()
        var images = 0
        Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
            override fun doFrame(t: Long) {
                if (++images < IMAGES) {
                    calque.invalidate()
                    Choreographer.getInstance().postFrameCallback(this)
                    return
                }
                racine.removeView(calque)
                fait()
            }
        })
    }

    /** Une carte d'exemple : ni le dictionnaire ni le carnet ne sont lus. */
    private fun exemple(palier: Rarete, jour: String) = ContenuCarte(
        carte = CarteMot(
            forme = if (palier == Rarete.TRES_RARE) "zèl" else "kréyòl",
            premiereFois = midi(jour),
            rencontres = 1,
            numero = 1
        ),
        rarete = palier,
        rang = if (palier == Rarete.TRES_RARE) 3558 else 103,
        glose = "…",
        exemple = "…"
    )

    /** Midi, heure locale, du jour [iso] (« 2026-07-15 ») : une saison sans ambiguïté. */
    private fun midi(iso: String): Long {
        val (a, m, j) = iso.split("-").map { it.toInt() }
        return java.util.Calendar.getInstance().apply {
            clear()
            set(a, m - 1, j, 12, 0)
        }.timeInMillis
    }

    private fun marqueInstallation(context: Context): Long? = try {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val info = if (Build.VERSION.SDK_INT >= 33)
            pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        else pm.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime
    } catch (e: Exception) {
        null
    }
}
