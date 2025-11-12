package com.example.kreyolkeyboard

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.kreyolkeyboard.gamification.VocabularyStatsActivity
import com.example.kreyolkeyboard.wordsearch.WordSearchActivity

/**
 * Activité principale avec navigation par onglets
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_tabs)
        
        // Configuration de la barre d'action
        supportActionBar?.apply {
            title = "Klavyé Kréyòl Karukera"
            subtitle = "🇬🇵 Potomitan™"
        }
        
        setupTabs()
    }
    
    private fun setupTabs() {
        // Onglet Clavier (principal)
        findViewById<Button>(R.id.btnTabKeyboard)?.setOnClickListener {
            // Rester sur l'écran actuel (clavier)
            // Ici on pourrait afficher des instructions ou des paramètres
        }
        
        // Onglet Statistiques
        findViewById<Button>(R.id.btnTabStats)?.setOnClickListener {
            startActivity(Intent(this, VocabularyStatsActivity::class.java))
        }
        
        // Onglet Mots Mêlés
        findViewById<Button>(R.id.btnTabWordSearch)?.setOnClickListener {
            startActivity(Intent(this, WordSearchActivity::class.java))
        }
        
        // Onglet Paramètres
        findViewById<Button>(R.id.btnTabSettings)?.setOnClickListener {
            openKeyboardSettings()
        }
    }
    
    private fun openKeyboardSettings() {
        try {
            // Ouvrir les paramètres de méthodes de saisie
            val intent = Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback : paramètres généraux
            val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
            startActivity(intent)
        }
    }
}