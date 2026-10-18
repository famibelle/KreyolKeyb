package com.example.kreyolkeyboard

import android.content.Context

/**
 * Réglages de comportement du clavier, partagés entre l'écran de l'application
 * (SettingsActivity) et le service de saisie (KreyolInputMethodServiceRefactored).
 *
 * Le service et l'activité vivent dans le même processus (aucun `android:process`
 * dans le manifeste), donc l'instance de SharedPreferences est la même des deux
 * côtés : une écriture depuis l'écran de réglages est visible immédiatement, sans
 * MODE_MULTI_PROCESS ni diffusion.
 *
 * ### Pourquoi ces réglages existent
 *
 * La v10.11.5 avait retiré `FLAG_IGNORE_GLOBAL_SETTING` du retour haptique pour que
 * le clavier obéisse au téléphone, et n'avait ajouté aucun réglage : le système
 * semblait déjà offrir l'interrupteur. C'était faux en pratique, constaté sur un
 * Samsung réel en 10.11.6 : **sur One UI, « Vibration au toucher » ne pilote que le
 * clavier Samsung**, et aucun réglage accessible ne couvre les claviers tiers. Le
 * clavier était donc devenu muet, sans aucun moyen de le rallumer.
 *
 * D'où ce retour en arrière assumé : le clavier reprend la main sur son retour de
 * frappe, comme le font Gboard et SwiftKey, qui ont eux aussi leurs propres
 * interrupteurs et ne dépendent pas du réglage générique du téléphone. Ces deux
 * réglages ne dupliquent donc pas un interrupteur du système, ils remplacent un
 * interrupteur qui n'existe pas.
 *
 * Ils restent indispensables : sans eux, forcer la vibration redeviendrait le
 * blocage sans échappatoire décrit au point 2 de ACCESSIBILITE.md.
 */
object KeyboardPreferences {

    private const val PREFS_NAME = "kreyol_clavier_prefs"
    private const val KEY_HAPTIC_ENABLED = "haptic_enabled"
    private const val KEY_SOUND_ENABLED = "sound_enabled"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_RESTORE_ACCENTS = "restore_accents"
    private const val KEY_DOUBLE_SPACE_PERIOD = "double_space_period"
    private const val KEY_DROP_AUTO_SPACE = "drop_auto_space"
    private const val KEY_LONG_PRESS_DELAY = "long_press_delay_ms"

    /** Les deux retours sont actifs par défaut, comme sur les autres claviers. */
    private const val DEFAULT_ENABLED = true

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hapticEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HAPTIC_ENABLED, DEFAULT_ENABLED)

    fun soundEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SOUND_ENABLED, DEFAULT_ENABLED)

    fun setHapticEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HAPTIC_ENABLED, enabled).apply()
        KeyFeedback.refresh(context)
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
        KeyFeedback.refresh(context)
    }

    /**
     * Les trois aides à l'écriture (voir [AidesEcriture]). Toutes actives par
     * défaut, comme sur les autres claviers, et toutes désactivables : elles
     * touchent à ce que l'utilisateur a tapé, ce qui se doit d'être un choix.
     *
     * La restauration des accents est la seule qui réécrive un mot. Elle est
     * bornée (jamais un autre mot, voir [AccentRestoration]) et un retour
     * arrière juste après elle rend le mot tel que tapé.
     */
    fun restoreAccents(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RESTORE_ACCENTS, DEFAULT_ENABLED)

    fun doubleSpacePeriod(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DOUBLE_SPACE_PERIOD, DEFAULT_ENABLED)

    fun dropAutoSpace(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DROP_AUTO_SPACE, DEFAULT_ENABLED)

    fun setRestoreAccents(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_RESTORE_ACCENTS, enabled).apply()
    }

    fun setDoubleSpacePeriod(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DOUBLE_SPACE_PERIOD, enabled).apply()
    }

    fun setDropAutoSpace(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DROP_AUTO_SPACE, enabled).apply()
    }

    /**
     * Thème du clavier. Par défaut il suit le téléphone.
     *
     * Un choix explicite plutôt que le seul suivi du système, pour la même raison
     * que les deux interrupteurs ci-dessus : sur plusieurs surcouches, le réglage
     * jour/nuit du téléphone ne descend pas jusqu'aux claviers tiers, et
     * l'utilisateur se retrouverait sans moyen d'obtenir le clavier qu'il veut.
     * Gboard et SwiftKey proposent également les trois positions.
     */
    fun themeMode(context: Context): KeyboardTheme.Mode =
        KeyboardTheme.Mode.depuisCle(prefs(context).getString(KEY_THEME_MODE, null))

    fun setThemeMode(context: Context, mode: KeyboardTheme.Mode) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode.cle).apply()
        KeyboardTheme.refresh(context)
    }

    /**
     * Délai avant que l'appui long n'ouvre la popup d'accents d'une touche.
     *
     * Jusqu'à la 22.5.1, ce délai était fixe et s'ajoutait à celui du système :
     * le long-clic natif d'Android (400 ms par défaut, jusqu'à 1,5 s selon le
     * réglage d'accessibilité du téléphone) puis 500 ms d'AccentHandler, soit
     * près d'une seconde avant de voir « ò ». Des utilisateurs de LuxKeyb l'ont trouvé trop
     * long, et Gboard tourne autour de 300 ms. Le délai part désormais du
     * moment où le doigt se pose, et un seul.
     *
     * Les crans longs restent pour qui relâche lentement (ACCESSIBILITE.md,
     * point 4) : une popup ouverte sans le vouloir coûte un appui de plus.
     */
    enum class DelaiAppuiLong(val ms: Long, val libelle: String) {
        COURT(300L, "Court (0,3 s)"),
        MOYEN(500L, "Moyen (0,5 s)"),
        LONG(800L, "Long (0,8 s)"),
        TRES_LONG(1200L, "Très long (1,2 s)");

        companion object {
            val DEFAUT = COURT

            /** Tolérante, comme [KeyboardTheme.Mode.depuisCle] : une valeur
             *  inconnue retombe sur le défaut plutôt que de jeter. */
            fun depuisMs(ms: Long?): DelaiAppuiLong =
                entries.firstOrNull { it.ms == ms } ?: DEFAUT
        }
    }

    fun delaiAppuiLong(context: Context): DelaiAppuiLong =
        DelaiAppuiLong.depuisMs(prefs(context).getLong(KEY_LONG_PRESS_DELAY, DelaiAppuiLong.DEFAUT.ms))

    fun setDelaiAppuiLong(context: Context, delai: DelaiAppuiLong) {
        prefs(context).edit().putLong(KEY_LONG_PRESS_DELAY, delai.ms).apply()
    }
}
