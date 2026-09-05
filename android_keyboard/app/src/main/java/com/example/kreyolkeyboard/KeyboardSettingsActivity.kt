package com.example.kreyolkeyboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Écran des réglages du clavier, atteint par l'engrenage du bandeau de
 * [SettingsActivity].
 *
 * Écran à part et non onglet : les réglages vivent derrière un engrenage sur
 * Android, et la barre de [SettingsActivity] porte déjà sept onglets. Ils ont
 * d'abord vécu dans une carte de l'onglet À Propos (v10.11.7), qui est une page de
 * présentation où personne ne cherche un interrupteur.
 *
 * Cet écran a vocation à grandir : les points 3 à 6 de ACCESSIBILITE.md sont tous
 * des réglages (taille des touches, délai de l'appui long, nombre de propositions),
 * dont plusieurs à plusieurs crans.
 */
class KeyboardSettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "KeyboardSettings"
        private const val BLEU = "#0080FF"
        private const val ENCRE = "#333333"
        private const val ENCRE_DOUCE = "#666666"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Le thème de l'application est Theme.AppCompat, qui pose une barre d'action
        // sombre : elle doublerait le bandeau bleu ci-dessous. SettingsActivity la
        // masque de la même façon.
        supportActionBar?.hide()

        val racine = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
        racine.addView(bandeau())
        racine.addView(ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            addView(contenu())
        })
        setContentView(racine)
    }

    /** Bandeau bleu avec la flèche de retour, repris de l'écran principal. */
    private fun bandeau(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setBackgroundColor(Color.parseColor(BLEU))
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(12), dp(16), dp(12))

        addView(TextView(this@KeyboardSettingsActivity).apply {
            text = "←"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            // Cible confortable : cette flèche est le seul moyen de sortir.
            minWidth = dp(48)
            minHeight = dp(48)
            contentDescription = "Retour"
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        })

        addView(TextView(this@KeyboardSettingsActivity).apply {
            text = "Réglages du clavier"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(8), 0, 0, 0)
        })
    }

    private fun contenu(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setPadding(dp(16), dp(16), dp(16), dp(24))

        addView(carte().apply {
            addView(titreSection("Apparence"))
            addView(explication(
                "La couleur des touches. Le vert, l'orange et le bleu de la charte " +
                        "ne changent pas : seul le blanc des lettres passe en anthracite."
            ))
            addView(choixTheme())
            addView(explication(
                "« Comme le téléphone » suit le mode sombre du système. Les deux " +
                        "autres positions existent parce que sur plusieurs surcouches ce " +
                        "mode ne descend pas jusqu'aux claviers tiers."
            ))
        })
        addView(espacement())

        addView(carte().apply {
            addView(titreSection("Retour de frappe"))
            addView(explication(
                "Ce que le clavier fait à chaque appui. Le choix s'applique dès le " +
                        "retour dans un champ de saisie."
            ))
            addView(interrupteur(
                "Vibration à la frappe",
                KeyboardPreferences.hapticEnabled(this@KeyboardSettingsActivity)
            ) { actif ->
                KeyboardPreferences.setHapticEnabled(this@KeyboardSettingsActivity, actif)
            })
            addView(interrupteur(
                "Son de frappe",
                KeyboardPreferences.soundEnabled(this@KeyboardSettingsActivity)
            ) { actif ->
                KeyboardPreferences.setSoundEnabled(this@KeyboardSettingsActivity, actif)
            })
            addView(explication(
                "Ces deux réglages sont dans l'application et non dans ceux du " +
                        "téléphone : sur beaucoup d'appareils, le réglage de vibration au " +
                        "toucher ne gouverne que le clavier du constructeur."
            ))
        })
        addView(espacement())

        addView(carte().apply {
            addView(titreSection("Emojis récents"))
            addView(explication(
                "Le panneau emoji place en tête les 30 derniers emojis que vous " +
                        "avez employés. Cette liste ne quitte pas le téléphone, et rien " +
                        "n'y est ajouté depuis un champ de mot de passe."
            ))
            addView(boutonSecondaire("Vider les emojis récents") {
                EmojiRecents.vider(this@KeyboardSettingsActivity)
                Toast.makeText(
                    this@KeyboardSettingsActivity,
                    "Emojis récents effacés",
                    Toast.LENGTH_SHORT
                ).show()
            })
        })
    }

    /**
     * Un bouton d'action discret, sur le modèle des interrupteurs voisins :
     * couleurs posées à la main pour ne pas dépendre du thème AppCompat, qui
     * rendrait le libellé presque invisible sur la carte blanche.
     */
    private fun boutonSecondaire(libelle: String, onClick: () -> Unit): View =
        Button(this).apply {
            text = libelle
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.parseColor(BLEU))
            setBackgroundColor(Color.parseColor("#F1F5F9"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            setOnClickListener {
                onClick()
                Log.d(TAG, "Action « $libelle » déclenchée")
            }
        }

    /**
     * Les trois positions du thème, en boutons radio.
     *
     * Un groupe radio et non un interrupteur : trois états, dont un, « comme le
     * téléphone », n'est ni l'un ni l'autre des deux autres et se perdrait dans
     * une bascule à deux positions.
     *
     * Les couleurs sont posées à la main pour la même raison que sur les
     * interrupteurs voisins : l'état non coché du thème est un gris presque blanc,
     * invisible sur une carte blanche.
     */
    private fun choixTheme(): View {
        val actuel = KeyboardPreferences.themeMode(this)
        return RadioGroup(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            KeyboardTheme.Mode.entries.forEach { mode ->
                addView(RadioButton(this@KeyboardSettingsActivity).apply {
                    id = View.generateViewId()
                    tag = mode
                    text = mode.libelle
                    textSize = 16f
                    setTextColor(Color.parseColor(ENCRE))
                    isChecked = mode == actuel
                    setPadding(dp(8), dp(12), 0, dp(12))
                    buttonTintList = ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_checked),
                            intArrayOf(-android.R.attr.state_checked)
                        ),
                        intArrayOf(Color.parseColor(BLEU), Color.parseColor("#757575"))
                    )
                })
            }
            setOnCheckedChangeListener { groupe, idCoche ->
                val mode = groupe.findViewById<View>(idCoche)?.tag as? KeyboardTheme.Mode
                    ?: return@setOnCheckedChangeListener
                KeyboardPreferences.setThemeMode(this@KeyboardSettingsActivity, mode)
                Log.d(TAG, "Thème du clavier : ${mode.cle}")
            }
        }
    }

    private fun espacement(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(16)
        )
    }

    private fun carte(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.WHITE)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun titreSection(texte: String): TextView = TextView(this).apply {
        text = texte
        textSize = 17f
        setTextColor(Color.parseColor(BLEU))
        setTypeface(null, Typeface.BOLD)
        setPadding(0, 0, 0, dp(4))
    }

    private fun explication(texte: String): TextView = TextView(this).apply {
        text = texte
        textSize = 14f
        setTextColor(Color.parseColor(ENCRE_DOUCE))
        setLineSpacing(0f, 1.3f)
        setPadding(0, dp(4), 0, dp(4))
    }

    /**
     * Une ligne de réglage avec son interrupteur.
     *
     * Les couleurs sont posées explicitement : les états non cochés du thème sont un
     * gris presque blanc, invisible sur une carte blanche, ce qui avait déjà fait
     * disparaître des boutons radio d'un premier essai de cet écran.
     */
    private fun interrupteur(
        libelle: String,
        actifAuDepart: Boolean,
        onChange: (Boolean) -> Unit
    ): View = Switch(this).apply {
        text = libelle
        textSize = 16f
        setTextColor(Color.parseColor(ENCRE))
        isChecked = actifAuDepart
        setPadding(0, dp(14), 0, dp(14))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val etats = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        thumbTintList = ColorStateList(
            etats, intArrayOf(Color.parseColor(BLEU), Color.parseColor("#BDBDBD"))
        )
        trackTintList = ColorStateList(
            etats, intArrayOf(Color.parseColor("#90CAF9"), Color.parseColor("#757575"))
        )
        setOnCheckedChangeListener { _, coche ->
            onChange(coche)
            Log.d(TAG, "Réglage « $libelle » : $coche")
        }
    }

    private fun dp(valeur: Int): Int = (valeur * resources.displayMetrics.density).toInt()
}
