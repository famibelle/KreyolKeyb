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
        // Bord à bord sous Android 15 : le bandeau bleu passe sous la barre d'état.
        BordABord.appliquer(
            racine, haut = racine.getChildAt(0),
            lateraux = { listOf(racine.getChildAt(0), racine.getChildAt(1)) }
        )
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

        // Sur tablette seulement : un téléphone couché n'a pas la largeur d'un
        // clavier coupé en deux.
        if (KeyboardLayoutManager.isTablet(this@KeyboardSettingsActivity)) {
            addView(carte().apply {
                addView(titreSection("Disposition"))
                addView(interrupteur(
                    "Clavier scindé en paysage",
                    KeyboardPreferences.clavierScinde(this@KeyboardSettingsActivity)
                ) { actif ->
                    KeyboardPreferences.setClavierScinde(this@KeyboardSettingsActivity, actif)
                })
                addView(explication(
                    "Sur une tablette tenue à deux mains, les touches se partagent entre " +
                            "les deux bords, à portée des pouces. Tablettes seulement, tenues couchées."
                ))
            })
            addView(espacement())
        }

        addView(carte().apply {
            addView(titreSection("Suggestions"))
            addView(explication(
                "Les mots kréyòl sont proposés en vert. À partir de trois " +
                        "lettres, une seconde rangée en bleu propose aussi des mots français."
            ))
            addView(interrupteur(
                "Propositions en français",
                KeyboardPreferences.propositionsFrancais(this@KeyboardSettingsActivity)
            ) { actif ->
                KeyboardPreferences.setPropositionsFrancais(this@KeyboardSettingsActivity, actif)
                Log.d(TAG, "Propositions en français : $actif")
            })
            addView(explication(
                "Sans elles, le clavier garde une seule rangée de suggestions et " +
                        "laisse un peu plus de place à l'écran. Le correcteur " +
                        "orthographique continue de reconnaître le français."
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
            addView(titreSection("Écriture"))
            addView(explication(
                "Trois aides qui touchent à ce que vous tapez. Elles ne servent que " +
                        "dans les champs de texte courant, jamais dans un mot de passe, un " +
                        "nombre ou une adresse."
            ))
            addView(interrupteur(
                "Rétablir les accents",
                KeyboardPreferences.restoreAccents(this@KeyboardSettingsActivity)
            ) { actif ->
                KeyboardPreferences.setRestoreAccents(this@KeyboardSettingsActivity, actif)
            })
            addView(explication(
                "Un mot tapé sans accent (« pale ») reçoit ses accents (« palé ») quand " +
                        "vous le validez, à condition que le dictionnaire n'en connaisse qu'une " +
                        "graphie. Le clavier ne remplace jamais un mot par un autre. Un retour " +
                        "arrière juste après rend le mot tel que vous l'aviez tapé."
            ))
            addView(interrupteur(
                "Deux espaces posent un point",
                KeyboardPreferences.doubleSpacePeriod(this@KeyboardSettingsActivity)
            ) { actif ->
                KeyboardPreferences.setDoubleSpacePeriod(this@KeyboardSettingsActivity, actif)
            })
            addView(interrupteur(
                "Ponctuation collée au mot",
                KeyboardPreferences.dropAutoSpace(this@KeyboardSettingsActivity)
            ) { actif ->
                KeyboardPreferences.setDropAutoSpace(this@KeyboardSettingsActivity, actif)
            })
            addView(explication(
                "Après une suggestion, le clavier pose un espace. Une virgule ou un " +
                        "point tapé aussitôt le retire, pour que « bonjou » ne devienne pas " +
                        "« bonjou , ». Les points d'interrogation et d'exclamation gardent " +
                        "l'espace que vous tapez."
            ))
        })
        addView(espacement())

        addView(carte().apply {
            addView(titreSection("Appui long"))
            addView(explication(
                "Le temps qu'il faut maintenir une touche pour ouvrir ses accents " +
                        "et ses symboles (à sous a, ò sous o, ? sous le point…)."
            ))
            addView(choixDelaiAppuiLong())
            addView(explication(
                "Si les accents s'ouvrent alors que vous vouliez seulement taper la " +
                        "lettre, choisissez un délai plus long."
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
     */
    private fun choixTheme(): View = groupeRadio(
        options = KeyboardTheme.Mode.entries,
        actuel = KeyboardPreferences.themeMode(this),
        libelle = { it.libelle }
    ) { mode ->
        KeyboardPreferences.setThemeMode(this, mode)
        Log.d(TAG, "Thème du clavier : ${mode.cle}")
    }

    /**
     * Les crans du délai d'appui long (v23.0.0, repris de LuxKeyb 29.5.0), en boutons radio comme le
     * thème : quatre durées nommées se lisent mieux qu'un curseur, et chacune
     * se retrouve à l'identique d'un téléphone à l'autre.
     */
    private fun choixDelaiAppuiLong(): View = groupeRadio(
        options = KeyboardPreferences.DelaiAppuiLong.entries,
        actuel = KeyboardPreferences.delaiAppuiLong(this),
        libelle = { it.libelle }
    ) { delai ->
        KeyboardPreferences.setDelaiAppuiLong(this, delai)
        Log.d(TAG, "Délai d'appui long : ${delai.ms} ms")
    }

    /**
     * Un choix exclusif parmi [options], en boutons radio verticaux.
     *
     * Les couleurs sont posées à la main pour la même raison que sur les
     * interrupteurs voisins : l'état non coché du thème est un gris presque blanc,
     * invisible sur une carte blanche.
     */
    private fun <T : Any> groupeRadio(
        options: List<T>,
        actuel: T,
        libelle: (T) -> String,
        onChoix: (T) -> Unit
    ): View = RadioGroup(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        options.forEach { option ->
            addView(RadioButton(this@KeyboardSettingsActivity).apply {
                id = View.generateViewId()
                text = libelle(option)
                textSize = 16f
                setTextColor(Color.parseColor(ENCRE))
                isChecked = option == actuel
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
            val rang = groupe.indexOfChild(groupe.findViewById<View>(idCoche))
            options.getOrNull(rang)?.let(onChoix)
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
