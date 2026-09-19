package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.kreyolkeyboard.TranslationDictionary

/**
 * Le branchement d'un jeu sur le carnet, écrit une fois pour les cinq.
 *
 * Chaque jeu n'a que deux choses à faire : verser au carnet ce qu'il fait
 * gagner ([Carnet.ajouter]), et appeler [ouvrir] quand sa partie se termine.
 * Tout ce qu'il y a autour — charger le dictionnaire hors du fil principal,
 * laisser à la carte de félicitations le temps d'exister, trouver la vue hôte,
 * ouvrir le carnet depuis le bilan — est identique partout, et n'avait aucune
 * raison d'être recopié cinq fois.
 *
 * Deux précautions de fil, reprises du carnet luxembourgeois où elles ont été
 * trouvées : l'assemblage des cartes demande les phrases du corpus (188 Ko) et
 * les rangs de fréquence, donc il est fait en fond ; et l'ouverture
 * attend un délai plancher depuis la fin de la partie, sans quoi, sur un
 * appareil rapide, la pochette recouvrirait aussitôt le message de fin.
 */
object Pochette {

    /**
     * Délai minimum entre la fin d'une partie et l'ouverture de la pochette :
     * le temps de lire le message de fin et de voir tomber les confettis. Ce
     * n'est pas une attente ajoutée mais un plancher — l'assemblage des cartes
     * prend souvent plus que cela.
     */
    const val DELAI = 1500L

    /**
     * Ouvre la pochette des [formes] gagnées pendant la partie qui vient de
     * finir.
     *
     * [encoreValide] est réinterrogé juste avant l'ouverture : entre la fin de
     * la partie et le bout du délai, le joueur a pu relancer une manche ou
     * changer d'onglet, et une pochette qui s'ouvrirait alors appartiendrait à
     * une partie qui n'existe plus.
     *
     * [surVue] reçoit la vue de la pochette, ou `null` si rien ne s'est
     * ouvert : l'appelant la garde pour pouvoir la retirer lui-même, par
     * exemple quand il relance une partie.
     *
     * [surFin] est appelé **dans tous les cas** une fois la pochette refermée,
     * et tout de suite s'il n'y avait rien à ouvrir. C'est là que va le bilan
     * de fin de manche : trois des cinq jeux le donnent dans un `AlertDialog`,
     * qui est une fenêtre à part et recouvrirait la pochette. Les faire se
     * suivre vaut mieux que de les faire se disputer l'écran.
     */
    fun ouvrir(
        fragment: Fragment,
        jeu: JeuCarte,
        formes: List<String>,
        neuves: Set<String>,
        delai: Long = DELAI,
        encoreValide: () -> Boolean = { true },
        surVue: (View?) -> Unit = {},
        surFin: () -> Unit = {}
    ) {
        val ctx = fragment.context?.applicationContext ?: return
        // Rien de gagné : le jeu reprend la main tout de suite, sans délai —
        // attendre une pochette qui n'ouvrira pas ne ferait que retarder son
        // bilan.
        if (formes.isEmpty()) {
            surFin()
            return
        }
        val anime = !animationsReduites(ctx)
        val depuis = System.currentTimeMillis()
        val principal = Handler(Looper.getMainLooper())
        // Un même mot gagné deux fois dans la manche ne fait qu'une carte, et
        // c'est l'ordre du gain qui range le paquet.
        val distinctes = formes.distinct()

        Thread {
            TranslationDictionary.charger(ctx)
            TranslationDictionary.chargerExemples(ctx)
            val connues = Carnet.cartes(ctx).associateBy { it.forme }
            val contenus = distinctes.mapNotNull { connues[it] }
                .map { CarteCarnet.contenu(ctx, it) }
            val reste = delai - (System.currentTimeMillis() - depuis)
            principal.postDelayed({
                if (!fragment.isAdded || !encoreValide()) return@postDelayed
                val hote = fragment.activity
                    ?.findViewById<ViewGroup>(android.R.id.content)
                if (contenus.isEmpty() || hote == null) {
                    surFin()
                    return@postDelayed
                }
                surVue(
                    Booster.ouvrir(
                        hote, jeu, contenus, neuves, anime,
                        surCarnet = { montrerLeCarnet(fragment) },
                        surFin = {
                            surVue(null)
                            surFin()
                        }
                    )
                )
            }, reste.coerceAtLeast(0L))
        }.start()
    }

    /** Ouvre le carnet par-dessus le jeu en cours. */
    fun montrerLeCarnet(fragment: Fragment) {
        if (!fragment.isAdded) return
        CarnetFragment().show(fragment.parentFragmentManager, "carnet")
    }

    /**
     * L'entrée du carnet posée dans un jeu : une pastille qui porte le total.
     *
     * Elle est **visible même quand rien n'a encore été gagné**, pour qu'un
     * joueur qui revient retrouve sa collection sans avoir à finir une partie
     * d'abord. Elle vaut pour les cinq jeux.
     */
    fun bouton(
        fragment: Fragment,
        couleur: Int,
        petit: Boolean = false
    ): TextView {
        val ctx = fragment.requireContext()
        val d = ctx.resources.displayMetrics.density
        return TextView(ctx).apply {
            textSize = if (petit) 12f else 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            val h = ((if (petit) 8f else 10f) * d).toInt()
            val v = ((if (petit) 5f else 6f) * d).toInt()
            setPadding(h, v, h, v)
            background = GradientDrawable().apply {
                cornerRadius = 20f * d
                setColor(couleur)
            }
            isClickable = true
            setOnClickListener { montrerLeCarnet(fragment) }
            rafraichir(this, ctx)
        }
    }

    /** Remet le total à jour sur une pastille créée par [bouton]. */
    fun rafraichir(bouton: TextView, context: Context) {
        val total = Carnet.taille(context)
        bouton.text = if (total == 0) "📔 Carnet" else "📔 Carnet · $total"
    }

    /**
     * Le joueur a-t-il coupé les animations du système ?
     *
     * Le retournement des cartes est la récompense de la pochette, mais il
     * n'est pas la pochette : sans animations, les cartes arrivent face
     * visible, et rien n'est perdu de ce qu'elles apprennent.
     */
    fun animationsReduites(context: Context): Boolean = try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    } catch (e: Exception) {
        false
    }
}
