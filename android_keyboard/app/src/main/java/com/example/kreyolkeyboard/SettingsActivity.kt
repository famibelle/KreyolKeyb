package com.example.kreyolkeyboard

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d("SettingsActivity", "Création de l'activité principale Kreyòl Karukera")
        
        // Créer un ScrollView pour éviter les problèmes d'affichage
        val scrollView = ScrollView(this)
        
        // Layout principal avec le design Guadeloupe
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 32)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
        
        // En-tête avec design Guadeloupe
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 32)
            setBackgroundColor(Color.parseColor("#0080FF")) // Bleu Caraïbe
        }
        
        val logoImage = ImageView(this).apply {
            setImageResource(R.drawable.logo_potomitan)
            layoutParams = LinearLayout.LayoutParams(200, 80) // Taille adaptée
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(0, 0, 0, 8)
        }
        
        val appTitle = TextView(this).apply {
            text = "Klavyé Kreyòl Karukera 🇬🇵"
            textSize = 28f
            setTextColor(Color.parseColor("#F8F8FF")) // Blanc Coral
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }
        
        headerLayout.addView(logoImage)
        headerLayout.addView(appTitle)
        
        // Description principale - Mission claire
        val descriptionCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.WHITE)
        }
        
        val missionTitle = TextView(this).apply {
            text = "🌟 Notre Mission"
            textSize = 20f
            setTextColor(Color.parseColor("#0080FF")) // Bleu Caraïbe
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        
        val missionText = TextView(this).apply {
            text = "Ce clavier a été spécialement conçu pour préserver et promouvoir le Kreyòl Guadeloupéen (Karukera). Il met à disposition de tous un outil moderne pour écrire dans notre belle langue créole avec :\n\n" +
                    "💡 Suggestions de mots en Kreyòl\n" +
                    "🔢 Mode numérique intégré\n" +
                    "🌈 Design aux couleurs de la Guadeloupe\n" +
                    "🇬🇵 Identité guadeloupéenne forte"
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            setLineSpacing(0f, 1.2f)
        }
        
        descriptionCard.addView(missionTitle)
        descriptionCard.addView(missionText)
        
        // Section Sources littéraires créoles
        val sourcesCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#F0F8E8")) // Vert très pâle
        }
        
        val sourcesTitle = TextView(this).apply {
            text = "📚 Sources littéraires créoles"
            textSize = 18f
            setTextColor(Color.parseColor("#228B22")) // Vert Canne
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        
        val sourcesText = TextView(this).apply {
            text = "Les suggestions de mots en kreyòl sont construites sur les travaux des défenseurs du kreyòl :\n\n" +
                    "✍️ Sylviane Telchid, Sonny Rupaire, Robert Fontes, Max Rippon, Alain Rutil, Alain Vérin, Katel, Esnard Boisdur, Pierre Édouard Décimus,\n\n" +
                    "Grâce à leur riche contributions, ce clavier vous propose des suggestions authentiques et fidèles à notre créole guadeloupéen."
            textSize = 14f
            setTextColor(Color.parseColor("#2F5233")) // Vert foncé
            setLineSpacing(0f, 1.3f)
        }
        
        sourcesCard.addView(sourcesTitle)
        sourcesCard.addView(sourcesText)
        
        // Instructions d'installation
        val installCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#E8F4FD"))
        }
        
        val installTitle = TextView(this).apply {
            text = "📱 Comment activer le clavier ?"
            textSize = 18f
            setTextColor(Color.parseColor("#0080FF")) // Bleu Caraïbe
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        
        val installSteps = TextView(this).apply {
            text = "1️⃣ Appuyez sur 'Activer le clavier' ci-dessous\n" +
                    "2️⃣ Dans les paramètres, activez 'Klavyé Kreyòl Karukera'\n" +
                    "3️⃣ Revenez ici et testez le clavier\n" +
                    "4️⃣ Changez de clavier en appuyant sur l'icône clavier dans la barre de notifications"
            textSize = 15f
            setTextColor(Color.parseColor("#444444"))
            setLineSpacing(0f, 1.3f)
        }
        
        installCard.addView(installTitle)
        installCard.addView(installSteps)
        
        // Boutons d'action
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
        }
        
        val activateButton = Button(this).apply {
            text = "🔧 Activer le clavier dans les paramètres"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#0080FF")) // Bleu Caraïbe
            setTextColor(Color.parseColor("#F8F8FF")) // Blanc Coral
            setPadding(20, 16, 20, 16)
            setOnClickListener {
                val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                startActivity(intent)
            }
        }
        
        val testTitle = TextView(this).apply {
            text = "✍️ Zone de test du clavier"
            textSize = 18f
            setTextColor(Color.parseColor("#0080FF")) // Bleu Caraïbe
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 12)
        }
        
        val testDescription = TextView(this).apply {
            text = "Tapez dans le champ ci-dessous pour tester le clavier Kreyòl :"
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        }
        
        val testEditText = EditText(this).apply {
            hint = "Ékri an kreyòl la... (Écrivez en créole...)"
            textSize = 16f
            setPadding(16, 16, 16, 16)
            minHeight = 120
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.parseColor("#1C1C1C")) // Noir volcanique pour le texte
            setHintTextColor(Color.parseColor("#999999")) // Gris pour le hint
            // Ajouter une bordure subtile avec padding
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(8, 8, 8, 8)
            this.layoutParams = layoutParams
        }
        
        val switchButton = Button(this).apply {
            text = "🔄 Basculer vers Klavyé Kreyòl"
            textSize = 14f
            setBackgroundColor(Color.parseColor("#228B22")) // Vert Canne
            setTextColor(Color.parseColor("#F8F8FF")) // Blanc Coral
            setPadding(16, 12, 16, 12)
            setOnClickListener {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }
        
        // Footer avec informations patrimoniales
        val footerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#F8F9FA"))
        }
        
        val footerText = TextView(this).apply {
            text = "🏝️ Fait avec ❤️ pour la Guadeloupe\n" +
                    "Préservons notre langue créole pour les générations futures !\n\n" +
                    "© Potomitan™ - Clavier Kreyòl Karukera\n" +
                    "Design aux couleurs authentiques de nos îles"
            textSize = 12f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.2f)
        }
        
        footerCard.addView(footerText)
        
        // Assembler tous les éléments
        buttonLayout.addView(activateButton)
        buttonLayout.addView(testTitle)
        buttonLayout.addView(testDescription)
        buttonLayout.addView(testEditText)
        buttonLayout.addView(switchButton)
        
        mainLayout.addView(headerLayout)
        mainLayout.addView(descriptionCard)
        mainLayout.addView(sourcesCard)
        mainLayout.addView(installCard)
        mainLayout.addView(buttonLayout)
        mainLayout.addView(footerCard)
        
        scrollView.addView(mainLayout)
        setContentView(scrollView)
        
        Log.d("SettingsActivity", "Interface Kreyòl Karukera créée avec succès")
    }
}
