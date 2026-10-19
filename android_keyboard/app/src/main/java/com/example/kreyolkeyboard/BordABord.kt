package com.example.kreyolkeyboard

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.WeakHashMap

/**
 * Affichage bord à bord, imposé par Android 15 aux applis qui ciblent le SDK 35.
 *
 * La fenêtre passe alors sous la barre d'état et la barre de navigation, et
 * `adjustResize` ne rétrécit plus le contenu quand le clavier s'ouvre : c'est à
 * l'appli de s'écarter. On ne passe pas par `enableEdgeToEdge()` d'androidx :
 * pour les versions antérieures il appelle `Window.setStatusBarColor` et
 * `setNavigationBarColor`, que la Play Console signale comme obsolètes (retirés
 * en 27.0.0 avec la bibliothèque Material).
 *
 * Avant Android 15 la fenêtre n'est pas bord à bord, le système a déjà écarté le
 * contenu et les encarts reçus sont nuls : ces marges ne changent rien.
 */
object BordABord {

    /** Marges latérales d'origine des vues de `lateraux`, pour ne pas les cumuler. */
    private val margesLaterales = WeakHashMap<View, Pair<Int, Int>>()

    /**
     * [haut] reçoit la hauteur de la barre d'état en marge intérieure, pour que
     * son fond se prolonge dessous ; avec [couleurHaut], cette bande est peinte
     * de cette couleur plutôt que du fond de [haut]. [bas] reçoit la hauteur de
     * la barre de navigation, ou du clavier quand il est ouvert.
     *
     * Les encoches latérales, en paysage, vont par défaut sur [racine], ce qui
     * laisse une bande de son fond du côté de la caméra. Avec [lateraux], elles
     * vont sur ces vues-là : chacune garde son fond jusqu'au bord de l'écran et
     * seul son contenu s'écarte. Une vue reconstruite ensuite passe par
     * [ecarterLateralement].
     */
    fun appliquer(
        racine: View,
        haut: View,
        bas: View = racine,
        couleurHaut: Int? = null,
        lateraux: (() -> List<View>)? = null
    ) {
        val hautInitial = haut.paddingTop
        val basInitial = bas.paddingBottom
        val fondInitial: Drawable? = haut.background
        ViewCompat.setOnApplyWindowInsetsListener(racine) { _, insets ->
            val barres = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val clavier = insets.getInsets(WindowInsetsCompat.Type.ime())
            if (lateraux == null) {
                racine.setPadding(barres.left, racine.paddingTop, barres.right, racine.paddingBottom)
            } else {
                ecarter(lateraux(), barres.left, barres.right)
            }
            haut.setPadding(haut.paddingLeft, hautInitial + barres.top, haut.paddingRight, haut.paddingBottom)
            if (couleurHaut != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                haut.background = if (barres.top == 0) fondInitial else
                    LayerDrawable(arrayOf(fondInitial ?: ColorDrawable(0), ColorDrawable(couleurHaut))).apply {
                        setLayerGravity(1, Gravity.TOP or Gravity.FILL_HORIZONTAL)
                        setLayerHeight(1, barres.top)
                    }
            }
            bas.setPadding(
                bas.paddingLeft, bas.paddingTop, bas.paddingRight,
                basInitial + maxOf(barres.bottom, clavier.bottom)
            )
            insets
        }
    }

    /**
     * Donne à [vues], reconstruites après coup, l'écart latéral que
     * [appliquer] leur aurait donné. Redemander un passage des encarts ne
     * suffit pas : mesuré sur API 36, il n'arrive pas quand la reconstruction a
     * lieu pendant la première mise en page.
     */
    fun ecarterLateralement(vues: List<View>) {
        val barres = vues.firstOrNull()?.let { ViewCompat.getRootWindowInsets(it) }?.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        ) ?: return
        ecarter(vues, barres.left, barres.right)
    }

    private fun ecarter(vues: List<View>, gauche: Int, droite: Int) {
        vues.forEach { vue ->
            val (g, d) = margesLaterales.getOrPut(vue) { vue.paddingLeft to vue.paddingRight }
            vue.setPadding(g + gauche, vue.paddingTop, d + droite, vue.paddingBottom)
        }
    }
}
