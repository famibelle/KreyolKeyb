package com.example.kreyolkeyboard

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import com.example.kreyolkeyboard.gamification.CreoleLevels
import com.example.kreyolkeyboard.gamification.LevelUpNotifier
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.CountDownTimer
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlin.random.Random
import com.example.kreyolkeyboard.cloze.ClozeData
import com.example.kreyolkeyboard.cloze.ClozeDifficulty
import com.example.kreyolkeyboard.cloze.ClozeQuestion
import com.example.kreyolkeyboard.wordsearch.WordSearchGenerator
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.example.kreyolkeyboard.wordsearch.WordSearchPuzzle
import com.example.kreyolkeyboard.wordsearch.WordSearchWord
import com.example.kreyolkeyboard.wordsearch.WordSearchDifficulty
import com.example.kreyolkeyboard.wordsearch.WordSearchThemes
import com.example.kreyolkeyboard.wordsearch.WordSearchGridAdapter
import com.example.kreyolkeyboard.mokarenaj.MoKarenajData
import com.example.kreyolkeyboard.mokarenaj.MoKarenajRow
import com.example.kreyolkeyboard.mokarenaj.LetterState
import com.example.kreyolkeyboard.mokarenaj.color
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.content.FileProvider
import java.io.FileOutputStream
import android.widget.Toast
import android.widget.GridView
import android.widget.ScrollView

class SettingsActivity : AppCompatActivity() {
    private var currentTab = 0 // 0 = démarrage, 1 = stats, 2 = mots mêlés, 3 = mots mélangés, 4 = worldle, 5 = guide, 6 = à propos
    private lateinit var viewPager: ViewPager2
    private lateinit var tabBar: LinearLayout
    private lateinit var bottomInstallBanner: LinearLayout

    /**
     * Étape dépliée dans la carte « Configuration rapide » : null laisse
     * l'ouverture automatique décider (l'étape qui reste à faire), -1 signifie
     * que l'utilisateur les a toutes repliées.
     */
    private var etapeConfigOuverte: Int? = null

    /** Les 3 lignes restent-elles visibles une fois la configuration terminée ? */
    private var detailsConfigDeplies = false

    /**
     * Présence de la pastille de niveau dans la barre d'onglets telle qu'elle
     * est actuellement dessinée, à distinguer de l'état enregistré dans les
     * préférences. Le service de saisie pose la pastille pendant que
     * l'application est en arrière-plan : sans ce repère, [onResume] ne saurait
     * pas si la barre affichée est à jour.
     */
    private var levelBadgeDrawn = false
    
    // 🔧 FIX CRITIQUE: Scope lié au lifecycle de l'activité
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    companion object {
        const val PRIVACY_POLICY_URL = "https://famibelle.github.io/KreyolKeyb/privacy/privacy-policy.html"

        /** Onglet à ouvrir au démarrage, quand l'activité est lancée depuis le clavier. */
        const val EXTRA_OPEN_TAB = "open_tab"
        const val TAB_STATS = 1
        const val TAB_JEUX = 2
        const val TAB_DICO = 3

        /** Code de la demande de permission POST_NOTIFICATIONS (pastille de niveau). */
        private const val REQUEST_NOTIFICATIONS = 4201

        /**
         * Posé par le service de saisie au franchissement d'un palier, effacé
         * quand l'utilisateur affiche enfin ses statistiques. Sert la pastille
         * de la barre d'onglets. Écrit aussi dans KreyolInputMethodServiceRefactored.
         */
        const val PREF_LEVEL_BADGE_PENDING = "level_badge_pending"

        /**
         * Mot-dièse commun à tous les partages sortants, pour que les messages
         * envoyés depuis l'application se retrouvent entre eux sur les réseaux.
         *
         * Toujours placé en dernier, après une ligne vide : collé juste derrière
         * le lien Play Store, certains clients l'aspireraient dans l'URL.
         * Le message inséré par la puce du clavier fait exception, il tient sur
         * une seule ligne au milieu de ce que l'utilisateur écrit.
         */
        const val SHARE_HASHTAG = "#KlavyéKréyòl"

        // Astuces de la carte « Astuce de la semaine ». Chaque entrée décrit
        // une fonctionnalité réellement présente dans l'application : ne rien y
        // ajouter qui ne soit pas vérifiable dans le clavier ou les onglets.
        // ASTUCES.md donne, pour chacune, le code qui la justifie ; toute
        // astuce ajoutée ici doit y être sourcée, et toute astuce dont la
        // source disparaît doit être retirée des deux côtés.
        // Les thèmes (saisie, suggestions, jeux, progression, correcteur) sont
        // volontairement entrelacés : l'index avance d'un cran par semaine,
        // donc deux astuces voisines dans la liste se suivent à l'écran.
        private val WEEKLY_TIPS = listOf(
            "Appuyez longuement sur une lettre pour accéder aux accents et caractères spéciaux (é, è, à, ò, etc.). Glissez le doigt vers celui que vous voulez, puis relâchez.",
            "Touchez un mot de la barre de suggestions pour le compléter d'un coup : l'espace est ajouté automatiquement.",
            "Appui long d'une seconde sur la barre d'espace (le petit 🌐) : vous basculez vers un autre clavier sans quitter votre message.",
            "Chaque mot que vous tapez fait progresser votre niveau dans l'onglet « Kréyòl an mwen ».",
            "Les petits accents affichés dans le coin d'une touche annoncent ce que cache son appui long.",
            "Tapez sans vous soucier des accents : « kreyol » vous propose quand même « kréyòl ».",
            "« é » et « è » ont leur propre touche en bas du clavier, et « ò » la sienne en haut, entre « o » et « p ».",
            "Mo an Karénaj : un mot kréyòl de 5 lettres à deviner en 6 essais. Vert, la lettre est bien placée ; jaune, elle est dans le mot mais ailleurs.",
            "La touche majuscule a trois états : un appui pour une seule majuscule, deux pour le verrouillage, trois pour revenir au normal.",
            "Une lettre oubliée, en trop ou tapée à côté n'empêche pas les suggestions d'arriver : le clavier tolère les fautes de frappe.",
            "Appui long sur la virgule : point-virgule, deux-points, apostrophe. Appui long sur le point : point d'exclamation, point d'interrogation, points de suspension.",
            "Activez le correcteur kréyòl (onglet Démarrage, étape 4) pour que vos mots créoles ne soient plus soulignés en rouge dans Messages ou Notes.",
            "Le bouton « 123 » ouvre les chiffres et les symboles, euro compris. Le bouton « ABC » ramène aux lettres.",
            "Après un espace, le clavier vous propose la suite probable de votre phrase, d'après les deux mots que vous venez d'écrire.",
            "La touche emoji, en bas à droite, ouvre un panneau de près de 1900 emojis classés par catégories.",
            "Plus vous employez un mot, plus il remonte dans vos suggestions : le clavier s'ajuste à votre façon d'écrire.",
            "« Mots Mélangés », dans l'onglet « Jé », vous donne 10 mots à remettre dans l'ordre contre la montre, avec un bouton « Indice » quand vous bloquez.",
            "Appuyez longuement sur un emoji représentant une personne pour choisir sa couleur de peau.",
            "La suggestion respecte votre casse : commencez le mot par une majuscule, elle arrive avec.",
            "L'onglet « Kréyòl an mwen » vous dit quelle part du dictionnaire kréyòl vous avez déjà employée.",
            "Les digraphes du kréyòl sont en appui long : « ch » sous le c, « dj » sous le d, « tj » sous le t, « ng » et « ny » sous le n.",
            "Le clavier fonctionne entièrement hors ligne : rien de ce que vous tapez ne quitte votre téléphone.",
            "« Mots Mêlés » : selon la difficulté choisie, les mots se cachent aussi en diagonale et à l'envers.",
            "Pour reprendre un mot déjà écrit, replacez simplement le curseur dedans : les suggestions repartent de ce mot.",
            "Un « Mot du jour » vous attend chaque jour en haut de l'onglet « Kréyòl an mwen ».",
            "Les mots kréyòl passent en premier dans les suggestions. Le français prend le relais à partir de 3 lettres si aucun mot créole ne correspond.",
            "La touche Entrée s'adapte au champ où vous écrivez : « Rechercher », « Envoyer », ou simplement un retour à la ligne.",
            "Le classement de vos mots les plus utilisés se trouve dans l'onglet « Kréyòl an mwen ».",
            "Le retour arrière efface un emoji en entier, couleur de peau comprise : plus de caractère cassé à la place.",
            "Sept niveaux jalonnent votre parcours, de Pipirit à Potomitan. Un huitième existe : à vous de le découvrir.",
            "Les suggestions s'appuient sur les textes de Sylviane Telchid, Sonny Rupaire, Max Rippon et d'autres voix du kréyòl, à retrouver dans « À propos », en bas de l'onglet Démarrage.",
            "La première lettre de chaque phrase prend automatiquement la majuscule, comme sur un clavier classique.",
            "Depuis « Kréyòl an mwen », partagez votre carte de niveau avec votre famille et vos amis.",
            "Le correcteur se choisit dans les réglages Android sous « Clavier », et non sous « Langues ». Le bouton de l'étape 4 vous y mène directement.",
            "Après une mise à jour de l'application, le correcteur peut rester muet jusqu'au redémarrage du téléphone : cela vient d'Android, pas du clavier.",
            "Le guide, en bas de l'onglet Démarrage, reprend toutes les étapes en images, suivies des questions fréquentes.",
            "« Fraz a twou » vous montre une vraie phrase kréyòl à laquelle il manque un mot : sur les quatre propositions, une seule est celle qu'a écrite l'auteur.",
            "L'onglet « Dictionnaire » cherche dans les deux sens : tapez « kaz » ou tapez « maison ». Les jeux, eux, vous disent maintenant ce que veut dire le mot qu'ils vous font chercher.",
            "Glissez le doigt le long de la barre d'espace pour promener le curseur lettre par lettre : plus besoin de viser entre deux caractères pour corriger un mot."
        )
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Restaurer l'onglet actif si l'activité a été recréée, ou honorer
        // l'onglet demandé par l'intent (puce de niveau tapée depuis le clavier).
        //
        // Gardé dans une variable locale en plus de currentTab : la mise en page
        // du ViewPager déclenche onPageSelected(), qui réécrit currentTab à 0
        // avant que le post{} plus bas ne le lise. Sans cette copie, l'onglet
        // demandé est systématiquement perdu entre les deux.
        val requestedTab = savedInstanceState?.getInt("currentTab", 0)
            ?: intent?.getIntExtra(EXTRA_OPEN_TAB, 0)
            ?: 0
        currentTab = requestedTab
        
        // Masquer la barre d'action (bandeau noir)
        supportActionBar?.hide()
        
        Log.d("SettingsActivity", "Création de l'activité principale Kréyòl Karukera")
        
        // Layout principal vertical : Tabs en haut, puis ViewPager
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }
        
        // Créer la barre d'onglets horizontale
        tabBar = createTabBar()
        
        // ViewPager2 pour le contenu avec navigation swipe
        viewPager = ViewPager2(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            adapter = SettingsPagerAdapter(this@SettingsActivity)
            
            // 🎨 Effet de swipe style Tinder
            setPageTransformer(TinderSwipeTransformer())
            
            // Callback pour synchroniser avec la barre d'onglets
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    // Calculer la position réelle (0, 1 ou 2) avec modulo
                    currentTab = position % SettingsPagerAdapter.REAL_COUNT
                    updateTabBar()
                    majBandeauInstallation()
                }
            })
            
            // 🔄 Démarrer au milieu de la plage virtuelle pour permettre le swipe dans les deux sens
            post {
                val startPosition = SettingsPagerAdapter.START_POSITION - (SettingsPagerAdapter.START_POSITION % SettingsPagerAdapter.REAL_COUNT) + requestedTab
                setCurrentItem(startPosition, false)
            }
        }
        
        mainLayout.addView(tabBar)
        mainLayout.addView(viewPager)

        // FrameLayout racine : mainLayout en plein écran + bandeau d'installation
        // superposé, ancré en bas, visible dès l'onboarding (indépendant du scroll
        // du contenu en dessous)
        bottomInstallBanner = createBottomInstallBanner()
        val rootLayout = FrameLayout(this).apply {
            addView(mainLayout, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            addView(bottomInstallBanner, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.BOTTOM })
        }

        setContentView(rootLayout)

        recordFunnelStep("funnel_first_open")
        applyFirstRunMode()

        Log.d("SettingsActivity", "Interface avec tabs en haut et swipe cyclique créée avec succès")

        maybeAskForReview()
        maybeAskForNotificationPermission()
        maybeOfferUpdate()
    }

    // ────────────────────────────────────────────────────────────────────
    // Mise à jour depuis l'application
    // ────────────────────────────────────────────────────────────────────

    /**
     * Le gestionnaire de mise à jour de Play, créé à la demande.
     *
     * Il n'existe que pour cet écran : un clavier s'utilise sans jamais ouvrir
     * son application, et le service de saisie n'a ni activité ni droit
     * d'interrompre quelqu'un en train d'écrire. C'est donc ici, et nulle part
     * ailleurs, qu'une mise à jour peut être proposée.
     */
    private val updateManager: AppUpdateManager by lazy {
        AppUpdateManagerFactory.create(this)
    }

    /**
     * Suit l'avancement du téléchargement pour savoir quand proposer
     * l'installation. Retiré dans [onDestroy] : le gestionnaire garde une
     * référence forte sur l'écouteur, qui retiendrait l'activité entière.
     */
    private val updateListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) proposerInstallation()
    }

    /**
     * Le retour du dialogue de Play. On n'y fait rien d'autre que tracer :
     * un refus n'appelle aucune insistance, et une acceptation est déjà suivie
     * par [updateListener].
     */
    private val updateLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { resultat ->
        Log.d("SettingsActivity", "Retour du flux de mise à jour: ${resultat.resultCode}")
    }

    /**
     * Propose la mise à jour si Play en connaît une.
     *
     * En mode **FLEXIBLE** : le téléchargement se fait en fond, l'application
     * reste utilisable, et l'installation attend un geste. Le mode IMMEDIATE
     * aurait bloqué l'écran derrière une barre de progression, ce qui est
     * hostile pour quelqu'un venu changer le son des touches.
     *
     * Trois cas où il ne se passe rien, tous silencieux et tous normaux :
     * l'application est à jour ; Play ne répond pas ; ou l'application n'a pas
     * été installée depuis le Play Store. Ce dernier cas n'est pas
     * hypothétique ici : le projet publie ses APK sur GitHub à chaque version,
     * et l'API refuse alors de répondre. Une erreur visible ferait passer une
     * installation parfaitement valide pour un problème.
     */
    private fun maybeOfferUpdate() {
        try {
            updateManager.registerListener(updateListener)
            updateManager.appUpdateInfo.addOnSuccessListener { info ->
                val disponible =
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                if (!disponible) {
                    Log.d("SettingsActivity", "Pas de mise à jour à proposer")
                    return@addOnSuccessListener
                }
                Log.d("SettingsActivity", "Mise à jour disponible: code ${info.availableVersionCode()}")
                updateManager.startUpdateFlowForResult(
                    info,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                )
            }.addOnFailureListener { erreur ->
                Log.d("SettingsActivity", "Mise à jour indisponible: ${erreur.message}")
            }
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur de mise à jour: ${e.message}")
        }
    }

    /**
     * Invite à redémarrer l'application une fois la mise à jour téléchargée.
     *
     * Une Snackbar et non une boîte de dialogue : le téléchargement se termine
     * à un moment que la personne n'a pas choisi, souvent au milieu d'autre
     * chose. Elle reste affichée jusqu'à ce qu'on la touche ou qu'on la
     * balaie, parce qu'un message de quelques secondes se rate.
     */
    private fun proposerInstallation() {
        Snackbar.make(
            findViewById(android.R.id.content),
            "Mise à jour téléchargée",
            Snackbar.LENGTH_INDEFINITE
        ).setAction("Installer") { updateManager.completeUpdate() }.show()
    }

    /**
     * Demande la permission de notification, une seule fois, et seulement une
     * fois le clavier réellement configuré : avant cela l'utilisateur est en
     * pleine installation, et une demande de plus dans ce tunnel déjà long ne
     * serait qu'une occasion supplémentaire d'abandonner.
     *
     * Cette permission ne sert qu'à la pastille de passage de niveau. Refusée,
     * le clavier fonctionne exactement comme avant : voir [LevelUpNotifier],
     * qui ne publie rien sans elle. La demande vient de l'activité parce qu'un
     * service de saisie ne peut pas afficher de dialogue de permission.
     */
    private fun maybeAskForNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!isKeyboardEnabled() || !isKeyboardSelected()) return
        if (LevelUpNotifier.canNotify(this)) {
            LevelUpNotifier.ensureChannel(this)
            return
        }

        val prefs = getSharedPreferences("kreyol_onboarding_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("notification_permission_asked", false)) return
        prefs.edit().putBoolean("notification_permission_asked", true).apply()

        LevelUpNotifier.ensureChannel(this)
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
    }

    // Mode « première ouverture » : tant que le clavier n'a jamais été activé,
    // la barre d'onglets et le swipe sont masqués pour concentrer sur la
    // configuration. Le flag ne se pose qu'une fois : un utilisateur configuré
    // qui désélectionne plus tard le clavier garde l'accès à tout.
    //
    // Le mode restreint s'arrêtait auparavant à la configuration *complète* —
    // clavier activé **et** sélectionné comme clavier courant. C'était trop
    // tard : quelqu'un qui a activé le clavier puis essayé un autre, ou qu'une
    // mise à jour système a désélectionné, se retrouvait renvoyé dans le
    // tunnel, sans jeux ni statistiques, alors qu'il connaît déjà
    // l'application. Or ces onglets ne dépendent que des actifs livrés et
    // marchent sans clavier installé. La barre revient donc dès l'activation,
    // et ne repart plus.
    private fun onboardingPrefs() =
        getSharedPreferences("kreyol_onboarding_prefs", Context.MODE_PRIVATE)

    // Tunnel d'activation local : horodate chaque jalon du parcours
    // (première ouverture, activation, sélection, premier mot) une seule
    // fois, en SharedPreferences — diagnostic consultable dans À Propos,
    // rien ne quitte le téléphone, cohérent avec la politique « aucune
    // collecte » de l'app
    private fun recordFunnelStep(key: String) {
        val prefs = onboardingPrefs()
        if (!prefs.contains(key)) {
            prefs.edit().putLong(key, System.currentTimeMillis()).apply()
        }
    }

    private fun applyFirstRunMode() {
        if (onboardingPrefs().getBoolean("onboarding_completed", false)) return
        if (isKeyboardEnabled() && isKeyboardSelected()) {
            // Utilisateur déjà configuré (ex. mise à jour de l'app) :
            // poser le flag sans jamais montrer le mode restreint
            onboardingPrefs().edit().putBoolean("onboarding_completed", true).apply()
            return
        }
        if (!aDejaActiveLeClavier()) {
            tabBar.visibility = View.GONE
            viewPager.isUserInputEnabled = false
        }
        majBandeauInstallation()
    }

    /**
     * Le clavier a-t-il déjà été activé, maintenant ou par le passé ?
     *
     * `funnel_keyboard_enabled` est horodaté une seule fois, au premier passage
     * à l'état activé : il survit donc à une désactivation, à une mise à jour
     * système qui désélectionne le clavier, ou au détour par un autre clavier.
     */
    private fun aDejaActiveLeClavier(): Boolean =
        isKeyboardEnabled() || onboardingPrefs().contains("funnel_keyboard_enabled")

    /**
     * Rend la navigation dès l'activation, sans attendre la sélection.
     *
     * [onOnboardingCompleted] ne se déclenche qu'à la configuration complète ;
     * sans ce complément, quelqu'un qui active le clavier puis referme le
     * sélecteur système resterait enfermé jusqu'à sa prochaine ouverture de
     * l'application, alors que la condition d'accès est déjà remplie.
     */
    fun revelerNavigationSiClavierActive() {
        if (tabBar.visibility == View.VISIBLE) return
        if (!aDejaActiveLeClavier()) return
        tabBar.visibility = View.VISIBLE
        tabBar.alpha = 0f
        tabBar.animate().alpha(1f).setDuration(400).start()
        viewPager.isUserInputEnabled = true
    }

    /**
     * Le bandeau d'installation ne paraît que sur Démarrage, et seulement tant
     * que le clavier n'a jamais été configuré.
     *
     * Il était posé une fois pour toutes, ce qui suffisait quand Démarrage
     * était le seul onglet atteignable avant configuration. Maintenant que les
     * trois le sont, il se superposerait au bas de la grille des Mots Mêlés et
     * de la liste des statistiques — un rappel permanent qui mangerait le
     * contenu qu'il est censé faire découvrir.
     */
    private fun majBandeauInstallation() {
        if (!::bottomInstallBanner.isInitialized) return
        val aConfigurer = !onboardingPrefs().getBoolean("onboarding_completed", false)
        if (aConfigurer && currentTab == 0) {
            bottomInstallBanner.alpha = 1f
            bottomInstallBanner.visibility = View.VISIBLE
        } else {
            bottomInstallBanner.visibility = View.GONE
        }
    }

    // Appelé par l'onboarding quand la configuration vient d'aboutir :
    // pose le flag et révèle la navigation avec un léger fondu
    fun onOnboardingCompleted() {
        val prefs = onboardingPrefs()
        if (!prefs.getBoolean("onboarding_completed", false)) {
            prefs.edit().putBoolean("onboarding_completed", true).apply()
        }
        viewPager.isUserInputEnabled = true
        if (tabBar.visibility != View.VISIBLE) {
            tabBar.visibility = View.VISIBLE
            tabBar.alpha = 0f
            tabBar.animate().alpha(1f).setDuration(400).start()
        }
        if (bottomInstallBanner.visibility == View.VISIBLE) {
            bottomInstallBanner.animate().alpha(0f).setDuration(300)
                .withEndAction { bottomInstallBanner.visibility = View.GONE }
                .start()
        }
        // Le clavier d'essai (dictionnaires + moteur de suggestions chargés
        // dans le processus de l'app) n'a plus de raison d'exister une fois
        // la configuration terminée : la carte qui l'hébergeait disparaît
        // du wizard, mais l'objet restait sinon référencé indéfiniment
        demoKeyboardManager?.cleanup()
        demoKeyboardManager = null
        demoEngine = null
        demoEngineReady = false

        maybeShowActivationSuccessCard()
    }

    // Récompense l'utilisateur juste après un parcours d'activation identifié
    // comme un point de friction (interstitiel + réglages système) : un seul
    // affichage, jamais reposé même si l'onboarding se rejoue. Le message
    // proposé au partage est fixe, écrit avant que l'utilisateur ait tapé
    // quoi que ce soit avec le clavier — aucun contenu personnel n'est lu.
    private fun maybeShowActivationSuccessCard() {
        val prefs = onboardingPrefs()
        if (prefs.getBoolean("activation_success_card_shown", false)) return
        prefs.edit().putBoolean("activation_success_card_shown", true).apply()

        AlertDialog.Builder(this)
            .setTitle("🎉 Klavyé Kréyòl aktivé !")
            .setMessage("Bravo, ou fè'y ! Klavyé Kréyòl paré pou maké an kréyòl asi tout aplikasyon aw.")
            .setPositiveButton("Partager la nouvelle") { _, _ -> shareActivationSuccess() }
            .setNegativeButton("Plus tard", null)
            .setCancelable(true)
            .show()
    }

    // Partage natif (chooser Android), message pré-rédigé et fixe : célèbre
    // l'activation, pas un contenu écrit par l'utilisateur
    private fun shareActivationSuccess() {
        val message = "Mwen viré ka maké an kréyòl ! Mwen aktivé Klavyé Kréyòl Karukéra 🎉\n" +
            "Sé on klavyé Android gratui ki ba'w sigjesyon mo an kréyòl Gwadloup.\n\n" +
            "Télécharge-le gratuitement :\n" +
            "https://play.google.com/store/apps/details?id=$packageName" +
            "&referrer=utm_source%3Din_app_share%26utm_campaign%3Dactivation_success\n\n" +
            SHARE_HASHTAG
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
            startActivity(Intent.createChooser(intent, "Partager Klavyé Kréyòl Karukéra"))
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur partage activation: ${e.message}")
            Toast.makeText(this, "Impossible de partager pour le moment", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Demande d'avis Google Play (In-App Review), déclenchée seulement après
     * un vrai usage du clavier (flag posé par le service IME) et à partir de
     * la 2ᵉ ouverture de l'app — le moment où l'utilisateur revient de lui-même.
     * L'API Play limite elle-même la fréquence d'affichage ; on ne tente
     * qu'une fois pour ne pas consommer le quota inutilement.
     */
    private fun maybeAskForReview() {
        // Mêmes clés que dans KreyolInputMethodServiceRefactored
        val prefs = getSharedPreferences("kreyol_onboarding_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("first_real_use_tip_shown", false)) return
        if (prefs.getBoolean("review_flow_requested", false)) return

        val openCount = prefs.getInt("settings_open_count_after_use", 0) + 1
        prefs.edit().putInt("settings_open_count_after_use", openCount).apply()
        if (openCount < 2) return

        try {
            val manager = ReviewManagerFactory.create(this)
            manager.requestReviewFlow().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    prefs.edit().putBoolean("review_flow_requested", true).apply()
                    manager.launchReviewFlow(this, task.result)
                    Log.d("SettingsActivity", "Flux d'avis Google Play lancé")
                } else {
                    Log.d("SettingsActivity", "Flux d'avis indisponible: ${task.exception?.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur demande d'avis: ${e.message}")
        }
    }
    
    /**
     * La pastille de l'onglet statistiques n'était lue qu'au moment de
     * construire la barre d'onglets, donc uniquement au démarrage à froid.
     * Un palier franchi pendant que l'application dormait dans la pile des
     * tâches restait invisible au retour (constaté sur émulateur le
     * 08/08/2026) — et quand la permission de notification a été refusée,
     * cette pastille est le seul signal qui existe.
     */
    override fun onResume() {
        super.onResume()

        if (::tabBar.isInitialized && hasPendingLevelBadge() != levelBadgeDrawn) {
            Log.d("SettingsActivity", "🔄 Pastille de niveau à rafraîchir au retour au premier plan")
            updateTabBar()
        }

        // Une mise à jour peut avoir fini de se télécharger pendant que
        // l'application était en arrière-plan : l'écouteur a bien reçu
        // l'événement, mais la Snackbar est partie avec la vue. Sans cette
        // relance, la mise à jour reste téléchargée et jamais installée.
        try {
            updateManager.appUpdateInfo.addOnSuccessListener { info ->
                if (info.installStatus() == InstallStatus.DOWNLOADED) proposerInstallation()
            }
        } catch (e: Exception) {
            Log.d("SettingsActivity", "État de mise à jour illisible: ${e.message}")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Sauvegarder l'onglet actif avant que l'activité soit recréée
        outState.putInt("currentTab", currentTab)
        Log.d("SettingsActivity", "💾 Sauvegarde de l'onglet actif: $currentTab")
    }
    
    override fun onDestroy() {
        // 🔧 FIX CRITIQUE: Annuler toutes les coroutines de l'activité
        activityScope.cancel()

        try {
            updateManager.unregisterListener(updateListener)
        } catch (e: Exception) {
            Log.d("SettingsActivity", "Écouteur de mise à jour déjà retiré: ${e.message}")
        }
        Log.d("SettingsActivity", "✅ Coroutines de l'activité annulées proprement")
        
        super.onDestroy()
    }
    
    /**
     * 🔧 FIX CRITIQUE: Ajouter délai avant fermeture pour éviter "Consumer closed input channel"
     * Laisse le temps aux derniers événements tactiles d'être traités
     */
    override fun onBackPressed() {
        // Délai de 100ms pour traiter les événements en cours
        Handler(Looper.getMainLooper()).postDelayed({
            super.onBackPressed()
        }, 100)
    }
    
    // Bandeau d'installation ancré en bas, superposé au contenu de l'onboarding
    // (indépendant du scroll) : rappel visuel permanent tant que le clavier
    // n'est ni activé ni sélectionné. S'ajoute au CTA déjà présent dans la
    // carte de démo (createDemoKeyboardCard) sans le remplacer — même style
    // et même action pour rester cohérent.
    private fun createBottomInstallBanner(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#0080FF"))
            elevation = 12f
            setPadding(32, 28, 32, 28)
            setOnClickListener { showPreSettingsWarningDialog() }

            val label = TextView(this@SettingsActivity).apply {
                text = "Ça vous plaît ? Installez-le →"
                textSize = 15f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            addView(label)
        }
    }

    private fun createTabBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.WHITE)
            elevation = 4f // Ombre légère pour séparer du contenu
            
            // Bandeau bleu en haut
            val appHeader = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(Color.parseColor("#0080FF"))
                gravity = Gravity.CENTER
                setPadding(16, 16, 16, 16)
            }
            
            val appTitle = TextView(this@SettingsActivity).apply {
                text = "Klavyé Kréyòl"
                textSize = 22f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                // Poids 1 : le titre occupe la place laissée par l'engrenage et reste
                // centré sur le bandeau entier.
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }

            // Les réglages du clavier vivent derrière cet engrenage, dans leur propre
            // écran : c'est la convention Android, et la barre porte déjà sept onglets.
            // Icône vectorielle blanche, et non l'emoji ⚙️ : la police emoji du
            // système le dessine en gris bleuté, une teinte que le bandeau bleu
            // avale. Le tracé blanc ne dépend plus de la police du téléphone et
            // ressort de la même façon sur tous les appareils.
            val densite = resources.displayMetrics.density
            val settingsButton = ImageView(this@SettingsActivity).apply {
                setImageResource(R.drawable.ic_settings_gear)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = "Réglages du clavier"
                // 48 dp de zone tactile, icône dessinée à 26 dp au centre : c'est
                // l'encombrement qu'avait déjà le TextView, la hauteur du bandeau
                // ne bouge donc pas.
                val zone = (48 * densite).toInt()
                layoutParams = LinearLayout.LayoutParams(zone, zone)
                val marge = (11 * densite).toInt()
                setPadding(marge, marge, marge, marge)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    startActivity(Intent(this@SettingsActivity, KeyboardSettingsActivity::class.java))
                }
            }

            // Cale de la largeur de l'engrenage, à gauche : sans elle le titre,
            // centré dans la place restante, se décale visiblement vers la gauche.
            appHeader.addView(View(this@SettingsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (48 * densite).toInt(), 1
                )
            })
            appHeader.addView(appTitle)
            appHeader.addView(settingsButton)
            
            // Container pour les onglets
            val tabContainer = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                // Hauteur suivant le contenu, et non 140 px figés : l'emoji seul en
                // réclamait 165 (60 dp), donc le libellé de chaque onglet était rogné
                // hors de la vue et la barre n'identifiait sept destinations que par
                // des emojis nus.
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER
            }
            
            // Tab Démarrage
            val startTab = createTab(0, "🚀", "Démarrage")
            tabContainer.addView(startTab)
            Log.d("SettingsActivity", "Onglet Démarrage créé et ajouté")
            
            // Tab Statistiques  
            val statsTab = createTab(1, "📊", "Kréyòl an mwen")
            tabContainer.addView(statsTab)
            Log.d("SettingsActivity", "Onglet Statistiques créé et ajouté")
            
            // Tab Jé : les trois jeux derrière une seule destination. Ils
            // occupaient trois onglets sur sept, soit 43 % de la barre, pour
            // une activité que l'on choisit une fois par session.
            val gamesTab = createTab(TAB_JEUX, "🎮", "Jé")
            tabContainer.addView(gamesTab)
            Log.d("SettingsActivity", "Onglet Jé créé et ajouté")

            // Tab Dictionnaire : le seul onglet qui ne joue à rien. On y cherche
            // un mot, dans un sens ou dans l'autre, et on lit ce qu'il veut
            // dire. Libellé français faute de mieux : aucune des sources du
            // projet n'atteste de forme kréyòl pour « dictionnaire », et on
            // n'en invente pas.
            val dictionaryTab = createTab(TAB_DICO, "📚", "Dictionnaire")
            tabContainer.addView(dictionaryTab)
            Log.d("SettingsActivity", "Onglet Dictionnaire créé et ajouté")

            // Guide et À Propos ne sont plus des onglets : ce sont des pages de
            // référence que l'on lit une fois, pas des destinations
            // quotidiennes. Elles s'ouvrent depuis le pied de l'onglet
            // Démarrage, en plein écran (voir SheetFragment).

            // Ligne de séparation en bas (fine)
            val separator = View(this@SettingsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    2
                )
                setBackgroundColor(Color.parseColor("#E0E0E0"))
            }
            
            addView(appHeader)
            addView(tabContainer)
            addView(separator)
        }
    }
    
    /** Préférences partagées avec le service de saisie pour la progression. */
    private fun gamificationPrefs() =
        getSharedPreferences("kreyol_gamification_prefs", Context.MODE_PRIVATE)

    /** Un palier a-t-il été franchi sans que l'utilisateur ait rouvert ses statistiques ? */
    private fun hasPendingLevelBadge(): Boolean =
        gamificationPrefs().getBoolean(PREF_LEVEL_BADGE_PENDING, false)

    private fun createTab(tabIndex: Int, emoji: String, label: String): LinearLayout {
        Log.d("SettingsActivity", "Création onglet $tabIndex: $emoji $label")
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(8, 10, 8, 8)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
            // Background légèrement coloré si onglet actif
            setBackgroundColor(
                if (tabIndex == currentTab) 
                    Color.parseColor("#FFF5E6") // Beige clair orangé
                else 
                    Color.WHITE
            )
            
            // Emoji du tab
            val emojiView = TextView(this@SettingsActivity).apply {
                text = emoji
                textSize = 32f // Augmenté encore plus
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 2)
                // Emoji légèrement teinté si actif pour plus de cohérence visuelle
                alpha = if (tabIndex == currentTab) 1.0f else 0.6f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                // Plus de minHeight de 60 dp : il réservait à l'emoji seul plus de
                // hauteur que la barre n'en avait, ce qui chassait le libellé.
            }
            
            // Label du tab
            val labelView = TextView(this@SettingsActivity).apply {
                text = label
                // 11sp : quatre onglets se partagent la largeur au lieu de
                // sept. Vérifié à 360 dp : les quatre libellés tiennent encore
                // sur une ligne, « Kréyòl an mwen » compris. À police système
                // agrandie il passe sur deux lignes, ce que le garde-fou
                // ci-dessous autorise ; il n'est jamais rogné.
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 2)
                // Un libellé plus long que sa colonne doit passer à la ligne ou
                // se terminer en points de suspension, jamais repousser ses
                // voisins.
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(
                    if (tabIndex == currentTab) 
                        Color.parseColor("#FF8C00") 
                    else 
                        Color.GRAY
                )
                setTypeface(null, if (tabIndex == currentTab) Typeface.BOLD else Typeface.NORMAL)
            }
            
            // Pastille de niveau non vu : le franchissement d'un palier n'est
            // célébré qu'au dessin du contenu de l'onglet statistiques. Sans ce
            // repère, quelqu'un qui ouvre l'application et reste sur Démarrage
            // ne saurait jamais qu'il a quelque chose à y voir.
            if (tabIndex == TAB_STATS && hasPendingLevelBadge()) {
                levelBadgeDrawn = true
                addView(FrameLayout(this@SettingsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    addView(emojiView)
                    addView(View(this@SettingsActivity).apply {
                        val d = resources.displayMetrics.density
                        layoutParams = FrameLayout.LayoutParams(
                            (10 * d).toInt(), (10 * d).toInt()
                        ).apply { gravity = Gravity.TOP or Gravity.END }
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.parseColor("#FF8C00"))
                            setStroke((2 * d).toInt(), Color.WHITE)
                        }
                    })
                })
            } else {
                if (tabIndex == TAB_STATS) levelBadgeDrawn = false
                addView(emojiView)
            }
            addView(labelView)

            // Indicateur orange en bas si tab actif
            if (tabIndex == currentTab) {
                val indicator = View(this@SettingsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        60,
                        4
                    ).apply {
                        topMargin = 6
                    }
                    setBackgroundColor(Color.parseColor("#FF8C00"))
                }
                addView(indicator)
            }
            
            setOnClickListener { allerAOnglet(tabIndex) }
        }
    }
    
    /**
     * Reconstruit la barre après un changement d'onglet, pour que l'onglet actif
     * change d'aspect.
     *
     * Elle recopiait auparavant toute la construction de [createTabBar], et les deux
     * copies ont divergé : l'engrenage des réglages et la correction de hauteur des
     * onglets n'existaient que dans l'original, donc ne s'affichaient jamais, cette
     * fonction étant appelée dès le premier changement d'onglet. Une seule
     * construction fait désormais autorité.
     */
    private fun updateTabBar() {
        tabBar.removeAllViews()
        val fraiche = createTabBar()
        val enfants = (0 until fraiche.childCount).map { fraiche.getChildAt(it) }
        fraiche.removeAllViews() // une vue ne peut pas avoir deux parents
        enfants.forEach { tabBar.addView(it) }
    }

    // Onglet 1 : Démarrage / Onboarding
    fun createOnboardingContent(): LinearLayout {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 32)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val isEnabled = isKeyboardEnabled()
        val isSelected = isKeyboardSelected()
        // Distingue le tout premier setup d'un retour après désélection
        // (mise à jour système, changement de clavier...) : le ton et
        // l'habillage diffèrent, mais les étapes restent les mêmes
        val hasCompletedBefore = onboardingPrefs().getBoolean("onboarding_completed", false)

        // Toute reconstruction complète de l'onglet (retour au premier plan,
        // changement détecté dans les réglages système) rend la main à
        // l'ouverture automatique : c'est l'étape qui reste à faire qui doit
        // être dépliée, pas celle que l'utilisateur avait ouverte à la main
        // deux écrans plus tôt.
        etapeConfigOuverte = null

        // 🔍 Log pour déboguer l'état du clavier
        Log.d("SettingsActivity", "📋 État du clavier: isEnabled=$isEnabled, isSelected=$isSelected")

        // Jalons du tunnel d'activation (horodatés une seule fois)
        if (isEnabled) recordFunnelStep("funnel_keyboard_enabled")
        if (isSelected) recordFunnelStep("funnel_keyboard_selected")

        // Nudge « activation inachevée » : l'utilisateur est allé dans les
        // réglages mais le clavier n'est toujours pas activé — le cas le
        // plus fréquent est l'abandon au second des deux avertissements
        // système (qui annule silencieusement l'activation)
        val settingsVisitAt = onboardingPrefs().getLong("settings_visit_at", 0L)
        if (isEnabled && settingsVisitAt != 0L) {
            onboardingPrefs().edit().remove("settings_visit_at").apply()
        }
        val showIncompleteNudge = !isEnabled && settingsVisitAt != 0L
        if (showIncompleteNudge) recordFunnelStep("funnel_settings_return_no_enable")

        // Retour au clavier après un détour : l'utilisateur a déjà configuré
        // l'application, le clavier est toujours installé, mais ce n'est plus
        // lui qui s'ouvre. C'est l'état où l'on se retrouve après un appui
        // long sur la barre d'espace suivi d'un choix dans le sélecteur
        // système — et l'observation de terrain qui a motivé cette carte est
        // que personne n'en revient tout seul.
        //
        // Android interdit à un clavier de se remettre en service lui-même :
        // un IME peut demander à partir (showInputMethodPicker), jamais à
        // revenir. Une fois l'autre clavier actif, il n'y a plus rien à
        // chercher dans le clavier, et le seul chemin de retour passe par
        // l'application ou par la petite icône de la barre de navigation. La
        // carte est donc placée en tête, avant même la configuration rapide.
        if (hasCompletedBefore && isEnabled && !isSelected) {
            mainLayout.addView(createRetourClavierCard())
            mainLayout.addView(createSpacing(16))
        }

        // Bandeau de réussite : le clavier est utilisable dès qu'il est
        // activé et sélectionné, avant même que l'utilisateur ait écrit quoi
        // que ce soit. Son texte le dit alors sans prétendre que la
        // configuration est terminée, sinon il contredirait l'anneau 2/3.
        if (isEnabled && isSelected) {
            mainLayout.addView(createReadyBanner(aEcritUnMot()))
            mainLayout.addView(createSpacing(12))
        }

        // Carte de configuration, isolée dans son propre conteneur : déplier
        // une étape ne reconstruit qu'elle, et ne refait pas le clavier
        // d'essai (rechargement des dictionnaires, perte du texte déjà tapé
        // dedans).
        val conteneurConfig = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        remplirCarteConfig(conteneurConfig, isEnabled, isSelected, hasCompletedBefore, showIncompleteNudge)
        mainLayout.addView(conteneurConfig)
        mainLayout.addView(createSpacing(24))

        // Essai du clavier : un vrai clavier interactif avec suggestions
        // bilingues, à essayer sans rien installer. Il ouvrait l'onglet, pour
        // que la motivation précède la mécanique, mais il occupait alors toute
        // la hauteur visible et repoussait sous la ligne de flottaison le
        // bouton qui ouvre les réglages Android : l'action attendue de
        // l'utilisateur ne se voyait plus sans faire défiler. Il passe donc
        // sous la carte de configuration, où il reste la première chose que
        // l'on rencontre en descendant. Inutile pour un utilisateur qui
        // revient après une désélection : il connaît déjà.
        if ((!isEnabled || !isSelected) && !hasCompletedBefore) {
            mainLayout.addView(createDemoKeyboardCard())
            mainLayout.addView(createSpacing(24))
        }

        // Correcteur orthographique : fonctionnalité indépendante des 3 étapes
        // critiques (fonctionne même sans avoir activé le clavier Kréyòl),
        // sortie du parcours numéroté pour ne pas laisser croire à une
        // "étape 4" alors que la barre de progression annonce 3 étapes
        val extrasTitle = TextView(this).apply {
            text = "🚀 Pou ay pli lwen (optionnel)"
            textSize = 16f
            setTextColor(Color.parseColor("#666666"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        mainLayout.addView(extrasTitle)

        mainLayout.addView(createSpellCheckerCard())
        mainLayout.addView(createSpacing(24))

        // Bascule d'un clavier à l'autre : l'aller et le retour n'utilisent
        // pas le même geste (chemins vérifiés à l'émulateur), et c'est le
        // retour vers le kréyòl qui bloque les utilisateurs, l'appui long sur
        // la barre d'espace des autres claviers ne changeant que leur propre
        // langue. Affiché une fois la configuration terminée, au moment où la
        // question se pose vraiment.
        if (isEnabled && isSelected) {
            val switchCard = createRoundedCard("#E3F2FD")

            val switchTitle = TextView(this).apply {
                text = "🔄 Passer du français au kréyòl, et l'inverse"
                textSize = 18f
                setTextColor(Color.parseColor("#0D47A1"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 12)
            }

            val switchIntro = TextView(this).apply {
                text = "Klavyé Kréyòl ne remplace pas vos autres claviers : il s'ajoute à la " +
                        "liste, et vous passez de l'un à l'autre en deux secondes."
                textSize = 14f
                setTextColor(Color.parseColor("#1565C0"))
                setLineSpacing(0f, 1.3f)
                setPadding(0, 0, 0, 12)
            }

            val switchAwayTitle = TextView(this).apply {
                text = "➡️ Quitter le kréyòl"
                textSize = 15f
                setTextColor(Color.parseColor("#0D47A1"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 4)
            }

            val switchAway = TextView(this).apply {
                text = "Appuyez une seconde sur la barre d'espace du clavier créole (le 🌐 dans " +
                        "son coin le rappelle), puis touchez le clavier voulu."
                textSize = 14f
                setTextColor(Color.parseColor("#1565C0"))
                setLineSpacing(0f, 1.3f)
                setPadding(0, 0, 0, 12)
            }

            val switchBackTitle = TextView(this).apply {
                text = "⬅️ Revenir au kréyòl"
                textSize = 15f
                setTextColor(Color.parseColor("#0D47A1"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 4)
            }

            val switchBack = TextView(this).apply {
                text = "Le geste n'est pas le même : ailleurs, l'appui long sur la barre d'espace " +
                        "ne change que la langue de ce clavier. Touchez plutôt l'icône de clavier " +
                        "en bas de l'écran, dans la barre de navigation."
                textSize = 14f
                setTextColor(Color.parseColor("#1565C0"))
                setLineSpacing(0f, 1.3f)
                setPadding(0, 0, 0, 12)
            }

            val switchNote = TextView(this).apply {
                text = "Le choix vaut pour toutes vos applications et survit au redémarrage. " +
                        "Sans icône de clavier en bas, le bouton ci-dessous ouvre le même sélecteur."
                textSize = 13f
                setTextColor(Color.parseColor("#5C6BC0"))
                setLineSpacing(0f, 1.3f)
                setPadding(0, 0, 0, 16)
            }

            val switchButton = Button(this).apply {
                text = "Ouvrir le sélecteur de claviers"
                textSize = 15f
                setBackgroundColor(Color.parseColor("#0080FF"))
                setTextColor(Color.WHITE)
                setPadding(24, 16, 24, 16)
                setOnClickListener { openInputMethodPicker() }
            }

            switchCard.addView(switchTitle)
            switchCard.addView(switchIntro)
            switchCard.addView(switchAwayTitle)
            switchCard.addView(switchAway)
            switchCard.addView(switchBackTitle)
            switchCard.addView(switchBack)
            switchCard.addView(switchNote)
            switchCard.addView(switchButton)

            mainLayout.addView(switchCard)
            mainLayout.addView(createSpacing(16))
        }

        // Section "Astuce" si tout est configuré
        if (isEnabled && isSelected) {
            val tipCard = createRoundedCard("#FFF9E6")
            
            val tipHeader = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, 8)
            }
            
            val tipIcon = TextView(this).apply {
                text = "💡"
                textSize = 24f
                setPadding(0, 0, 12, 0)
            }
            
            val tipTitle = TextView(this).apply {
                text = "Astuce de la semaine"
                textSize = 16f
                setTextColor(Color.parseColor("#F57C00"))
                setTypeface(null, Typeface.BOLD)
            }
            
            tipHeader.addView(tipIcon)
            tipHeader.addView(tipTitle)
            
            val tipText = TextView(this).apply {
                text = getTipOfTheWeek()
                textSize = 14f
                setTextColor(Color.parseColor("#666666"))
                setLineSpacing(0f, 1.3f)
            }
            
            tipCard.addView(tipHeader)
            tipCard.addView(tipText)
            
            mainLayout.addView(tipCard)
            mainLayout.addView(createSpacing(16))
            
            // Lien vers statistiques
            val statsLinkCard = createRoundedCard("#E8F5E9")
            
            val statsLinkLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            
            val statsIcon = TextView(this).apply {
                text = "📊"
                textSize = 32f
                setPadding(0, 0, 16, 0)
            }
            
            val statsTextContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            
            val statsTitle = TextView(this).apply {
                text = "Découvrez vos statistiques"
                textSize = 16f
                setTextColor(Color.parseColor("#2E7D32"))
                setTypeface(null, Typeface.BOLD)
            }
            
            val statsDesc = TextView(this).apply {
                text = "Suivez votre progression et montez en niveau !"
                textSize = 13f
                setTextColor(Color.parseColor("#558B2F"))
            }
            
            statsTextContainer.addView(statsTitle)
            statsTextContainer.addView(statsDesc)
            
            val statsArrow = TextView(this).apply {
                text = "→"
                textSize = 24f
                setTextColor(Color.parseColor("#2E7D32"))
            }
            
            statsLinkLayout.addView(statsIcon)
            statsLinkLayout.addView(statsTextContainer)
            statsLinkLayout.addView(statsArrow)
            
            statsLinkCard.addView(statsLinkLayout)
            statsLinkCard.setOnClickListener {
                // Et non `currentItem = 1` : le pager est cyclique, et la
                // position absolue 1 est le bord gauche de la plage virtuelle.
                // Le contenu affiché était bien celui des statistiques, mais on
                // atterrissait là où il n'y a plus rien à balayer vers la
                // gauche. On vise la position la plus proche du bon onglet.
                allerAOnglet(TAB_STATS)
            }
            
            mainLayout.addView(statsLinkCard)
        }

        // Guide et À Propos ont quitté la barre d'onglets : ce sont des pages
        // qu'on lit une fois, et elles y coûtaient deux septièmes de la largeur
        // à chaque ouverture de l'application. Elles atterrissent ici, en pied
        // de l'écran de configuration, qui est déjà l'endroit où l'on vient
        // quand on cherche à comprendre plutôt qu'à jouer.
        mainLayout.addView(createSpacing(8))
        mainLayout.addView(createReferenceLink(
            "📖", "Guide d'utilisation",
            "Réglages, correcteur, astuces de saisie",
            SheetFragment.PAGE_GUIDE
        ))
        mainLayout.addView(createSpacing(8))
        mainLayout.addView(createReferenceLink(
            "ℹ️", "À propos",
            "Version, sources des données, licences",
            SheetFragment.PAGE_A_PROPOS
        ))
        mainLayout.addView(createSpacing(16))

        return mainLayout
    }

    /**
     * Ligne d'accès à une page de référence, ouverte en plein écran.
     *
     * Volontairement plus discrète que les cartes de configuration au-dessus :
     * ces deux pages ne demandent aucune action, elles répondent à une question
     * que l'utilisateur ne se pose pas encore.
     */
    private fun createReferenceLink(
        emoji: String,
        titre: String,
        resume: String,
        page: String
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.WHITE)
        setPadding(20, 18, 20, 18)
        isClickable = true
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        addView(TextView(this@SettingsActivity).apply {
            text = emoji
            textSize = 22f
            setPadding(0, 0, 18, 0)
        })
        addView(LinearLayout(this@SettingsActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            addView(TextView(this@SettingsActivity).apply {
                text = titre
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1C1C1C"))
            })
            addView(TextView(this@SettingsActivity).apply {
                text = resume
                textSize = 13f
                setTextColor(Color.parseColor("#888888"))
            })
        })
        addView(TextView(this@SettingsActivity).apply {
            text = "›"
            textSize = 24f
            setTextColor(Color.parseColor("#BBBBBB"))
        })

        setOnClickListener {
            SheetFragment.pour(page).show(supportFragmentManager, "sheet_$page")
        }
    }

    /**
     * Amène le pager sur un onglet, en restant dans le cycle courant.
     *
     * Le ViewPager répète les onglets sur une longue plage virtuelle pour que
     * le balayage puisse tourner dans les deux sens indéfiniment ; une position
     * absolue n'a donc de sens que relativement à celle où l'on se trouve.
     *
     * Le défilement n'est animé que d'un onglet à son voisin : au-delà,
     * ViewPager2 s'arrête en chemin, et la barre affichait l'onglet demandé
     * pendant que le contenu était celui d'un autre.
     */
    private fun allerAOnglet(onglet: Int) {
        val position = viewPager.currentItem
        val actuel = position % SettingsPagerAdapter.REAL_COUNT
        val avant = (onglet - actuel + SettingsPagerAdapter.REAL_COUNT) % SettingsPagerAdapter.REAL_COUNT
        val arriere = (actuel - onglet + SettingsPagerAdapter.REAL_COUNT) % SettingsPagerAdapter.REAL_COUNT
        val cible = if (avant <= arriere) position + avant else position - arriere
        viewPager.setCurrentItem(cible, avant.coerceAtMost(arriere) <= 1)
    }

    /** Conversion en pixels d'une dimension exprimée en dp. */
    private fun enDp(valeur: Int): Int = (valeur * resources.displayMetrics.density).toInt()

    /**
     * Une couleur `#RRGGBB` reprise avec l'opacité demandée.
     *
     * Concaténer les deux chaînes — `"$couleur20"` — ne donne pas ce qu'on
     * croit : `Color.parseColor` lit huit chiffres comme `#AARRGGBB`, si bien
     * que `"#4CAF50" + "20"` devient un alpha de 0x4C sur le brun `#AF5020`.
     * L'astuce se lit comme un ajout de transparence et produit une autre
     * teinte, opaque.
     */
    fun avecOpacite(couleur: String, alpha: Int): Int =
        (alpha shl 24) or (Color.parseColor(couleur) and 0x00FFFFFF)

    /**
     * Un mot a-t-il déjà été écrit avec le clavier ? Le jalon est posé par le
     * service de saisie au premier mot validé, y compris dans le champ de test
     * de l'application : c'est donc le seul signal honnête pour cocher la
     * troisième étape, qui n'est pas un réglage mais un essai.
     */
    private fun aEcritUnMot(): Boolean = onboardingPrefs().contains("funnel_first_word")

    /**
     * Carte à coins arrondis, réservée à l'onglet Démarrage. [createCard] reste
     * la carte carrée utilisée par tous les autres onglets : les arrondir tous
     * d'un coup dépasserait ce qui a été demandé ici.
     */
    private fun createRoundedCard(backgroundColor: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(enDp(18), enDp(18), enDp(18), enDp(18))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = enDp(16).toFloat()
                setColor(Color.parseColor(backgroundColor))
            }
        }
    }

    /**
     * Anneau « n/3 » : arc proportionnel au nombre d'étapes faites, chiffre au
     * centre. Il remplace l'ancienne barre de progression, qui disait où on en
     * était mais pas ce qu'il restait.
     */
    private class ProgressRingView(context: Context, private val total: Int) : View(context) {
        var done: Int = 0
            set(value) {
                field = value
                invalidate()
            }

        private val densite = context.resources.displayMetrics.density
        private val piste = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f * densite
            color = Color.parseColor("#E8EAED")
        }
        private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f * densite
            strokeCap = Paint.Cap.ROUND
        }
        private val encre = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 15f * densite
            typeface = Typeface.DEFAULT_BOLD
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val marge = piste.strokeWidth / 2f
            val cadre = android.graphics.RectF(marge, marge, width - marge, height - marge)
            val couleur = Color.parseColor(if (done >= total) "#4CAF50" else "#0080FF")
            arc.color = couleur
            encre.color = couleur
            canvas.drawArc(cadre, 0f, 360f, false, piste)
            if (done > 0) {
                canvas.drawArc(cadre, -90f, 360f * done / total, false, arc)
            }
            val ligneDeBase = height / 2f - (encre.descent() + encre.ascent()) / 2f
            canvas.drawText("$done/$total", width / 2f, ligneDeBase, encre)
        }
    }

    /**
     * Bandeau vert de réussite. Deux textes distincts selon que l'utilisateur
     * a déjà écrit un mot ou non : afficher « Tout est prêt » au-dessus d'un
     * anneau qui affiche 2/3 ferait dire deux choses différentes au même écran.
     */
    private fun createReadyBanner(aDejaEcrit: Boolean): LinearLayout {
        val banner = createRoundedCard("#4CAF50").apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icone = TextView(this).apply {
            text = "✅"
            textSize = 24f
            setPadding(0, 0, enDp(14), 0)
        }

        val textes = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }

        val titre = TextView(this).apply {
            text = if (aDejaEcrit) "Tout est prêt !" else "Clavier en place !"
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }

        val sousTitre = TextView(this).apply {
            text = if (aDejaEcrit) "Vous pouvez taper en kréyòl partout."
                   else "Écrivez un mot pour terminer la configuration."
            textSize = 13f
            setTextColor(Color.parseColor("#E8F5E9"))
            setPadding(0, enDp(2), 0, 0)
        }

        textes.addView(titre)
        textes.addView(sousTitre)
        banner.addView(icone)
        banner.addView(textes)
        return banner
    }

    /**
     * (Re)construit la carte de configuration dans son conteneur. Passer par le
     * conteneur plutôt que par un rafraîchissement complet de l'onglet évite de
     * reconstruire le clavier d'essai à chaque fois qu'une étape se déplie.
     */
    private fun remplirCarteConfig(
        conteneur: LinearLayout,
        isEnabled: Boolean,
        isSelected: Boolean,
        hasCompletedBefore: Boolean,
        showIncompleteNudge: Boolean
    ) {
        conteneur.removeAllViews()
        conteneur.addView(
            createQuickSetupCard(isEnabled, isSelected, hasCompletedBefore, showIncompleteNudge) {
                remplirCarteConfig(conteneur, isEnabled, isSelected, hasCompletedBefore, showIncompleteNudge)
            }
        )
    }

    /**
     * Carte « Configuration rapide » : une ligne compacte par étape, et une
     * seule dépliée à la fois — celle qui reste à faire, sauf si l'utilisateur
     * en ouvre une autre.
     *
     * Le repli est piloté par l'état, jamais systématique : la description de
     * l'étape en cours, l'avertissement Android et le rappel des deux
     * validations successives sont ce qui fait passer l'utilisateur à travers
     * les réglages système. Les réduire à un chevron ferait gagner de la place
     * là où le tunnel se joue.
     */
    private fun createQuickSetupCard(
        isEnabled: Boolean,
        isSelected: Boolean,
        hasCompletedBefore: Boolean,
        showIncompleteNudge: Boolean,
        onRebuild: () -> Unit
    ): LinearLayout {
        val card = createRoundedCard("#FFFFFF")

        val etape3Faite = isEnabled && isSelected && aEcritUnMot()
        val faites = listOf(isEnabled, isSelected, etape3Faite).count { it }
        val toutFait = faites == 3

        // === En-tête : titre, état, anneau ===
        val entete = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val textesEntete = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }

        val titre = TextView(this).apply {
            text = "Configuration rapide"
            textSize = 18f
            setTextColor(Color.parseColor("#1C1C1C"))
            setTypeface(null, Typeface.BOLD)
        }

        val sousTitre = TextView(this).apply {
            text = when {
                toutFait -> "Les 3 étapes sont faites."
                isEnabled && isSelected -> "Plus qu'à l'essayer."
                // Sans « sans doute après une mise à jour » : la cause la plus
                // fréquente est un détour volontaire par un autre clavier, et
                // annoncer un incident système à quelqu'un qui a simplement
                // changé de clavier l'envoie chercher au mauvais endroit.
                hasCompletedBefore && isEnabled -> "Le clavier kréyòl n'est plus celui qui s'affiche quand vous écrivez."
                hasCompletedBefore -> "Le clavier kréyòl n'est plus actif sur ce téléphone."
                isEnabled -> "Plus qu'une étape."
                else -> "3 étapes pour taper en kréyòl partout."
            }
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, enDp(3), enDp(12), 0)
            setLineSpacing(0f, 1.25f)
        }

        val anneau = ProgressRingView(this, 3).apply {
            done = faites
            contentDescription = "$faites étapes sur 3 terminées"
            layoutParams = LinearLayout.LayoutParams(enDp(52), enDp(52))
        }

        textesEntete.addView(titre)
        textesEntete.addView(sousTitre)
        entete.addView(textesEntete)
        entete.addView(anneau)
        card.addView(entete)

        // Une fois les 3 étapes faites, la carte n'a plus rien à demander :
        // elle se replie sur son en-tête et laisse la place au reste de
        // l'onglet, au lieu de garder trois lignes cochées en haut de l'écran
        // pour toujours.
        if (toutFait && !detailsConfigDeplies) {
            card.addView(TextView(this).apply {
                text = "Voir les 3 étapes"
                textSize = 14f
                setTextColor(Color.parseColor("#0080FF"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, enDp(14), 0, 0)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    detailsConfigDeplies = true
                    onRebuild()
                }
            })
            return card
        }

        // === Étape dépliée : celle qui reste à faire, sauf choix contraire ===
        val etapeParDefaut = when {
            !isEnabled -> 0
            !isSelected -> 1
            !etape3Faite -> 2
            else -> -1
        }
        val ouverte = etapeConfigOuverte ?: etapeParDefaut

        fun basculer(index: Int): () -> Unit = {
            etapeConfigOuverte = if (ouverte == index) -1 else index
            onRebuild()
        }

        fun corps(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(enDp(40), 0, 0, enDp(16))
        }

        fun texte(contenu: String): TextView = TextView(this).apply {
            text = contenu
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setLineSpacing(0f, 1.35f)
            setPadding(0, 0, 0, enDp(12))
        }

        fun encart(fond: String, encre: String, contenu: String): TextView = TextView(this).apply {
            text = contenu
            textSize = 13f
            setTextColor(Color.parseColor(encre))
            setLineSpacing(0f, 1.3f)
            setPadding(enDp(12), enDp(12), enDp(12), enDp(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = enDp(10).toFloat()
                setColor(Color.parseColor(fond))
            }
        }

        // Le libellé porte le fait que le bouton quitte l'application : un
        // chevron seul laisserait croire à une navigation interne, alors que
        // ces deux étapes se terminent dans les réglages Android.
        fun bouton(libelle: String, action: () -> Unit): Button = Button(this).apply {
            text = libelle
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setPadding(enDp(20), enDp(14), enDp(20), enDp(14))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = enDp(10).toFloat()
                setColor(Color.parseColor("#0080FF"))
            }
            setOnClickListener { action() }
        }

        // === Étape 1 : activer le clavier ===
        val corps1 = corps().apply {
            addView(texte("Trouvez « Klavyé Kréyòl Karukéra » dans l'écran qui s'ouvre, " +
                    "activez l'interrupteur, puis revenez ici."))
            when {
                showIncompleteNudge -> addView(encart("#FFF3E0", "#BF360C",
                    "💡 Presque ! Validez bien les 2 avertissements Android l'un après " +
                    "l'autre : s'arrêter au premier annule l'activation."))
                !isEnabled -> addView(encart("#FFF8E1", "#5D4037",
                    "ℹ️ Android affiche un avertissement de sécurité standard, montré pour " +
                    "tous les claviers tiers. Klavyé Kréyòl ne collecte aucune donnée."))
            }
            if (!isEnabled) {
                addView(TextView(this@SettingsActivity).apply {
                    text = "Lire la politique de confidentialité"
                    textSize = 13f
                    setTextColor(Color.parseColor("#0080FF"))
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, enDp(10), 0, 0)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { openPrivacyPolicy() }
                })
            }
            addView(createSpacing(12))
            addView(bouton(
                if (isEnabled) "Rouvrir les réglages Android ↗" else "Ouvrir les réglages Android ↗"
            ) { showPreSettingsWarningDialog() })
        }

        val ligne1 = createSetupRow(
            numero = 1,
            faite = isEnabled,
            verrouillee = false,
            titre = "Activer le clavier",
            sousTitre = if (isEnabled) "Le clavier est activé." else "Dans les réglages Android.",
            ouverte = ouverte == 0,
            contenu = corps1,
            onToggle = basculer(0)
        )

        // === Étape 2 : sélectionner le clavier ===
        val corps2 = corps().apply {
            addView(texte("Choisissez « Klavyé Kréyòl Karukéra » dans la liste des claviers " +
                    "qui s'affiche. Vos autres claviers restent installés."))
            addView(bouton("Ouvrir le sélecteur de claviers ↗") { openInputMethodPicker() })
        }

        val ligne2 = createSetupRow(
            numero = 2,
            faite = isSelected,
            verrouillee = !isEnabled,
            titre = "Sélectionner le clavier",
            sousTitre = when {
                isSelected -> "Klavyé Kréyòl est sélectionné."
                !isEnabled -> "Terminez d'abord l'étape 1."
                else -> "Choisissez-le dans la liste."
            },
            ouverte = ouverte == 1,
            contenu = corps2,
            onToggle = basculer(1)
        )

        // === Étape 3 : essayer le clavier ===
        val champTest = EditText(this).apply {
            tag = "onboarding_test_field"
            hint = "Koumansé maké on bèl kréyòl isit..."
            textSize = 16f
            setPadding(enDp(14), enDp(14), enDp(14), enDp(14))
            minHeight = enDp(56)
            setTextColor(Color.parseColor("#1C1C1C"))
            setHintTextColor(Color.parseColor("#999999"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = enDp(10).toFloat()
                setColor(Color.parseColor("#F7F7F7"))
                setStroke(enDp(1), Color.parseColor("#E0E0E0"))
            }
            // Force le scroll vers ce champ quand il obtient le focus
            setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    // Délai : laisser le clavier s'ouvrir avant de recadrer
                    Handler(Looper.getMainLooper()).postDelayed({
                        view.parent?.requestChildFocus(view, view)
                    }, 300)
                }
            }
        }

        val corps3 = corps().apply {
            addView(texte("Écrivez « Bonjou tout moun » et regardez les suggestions vous aider."))
            addView(champTest)
        }

        val ligne3 = createSetupRow(
            numero = 3,
            faite = etape3Faite,
            verrouillee = !isEnabled || !isSelected,
            titre = "Essayer le clavier",
            sousTitre = when {
                etape3Faite -> "Vous avez écrit vos premiers mots."
                !isEnabled || !isSelected -> "Terminez les étapes 1 et 2."
                else -> "Écrivez un mot pour vérifier."
            },
            ouverte = ouverte == 2,
            contenu = corps3,
            onToggle = basculer(2)
        )

        // La troisième étape se coche pendant la frappe, sans reconstruire la
        // carte : reconstruire ferait perdre le focus et refermerait le clavier
        // au premier mot écrit. Le jalon lu est celui du service de saisie, donc
        // la pastille ne s'allume pas sur un texte collé ou tapé avec un autre
        // clavier — et ne se rallume pas à faux au rafraîchissement suivant.
        if (!etape3Faite) {
            champTest.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (s.isNullOrEmpty() || !aEcritUnMot()) return
                    marquerEtapeFaite(ligne3)
                    anneau.done = 3
                    sousTitre.text = "Les 3 étapes sont faites."
                }
            })
        }

        card.addView(createSpacing(6))
        card.addView(ligne1)
        card.addView(createSetupSeparator())
        card.addView(ligne2)
        card.addView(createSetupSeparator())
        card.addView(ligne3)

        if (toutFait) {
            card.addView(TextView(this).apply {
                text = "Masquer le détail"
                textSize = 14f
                setTextColor(Color.parseColor("#0080FF"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, enDp(12), 0, 0)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    detailsConfigDeplies = false
                    onRebuild()
                }
            })
        }

        return card
    }

    /**
     * Une ligne d'étape : pastille d'état, titre, sous-titre d'une ligne, et
     * son contenu déplié en dessous. Verrouillée, la ligne est grisée et sans
     * chevron — pas de cadenas : c'est une icône de plus pour dire ce que le
     * gris dit déjà.
     */
    private fun createSetupRow(
        numero: Int,
        faite: Boolean,
        verrouillee: Boolean,
        titre: String,
        sousTitre: String,
        ouverte: Boolean,
        contenu: View?,
        onToggle: () -> Unit
    ): LinearLayout {
        val bloc = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val ligne = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // 56 dp : au-delà des 48 dp de cible tactile minimale, la ligne
            // reste confortable à viser pour une main qui tremble
            minimumHeight = enDp(56)
            setPadding(0, enDp(12), 0, enDp(12))
            contentDescription = when {
                faite -> "$titre, étape terminée"
                verrouillee -> "$titre, étape verrouillée"
                else -> titre
            }
            if (!verrouillee) {
                isClickable = true
                isFocusable = true
                setOnClickListener { onToggle() }
            }
        }

        val pastille = TextView(this).apply {
            tag = "pastille_etape"
            text = if (faite) "✓" else numero.toString()
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(when {
                faite -> Color.WHITE
                verrouillee -> Color.parseColor("#9E9E9E")
                else -> Color.parseColor("#0080FF")
            })
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(when {
                    faite -> "#4CAF50"
                    verrouillee -> "#F0F0F0"
                    else -> "#E3F2FD"
                }))
            }
            layoutParams = LinearLayout.LayoutParams(enDp(28), enDp(28)).apply {
                rightMargin = enDp(12)
            }
        }

        val colonne = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }

        colonne.addView(TextView(this).apply {
            text = titre
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor(if (verrouillee) "#9E9E9E" else "#1C1C1C"))
        })

        colonne.addView(TextView(this).apply {
            text = sousTitre
            textSize = 13f
            setTextColor(Color.parseColor(if (verrouillee) "#BDBDBD" else "#666666"))
            setPadding(0, enDp(2), enDp(8), 0)
        })

        val chevron = TextView(this).apply {
            text = "›"
            textSize = 22f
            setTextColor(Color.parseColor("#B0B0B0"))
            rotation = if (ouverte) 90f else 0f
            visibility = if (verrouillee) View.INVISIBLE else View.VISIBLE
        }

        ligne.addView(pastille)
        ligne.addView(colonne)
        ligne.addView(chevron)
        bloc.addView(ligne)

        if (ouverte && !verrouillee && contenu != null) {
            bloc.addView(contenu)
        }

        return bloc
    }

    /** Filet de séparation entre deux lignes, aligné sur le texte. */
    private fun createSetupSeparator(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1
        ).apply { leftMargin = enDp(40) }
        setBackgroundColor(Color.parseColor("#EEEEEE"))
    }

    /** Passe la pastille d'une ligne d'étape au vert, sans reconstruire la carte. */
    private fun marquerEtapeFaite(ligne: View) {
        val pastille = ligne.findViewWithTag<TextView>("pastille_etape") ?: return
        pastille.text = "✓"
        pastille.setTextColor(Color.WHITE)
        (pastille.background as? GradientDrawable)?.setColor(Color.parseColor("#4CAF50"))
    }

    // Fonction pour créer une card d'étape
    /**
     * Carte du correcteur orthographique. Elle emprunte l'habillage des étapes
     * de configuration (badge, icône, titre) mais pas leur mécanique : ce n'est
     * pas une étape du parcours numéroté, et sa mise en page diverge sur trois
     * points.
     *
     * La carte entière est la cible de clic, signalée par un chevron : un
     * bouton pleine largeur donnait à une fonction optionnelle le même poids
     * visuel qu'aux trois étapes qui, elles, conditionnent l'usage du clavier.
     *
     * Ne reste visible que la promesse, plus l'avertissement qu'Android
     * affichera. Ce dernier ne peut pas être replié : le dialogue système
     * prévient que le correcteur « peut collecter tout le texte que vous tapez,
     * y compris des données personnelles comme les mots de passe », et c'est là
     * que l'utilisateur non prévenu abandonne. Une ligne le désamorce.
     *
     * La marche à suivre, elle, se déplie à la demande, sous un intitulé qui
     * annonce ce qu'on y trouve : elle ne sert qu'une fois l'écran système
     * ouvert, où la sélection se fait dans un sous-menu (« Correcteur par
     * défaut ») que rien ne signale.
     */
    private fun createSpellCheckerCard(): LinearLayout {
        val estActif = isSpellCheckerSelected()
        val card = createRoundedCard("#FFFFFF")

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 12)
        }

        val badgeView = TextView(this).apply {
            text = "✚"
            textSize = 20f
            setTextColor(Color.parseColor(if (estActif) "#4CAF50" else "#0080FF"))
            setTypeface(null, Typeface.BOLD)
            setPadding(12, 8, 12, 8)
            setBackgroundColor(Color.parseColor(if (estActif) "#E8F5E9" else "#E3F2FD"))
        }

        val iconText = TextView(this).apply {
            text = "🔤"
            textSize = 24f
            setPadding(16, 0, 12, 0)
        }

        val titleText = TextView(this).apply {
            text = "Corriger l'orthographe partout"
            textSize = 18f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val marqueur = TextView(this).apply {
            text = if (estActif) "✓" else "›"
            textSize = if (estActif) 24f else 34f
            setTextColor(Color.parseColor(if (estActif) "#4CAF50" else "#757575"))
            setTypeface(null, Typeface.BOLD)
            setPadding(12, 0, 4, 0)
        }

        header.addView(badgeView)
        header.addView(iconText)
        header.addView(titleText)
        header.addView(marqueur)

        val descText = TextView(this).apply {
            // Le bénéfice s'énonce par ce qu'il apporte, pas par ce qu'il
            // supprime, mais le trait rouge reste nommé : c'est à lui que
            // l'utilisateur reconnaît la gêne qu'il subit tous les jours.
            text = if (estActif) {
                "Vos mots kréyòl sont reconnus dans Messages, Notes et ailleurs, " +
                    "sans trait rouge dessous."
            } else {
                "Faites reconnaître vos mots kréyòl dans Messages, Notes et " +
                    "ailleurs, sans trait rouge dessous."
            }
            textSize = 16f
            setTextColor(Color.parseColor("#666666"))
            setLineSpacing(0f, 1.3f)
            setPadding(0, 0, 0, if (estActif) 0 else 12)
        }

        card.addView(header)
        card.addView(descText)

        if (estActif) return card

        val avertissement = TextView(this).apply {
            text = "Android vous préviendra qu'un correcteur peut lire le texte saisi. " +
                "Le nôtre le compare au dictionnaire de l'application, sans rien " +
                "conserver ni rien envoyer."
            textSize = 13f
            setTextColor(Color.parseColor("#9E9E9E"))
            setLineSpacing(0f, 1.3f)
            setPadding(0, 0, 0, 12)
        }
        card.addView(avertissement)

        val details = TextView(this).apply {
            text = "Dans l'écran qui s'ouvre :\n" +
                "1. touchez « Correcteur par défaut »\n" +
                "2. choisissez « Correcteur Kréyòl Karukéra »\n" +
                "3. confirmez l'avertissement d'Android"
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setLineSpacing(0f, 1.35f)
            setPadding(0, 0, 0, 12)
            visibility = View.GONE
        }

        val lien = TextView(this).apply {
            text = "ⓘ  Ce qu'Android va vous demander"
            textSize = 14f
            setTextColor(Color.parseColor("#0080FF"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
            setOnClickListener {
                val ouvert = details.visibility == View.VISIBLE
                details.visibility = if (ouvert) View.GONE else View.VISIBLE
                text = if (ouvert) "ⓘ  Ce qu'Android va vous demander" else "▲  Masquer"
            }
        }

        card.addView(details)
        card.addView(lien)

        card.isClickable = true
        card.setOnClickListener { openSpellCheckerSettings() }

        return card
    }

    private fun createStepCard(
        badge: String,
        isCompleted: Boolean,
        isLocked: Boolean,
        icon: String,
        title: String,
        description: String,
        buttonText: String,
        buttonEnabled: Boolean,
        buttonAction: () -> Unit
    ): LinearLayout {
        val card = createRoundedCard("#FFFFFF")
        
        // Appliquer une opacité si verrouillé
        if (isLocked) {
            card.alpha = 0.6f
        }
        
        // Header avec numéro et icône
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 12)
        }
        
        val badgeView = TextView(this).apply {
            text = badge
            textSize = 20f
            setTextColor(
                when {
                    isLocked -> Color.parseColor("#999999")
                    isCompleted -> Color.parseColor("#4CAF50")
                    else -> Color.parseColor("#0080FF")
                }
            )
            setTypeface(null, Typeface.BOLD)
            setPadding(12, 8, 12, 8)
            setBackgroundColor(
                when {
                    isLocked -> Color.parseColor("#F5F5F5")
                    isCompleted -> Color.parseColor("#E8F5E9")
                    else -> Color.parseColor("#E3F2FD")
                }
            )
        }
        
        val iconText = TextView(this).apply {
            text = icon
            textSize = 24f
            setPadding(16, 0, 12, 0)
        }
        
        val titleText = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(if (isLocked) Color.parseColor("#999999") else Color.parseColor("#333333"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        
        if (isCompleted) {
            val checkIcon = TextView(this).apply {
                text = "✓"
                textSize = 24f
                setTextColor(Color.parseColor("#4CAF50"))
                setTypeface(null, Typeface.BOLD)
            }
            header.addView(badgeView)
            header.addView(iconText)
            header.addView(titleText)
            header.addView(checkIcon)
        } else if (isLocked) {
            val lockIcon = TextView(this).apply {
                text = "🔒"
                textSize = 20f
            }
            header.addView(badgeView)
            header.addView(iconText)
            header.addView(titleText)
            header.addView(lockIcon)
        } else {
            header.addView(badgeView)
            header.addView(iconText)
            header.addView(titleText)
        }
        
        val descText = TextView(this).apply {
            text = description
            textSize = 16f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, 0, 0, 16)
            setLineSpacing(0f, 1.3f)
        }
        
        val button = Button(this).apply {
            text = buttonText
            textSize = 15f
            setBackgroundColor(
                when {
                    isLocked -> Color.parseColor("#EEEEEE")
                    isCompleted -> Color.parseColor("#E0E0E0")
                    buttonEnabled -> Color.parseColor("#0080FF")
                    else -> Color.parseColor("#BDBDBD")
                }
            )
            setTextColor(
                when {
                    isLocked -> Color.parseColor("#999999")
                    isCompleted -> Color.parseColor("#757575")
                    else -> Color.WHITE
                }
            )
            setPadding(24, 16, 24, 16)
            this.isEnabled = buttonEnabled && !isCompleted && !isLocked
            setOnClickListener {
                if (!isCompleted && !isLocked) {
                    buttonAction()
                }
            }
        }
        
        card.addView(header)
        card.addView(descText)
        card.addView(button)
        
        return card
    }
    
    // Onglet 3 : À Propos
    fun createAboutContent(): LinearLayout {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 32)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Mission
        val missionCard = createCard("#FFFFFF")
        
        val missionTitle = TextView(this).apply {
            text = "🌟 Notre Mission"
            textSize = 20f
            setTextColor(Color.parseColor("#0080FF"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        
        val missionText = TextView(this).apply {
            text = "Ce clavier a été spécialement conçu pour préserver et promouvoir le Kréyòl Guadeloupéen (Karukéra). " +
                    "Il met à disposition de tous un outil moderne pour écrire dans notre belle langue créole avec :\n\n" +
                    "💡 Suggestions de mots en Kréyòl\n" +
                    "🌈 Design aux couleurs de la Guadeloupe\n" +
                    "🪘 Identité guadeloupéenne forte"
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            setLineSpacing(0f, 1.3f)
        }
        
        missionCard.addView(missionTitle)
        missionCard.addView(missionText)
        mainLayout.addView(missionCard)
        mainLayout.addView(createSpacing(16))

        // Partage
        val shareCard = createCard("#E8F5FF")

        val shareTitle = TextView(this).apply {
            text = "📣 Ba kréyòl-la lanmou a-w !"
            textSize = 18f
            setTextColor(Color.parseColor("#0080FF"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }

        val shareText = TextView(this).apply {
            text = "Ce clavier grandit grâce au bouche-à-oreille. Partage-le avec ta famille " +
                    "et tes amis créolophones : chaque partage aide notre langue à exister davantage " +
                    "sur les téléphones."
            textSize = 14f
            setTextColor(Color.parseColor("#333333"))
            setLineSpacing(0f, 1.3f)
            setPadding(0, 0, 0, 16)
        }

        val shareButton = Button(this).apply {
            text = "📤 Partager l'application"
            textSize = 15f
            setBackgroundColor(Color.parseColor("#0080FF"))
            setTextColor(Color.WHITE)
            setPadding(24, 24, 24, 24)
            setOnClickListener { shareApp() }
        }

        val rateButton = Button(this).apply {
            text = "⭐ Noter l'application"
            textSize = 15f
            setBackgroundColor(Color.parseColor("#FFB300"))
            setTextColor(Color.parseColor("#333333"))
            setPadding(24, 24, 24, 24)
            setOnClickListener { openPlayStoreListing() }
        }

        val shareProverb = TextView(this).apply {
            text = "« Dé mòn pa ka jwenn, mé dé moun toujou ka jwenn »"
            textSize = 14f
            setTextColor(Color.parseColor("#0080FF"))
            setTypeface(null, Typeface.ITALIC)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.3f)
        }

        shareCard.addView(shareTitle)
        shareCard.addView(shareText)
        shareCard.addView(shareButton)
        shareCard.addView(createSpacing(12))
        shareCard.addView(rateButton)
        shareCard.addView(createSpacing(16))
        shareCard.addView(shareProverb)
        mainLayout.addView(shareCard)
        mainLayout.addView(createSpacing(16))

        // Sources littéraires
        val sourcesCard = createCard("#F0F8E8")
        
        val sourcesTitle = TextView(this).apply {
            text = "📚 Sources littéraires"
            textSize = 18f
            setTextColor(Color.parseColor("#228B22"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        
        val sourcesText = TextView(this).apply {
            text = "Les suggestions de mots en Kréyòl sont construites sur les travaux des défenseurs du Kréyòl :\n\n" +
                    "✍️ Sylviane Telchid, Sonny Rupaire, Robert Fontes, Max Rippon, Alain Rutil, Alain Vérin, Katel, " +
                    "Esnard Boisdur, Pierre Édouard Décimus, Corinne Famibelle\n\n" +
                    "Grâce à leurs riches contributions, ce clavier vous propose des suggestions authentiques et fidèles à notre créole guadeloupéen."
            textSize = 14f
            setTextColor(Color.parseColor("#2F5233"))
            setLineSpacing(0f, 1.3f)
        }
        
        sourcesCard.addView(sourcesTitle)
        sourcesCard.addView(sourcesText)
        mainLayout.addView(sourcesCard)
        mainLayout.addView(createSpacing(16))
        
        // Informations app
        val infoCard = createCard("#F8F9FA")
        
        val infoTitle = TextView(this).apply {
            text = "ℹ️ Informations"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#333333"))
            setPadding(0, 0, 0, 24)
        }

        val versionText = TextView(this).apply {
            text = "Version : ${BuildConfig.VERSION_NAME}\n" +
                    "© Potomitan™ - Clavier Kréyòl Karukéra\n\n" +
                    "Fait avec ❤️ pour la Guadeloupe\n" +
                    "Préservons notre langue créole pour les générations futures !"
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setLineSpacing(0f, 1.3f)
            gravity = Gravity.CENTER
        }
        
        infoCard.addView(infoTitle)
        infoCard.addView(versionText)
        mainLayout.addView(infoCard)
        mainLayout.addView(createSpacing(16))

        // Confidentialité
        val privacyCard = createCard("#FFF8E1")

        val privacyTitle = TextView(this).apply {
            text = "🔒 Confidentialité"
            textSize = 18f
            setTextColor(Color.parseColor("#5D4037"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }

        val privacyText = TextView(this).apply {
            text = "Zéro collecte de données personnelles : ce clavier fonctionne entièrement " +
                    "en local, rien de ce que vous tapez ne quitte votre téléphone."
            textSize = 14f
            setTextColor(Color.parseColor("#5D4037"))
            setLineSpacing(0f, 1.3f)
        }

        val privacyLink = TextView(this).apply {
            text = "Lire la politique de confidentialité"
            textSize = 14f
            setTextColor(Color.parseColor("#0080FF"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 12, 0, 0)
            setOnClickListener { openPrivacyPolicy() }
        }

        privacyCard.addView(privacyTitle)
        privacyCard.addView(privacyText)
        privacyCard.addView(privacyLink)
        mainLayout.addView(privacyCard)
        mainLayout.addView(createSpacing(16))

        // Tunnel d'activation : diagnostic local du parcours de configuration
        mainLayout.addView(createFunnelCard())

        return mainLayout
    }

    // ═══ Clavier d'essai du wizard ═══
    // Un vrai clavier Kréyòl interactif (les mêmes composants que l'IME :
    // KeyboardLayoutManager + SuggestionEngine) branché sur un champ de
    // démonstration : l'utilisateur essaie les suggestions bilingues AVANT
    // d'accepter les avertissements système. Aucune activation requise,
    // tout tourne dans l'activité.
    private var demoEngine: SuggestionEngine? = null
    private var demoEngineReady = false
    private var demoKeyboardManager: KeyboardLayoutManager? = null

    private fun createDemoKeyboardCard(): LinearLayout {
        val card = createRoundedCard("#FFFFFF")

        val title = TextView(this).apply {
            text = "🎹 Essayez-le tout de suite !"
            textSize = 18f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 4)
        }
        val caption = TextView(this).apply {
            text = "Tapez « bonjou » et touchez une suggestion : rien à installer pour essayer"
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, 0, 0, 12)
        }

        val demoField = EditText(this).apply {
            hint = "Éséyé maké kréyòl isit ..."
            // Ne jamais ouvrir le clavier système (Gboard) sur ce champ :
            // c'est le clavier d'essai ci-dessous qui écrit dedans
            showSoftInputOnFocus = false
            // Sans correcteur système : il soulignerait les mots créoles en
            // rouge, à rebours de ce que la démo veut montrer
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            textSize = 16f
            setPadding(16, 16, 16, 16)
            minHeight = 90
            setBackgroundColor(Color.parseColor("#F9F9F9"))
            setTextColor(Color.parseColor("#1C1C1C"))
            setHintTextColor(Color.parseColor("#999999"))
        }

        val suggestionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 4)
            minimumHeight = 110
        }

        // Pont entre le « aha » et l'action : la démo crée la motivation
        // mais ne la convertissait pas encore — l'utilisateur devait
        // comprendre seul qu'il fallait redescendre vers l'étape 1. Ce
        // bouton apparaît au premier signe d'engagement (première touche
        // pressée) et enchaîne directement vers l'activation système
        var installCtaShown = false
        val installCta = Button(this).apply {
            text = "Ça vous plaît ? Installez-le →"
            textSize = 15f
            isAllCaps = false
            setBackgroundColor(Color.parseColor("#0080FF"))
            setTextColor(Color.WHITE)
            setPadding(24, 20, 24, 20)
            // INVISIBLE (pas GONE) dès la création : réserve sa place tout de
            // suite pour que son apparition ne décale jamais le clavier situé
            // juste en dessous. Un décalage au premier caractère tapé ferait
            // rater les touches suivantes, frappées de mémoire par l'utilisateur
            // (repro confirmée en test automatisé : les taps suivants
            // atterrissaient sur ce bouton une fois révélé, ouvrant les
            // réglages système en pleine frappe)
            visibility = View.INVISIBLE
            alpha = 0f
            setOnClickListener { showPreSettingsWarningDialog() }
        }

        fun revealInstallCta() {
            recordFunnelStep("funnel_demo_first_key")
            if (!installCtaShown) {
                installCtaShown = true
                installCta.visibility = View.VISIBLE
                installCta.animate().alpha(1f).setDuration(300).start()
            }
        }

        val keyboardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#ECEFF1"))
        }

        // Moteur partagé entre les refreshs du wizard : dictionnaires
        // chargés une seule fois
        val engine = demoEngine ?: SuggestionEngine(this).also { created ->
            demoEngine = created
            activityScope.launch {
                created.initialize()
                created.enableBilingualSupport()
                demoEngineReady = true
            }
        }

        fun cursor(): Int =
            demoField.selectionStart.let { if (it >= 0) it else demoField.text.length }

        fun currentWord(): String =
            demoField.text.toString().substring(0, cursor())
                .takeLastWhile { it.isLetter() || it == '\'' || it == '-' }

        fun clearChips() = suggestionsRow.removeAllViews()

        fun refreshSuggestions() {
            val word = currentWord()
            if (word.isNotEmpty() && demoEngineReady) {
                engine.generateBilingualSuggestions(word)
            } else {
                clearChips()
            }
        }

        engine.setSuggestionListener(object : SuggestionEngine.SuggestionListener {
            override fun onSuggestionsReady(suggestions: List<String>) {}
            override fun onBilingualSuggestionsReady(suggestions: List<BilingualSuggestion>) {
                clearChips()
                suggestions.take(3).forEach { suggestion ->
                    val chip = TextView(this@SettingsActivity).apply {
                        text = suggestion.word
                        textSize = 15f
                        setTextColor(Color.WHITE)
                        setTypeface(null, Typeface.BOLD)
                        setPadding(28, 14, 28, 14)
                        background = android.graphics.drawable.GradientDrawable().apply {
                            cornerRadius = 40f
                            setColor(suggestion.getColor())
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { rightMargin = 12 }
                        setOnClickListener {
                            val pos = cursor()
                            val word = currentWord()
                            val start = (pos - word.length).coerceAtLeast(0)
                            demoField.text.replace(start, pos, suggestion.word + " ")
                            clearChips()
                            revealInstallCta()
                        }
                    }
                    suggestionsRow.addView(chip)
                }
            }
            override fun onDictionaryLoaded(wordCount: Int) {}
            override fun onNgramModelLoaded() {}
            override fun onFrenchDictionaryLoaded(wordCount: Int) {}
            override fun onModeChanged(newMode: SuggestionEngine.SuggestionMode) {}
        })

        demoKeyboardManager?.cleanup()
        val manager = KeyboardLayoutManager(this)
        demoKeyboardManager = manager
        // Mirroir local de l'état shift (le manager n'expose pas de getter) :
        // cycle minuscules → majuscule ponctuelle → verrouillage → minuscules
        var demoCapital = false
        var demoCapsLock = false

        fun insertText(t: String) {
            demoField.text.insert(cursor(), t)
        }

        manager.setInteractionListener(object : KeyboardLayoutManager.KeyboardInteractionListener {
            override fun onKeyPress(key: String) {
                when (key) {
                    "⌫" -> {
                        val pos = cursor()
                        if (pos > 0) demoField.text.delete(pos - 1, pos)
                    }
                    "⏎" -> insertText("\n")
                    "⇧" -> {
                        when {
                            !demoCapital && !demoCapsLock -> demoCapital = true
                            demoCapital && !demoCapsLock -> demoCapsLock = true
                            else -> { demoCapital = false; demoCapsLock = false }
                        }
                        manager.updateKeyboardStates(manager.isNumericMode(), manager.isEmojiMode(), demoCapital, demoCapsLock)
                        manager.updateKeyboardDisplay()
                    }
                    "123", "ABC" -> {
                        manager.switchKeyboardMode()
                        keyboardContainer.removeAllViews()
                        keyboardContainer.addView(manager.createKeyboardLayout())
                    }
                    "EMOJI" -> {
                        manager.switchToEmojiMode()
                        keyboardContainer.removeAllViews()
                        keyboardContainer.addView(manager.createKeyboardLayout())
                    }
                    else -> {
                        insertText(if (demoCapital || demoCapsLock) key.uppercase() else key)
                        if (demoCapital && !demoCapsLock) {
                            demoCapital = false
                            manager.updateKeyboardStates(manager.isNumericMode(), manager.isEmojiMode(), false, false)
                            manager.updateKeyboardDisplay()
                        }
                        revealInstallCta()
                    }
                }
                refreshSuggestions()
            }
            // Pas de popup d'accents en démo : é, è et ò sont déjà des touches directes
            override fun onLongPress(key: String, button: View) {}
            override fun onKeyRelease() {}
        })
        keyboardContainer.addView(manager.createKeyboardLayout())

        card.addView(title)
        card.addView(caption)
        card.addView(demoField)
        card.addView(suggestionsRow)
        card.addView(installCta)
        card.addView(createSpacing(8))
        card.addView(keyboardContainer)
        return card
    }

    // Carte diagnostic du tunnel d'activation : quand chaque jalon a été
    // franchi (données 100 % locales). Sert à comprendre où le parcours
    // accroche quand un utilisateur montre son téléphone, sans télémétrie
    private fun createFunnelCard(): LinearLayout {
        val card = createCard("#F8F9FA")

        val title = TextView(this).apply {
            text = "🔎 Diagnostic d'activation"
            textSize = 18f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        card.addView(title)

        val prefs = onboardingPrefs()
        val firstOpen = prefs.getLong("funnel_first_open", 0L)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)

        fun funnelLine(label: String, key: String): String {
            val ts = prefs.getLong(key, 0L)
            return when {
                ts == 0L -> "$label : pas encore"
                firstOpen == 0L || ts <= firstOpen -> "$label : ${dateFormat.format(Date(ts))}"
                else -> {
                    val minutes = (ts - firstOpen) / 60000
                    val delta = when {
                        minutes < 1 -> "moins d'une minute après l'ouverture"
                        minutes < 60 -> "$minutes min après l'ouverture"
                        minutes < 1440 -> "${minutes / 60} h après l'ouverture"
                        else -> "${minutes / 1440} j après l'ouverture"
                    }
                    "$label : $delta"
                }
            }
        }

        val lines = TextView(this).apply {
            text = listOf(
                if (firstOpen == 0L) "Première ouverture : pas encore"
                else "Première ouverture : ${dateFormat.format(Date(firstOpen))}",
                funnelLine("Premier essai (clavier de démo)", "funnel_demo_first_key"),
                funnelLine("Clavier activé", "funnel_keyboard_enabled"),
                funnelLine("Retour sans avoir activé", "funnel_settings_return_no_enable"),
                funnelLine("Clavier sélectionné", "funnel_keyboard_selected"),
                funnelLine("Premier mot tapé", "funnel_first_word")
            ).joinToString("\n")
            textSize = 14f
            setTextColor(Color.parseColor("#333333"))
            setLineSpacing(0f, 1.5f)
        }
        card.addView(lines)

        val note = TextView(this).apply {
            text = "Ces horodatages restent sur votre téléphone."
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 8, 0, 0)
        }
        card.addView(note)

        return card
    }

    fun createGuideContent(): LinearLayout {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 32)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val guideTitle = TextView(this).apply {
            text = "📖 Guide de l'utilisateur"
            textSize = 20f
            setTextColor(Color.parseColor("#0080FF"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        mainLayout.addView(guideTitle)
        mainLayout.addView(createSpacing(8))

        addGuideSection(
            mainLayout, "#E3F2FD", "📲 Installation et activation",
            "Le clavier doit être activé puis sélectionné avant de pouvoir l'utiliser, comme " +
                    "n'importe quel clavier tiers sur Android. Ces étapes interactives sont aussi " +
                    "disponibles dans l'onglet « Démarrage » ; voici à quoi elles ressemblent."
        )

        addGuideSection(
            mainLayout, "#FFFFFF", "1️⃣ Ouvrir les paramètres de clavier",
            "Depuis l'onglet Démarrage, le bouton « Ouvrir les réglages Android » mène directement " +
                    "à l'écran système « Clavier à l'écran », où « Klavyé Kréyòl Karukéra » apparaît " +
                    "à côté des autres claviers installés, interrupteur éteint."
        )
        addGuideImage(mainLayout, R.drawable.guide_screenshot_install_settings, "Écran système listant les claviers, interrupteur à activer")

        addGuideSection(
            mainLayout, "#FFF8E1", "2️⃣ Valider les avertissements Android",
            "En activant l'interrupteur, Android affiche un avertissement générique montré pour " +
                    "tous les claviers tiers, suivi d'une seconde note sur le redémarrage du téléphone. " +
                    "Klavyé Kréyòl ne collecte aucune donnée : appuyez sur OK aux deux pour continuer, " +
                    "puis revenez à l'application avec le bouton retour."
        )
        addGuideImage(mainLayout, R.drawable.guide_screenshot_install_warning, "Avertissement système affiché pour tout clavier tiers")

        addGuideSection(
            mainLayout, "#FFFFFF", "3️⃣ Sélectionner le clavier",
            "De retour dans l'application, l'étape 1 est cochée automatiquement et l'étape 2 se " +
                    "débloque. Le bouton « Ouvrir le sélecteur de claviers » ouvre la liste des claviers " +
                    "actifs : touchez « Klavyé Kréyòl Karukéra » pour en faire le clavier utilisé."
        )
        addGuideImage(mainLayout, R.drawable.guide_screenshot_install_picker, "Sélecteur système de mode de saisie")

        addGuideSection(
            mainLayout, "#F0F8E8", "✅ Configuration terminée",
            "Ces deux étapes faites, le clavier Kréyòl s'affiche partout où vous tapez, y compris " +
                    "dans le champ d'essai de l'onglet Démarrage. Un appui long sur la barre d'espace " +
                    "permet de rebasculer vers un autre clavier à tout moment."
        )
        addGuideImage(mainLayout, R.drawable.guide_screenshot_install_done, "Les trois étapes cochées, clavier actif dans le champ d'essai")

        addGuideSection(
            mainLayout, "#FFFFFF", "✍️ Écrire en Kréyòl",
            "Le clavier démarre en mode alphabétique. La première lettre de chaque " +
                    "message prend automatiquement une majuscule, comme sur un clavier classique."
        )

        addGuideSection(
            mainLayout, "#F0F8E8", "🔤 Accents et caractères spéciaux",
            "Appuyez longuement sur une lettre pour faire apparaître ses variantes accentuées " +
                    "(é, è, à, ò...) et caractères spéciaux du kréyòl. La fenêtre reste ouverte quand " +
                    "vous relâchez : touchez l'accent voulu pour l'insérer."
        )
        addGuideImage(mainLayout, R.drawable.guide_screenshot_accents, "Popup d'accents sur la lettre e")

        addGuideSection(
            mainLayout, "#FFFFFF", "💡 Suggestions et autocomplétion",
            "Une barre de suggestions apparaît au-dessus du clavier dès que vous tapez. " +
                    "Les mots en Kréyòl sont prioritaires ; le français prend le relais à partir de " +
                    "3 lettres si aucun mot créole ne correspond. Touchez un mot suggéré pour le compléter " +
                    "instantanément, espace inclus."
        )
        addGuideImage(mainLayout, R.drawable.guide_screenshot_suggestions, "Barre de suggestions active")

        addGuideSection(
            mainLayout, "#F0F8E8", "✅ Correction orthographique partout",
            "Activez le correcteur Kréyòl dans les paramètres système : onglet Démarrage, carte " +
                    "« Corriger l'orthographe partout », sous « Pou ay pli lwen ». Vos mots créoles " +
                    "et français cessent alors d'être soulignés en rouge dans Messages, Notes et " +
                    "les autres applications."
        )

        addGuideSection(
            mainLayout, "#FFFFFF", "🔢 Chiffres et symboles",
            "Le bouton « 123 » en bas à gauche du clavier bascule vers les chiffres et symboles usuels. " +
                    "La ponctuation de base (virgule, point, apostrophe) reste accessible directement " +
                    "sur le clavier alphabétique."
        )
        addGuideImage(mainLayout, R.drawable.guide_screenshot_numeric, "Mode chiffres et symboles")

        addGuideSection(
            mainLayout, "#F0F8E8", "🎮 Jeux de vocabulaire",
            "Quatre jeux — « Mots Mêlés », « Mots Mélangés », « Mo an Karénaj » et " +
                    "« Fraz a twou » — sont réunis sous l'onglet « Jé ». Ils font revoir du " +
                    "vocabulaire créole en s'amusant, à partir des mots déjà présents dans le " +
                    "dictionnaire du clavier."
        )

        addGuideSection(
            mainLayout, "#FFFFFF", "🏆 Progression",
            "Chaque mot que vous tapez fait progresser votre maîtrise du kréyòl, visible dans l'onglet " +
                    "« Kréyòl an mwen ». Sept niveaux culturels jalonnent le parcours : Pipirit, Ti moun, " +
                    "Débrouya, An mitan, Kompè Lapen, Kompè Zamba, Potomitan. Un huitième existe, " +
                    "à vous de le trouver."
        )

        val faqCard = createCard("#FFF8E1")
        val faqTitle = TextView(this).apply {
            text = "❓ Questions fréquentes"
            textSize = 18f
            setTextColor(Color.parseColor("#5D4037"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        val faqText = TextView(this).apply {
            text = "Le clavier créole n'apparaît pas quand je tape ?\n" +
                    "→ Vérifiez qu'il est bien sélectionné (pas seulement activé) : onglet Démarrage, " +
                    "étape 2, ou touchez l'icône de clavier de la barre de navigation, en bas de " +
                    "l'écran, pendant que vous écrivez.\n\n" +
                    "Comment revenir à un autre clavier ponctuellement ?\n" +
                    "→ Appui long sur la barre d'espace du clavier créole, puis choisissez un autre " +
                    "clavier dans la liste. Le retour au kréyòl passe par l'icône de clavier de la " +
                    "barre de navigation : sur les autres claviers, l'appui long sur la barre " +
                    "d'espace ne change que leur propre langue.\n\n" +
                    "Mes données sont-elles envoyées quelque part ?\n" +
                    "→ Non : le clavier fonctionne entièrement en local."
            textSize = 14f
            setTextColor(Color.parseColor("#5D4037"))
            setLineSpacing(0f, 1.3f)
        }
        val faqPrivacyLink = TextView(this).apply {
            text = "Lire la politique de confidentialité"
            textSize = 14f
            setTextColor(Color.parseColor("#0080FF"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 12, 0, 0)
            setOnClickListener { openPrivacyPolicy() }
        }
        faqCard.addView(faqTitle)
        faqCard.addView(faqText)
        faqCard.addView(faqPrivacyLink)
        mainLayout.addView(faqCard)

        return mainLayout
    }

    private fun addGuideSection(parent: LinearLayout, backgroundColor: String, title: String, body: String) {
        val card = createCard(backgroundColor)
        val titleView = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        val bodyView = TextView(this).apply {
            text = body
            textSize = 14f
            setTextColor(Color.parseColor("#333333"))
            setLineSpacing(0f, 1.3f)
        }
        card.addView(titleView)
        card.addView(bodyView)
        parent.addView(card)
        parent.addView(createSpacing(16))
    }

    private fun addGuideImage(parent: LinearLayout, drawableResId: Int, description: String) {
        val card = createCard("#FFFFFF")
        val image = ImageView(this).apply {
            setImageResource(drawableResId)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = description
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val caption = TextView(this).apply {
            text = description
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }
        card.addView(image)
        card.addView(caption)
        parent.addView(card)
        parent.addView(createSpacing(16))
    }

    // Helpers pour créer les éléments UI
    private fun createCard(backgroundColor: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor(backgroundColor))
        }
    }
    
    private fun createSpacing(heightDp: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (heightDp * resources.displayMetrics.density).toInt()
            )
        }
    }
    
    private fun createChecklistItem(isChecked: Boolean, title: String, description: String): LinearLayout {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        
        val checkbox = TextView(this).apply {
            text = if (isChecked) "✅" else "⚠️"
            textSize = 24f
            setPadding(0, 0, 16, 0)
        }
        
        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        
        val titleText = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(if (isChecked) Color.parseColor("#228B22") else Color.parseColor("#FF6B35"))
            setTypeface(null, Typeface.BOLD)
        }
        
        val descText = TextView(this).apply {
            text = description
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setLineSpacing(0f, 1.2f)
        }
        
        textContainer.addView(titleText)
        textContainer.addView(descText)
        
        item.addView(checkbox)
        item.addView(textContainer)
        
        return item
    }
    
    private fun createGuideCard(icon: String, title: String, description: String): LinearLayout {
        val card = createCard("#FFFFFF")
        
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        
        val iconText = TextView(this).apply {
            text = icon
            textSize = 28f
            setPadding(0, 0, 16, 0)
        }
        
        val titleText = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        
        header.addView(iconText)
        header.addView(titleText)
        
        val descText = TextView(this).apply {
            text = description
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            setLineSpacing(0f, 1.3f)
            setPadding(0, 8, 0, 0)
        }
        
        card.addView(header)
        card.addView(descText)
        
        return card
    }
    

    // Fonction pour vérifier si le clavier est activé
    fun isKeyboardEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledIMEs = imm.enabledInputMethodList
        val myPackageName = packageName
        
        return enabledIMEs.any { it.packageName == myPackageName }
    }
    
    // Fonction pour vérifier si le clavier est sélectionné comme clavier actif
    fun isKeyboardSelected(): Boolean {
        try {
            val currentIme = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
            return currentIme?.contains(packageName) == true
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur vérification clavier sélectionné: ${e.message}")
            return false
        }
    }
    
    // Fonction pour vérifier si notre correcteur orthographique est sélectionné
    fun isSpellCheckerSelected(): Boolean {
        return try {
            val current = Settings.Secure.getString(contentResolver, "selected_spell_checker")
            current?.contains(packageName) == true
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur vérification correcteur sélectionné: ${e.message}")
            false
        }
    }

    // Fonction pour ouvrir les paramètres où choisir le correcteur orthographique
    private fun openSpellCheckerSettings() {
        // ACTION_INPUT_METHOD_SETTINGS ouvre la liste des CLAVIERS, pas le
        // sélecteur de correcteur orthographique. Le seul point d'entrée public
        // vers cet écran est ce composant Settings (standard AOSP depuis
        // Android 4.2), avec repli sur l'écran clavier si absent sur certains ROM.
        try {
            val intent = Intent().apply {
                setClassName("com.android.settings", "com.android.settings.Settings\$SpellCheckersSettingsActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur ouverture écran correcteur, repli sur les paramètres clavier: ${e.message}")
            try {
                val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                // Seul cas où un Toast d'instruction reste utile : l'écran de
                // repli n'est pas celui attendu, la carte de l'étape 4 ne
                // décrit donc pas ce que l'utilisateur a sous les yeux
                Toast.makeText(this,
                    "Dans 'Langues et saisie', ouvrez 'Vérification orthographique' et choisissez 'Correcteur Kréyòl Karukéra'",
                    Toast.LENGTH_LONG
                ).show()
            } catch (ex: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                } catch (ex2: Exception) {
                    Toast.makeText(this, "Impossible d'ouvrir les paramètres", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Interstitiel montrant l'avertissement Android réel (capturé sur
    // l'émulateur) avant d'y envoyer l'utilisateur : le voir à l'avance,
    // annoté, le désamorce mieux qu'une description abstraite dans une
    // carte qu'il a pu ne pas lire. Un seul bouton d'action ; pas de bouton
    // d'annulation explicite, le retour matériel suffit à fermer sans
    // naviguer ailleurs.
    private fun showPreSettingsWarningDialog() {
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }

        val warningImage = ImageView(this).apply {
            setImageResource(R.drawable.onboarding_ime_warning_preview)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val annotationText = TextView(this).apply {
            text = "👆 Appuyez sur OK : c'est normal pour tous les claviers tiers"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#333333"))
            setPadding(0, 24, 0, 12)
            setLineSpacing(0f, 1.2f)
        }

        val reassuranceText = TextView(this).apply {
            text = "Klavyé Kréyòl n'a pas accès à Internet : rien ne quitte votre téléphone."
            textSize = 16f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, 0, 0, 12)
            setLineSpacing(0f, 1.2f)
        }

        val returnHintText = TextView(this).apply {
            text = "◀ Ensuite, appuyez sur Retour : vous revenez ici automatiquement"
            textSize = 16f
            setTextColor(Color.parseColor("#666666"))
            setLineSpacing(0f, 1.2f)
        }

        dialogLayout.addView(warningImage)
        dialogLayout.addView(annotationText)
        dialogLayout.addView(reassuranceText)
        dialogLayout.addView(returnHintText)

        val scrollView = ScrollView(this).apply { addView(dialogLayout) }

        AlertDialog.Builder(this)
            .setTitle("Avant de continuer")
            .setView(scrollView)
            .setCancelable(true)
            .setPositiveButton("J'ai compris, on y va") { _, _ -> openKeyboardSettings() }
            .show()
    }

    // Ouvre les paramètres de clavier système. Pas de Toast d'instruction :
    // la carte de l'étape 1 dit déjà quoi faire, avant le saut vers les
    // réglages (le Toast s'affichait par-dessus l'écran système, en bas,
    // sans garantie de position ni de durée suffisante)
    private fun openKeyboardSettings() {
        try {
            // Horodater le départ vers les réglages : si l'utilisateur
            // revient sans avoir activé le clavier (abandon au premier des
            // deux avertissements, ligne pas trouvée...), l'onboarding
            // affiche une carte d'encouragement ciblée
            onboardingPrefs().edit()
                .putLong("settings_visit_at", System.currentTimeMillis()).apply()
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            // Tentative de surlignage de la ligne IME dans l'écran système :
            // extra non documenté, respecté par les Settings AOSP/Pixel,
            // ignoré silencieusement ailleurs (pas d'effet de bord).
            intent.putExtra(
                ":settings:fragment_args_key",
                "$packageName/com.example.kreyolkeyboard.KreyolInputMethodServiceRefactored"
            )
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur ouverture paramètres clavier: ${e.message}")
            // Fallback vers paramètres généraux
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(this, "Impossible d'ouvrir les paramètres", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun openPrivacyPolicy() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur ouverture politique de confidentialité: ${e.message}")
            Toast.makeText(this, "Impossible d'ouvrir la politique de confidentialité", Toast.LENGTH_SHORT).show()
        }
    }

    // Ouvre la fiche Play Store pour noter l'app (complément de l'In-App Review, soumis à quota)
    private fun openPlayStoreListing() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
            } catch (ex: Exception) {
                Log.e("SettingsActivity", "Erreur ouverture fiche Play Store: ${ex.message}")
                Toast.makeText(this, "Play Store indisponible", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Fonction pour partager l'application (bouche-à-oreille)
    private fun shareApp() {
        val message = "Mwen ka sèvi épi Klavyé Kréyòl Karukéra pou maké kréyòl asi téléfòn an mwen !\n" +
                "Sé on klavyé Android gratui ki ba'w sigjesyon mo an kréyòl Gwadloup.\n\n" +
                "Télécharge-le gratuitement :\n" +
                "https://play.google.com/store/apps/details?id=$packageName" +
                "&referrer=utm_source%3Din_app_share%26utm_campaign%3Dlaunch10k\n\n" +
                SHARE_HASHTAG
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
            startActivity(Intent.createChooser(intent, "Partager Klavyé Kréyòl Karukéra"))
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur partage application: ${e.message}")
            Toast.makeText(this, "Impossible de partager pour le moment", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Carte « Revenir au clavier kréyòl », en tête de l'onglet Démarrage quand
     * le clavier est installé mais qu'un autre est en service.
     *
     * Elle existe parce qu'Android ne laisse pas un clavier se réactiver
     * lui-même : `showInputMethodPicker()` ne fonctionne que depuis le clavier
     * courant ou depuis une activité au premier plan. Une fois parti, le
     * clavier ne peut plus rien pour l'utilisateur ; seule l'application le
     * peut, et encore faut-il qu'elle le propose au lieu de rejouer un tunnel
     * de configuration en trois étapes à quelqu'un qui a tout configuré.
     *
     * Le clavier n'est ni désinstallé ni désactivé dans cet état, et le texte
     * le dit : la seule chose à faire est de le rechoisir. Un seul bouton,
     * pleine largeur, et l'autre chemin — l'icône de la barre de navigation —
     * rappelé en dessous pour la fois d'après, puisque c'est celui qui marche
     * sans quitter l'application où l'on écrit.
     *
     * Rien à rafraîchir à la main : [OnboardingFragment] observe
     * `DEFAULT_INPUT_METHOD` et reconstruit l'onglet dès que le choix est fait,
     * ce qui fait disparaître la carte et apparaître le bandeau de réussite.
     *
     * Les tailles de texte sont au-dessus de celles des autres cartes de
     * l'onglet : c'est l'écran que lit quelqu'un qui a perdu son clavier et qui
     * ne sait pas pourquoi, et rien d'utile n'y descend sous 16 sp.
     */
    private fun createRetourClavierCard(): LinearLayout {
        val card = createRoundedCard("#FFF3E0")

        val titre = TextView(this).apply {
            text = "⌨️ Revenir au clavier kréyòl"
            textSize = 19f
            setTextColor(Color.parseColor("#E65100"))
            setTypeface(null, Typeface.BOLD)
            setLineSpacing(0f, 1.25f)
            setPadding(0, 0, 0, enDp(10))
        }

        val explication = TextView(this).apply {
            text = "En ce moment, c'est un autre clavier qui s'ouvre quand vous écrivez. " +
                    "Le clavier kréyòl est toujours installé sur votre téléphone : " +
                    "il suffit de le rechoisir."
            textSize = 16f
            setTextColor(Color.parseColor("#5D4037"))
            setLineSpacing(0f, 1.35f)
            setPadding(0, 0, 0, enDp(16))
        }

        val bouton = Button(this).apply {
            text = "Choisir le clavier kréyòl"
            textSize = 17f
            // Le thème AppCompat met les Button en capitales dans un contexte
            // d'activité : les autres appels à l'action de l'onglet n'en sont
            // pas, celui-ci n'a pas à détonner.
            isAllCaps = false
            setBackgroundColor(Color.parseColor("#0080FF"))
            setTextColor(Color.WHITE)
            minHeight = enDp(56)
            setPadding(enDp(16), enDp(14), enDp(16), enDp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { openInputMethodPicker() }
        }

        val autreChemin = TextView(this).apply {
            text = "Sans passer par ici : pendant que vous écrivez, touchez la petite icône de " +
                    "clavier en bas de l'écran, dans la barre de navigation. Le même choix s'ouvre."
            textSize = 16f
            setTextColor(Color.parseColor("#795548"))
            setLineSpacing(0f, 1.35f)
            setPadding(0, enDp(14), 0, 0)
        }

        card.addView(titre)
        card.addView(explication)
        card.addView(bouton)
        card.addView(autreChemin)
        return card
    }

    // Ouvre le sélecteur de clavier système, immédiatement. Ne pas ajouter de
    // Toast d'instruction ici : sa gravité est ignorée depuis API 30, il
    // s'affiche en bas par-dessus le sélecteur et masque l'entrée à choisir ;
    // l'instruction est déjà portée par la carte de l'étape 2, visible
    // derrière le dialogue.
    private fun openInputMethodPicker() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur ouverture sélecteur clavier: ${e.message}")
            Toast.makeText(this, 
                "Impossible d'ouvrir le sélecteur. Touchez l'icône de clavier en bas de l'écran, " +
                    "dans la barre de navigation, pendant que vous écrivez.", 
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    fun createStatsContent(): LinearLayout {
        Log.d("SettingsActivity", "Création du contenu des statistiques")
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        val stats = loadVocabularyStats()
        Log.d("SettingsActivity", "Stats chargées: ${stats.wordsDiscovered} mots découverts, ${stats.totalUsages} utilisations")
        
        // Container principal
        val statsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 32)
        }
        
        // === Niveau - Badge minimaliste ===
        val level = getCurrentLevel(stats.wordsDiscovered)
        val levelParts = level.split(" ")
        val levelEmoji = levelParts[0]
        val levelName = if (levelParts.size > 1) levelParts.drop(1).joinToString(" ") else ""
        
        // Calcul des mots restants pour le niveau suivant
        val (nextLevelName, wordsRemaining) = getNextLevelInfo(stats.wordsDiscovered)

        // L'utilisateur consulte enfin sa progression : la pastille de l'onglet
        // a rempli son office, on l'éteint et on redessine la barre.
        if (hasPendingLevelBadge()) {
            gamificationPrefs().edit().putBoolean(PREF_LEVEL_BADGE_PENDING, false).apply()
            if (::tabBar.isInitialized) tabBar.post { updateTabBar() }
        }

        // La notification et la pastille d'icône disent « il y a quelque chose à
        // voir ici » : cet écran est précisément ce quelque chose. setAutoCancel
        // ne les efface qu'au tap sur la notification, si bien que l'utilisateur
        // arrivé par le lanceur gardait une pastille sur son écran d'accueil
        // après avoir déjà tout vu.
        LevelUpNotifier.clear(this)

        // Célébration + carte partageable si un niveau vient d'être franchi
        maybeCelebrateLevelUp(stats.wordsDiscovered, levelEmoji, levelName)

        // 🔍 DEBUG: Log pour vérifier les calculs
        val thresholdsDebug = calculateGaussianThresholds()
        Log.d("SettingsActivity", "📊 DEBUG Niveau: wordsDiscovered=${stats.wordsDiscovered}, " +
                "levelName=$levelName, nextLevelName=$nextLevelName, wordsRemaining=$wordsRemaining")
        Log.d("SettingsActivity", "📊 DEBUG Seuils: ${thresholdsDebug.joinToString(", ")}")
        
        val levelContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 40)
        }
        
        // Message de progression vers le niveau suivant
        val progressMessage = TextView(this).apply {
            text = if (wordsRemaining > 0) {
                "Votre niveau actuel est $levelName, plus que $wordsRemaining mot${if (wordsRemaining > 1) "s" else ""} restant${if (wordsRemaining > 1) "s" else ""} à découvrir pour passer au niveau suivant ($nextLevelName)"
            } else if (levelName == "Benzo") {
                "Vous avez atteint le niveau maximum : $levelName ! 👑"
            } else {
                "Votre niveau actuel est $levelName"
            }
            textSize = 16f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setPadding(16, 0, 16, 24)
            setLineSpacing(6f, 1f)
        }
        
        levelContainer.addView(progressMessage)

        // Partage permanent de la carte de niveau. Jusqu'ici, shareLevelCard()
        // n'était atteignable que par le bouton de la boîte de célébration :
        // répondre « Plus tard » perdait la carte définitivement, puisque le
        // palier était déjà marqué comme célébré et que la boîte ne
        // réapparaissait jamais. L'astuce qui promet de partager sa carte
        // « depuis Kréyòl an mwen » décrit désormais quelque chose qui existe.
        val shareLevelButton = Button(this).apply {
            text = "📤 Partager ma carte de niveau"
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0E6E76"))
            setPadding(24, 16, 24, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24 }
            setOnClickListener {
                try {
                    shareLevelCard(
                        buildLevelCardBitmap(levelEmoji, levelName, stats.wordsDiscovered),
                        levelName
                    )
                } catch (e: Exception) {
                    Log.e("SettingsActivity", "Erreur partage carte de niveau: ${e.message}")
                    Toast.makeText(
                        this@SettingsActivity,
                        "Impossible de partager pour le moment",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        levelContainer.addView(shareLevelButton)

        val levelBadge = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(24, 16, 24, 16)
        }
        
        val levelEmojiText = TextView(this).apply {
            text = levelEmoji
            textSize = 48f
            setPadding(0, 0, 16, 0)
        }
        
        val levelNameText = TextView(this).apply {
            text = levelName
            textSize = 28f
            setTextColor(Color.parseColor("#1C1C1C"))
            setTypeface(null, Typeface.BOLD)
        }
        
        levelBadge.addView(levelEmojiText)
        levelBadge.addView(levelNameText)
        
        val percentageText = TextView(this).apply {
            text = "${String.format("%.1f", stats.coveragePercentage)}%"
            textSize = 32f
            setTextColor(Color.parseColor("#1C1C1C"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 8)
        }
        
        val percentageLabel = TextView(this).apply {
            text = "${stats.wordsDiscovered} mots découverts sur les ${stats.totalWords} mots du dictionnaire Kréyòl"
            textSize = 14f
            setTextColor(Color.parseColor("#999999"))
            gravity = Gravity.CENTER
        }
        
        levelContainer.addView(levelBadge)
        levelContainer.addView(percentageText)
        levelContainer.addView(percentageLabel)
        
        // === Mot du Jour - Design épuré ===
        val (wordOfDay, usageCount) = getWordOfTheDay()
        
        val wordContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 40, 24, 40)
            setBackgroundColor(Color.parseColor("#FAFAFA"))
        }
        
        val wordLabel = TextView(this).apply {
            text = "MOT DU JOUR"
            textSize = 12f
            setTextColor(Color.parseColor("#FF8C00"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.1f
            setPadding(0, 0, 0, 16)
        }
        
        val wordText = TextView(this).apply {
            text = wordOfDay
            textSize = 48f
            setTextColor(Color.parseColor("#1C1C1C"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        
        val wordUsage = TextView(this).apply {
            text = if (usageCount > 0) "utilisé $usageCount fois" else "nouveau mot à découvrir"
            textSize = 14f
            setTextColor(Color.parseColor("#999999"))
            gravity = Gravity.CENTER
        }
        
        // La traduction est annoncée plutôt que posée sous le mot : « chaussure »
        // seul, sous un mot kréyòl, ne dit pas dans quelle langue on le lit.
        val wordGloss = TranslationDictionary.traduire(this, wordOfDay)?.let { glose ->
            TextView(this).apply {
                text = "en français : $glose"
                textSize = 16f
                setTextColor(Color.parseColor("#666666"))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 16)
            }
        }

        wordContainer.addView(wordLabel)
        wordContainer.addView(wordText)
        wordGloss?.let { wordContainer.addView(it) }
        wordContainer.addView(wordUsage)
        
        // === Top 5 - Liste simple ===
        val top5Container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 0, 24, 40)
        }
        
        val top5Title = TextView(this).apply {
            text = "Mots les plus utilisés"
            textSize = 16f
            setTextColor(Color.parseColor("#1C1C1C"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 24)
        }
        
        top5Container.addView(top5Title)
        
        stats.topWords.take(5).forEachIndexed { index, word ->
            val wordRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, 16)
            }
            
            val rank = TextView(this).apply {
                text = "${index + 1}."
                textSize = 20f
                setTextColor(Color.parseColor("#FF8C00"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 16, 0)
            }
            
            // Le mot et sa glose forment une colonne, pour que la traduction
            // s'aligne sous le mot et non entre lui et son compteur.
            val wordName = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                addView(TextView(this@SettingsActivity).apply {
                    text = word.first
                    textSize = 20f
                    setTextColor(Color.parseColor("#1C1C1C"))
                })
                TranslationDictionary.traduire(this@SettingsActivity, word.first)?.let { glose ->
                    addView(TextView(this@SettingsActivity).apply {
                        text = glose
                        textSize = 13f
                        setTextColor(Color.parseColor("#999999"))
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                }
            }

            val wordCount = TextView(this).apply {
                text = "${word.second}"
                textSize = 20f
                setTextColor(Color.parseColor("#999999"))
                gravity = Gravity.END
            }
            
            wordRow.addView(rank)
            wordRow.addView(wordName)
            wordRow.addView(wordCount)
            top5Container.addView(wordRow)
        }
        
        // === Statistiques - Grille 2x2 ===
        val statsGridContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 40, 24, 40)
        }
        
        val statsGridTitle = TextView(this).apply {
            text = "Statistiques globales"
            textSize = 16f
            setTextColor(Color.parseColor("#1C1C1C"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 32)
        }
        
        statsGridContainer.addView(statsGridTitle)
        
        // Ligne unique: Découverts | Utilisations
        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 32)
        }
        
        statsRow.addView(createStatBlock("${stats.wordsDiscovered}", "Mots découverts"))
        statsRow.addView(createStatBlock("${stats.totalUsages}", "Utilisations"))
        
        statsGridContainer.addView(statsRow)
        
        // === Mots à Découvrir ===
        val wordsToDiscoverContainer = createWordListSection(
            "🌟 Mots à Découvrir",
            stats.wordsToDiscover,
            "#2196F3"
        )
        
        // === Mots Découverts ===
        val discoveredWordsContainer = createWordListSection(
            "🔍 Mots Découverts (${stats.discoveredWordsList.size})",
            stats.discoveredWordsList,
            "#4CAF50"
        )
        
        // Assembler
        statsContainer.addView(levelContainer)
        statsContainer.addView(wordContainer)
        statsContainer.addView(wordsToDiscoverContainer)
        statsContainer.addView(top5Container)
        statsContainer.addView(statsGridContainer)
        statsContainer.addView(discoveredWordsContainer)
        
        mainLayout.addView(statsContainer)
        
        return mainLayout
    }
    
    private fun createStatBlock(number: String, label: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            
            val numText = TextView(this@SettingsActivity).apply {
                text = number
                textSize = 36f
                setTextColor(Color.parseColor("#1C1C1C"))
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 8)
            }
            
            val labelText = TextView(this@SettingsActivity).apply {
                text = label
                textSize = 12f
                setTextColor(Color.parseColor("#999999"))
                gravity = Gravity.CENTER
            }
            
            addView(numText)
            addView(labelText)
        }
    }
    
    private fun createWordListSection(title: String, words: List<String>, accentColor: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            
            // Titre de la section
            val sectionTitle = TextView(this@SettingsActivity).apply {
                text = title
                textSize = 24f  // Augmenté de 1.5x (16f * 1.5)
                setTextColor(Color.parseColor("#1C1C1C"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 16)
            }
            addView(sectionTitle)
            
            if (words.isEmpty()) {
                // Message si aucun mot
                val emptyMessage = TextView(this@SettingsActivity).apply {
                    text = "Aucun mot dans cette catégorie pour le moment"
                    textSize = 21f  // Augmenté de 1.5x (14f * 1.5)
                    setTextColor(Color.parseColor("#999999"))
                    setTypeface(null, Typeface.ITALIC)
                    setPadding(16, 12, 16, 12)
                    setBackgroundColor(Color.parseColor("#F5F5F5"))
                }
                addView(emptyMessage)
            } else {
                // Conteneur pour les mots avec scroll
                val scrollView = ScrollView(this@SettingsActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        450 // Hauteur maximale augmentée de 1.5x (300 * 1.5)
                    )
                }
                
                // Container avec retour à la ligne automatique (FlowLayout simulé)
                val wordsContainer = LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(12, 12, 12, 12)
                    setBackgroundColor(Color.parseColor("#FAFAFA"))
                }
                
                // Créer des lignes dynamiques qui s'adaptent à la largeur
                var currentRow = LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 6
                    }
                }
                wordsContainer.addView(currentRow)
                
                var currentRowWidth = 0
                // Calculer la largeur disponible: largeur écran - padding container (24) - padding statsContainer (48) - marges (24)
                val screenWidth = resources.displayMetrics.widthPixels - 96
                
                words.forEach { word ->
                    // Créer le chip du mot
                    val wordChip = TextView(this@SettingsActivity).apply {
                        text = word
                        textSize = 19.5f  // Augmenté de 1.5x (13f * 1.5)
                        setTextColor(Color.parseColor(accentColor))
                        setPadding(15, 7, 15, 7)  // Augmenté de 1.5x (10, 5, 10, 5)
                        setBackgroundColor(avecOpacite(accentColor, 0x20))
                        setSingleLine(true)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            rightMargin = 5
                            bottomMargin = 5
                        }
                    }
                    
                    // Mesurer la largeur du mot avant de l'ajouter
                    wordChip.measure(
                        View.MeasureSpec.UNSPECIFIED,
                        View.MeasureSpec.UNSPECIFIED
                    )
                    val wordWidth = wordChip.measuredWidth + 10 // +marge droite + espace sécurité
                    
                    // Si le mot ne rentre pas dans la ligne actuelle, créer une nouvelle ligne
                    if (currentRowWidth + wordWidth > screenWidth && currentRowWidth > 0) {
                        currentRow = LinearLayout(this@SettingsActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.START or Gravity.CENTER_VERTICAL
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                bottomMargin = 6
                            }
                        }
                        wordsContainer.addView(currentRow)
                        currentRowWidth = 0
                    }
                    
                    currentRow.addView(wordChip)
                    currentRowWidth += wordWidth
                }
                
                scrollView.addView(wordsContainer)
                addView(scrollView)
            }
        }
    }
    
    // === Fonctions de chargement de données ===
    
    data class VocabularyStats(
        val totalWords: Int,
        val wordsDiscovered: Int,
        val totalUsages: Int,
        val topWords: List<Pair<String, Int>>,
        val coveragePercentage: Float,
        val discoveredWordsList: List<String>,
        val wordsToDiscover: List<String>
    )
    
    private fun loadVocabularyStats(): VocabularyStats {
        Log.d("SettingsActivity", "🔍 Chargement des statistiques du vocabulaire")
        return try {
            // Toujours charger le total depuis le dictionnaire source
            val totalDictWords = getTotalDictionaryWords()
            
            // Essayer le fichier avec usage
            val usageFile = File(filesDir, "creole_dict_with_usage.json")
            Log.d("SettingsActivity", "📂 Fichier usage existe: ${usageFile.exists()}")
            Log.d("SettingsActivity", "📂 Chemin fichier: ${usageFile.absolutePath}")
            
            if (usageFile.exists()) {
                val jsonString = usageFile.readText()
                Log.d("SettingsActivity", "📄 Contenu fichier (${jsonString.length} chars): ${jsonString.take(200)}...")
                val jsonObject = JSONObject(jsonString)
                Log.d("SettingsActivity", "🔑 Clés JSON trouvées: ${jsonObject.keys().asSequence().toList().size}")
                
                var wordsDiscovered = 0
                var totalUsages = 0
                val wordUsages = mutableListOf<Pair<String, Int>>()
                val discoveredWords = mutableListOf<String>()
                
                val motsTrouves = mutableListOf<String>()
                jsonObject.keys().forEach { word ->
                    // Gérer les deux formats possibles
                    val userCount = try {
                        val rawValue = jsonObject.get(word)
                        when (rawValue) {
                            is Int -> {
                                // Format simplifié: "mot": 1
                                rawValue
                            }
                            is JSONObject -> {
                                // Format complet: "mot": {"frequency": X, "user_count": Y}
                                rawValue.optInt("user_count", 0)
                            }
                            else -> 0
                        }
                    } catch (e: Exception) {
                        Log.e("SettingsActivity", "Erreur lecture '$word': ${e.message}")
                        0
                    }
                    
                    if (userCount > 0) {
                        totalUsages += userCount
                        wordUsages.add(Pair(word, userCount))
                        motsTrouves.add("$word($userCount)")

                        // Un mot est "découvert" dès qu'il a été utilisé au moins une fois
                        // (même définition que CreoleDictionaryWithUsage.getDiscoveredWordsCount())
                        wordsDiscovered++
                        // Ne garder que les mots de 3 lettres ou plus pour l'affichage
                        if (word.length >= 3) {
                            discoveredWords.add(word)
                        }
                    }
                }
                
                Log.d("SettingsActivity", "Mots avec usage > 0: ${motsTrouves.joinToString(", ")}")
                Log.d("SettingsActivity", "Total: $totalDictWords mots, Usage: $totalUsages, Découverts: $wordsDiscovered")
                
                val topWords = wordUsages.filter { it.first.length >= 3 }.sortedByDescending { it.second }.take(5)
                val coverage = if (totalDictWords > 0) (wordsDiscovered.toFloat() / totalDictWords * 100) else 0f
                
                // Générer les mots à découvrir (utilisations <= 2 et longueur >= 3)
                val wordsToDiscoverCandidates = jsonObject.keys().asSequence().toList().filter { word ->
                    val count = jsonObject.optInt(word, 0)
                    count <= 2 && word.length >= 3
                }
                val wordsToDiscoverList = wordsToDiscoverCandidates.shuffled().take(5)
                
                return VocabularyStats(
                    totalDictWords,
                    wordsDiscovered,
                    totalUsages,
                    topWords,
                    coverage,
                    discoveredWords.sorted(),
                    wordsToDiscoverList
                )
            }
            
            // Sinon créer un fichier vide pour la première installation
            val emptyUsageObject = JSONObject()
            usageFile.writeText(emptyUsageObject.toString())
            
            // Retourner des statistiques avec le vrai total de mots du dictionnaire
            return VocabularyStats(
                totalWords = totalDictWords,
                wordsDiscovered = 0,
                totalUsages = 0,
                topWords = emptyList(),
                coveragePercentage = 0f,
                discoveredWordsList = emptyList(),
                wordsToDiscover = emptyList()
            )
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur chargement stats: ${e.message}")
            VocabularyStats(0, 0, 0, emptyList(), 0f, emptyList(), emptyList())
        }
    }
    
    // Niveaux : la logique vit désormais dans gamification/CreoleLevels.kt, pour
    // que le service de saisie puisse détecter un passage de niveau au moment
    // où l'utilisateur tape. Les méthodes ci-dessous restent des raccourcis
    // locaux qui fournissent la taille du dictionnaire.

    private fun getCurrentLevel(wordsDiscovered: Int): String =
        CreoleLevels.labelFor(wordsDiscovered, getTotalDictionaryWords())

    private fun getNextLevelInfo(wordsDiscovered: Int): Pair<String, Int> =
        CreoleLevels.nextLevelInfo(wordsDiscovered, getTotalDictionaryWords())

    /**
     * Index du niveau actuel (0 = Pipirit ... 7 = Benzo), même logique que getCurrentLevel()
     */
    private fun getCurrentLevelIndex(wordsDiscovered: Int): Int =
        CreoleLevels.indexFor(wordsDiscovered, getTotalDictionaryWords())

    /**
     * Affiche la célébration de passage de niveau avec carte partageable.
     * Ne se déclenche que sur une progression réelle : au premier passage,
     * le niveau courant est mémorisé silencieusement (pas de célébration
     * rétroactive pour un utilisateur existant).
     */
    private fun maybeCelebrateLevelUp(wordsDiscovered: Int, levelEmoji: String, levelName: String) {
        try {
            val prefs = getSharedPreferences("kreyol_gamification_prefs", Context.MODE_PRIVATE)
            val currentIndex = getCurrentLevelIndex(wordsDiscovered)
            val lastCelebrated = prefs.getInt("last_celebrated_level_index", -1)

            if (lastCelebrated == -1) {
                prefs.edit().putInt("last_celebrated_level_index", currentIndex).apply()
                return
            }
            if (currentIndex <= lastCelebrated) return

            prefs.edit().putInt("last_celebrated_level_index", currentIndex).apply()

            val cardBitmap = buildLevelCardBitmap(levelEmoji, levelName, wordsDiscovered)

            val preview = ImageView(this).apply {
                setImageBitmap(cardBitmap)
                adjustViewBounds = true
                setPadding(32, 24, 32, 8)
            }

            AlertDialog.Builder(this)
                .setTitle("🎉 Bravo ! Ou vansé !")
                .setMessage("Ou rivé nivo $levelName ! Partage ta carte pour montrer ton niveau en kréyòl.")
                .setView(preview)
                .setPositiveButton("Partager 📤") { _, _ -> shareLevelCard(cardBitmap, levelName) }
                .setNegativeButton("Plus tard", null)
                .show()
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur célébration de niveau: ${e.message}")
        }
    }

    /**
     * Dessine la carte de niveau partageable (1080×1350, format portrait réseaux sociaux)
     */
    private fun buildLevelCardBitmap(levelEmoji: String, levelName: String, wordsDiscovered: Int): Bitmap {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = width / 2f

        // Fond dégradé mer des Caraïbes
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                Color.parseColor("#0E6E76"), Color.parseColor("#052E33"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Soleil décoratif en haut à droite
        val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#33F6E9D2")
        }
        canvas.drawCircle(width - 120f, 130f, 190f, sunPaint)

        // Eyebrow
        val eyebrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E3AE5E")
            textSize = 42f
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("NIVO AN MWEN AN KRÉYÒL", cx, 240f, eyebrowPaint)

        // Emoji du niveau
        val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 280f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(levelEmoji, cx, 620f, emojiPaint)

        // Nom du niveau
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 116f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(levelName, cx, 790f, namePaint)

        // Compteur de mots
        val statsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DDEEEE")
            textSize = 54f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("$wordsDiscovered mo kréyòl découvert !", cx, 900f, statsPaint)

        // Séparateur
        val linePaint = Paint().apply { color = Color.parseColor("#33FFFFFF"); strokeWidth = 3f }
        canvas.drawLine(cx - 220f, 1010f, cx + 220f, 1010f, linePaint)

        // Pied de carte
        val footerBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 56f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Klavyé Kréyòl Karukéra", cx, 1120f, footerBoldPaint)

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#A9D4D6")
            textSize = 42f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Klavyé gratui asi Google Play", cx, 1195f, footerPaint)

        return bitmap
    }

    /**
     * Enregistre la carte dans le cache et ouvre le sélecteur de partage
     * (image + texte avec lien tracké utm_source=level_share)
     */
    private fun shareLevelCard(bitmap: Bitmap, levelName: String) {
        try {
            val imagesDir = File(cacheDir, "images").apply { mkdirs() }
            val imageFile = File(imagesDir, "nivo_kreyol.png")
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", imageFile)

            val message = "An rivé nivo $levelName asi Klavyé Kréyòl Karukéra ! É vou menm, ki nivo a'w ?\n" +
                    "Télécharge le clavier gratuitement :\n" +
                    "https://play.google.com/store/apps/details?id=$packageName" +
                    "&referrer=utm_source%3Dlevel_share%26utm_campaign%3Dlaunch10k\n\n" +
                    SHARE_HASHTAG

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, message)
                // Le flag FLAG_GRANT_READ_URI_PERMISSION ne s'applique qu'à l'URI
                // porté par setData()/ClipData, pas à EXTRA_STREAM seul. Sans ClipData,
                // sous Android 14 l'aperçu du sélecteur ET l'app cible (ex. Messages)
                // reçoivent un SecurityException et l'image ne s'attache pas (le partage
                // retombe en SMS texte). On expose donc l'URI via ClipData pour que la
                // permission de lecture soit bien propagée.
                clipData = ClipData.newUri(contentResolver, "nivo_kreyol.png", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Partager ma carte de niveau"))
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur partage carte de niveau: ${e.message}")
            Toast.makeText(this, "Impossible de partager la carte", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Calcule les seuils de niveau de façon dynamique selon la taille du dictionnaire
     *
     * Progression motivante basée sur des pourcentages du dictionnaire total:
     * - Pipirit (début): 0% - démarrage
     * - Ti moun: 1.5% - premiers pas (rapide à atteindre!)
     * - Débrouya: 5% - débrouillard
     * - An mitan: 12% - au milieu du chemin
     * - Kompè Lapen: 25% - bon niveau
     * - Kompè Zamba: 45% - niveau avancé
     * - Potomitan: 70% - expert
     * - Benzo: 100% - maître absolu (tous les mots!)
     * 
     * Avantages:
     * - S'adapte automatiquement à la croissance du dictionnaire
     * - Progression douce au début (1.5% pour Ti moun)
     * - Écarts progressifs entre niveaux (motivant!)
     * - Benzo reste l'objectif ultime (100%)
     * 
     * Exemples pour 3680 mots:
     * - Ti moun: 55 mots, Débrouya: 184 mots, An mitan: 442 mots
     * - Kompè Lapen: 920 mots, Kompè Zamba: 1656 mots
     * - Potomitan: 2576 mots, Benzo: 3680 mots
     * 
     * @return IntArray avec 8 seuils calculés dynamiquement
     */
    private fun calculateGaussianThresholds(): IntArray =
        CreoleLevels.thresholds(getTotalDictionaryWords())
    
    /**
     * Récupère le nombre total de mots dans le dictionnaire
     * Utilise un cache pour éviter de relire le fichier à chaque fois
     */
    private var cachedTotalWords: Int? = null
    
    private fun getTotalDictionaryWords(): Int {
        // Retourner depuis le cache si disponible
        cachedTotalWords?.let { return it }
        
        return try {
            // Toujours charger le dictionnaire source depuis assets
            // car creole_dict_with_usage.json peut être vide (nouveau install)
            val jsonString = assets.open("creole_dict.json").bufferedReader().use { it.readText() }
            val jsonArray = org.json.JSONArray(jsonString)
            val count = jsonArray.length()
            
            cachedTotalWords = count
            Log.d("SettingsActivity", "📊 Total mots dictionnaire: $count")
            count
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur comptage mots: ${e.message}")
            14722 // Fallback sur la valeur connue du dictionnaire complet
        }
    }
    
    // Adapter pour ViewPager2 avec swipe cyclique
    private class SettingsPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        companion object {
            const val REAL_COUNT = 4 // Nombre réel d'onglets (jeux regroupés)
            const val VIRTUAL_COUNT = Int.MAX_VALUE // Nombre virtuel pour simuler l'infini
            const val START_POSITION = VIRTUAL_COUNT / 2 // Position de départ au milieu
        }

        override fun getItemCount(): Int = VIRTUAL_COUNT

        override fun createFragment(position: Int): Fragment {
            // Utiliser le modulo pour revenir aux vraies pages
            val realPosition = position % REAL_COUNT
            return when (realPosition) {
                0 -> OnboardingFragment()
                1 -> StatsFragment()
                2 -> GamesFragment()
                3 -> DictionaryFragment()
                else -> OnboardingFragment()
            }
        }
    }
    
    // Fragment pour le démarrage / onboarding
    class OnboardingFragment : Fragment() {
        private var rootView: ScrollView? = null

        // Observe les réglages système du clavier au lieu de les sonder
        // toutes les 2 secondes : réaction immédiate quand l'utilisateur
        // active ou sélectionne le clavier (notamment pendant que le
        // sélecteur système est affiché par-dessus l'activité, qui reste
        // resumed), et plus de Handler périodique qui tourne à vide.
        private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val activity = activity as? SettingsActivity ?: return
                val wasEnabled = lastKnownEnabled
                val wasSelected = lastKnownSelected
                val changed = shouldRefresh(
                    activity.isKeyboardEnabled(),
                    activity.isKeyboardSelected(),
                    activity.isSpellCheckerSelected()
                )
                if (changed) {
                    refreshContent()
                    chainNextStep(wasEnabled, wasSelected)
                }
            }
        }

        override fun onCreateView(
            inflater: android.view.LayoutInflater,
            container: android.view.ViewGroup?,
            savedInstanceState: android.os.Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity
            rootView = ScrollView(activity).apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = true
            }
            refreshContent()
            return rootView!!
        }
        
        override fun onResume() {
            super.onResume()
            // Rafraîchir pour rattraper les changements survenus pendant que
            // le fragment était masqué (ex. activation dans les réglages
            // système), puis observer les réglages en continu
            val wasEnabled = lastKnownEnabled
            val wasSelected = lastKnownSelected
            refreshContent()
            chainNextStep(wasEnabled, wasSelected)

            val resolver = requireContext().contentResolver
            resolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD), false, settingsObserver)
            resolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_INPUT_METHODS), false, settingsObserver)
            resolver.registerContentObserver(
                Settings.Secure.getUriFor("selected_spell_checker"), false, settingsObserver)
        }

        override fun onPause() {
            super.onPause()
            requireContext().contentResolver.unregisterContentObserver(settingsObserver)
        }

        private var lastKnownEnabled = false
        private var lastKnownSelected = false
        private var lastKnownSpellCheckerOn = false

        private fun shouldRefresh(currentEnabled: Boolean, currentSelected: Boolean, currentSpellCheckerOn: Boolean): Boolean {
            val hasChanged = currentEnabled != lastKnownEnabled || currentSelected != lastKnownSelected || currentSpellCheckerOn != lastKnownSpellCheckerOn
            lastKnownEnabled = currentEnabled
            lastKnownSelected = currentSelected
            lastKnownSpellCheckerOn = currentSpellCheckerOn
            return hasChanged
        }

        private fun refreshContent() {
            val activity = requireActivity() as SettingsActivity
            lastKnownEnabled = activity.isKeyboardEnabled()
            lastKnownSelected = activity.isKeyboardSelected()
            lastKnownSpellCheckerOn = activity.isSpellCheckerSelected()
            rootView?.removeAllViews()
            rootView?.addView(activity.createOnboardingContent())
            if (lastKnownEnabled && lastKnownSelected) {
                // Configuration aboutie : révéler la navigation (idempotent)
                activity.onOnboardingCompleted()
            } else {
                // Activé mais pas encore sélectionné : la navigation revient
                // quand même, elle ne dépend que de l'activation.
                activity.revelerNavigationSiClavierActive()
            }
            Log.d("SettingsActivity", "🔄 Contenu de l'onboarding rafraîchi (enabled=$lastKnownEnabled, selected=$lastKnownSelected, spellChecker=$lastKnownSpellCheckerOn)")
        }

        // Enchaîne automatiquement l'étape suivante quand une action système
        // vient d'aboutir, pour économiser des taps de navigation : clavier
        // sélectionné → focus sur le champ de test (le clavier Kréyòl
        // apparaît aussitôt) ; clavier activé (retour des réglages système)
        // → ouverture directe du sélecteur. À appeler après refreshContent(),
        // qui met à jour lastKnownEnabled/lastKnownSelected. Le délai initial
        // laisse l'utilisateur voir l'étape passer au vert avant la suite.
        private fun chainNextStep(wasEnabled: Boolean, wasSelected: Boolean) {
            when {
                !wasSelected && lastKnownSelected -> rootView?.postDelayed({
                    runWhenWindowFocused { focusTestField() }
                }, 400)
                !wasEnabled && lastKnownEnabled -> rootView?.postDelayed({
                    runWhenWindowFocused {
                        (activity as? SettingsActivity)?.openInputMethodPicker()
                    }
                }, 400)
            }
        }

        // showInputMethodPicker() et showSoftInput() sont ignorés par le
        // système tant que la fenêtre n'a pas repris le focus après le retour
        // des réglages (InputMethodManagerService rejette les clients non
        // courants, vu dans logcat : « Ignoring showInputMethodPickerFromClient »).
        // On attend donc le focus fenêtre, plus une courte marge pour que le
        // système réenregistre l'activité comme client de saisie courant.
        private fun runWhenWindowFocused(attemptsLeft: Int = 10, action: () -> Unit) {
            if (!isAdded) return
            if (requireActivity().hasWindowFocus()) {
                rootView?.postDelayed({ if (isAdded) action() }, 150)
            } else if (attemptsLeft > 0) {
                rootView?.postDelayed({ runWhenWindowFocused(attemptsLeft - 1, action) }, 200)
            }
        }

        private fun focusTestField() {
            val field = rootView?.findViewWithTag<EditText>("onboarding_test_field") ?: return
            field.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        }
    }
    
    // Fragment pour l'à propos
    class AboutFragment : Fragment() {
        override fun onCreateView(
            inflater: android.view.LayoutInflater,
            container: android.view.ViewGroup?,
            savedInstanceState: android.os.Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity
            val scrollView = ScrollView(activity)
            scrollView.addView(activity.createAboutContent())
            return scrollView
        }
    }

    // Fragment pour le guide de l'utilisateur
    class GuideFragment : Fragment() {
        override fun onCreateView(
            inflater: android.view.LayoutInflater,
            container: android.view.ViewGroup?,
            savedInstanceState: android.os.Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity
            val scrollView = ScrollView(activity)
            scrollView.addView(activity.createGuideContent())
            return scrollView
        }
    }

    // Fragment pour les statistiques
    class StatsFragment : Fragment() {
        private var scrollView: ScrollView? = null

        override fun onCreateView(
            inflater: android.view.LayoutInflater,
            container: android.view.ViewGroup?,
            savedInstanceState: android.os.Bundle?
        ): View {
            Log.d("SettingsActivity", "Création de la vue StatsFragment")
            val activity = requireActivity() as SettingsActivity

            // Créer le SwipeRefreshLayout pour le Pull-to-Refresh
            val swipeRefreshLayout = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(activity).apply {
                setColorSchemeColors(
                    Color.parseColor("#0080FF"), // Bleu principal
                    Color.parseColor("#4CAF50"), // Vert
                    Color.parseColor("#FF9800")  // Orange
                )
                setProgressBackgroundColorSchemeColor(Color.WHITE)

                // Configurer l'action de rafraîchissement
                setOnRefreshListener {
                    Log.d("SettingsActivity", "🔄 Pull-to-Refresh déclenché")

                    // Afficher un message
                    Toast.makeText(activity, "Actualisation des statistiques...", Toast.LENGTH_SHORT).show()

                    // Attendre un peu puis recréer l'activité
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        Log.d("SettingsActivity", "🔄 Rechargement de l'activité après pull-to-refresh")
                        activity.recreate() // Redémarre complètement l'activité
                    }, 500) // Attendre 500ms
                }
            }

            val scrollView = ScrollView(activity).apply {
                setBackgroundColor(Color.WHITE)
                isFillViewport = true
            }
            this.scrollView = scrollView
            val statsContent = activity.createStatsContent()
            scrollView.addView(statsContent)

            // Ajouter le ScrollView dans le SwipeRefreshLayout
            swipeRefreshLayout.addView(scrollView)
            
            Log.d("SettingsActivity", "StatsFragment créé avec Pull-to-Refresh")
            return swipeRefreshLayout
        }

        override fun onResume() {
            super.onResume()
            // Recharge les stats à chaque retour au premier plan (ex. après une session de
            // frappe) : sans ce rafraîchissement, l'onglet réaffiche les chiffres capturés à
            // sa création jusqu'à un pull-to-refresh manuel ou un redémarrage de l'activité.
            val activity = requireActivity() as? SettingsActivity ?: return
            val container = scrollView ?: return
            container.removeAllViews()
            container.addView(activity.createStatsContent())
        }

        override fun onDestroyView() {
            super.onDestroyView()
            scrollView = null
        }
    }
    
    /**
     * Astuce de la semaine : l'index suit le numéro de semaine plutôt qu'un
     * tirage aléatoire seedé sur la date (comme [getWordOfTheDay]), pour que la
     * liste soit parcourue en entier et que deux semaines de suite ne retombent
     * jamais sur la même astuce. Le décalage de fuseau est ajouté pour que le
     * changement se fasse à minuit local et non à minuit UTC.
     *
     * Le +3 cale la bascule sur le lundi : le jour 0 de l'ère Unix étant un
     * jeudi, sans lui l'astuce changerait en plein milieu de semaine.
     */
    private fun getTipOfTheWeek(): String {
        val calendar = Calendar.getInstance()
        val localMillis = calendar.timeInMillis +
                calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)
        val dayIndex = TimeUnit.MILLISECONDS.toDays(localMillis)
        val weekIndex = (dayIndex + 3) / 7
        return WEEKLY_TIPS[(weekIndex % WEEKLY_TIPS.size).toInt()]
    }

    /**
     * Tire un mot au hasard, puis avance jusqu'au premier qui porte une glose.
     *
     * Le mot du jour s'affiche avec sa traduction, et 513 des 5 296 mots
     * seulement en ont une : sans ce parcours, neuf jours sur dix la ligne
     * « en français » resterait vide sous un mot de quarante-huit points.
     *
     * Le parcours plutôt qu'un `filter` : la liste vient du fichier d'usage,
     * qui grossit à chaque mot tapé, et la filtrer entière allouerait une copie
     * à chaque ouverture de l'onglet pour n'en garder qu'un mot. Il est
     * circulaire, et rend le mot tiré si la table est vide — sans glose,
     * l'onglet montre un mot du jour muet, jamais rien.
     */
    private fun tirerMotGlose(mots: List<String>, random: Random): String {
        val depart = random.nextInt(mots.size)
        for (decalage in mots.indices) {
            val candidat = mots[(depart + decalage) % mots.size]
            if (TranslationDictionary.estProposable(this, candidat)) return candidat
        }
        return mots[depart]
    }

    private fun getWordOfTheDay(): Pair<String, Int> {
        return try {
            val usageFile = File(filesDir, "creole_dict_with_usage.json")
            
            val allWords: List<String>
            val usageCount: Int
            
            if (usageFile.exists()) {
                val jsonString = usageFile.readText()
                val jsonObject = JSONObject(jsonString)
                
                allWords = mutableListOf<String>().apply {
                    jsonObject.keys().forEach { word -> add(word) }
                }
                
                if (allWords.isEmpty()) {
                    return Pair("Bonjou", 0)
                }
                
                // Utiliser la date comme seed pour avoir le même mot toute la journée
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dateString = dateFormat.format(Date())
                val seed = dateString.hashCode().toLong()
                val random = Random(seed)
                
                val selectedWord = tirerMotGlose(allWords, random)
                // Lire directement l'entier
                usageCount = jsonObject.optInt(selectedWord, 0)
                
                return Pair(selectedWord, usageCount)
            } else {
                Log.d("SettingsActivity", "Fichier usage n'existe pas, création depuis assets")
                // Charger depuis les assets
                val jsonString = assets.open("creole_dict.json").bufferedReader().use { it.readText() }
                val jsonArray = org.json.JSONArray(jsonString)
                Log.d("SettingsActivity", "Dictionnaire chargé: ${jsonArray.length()} mots")
                
                allWords = mutableListOf<String>().apply {
                    for (i in 0 until jsonArray.length()) {
                        val wordArray = jsonArray.getJSONArray(i)
                        add(wordArray.getString(0))  // Premier élément = le mot
                    }
                }
                
                if (allWords.isEmpty()) {
                    return Pair("Bonjou", 0)
                }
                
                // Utiliser la date comme seed
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dateString = dateFormat.format(Date())
                val seed = dateString.hashCode().toLong()
                val random = Random(seed)
                
                val selectedWord = tirerMotGlose(allWords, random)

                return Pair(selectedWord, 0)
            }
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Erreur mot du jour: ${e.message}")
            Pair("Bonjou", 0)
        }
    }
    
    /**
     * 🎨 Transformateur personnalisé pour effet Tinder Swipe
     * 
     * Caractéristiques :
     * - Rotation de -15° à +15° selon la direction du swipe
     * - Translation verticale : la carte se soulève légèrement
     * - Scale : la carte rétrécit un peu en s'éloignant
     * - Fade out progressif
     * - Élévation : la page courante est au-dessus
     */
    private class TinderSwipeTransformer : ViewPager2.PageTransformer {
        override fun transformPage(page: View, position: Float) {
            page.apply {
                when {
                    position < -1 -> { // [-Infinity,-1)
                        // Page complètement à gauche, hors écran
                        alpha = 0f
                        translationX = 0f
                        translationY = 0f
                        rotation = 0f
                        scaleX = 1f
                        scaleY = 1f
                    }
                    position <= 1 -> { // [-1,1]
                        // Page visible ou en transition
                        
                        // 🎯 Effet Tinder : rotation + translation + scale
                        val absPosition = Math.abs(position)
                        
                        // Rotation de -15° à +15° selon la direction du swipe
                        rotation = -15f * position
                        
                        // Translation verticale : la carte se soulève légèrement
                        translationY = -Math.abs(position) * 50f
                        
                        // Translation horizontale pour accentuer le mouvement
                        translationX = position * width * 0.3f
                        
                        // Scale : la carte rétrécit un peu en s'éloignant
                        val scale = 1f - absPosition * 0.2f
                        scaleX = scale
                        scaleY = scale
                        
                        // Alpha : fade out progressif
                        alpha = 1f - absPosition * 0.5f
                        
                        // Élévation : la page courante est au-dessus
                        elevation = (1f - absPosition) * 10f
                    }
                    else -> { // (1,+Infinity]
                        // Page complètement à droite, hors écran
                        alpha = 0f
                        translationX = 0f
                        translationY = 0f
                        rotation = 0f
                        scaleX = 1f
                        scaleY = 1f
                    }
                }
            }
        }
    }
    
    // Fragment pour les mots mêlés
    class WordSearchFragment : Fragment() {
        
        private var currentPuzzle: WordSearchPuzzle? = null
        private var startTime: Long = 0
        private var wordsFound = 0
        private lateinit var gridView: GridView
        private lateinit var wordsListContainer: LinearLayout
        private lateinit var tvTheme: TextView
        private lateinit var tvScore: TextView
        
        override fun onCreateView(
            inflater: android.view.LayoutInflater,
            container: android.view.ViewGroup?,
            savedInstanceState: android.os.Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity
            
            // ScrollView pour permettre le défilement si nécessaire
            return ScrollView(activity).apply {
                setBackgroundColor(Color.parseColor("#F5F5F5"))
                
                val mainLayout = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(8, 8, 8, 8) // Réduction du padding de 16 à 8
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    
                    // En-tête avec thème et score
                    val headerLayout = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(8, 4, 8, 8) // Réduction du padding
                        gravity = Gravity.CENTER_VERTICAL
                        
                        tvTheme = TextView(activity).apply {
                            text = "⏳ Chargement..."
                            textSize = 16f
                            setTextColor(Color.parseColor("#9C27B0"))
                            setTypeface(null, Typeface.BOLD)
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            )
                        }
                        addView(tvTheme)
                        
                        tvScore = TextView(activity).apply {
                            text = "⭐ 0"
                            textSize = 16f
                            setTextColor(Color.parseColor("#FF9800"))
                            setTypeface(null, Typeface.BOLD)
                            gravity = Gravity.END
                        }
                        addView(tvScore)
                    }
                    addView(headerLayout)
                    
                    // Grille de mots mêlés
                    gridView = GridView(activity).apply {
                        // Calculer la taille disponible pour la grille
                        val screenWidth = resources.displayMetrics.widthPixels
                        val availableWidth = screenWidth - 48
                        
                        // La grille est toujours 8x8
                        val gridSize = 8
                        // Calculer la taille d'une cellule en fonction de la largeur
                        val cellSize = availableWidth / gridSize
                        // Hauteur de la grille = 8 cellules + espacements + padding
                        val gridHeight = (cellSize * gridSize) + (4 * (gridSize - 1)) + 24
                        
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            gridHeight
                        )
                        setPadding(12, 12, 12, 12)
                        stretchMode = GridView.STRETCH_COLUMN_WIDTH
                        setBackgroundColor(Color.parseColor("#F5F5F5")) // Fond gris très clair
                        verticalSpacing = 4 // Espacement vertical entre les lignes
                        horizontalSpacing = 4 // Espacement horizontal entre les colonnes
                        
                        // 🔧 FIX: Gérer les touches au niveau de la GridView pour permettre le swipe entre cellules
                        setOnTouchListener { view, event ->
                            // Demander au parent de ne pas intercepter les événements
                            parent?.requestDisallowInterceptTouchEvent(true)
                            
                            // Calculer quelle cellule est touchée
                            val position = pointToPosition(event.x.toInt(), event.y.toInt())
                            
                            if (position != android.widget.AdapterView.INVALID_POSITION) {
                                val adapter = adapter as? WordSearchGridAdapter
                                adapter?.handleTouchEvent(position, event)
                            }
                            
                            // Réactiver l'interception après ACTION_UP ou ACTION_CANCEL
                            if (event.action == android.view.MotionEvent.ACTION_UP ||
                                event.action == android.view.MotionEvent.ACTION_CANCEL) {
                                parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            
                            true // Consommer l'événement
                        }
                    }
                    addView(gridView)
                    
                    // Bouton nouvelle grille
                    val btnNewGame = Button(activity).apply {
                        text = "🔄 Nouvelle Grille"
                        textSize = 14f
                        setTextColor(Color.WHITE)
                        setBackgroundColor(Color.parseColor("#9C27B0"))
                        setPadding(24, 10, 24, 10) // Réduction du padding vertical
                        setTypeface(null, Typeface.BOLD)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 8, 0, 8) // Réduction des marges de 16 à 8
                        }
                        setOnClickListener {
                            generateNewPuzzle()
                        }
                    }
                    addView(btnNewGame)
                    
                    // Liste des mots à trouver
                    val wordsTitle = TextView(activity).apply {
                        text = "📝 Mots à trouver :"
                        textSize = 14f // Réduction de 16 à 14
                        setTextColor(Color.parseColor("#333333"))
                        setTypeface(null, Typeface.BOLD)
                        setPadding(8, 4, 8, 4) // Réduction du padding
                    }
                    addView(wordsTitle)
                    
                    wordsListContainer = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(8, 4, 8, 8) // Réduction du padding
                        setBackgroundColor(Color.parseColor("#FFFFFF"))
                    }
                    addView(wordsListContainer)
                }
                
                addView(mainLayout)
                
                // Générer la première grille après que la vue soit créée
                post {
                    // Le post() s'exécute au prochain passage de la boucle de messages :
                    // si l'utilisateur a déjà changé d'onglet entre-temps, le fragment
                    // n'est plus attaché et requireActivity()/requireContext() planterait.
                    if (isAdded) {
                        generateNewPuzzle()
                    }
                }
            }
        }

        private fun generateNewPuzzle() {
            try {
                val activity = requireActivity() as SettingsActivity

                // Générer une nouvelle grille 8x8 avec des mots aléatoires du dictionnaire
                currentPuzzle = WordSearchGenerator.generatePuzzle(
                    context = activity,
                    theme = "kreyol", // Thème unique
                    gridSize = 8,
                    difficulty = WordSearchDifficulty.NORMAL
                )

                // Afficher la grille
                displayPuzzle(currentPuzzle!!)

                // Réinitialiser
                startTime = System.currentTimeMillis()
                wordsFound = 0
                updateScore(0)

                Log.d("WordSearchFragment", "Nouvelle grille générée: ${currentPuzzle?.words?.size} mots")

            } catch (e: Exception) {
                Log.e("WordSearchFragment", "Erreur génération: ${e.message}", e)
                // context (nullable) au lieu de requireContext() : si le fragment vient
                // justement d'être détaché, ce bloc catch ne doit pas planter à son tour.
                context?.let { Toast.makeText(it, "Erreur lors de la génération", Toast.LENGTH_SHORT).show() }
            }
        }
        
        private fun displayPuzzle(puzzle: WordSearchPuzzle) {
            val activity = requireActivity() as SettingsActivity
            
            // Configurer l'adaptateur de la grille
            val adapter = WordSearchGridAdapter(activity, puzzle)
            adapter.setOnWordFoundListener { word ->
                onWordFound(word)
            }
            gridView.adapter = adapter
            gridView.numColumns = puzzle.gridSize
            
            // Afficher le titre simple sans thème
            tvTheme.text = "🎯 Mots Créoles"
            
            // Afficher la liste des mots
            displayWordsList(puzzle.words)
        }
        
        private fun displayWordsList(words: List<WordSearchWord>) {
            wordsListContainer.removeAllViews()
            val activity = requireActivity() as SettingsActivity
            
            words.forEach { word ->
                val wordView = TextView(activity).apply {
                    text = if (word.isFound) "✅ ${word.word.uppercase()}" else "📝 ${word.word.uppercase()}"
                    textSize = 14f
                    setPadding(12, 8, 12, 0)
                    setTextColor(if (word.isFound) Color.parseColor("#4CAF50") else Color.parseColor("#333333"))
                    setTypeface(null, if (word.isFound) Typeface.BOLD else Typeface.NORMAL)
                }
                wordsListContainer.addView(wordView)

                // La glose est montrée dès le départ, pas seulement une fois le
                // mot trouvé : la liste donne déjà le mot en toutes lettres,
                // elle ne cache rien. C'est en le cherchant dans la grille
                // qu'on a le temps de lire ce qu'il veut dire.
                TranslationDictionary.traduire(activity, word.word)?.let { glose ->
                    wordsListContainer.addView(TextView(activity).apply {
                        text = glose
                        textSize = 11f
                        setPadding(28, 0, 12, 8)
                        setTextColor(Color.parseColor("#999999"))
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                }
            }
        }
        
        private fun onWordFound(word: String) {
            wordsFound++
            
            // Mettre à jour la liste
            currentPuzzle?.words?.find { it.word.equals(word, ignoreCase = true) }?.isFound = true
            displayWordsList(currentPuzzle?.words ?: emptyList())
            
            // Calculer les points
            val points = word.length * 10
            updateScore(points)
            
            // Vérifier si tous les mots sont trouvés
            // Toast.setGravity() est ignoré par le système depuis Android 11 : on utilise
            // une Snackbar (vue applicative, pas une fenêtre système) pour l'ancrer en haut
            // et éviter qu'elle ne recouvre le mot qui vient de passer en vert dans la liste.
            val message = if (wordsFound == currentPuzzle?.words?.size) {
                "🎉 Félicitations ! Tous les mots trouvés !"
            } else {
                "✅ Mot trouvé : $word (+$points pts)"
            }
            val duration = if (wordsFound == currentPuzzle?.words?.size) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT
            Snackbar.make(requireView(), message, duration).apply {
                (view.layoutParams as? FrameLayout.LayoutParams)?.let {
                    it.gravity = Gravity.TOP
                    view.layoutParams = it
                }
            }.show()
        }
        
        private fun updateScore(points: Int) {
            val currentScore = tvScore.text.toString().replace("[^0-9]".toRegex(), "").toIntOrNull() ?: 0
            val newScore = currentScore + points
            tvScore.text = "⭐ $newScore"
        }
    }
    
    // Fragment pour le jeu de mots mélangés
    class WordScrambleFragment : Fragment() {
        private var rootView: ScrollView? = null
        
        private lateinit var tvScore: TextView
        private lateinit var tvWordNumber: TextView
        private lateinit var gridScrambled: GridView
        private lateinit var gridAnswer: GridView
        private lateinit var btnValidate: Button
        private lateinit var btnSkip: Button
        private lateinit var btnHint: Button
        private lateinit var btnClear: Button
        private lateinit var progressBar: ProgressBar
        
        private var scrambledAdapter: com.example.kreyolkeyboard.wordscramble.ScrambledLettersAdapter? = null
        private var answerAdapter: com.example.kreyolkeyboard.wordscramble.AnswerLettersAdapter? = null
        
        private var currentWord: String = ""
        private var scrambledLetters: List<Char> = listOf()
        private val currentAnswer = mutableListOf<Char?>()
        private val selectedPositions = mutableListOf<Int>()
        
        private var gameWords: List<String> = listOf()
        private var currentWordIndex = 0
        private var wordsCorrect = 0
        private var score = 0
        private var difficulty = com.example.kreyolkeyboard.wordscramble.ScrambleDifficulty.NORMAL
        
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity
            
            rootView = ScrollView(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#F5F5F5"))
                isFillViewport = true
                
                val mainLayout = LinearLayout(activity).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.VERTICAL
                    setPadding(32, 16, 32, 16)
                    
                    // En-tête avec score
                    val headerLayout = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        setBackgroundColor(Color.WHITE)
                        setPadding(24, 24, 24, 24)
                        elevation = 8f
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 32
                        
                        tvScore = TextView(activity).apply {
                            text = "Score: 0"
                            textSize = 24f
                            setTypeface(null, Typeface.BOLD)
                            gravity = Gravity.CENTER
                            setTextColor(Color.parseColor("#4CAF50"))
                        }
                        addView(tvScore)
                    }
                    addView(headerLayout)
                    
                    // Numéro du mot et progression
                    val progressLayout = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.VERTICAL
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 32
                        
                        tvWordNumber = TextView(activity).apply {
                            text = "Mot 1/10"
                            textSize = 16f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#333333"))
                            setPadding(0, 0, 0, 16)
                        }
                        addView(tvWordNumber)
                        
                        progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                16
                            )
                            max = 10
                            progress = 0
                        }
                        addView(progressBar)
                    }
                    addView(progressLayout)
                    
                    // Titre
                    val title = TextView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        text = "🔤 Remets les lettres dans l'ordre !"
                        textSize = 18f
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#1976D2"))
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 32
                    }
                    addView(title)
                    
                    // Label lettres disponibles
                    val labelScrambled = TextView(activity).apply {
                        text = "Lettres disponibles :"
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#333333"))
                        setPadding(0, 0, 0, 16)
                    }
                    addView(labelScrambled)
                    
                    // Grille des lettres mélangées
                    gridScrambled = GridView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        numColumns = 5
                        verticalSpacing = 16
                        horizontalSpacing = 16
                        stretchMode = GridView.STRETCH_COLUMN_WIDTH
                        gravity = Gravity.CENTER
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 48
                        
                        setOnItemClickListener { _, _, position, _ ->
                            if (!selectedPositions.contains(position)) {
                                addLetterToAnswer(position)
                            }
                        }
                    }
                    addView(gridScrambled)
                    
                    // Label réponse
                    val labelAnswer = TextView(activity).apply {
                        text = "Ta réponse :"
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#333333"))
                        setPadding(0, 0, 0, 16)
                    }
                    addView(labelAnswer)
                    
                    // Grille de la réponse
                    gridAnswer = GridView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        numColumns = 5
                        verticalSpacing = 16
                        horizontalSpacing = 16
                        stretchMode = GridView.STRETCH_COLUMN_WIDTH
                        gravity = Gravity.CENTER
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 48
                        
                        setOnItemClickListener { _, _, position, _ ->
                            removeLetterFromAnswer(position)
                        }
                    }
                    addView(gridAnswer)
                    
                    // Boutons d'action ligne 1
                    val buttonRow1 = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 24
                        
                        btnClear = Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply { setMargins(8, 0, 8, 0) }
                            text = "🔄 Effacer"
                            setBackgroundColor(Color.parseColor("#FF9800"))
                            setTextColor(Color.WHITE)
                            setOnClickListener { clearAnswer() }
                        }
                        addView(btnClear)
                        
                        btnHint = Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply { setMargins(8, 0, 8, 0) }
                            text = "💡 Indice"
                            setBackgroundColor(Color.parseColor("#FFC107"))
                            setTextColor(Color.WHITE)
                            setOnClickListener { showHint() }
                        }
                        addView(btnHint)
                    }
                    addView(buttonRow1)
                    
                    // Boutons d'action ligne 2
                    val buttonRow2 = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        
                        btnValidate = Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply { setMargins(8, 0, 8, 0) }
                            text = "✅ Valider"
                            setBackgroundColor(Color.parseColor("#4CAF50"))
                            setTextColor(Color.WHITE)
                            setTypeface(null, Typeface.BOLD)
                            isEnabled = false
                            setOnClickListener { validateAnswer() }
                        }
                        addView(btnValidate)
                        
                        btnSkip = Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply { setMargins(8, 0, 8, 0) }
                            text = "⏭️ Passer"
                            setBackgroundColor(Color.parseColor("#9E9E9E"))
                            setTextColor(Color.WHITE)
                            setOnClickListener { skipWord() }
                        }
                        addView(btnSkip)
                    }
                    addView(buttonRow2)
                }
                
                addView(mainLayout)

                post {
                    // Même précaution que WordSearchFragment.generateNewPuzzle() :
                    // ce post() peut s'exécuter après que l'utilisateur a changé
                    // d'onglet, auquel cas le fragment n'est plus attaché.
                    if (isAdded) {
                        startNewGame()
                    }
                }
            }

            return rootView!!
        }
        
        private fun startNewGame() {
            score = 0
            currentWordIndex = 0
            wordsCorrect = 0
            
            gameWords = com.example.kreyolkeyboard.wordscramble.WordScrambleData.loadWords(requireContext(), difficulty)
            
            if (gameWords.isEmpty()) {
                Toast.makeText(requireContext(), "Erreur de chargement", Toast.LENGTH_SHORT).show()
                return
            }
            
            progressBar.max = gameWords.size
            loadNextWord()
        }
        
        private fun loadNextWord() {
            if (currentWordIndex >= gameWords.size) {
                endGame()
                return
            }
            
            currentWord = gameWords[currentWordIndex]
            var allScrambledLetters = com.example.kreyolkeyboard.wordscramble.WordScrambleData.scrambleWord(currentWord)
            
            currentAnswer.clear()
            selectedPositions.clear()
            repeat(currentWord.length) { currentAnswer.add(null) }
            
            // Pré-remplir la première et la dernière lettre
            if (currentWord.isNotEmpty()) {
                currentAnswer[0] = currentWord[0]
                if (currentWord.length > 1) {
                    currentAnswer[currentWord.length - 1] = currentWord[currentWord.length - 1]
                }
                
                // Retirer la première et dernière lettre des lettres mélangées
                val lettersToRemove = mutableListOf<Char>()
                lettersToRemove.add(currentWord[0])
                if (currentWord.length > 1) {
                    lettersToRemove.add(currentWord[currentWord.length - 1])
                }
                
                scrambledLetters = allScrambledLetters.toMutableList().apply {
                    lettersToRemove.forEach { letter ->
                        remove(letter)
                    }
                }
            } else {
                scrambledLetters = allScrambledLetters
            }
            
            scrambledAdapter = com.example.kreyolkeyboard.wordscramble.ScrambledLettersAdapter(requireContext(), scrambledLetters)
            answerAdapter = com.example.kreyolkeyboard.wordscramble.AnswerLettersAdapter(requireContext(), currentAnswer)
            
            gridScrambled.adapter = scrambledAdapter
            gridAnswer.adapter = answerAdapter
            
            gridScrambled.numColumns = minOf(scrambledLetters.size, 5)
            gridAnswer.numColumns = minOf(currentWord.length, 5)
            
            // Ajuster la hauteur des grilles
            val numRowsScrambled = (scrambledLetters.size + 4) / 5
            val numRowsAnswer = (currentWord.length + 4) / 5
            gridScrambled.layoutParams.height = numRowsScrambled * 136 // 120 + 16 spacing
            gridAnswer.layoutParams.height = numRowsAnswer * 136
            
            tvWordNumber.text = "Mot ${currentWordIndex + 1}/${gameWords.size}"
            tvScore.text = "Score: $score"
            progressBar.progress = currentWordIndex
        }

        
        private fun addLetterToAnswer(position: Int) {
            val emptyIndex = currentAnswer.indexOfFirst { it == null }
            if (emptyIndex != -1) {
                currentAnswer[emptyIndex] = scrambledLetters[position]
                selectedPositions.add(position)
                
                scrambledAdapter?.markAsSelected(position)
                answerAdapter?.updateLetters(currentAnswer)
                
                if (currentAnswer.all { it != null }) {
                    btnValidate.isEnabled = true
                }
            }
        }
        
        private fun removeLetterFromAnswer(position: Int) {
            if (position < currentAnswer.size && currentAnswer[position] != null) {
                currentAnswer[position] = null
                
                if (position < selectedPositions.size) {
                    selectedPositions.removeAt(position)
                }
                
                val nonNullLetters = currentAnswer.filterNotNull().toMutableList()
                currentAnswer.clear()
                currentAnswer.addAll(nonNullLetters)
                repeat(currentWord.length - nonNullLetters.size) { currentAnswer.add(null) }
                
                scrambledAdapter?.clearSelections()
                selectedPositions.forEachIndexed { index, pos ->
                    if (index < selectedPositions.size) {
                        scrambledAdapter?.markAsSelected(pos)
                    }
                }
                
                answerAdapter?.updateLetters(currentAnswer)
                btnValidate.isEnabled = false
            }
        }
        
        private fun validateAnswer() {
            val answer = currentAnswer.filterNotNull().joinToString("")
            
            if (answer.equals(currentWord, ignoreCase = true)) {
                score += 100

                // La traduction n'arrive qu'une fois le mot reconstitué : la
                // donner avant reviendrait à donner la réponse.
                val glose = TranslationDictionary.libelle(
                    requireContext(), currentWord, prefixe = " — "
                )
                Toast.makeText(
                    requireContext(), "✅ Correct! +100 pts$glose", Toast.LENGTH_SHORT
                ).show()

                wordsCorrect++
                currentWordIndex++
                loadNextWord()
            } else {
                Toast.makeText(requireContext(), "❌ Essaie encore!", Toast.LENGTH_SHORT).show()
                clearAnswer()
            }
        }
        
        private fun skipWord() {
            val glose = TranslationDictionary.libelle(
                requireContext(), currentWord, prefixe = " — "
            )
            Toast.makeText(
                requireContext(), "Le mot était: $currentWord$glose", Toast.LENGTH_SHORT
            ).show()
            currentWordIndex++
            loadNextWord()
        }
        
        private fun showHint() {
            val firstEmpty = currentAnswer.indexOfFirst { it == null }
            if (firstEmpty != -1) {
                val correctLetter = currentWord[firstEmpty]
                
                val posInScrambled = scrambledLetters.indexOfFirst { 
                    it == correctLetter && !selectedPositions.contains(scrambledLetters.indexOf(it))
                }
                
                if (posInScrambled != -1) {
                    addLetterToAnswer(posInScrambled)
                    score -= 20
                    tvScore.text = "Score: $score"
                    Toast.makeText(requireContext(), "Indice (-20 pts)", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        private fun clearAnswer() {
            currentAnswer.clear()
            selectedPositions.clear()
            repeat(currentWord.length) { currentAnswer.add(null) }
            
            // Re-pré-remplir la première et dernière lettre
            if (currentWord.isNotEmpty()) {
                currentAnswer[0] = currentWord[0]
                if (currentWord.length > 1) {
                    currentAnswer[currentWord.length - 1] = currentWord[currentWord.length - 1]
                }
            }
            
            scrambledAdapter?.clearSelections()
            answerAdapter?.updateLetters(currentAnswer)
            btnValidate.isEnabled = false
        }
        
        private fun endGame() {
            AlertDialog.Builder(requireContext())
                .setTitle("🎉 Partie terminée!")
                .setMessage("Score final: $score\nMots réussis: $wordsCorrect/${gameWords.size}")
                .setPositiveButton("Rejouer") { _, _ ->
                    startNewGame()
                }
                .setNegativeButton("OK", null)
                .show()
        }
        
        override fun onDestroyView() {
            super.onDestroyView()
            rootView = null
        }
    }

    // Fragment pour le Mo an Karénaj : deviner un mot kréyòl de 5 lettres en 6 essais
    class MoKarenajFragment : Fragment() {
        private var rootView: ScrollView? = null

        private lateinit var gridBoard: LinearLayout
        private lateinit var editGuess: EditText
        private lateinit var btnSubmit: Button
        private lateinit var tvAttempts: TextView
        private lateinit var legendContainer: LinearLayout

        private var targetWord: String = ""
        private var currentAttempt = 0
        private var gameOver = false
        private val rows = mutableListOf<MoKarenajRow>()
        private val letterBestState = mutableMapOf<Char, LetterState>()

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity

            rootView = ScrollView(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#F5F5F5"))

                val mainLayout = LinearLayout(activity).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.VERTICAL
                    setPadding(32, 16, 32, 16)

                    // Titre + compteur d'essais sur la même ligne (gain de place vertical)
                    val headerRow = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 12

                        val title = TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            )
                            text = "🟩 Mo an Karénaj"
                            textSize = 18f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#1976D2"))
                        }
                        addView(title)

                        tvAttempts = TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            text = "Essai 1/${MoKarenajData.MAX_ATTEMPTS}"
                            textSize = 14f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#333333"))
                        }
                        addView(tvAttempts)
                    }
                    addView(headerRow)

                    // Grille de la partie (6 essais x 5 lettres) : un simple LinearLayout,
                    // pas une GridView — une GridView (AbsListView) vole le geste de scroll
                    // vertical à la ScrollView parente même quand elle est en lecture seule.
                    gridBoard = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 16 }
                        orientation = LinearLayout.VERTICAL
                    }
                    addView(gridBoard)

                    // Légende des lettres essayées : sur une seule ligne avec son label,
                    // juste sous la grille, pour rester visible au-dessus du clavier virtuel.
                    val legendRow = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 16

                        val legendTitle = TextView(activity).apply {
                            text = "Lèt ou ja maké :"
                            textSize = 14f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#333333"))
                            setPadding(0, 0, 12, 0)
                        }
                        addView(legendTitle)

                        legendContainer = LinearLayout(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            orientation = LinearLayout.HORIZONTAL
                        }
                        val legendScroll = HorizontalScrollView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            )
                            addView(legendContainer)
                        }
                        addView(legendScroll)
                    }
                    addView(legendRow)

                    // Saisie de la proposition (le champ porte directement son propre libellé en hint)
                    val inputRow = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 16

                        editGuess = EditText(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            ).apply { setMargins(0, 0, 16, 0) }
                            hint = "Maké mo la isit, an ${MoKarenajData.WORD_LENGTH} lèt"
                            setHintTextColor(Color.parseColor("#9E9E9E"))
                            textSize = 18f
                            setTextColor(Color.parseColor("#212121"))
                            setTypeface(null, Typeface.BOLD)
                            gravity = Gravity.CENTER
                            letterSpacing = 0.08f
                            setPadding(16, 24, 16, 24)
                            background = android.graphics.drawable.GradientDrawable().apply {
                                cornerRadius = 12f
                                setColor(Color.WHITE)
                                setStroke(4, Color.parseColor("#2196F3"))
                            }
                            filters = arrayOf(android.text.InputFilter.LengthFilter(MoKarenajData.WORD_LENGTH))
                            setSingleLine(true)
                            isAllCaps = true // après setSingleLine() : sinon la transformation majuscules est écrasée
                            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                            setOnEditorActionListener { _, actionId, _ ->
                                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                                    submitGuess()
                                    true
                                } else false
                            }
                        }
                        addView(editGuess)

                        btnSubmit = Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.MATCH_PARENT
                            )
                            text = "✅ Valider"
                            setBackgroundColor(Color.parseColor("#4CAF50"))
                            setTextColor(Color.WHITE)
                            setTypeface(null, Typeface.BOLD)
                            setOnClickListener { submitGuess() }
                        }
                        addView(btnSubmit)
                    }
                    addView(inputRow)

                    // Bouton nouvelle partie
                    val btnNewGame = Button(activity).apply {
                        text = "🔄 Fè on dòt pati"
                        textSize = 14f
                        setTextColor(Color.WHITE)
                        setBackgroundColor(Color.parseColor("#9C27B0"))
                        setPadding(24, 10, 24, 10)
                        setTypeface(null, Typeface.BOLD)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, 16, 0, 8) }
                        setOnClickListener { startNewGame() }
                    }
                    addView(btnNewGame)

                    // Règles du jeu, sous la zone de jeu
                    val rulesCard = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(24, 20, 24, 20)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 16 }
                        background = android.graphics.drawable.GradientDrawable().apply {
                            cornerRadius = 12f
                            setColor(Color.WHITE)
                        }

                        val rulesTitle = TextView(activity).apply {
                            text = "📜 Règles du jeu"
                            textSize = 16f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#1976D2"))
                            setPadding(0, 0, 0, 12)
                        }
                        addView(rulesTitle)

                        val rulesText = TextView(activity).apply {
                            text = "Devine le mot kréyòl de ${MoKarenajData.WORD_LENGTH} lettres en ${MoKarenajData.MAX_ATTEMPTS} essais maximum.\n\n" +
                                "Après chaque essai, la couleur des lettres t'indique :\n" +
                                "🟩 Vert : bonne lettre, bonne position\n" +
                                "🟨 Orange : la lettre est dans le mot, mais mal placée\n" +
                                "⬜ Gris : la lettre n'est pas dans le mot\n\n" +
                                "Le mot proposé doit exister dans le dictionnaire Kréyòl."
                            textSize = 14f
                            setTextColor(Color.parseColor("#333333"))
                        }
                        addView(rulesText)
                    }
                    addView(rulesCard)
                }

                addView(mainLayout)

                post {
                    // Même précaution que les autres jeux : si l'utilisateur a déjà
                    // changé d'onglet, le fragment n'est plus attaché.
                    if (isAdded) {
                        startNewGame()
                    }
                }
            }

            return rootView!!
        }

        private fun renderBoard() {
            val activity = requireActivity()
            gridBoard.removeAllViews()
            rows.forEach { row ->
                val rowLayout = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 6 }
                }
                row.letters.forEachIndexed { index, letter ->
                    val state = row.states[index]
                    val cell = TextView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 88, 1f).apply {
                            if (index > 0) marginStart = 6
                        }
                        gravity = Gravity.CENTER
                        text = letter?.toString()?.uppercase() ?: ""
                        textSize = 18f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(if (state == LetterState.EMPTY) Color.parseColor("#333333") else Color.WHITE)
                        setBackgroundColor(state.color())
                    }
                    rowLayout.addView(cell)
                }
                gridBoard.addView(rowLayout)
            }
        }

        private fun startNewGame() {
            val activity = requireActivity()
            targetWord = MoKarenajData.pickRandomWord(activity)
            currentAttempt = 0
            gameOver = false
            letterBestState.clear()
            rows.clear()
            repeat(MoKarenajData.MAX_ATTEMPTS) {
                rows.add(
                    MoKarenajRow(
                        letters = List(MoKarenajData.WORD_LENGTH) { null },
                        states = List(MoKarenajData.WORD_LENGTH) { LetterState.EMPTY }
                    )
                )
            }
            renderBoard()
            editGuess.setText("")
            editGuess.isEnabled = true
            btnSubmit.isEnabled = true
            tvAttempts.text = "Essai ${currentAttempt + 1}/${MoKarenajData.MAX_ATTEMPTS}"
            legendContainer.removeAllViews()
        }

        private fun submitGuess() {
            if (gameOver) return
            val activity = requireActivity()
            val guess = editGuess.text.toString().trim().lowercase()

            if (guess.length != MoKarenajData.WORD_LENGTH) {
                showTopMessage("Mo la dwèt ni ${MoKarenajData.WORD_LENGTH} lèt")
                return
            }
            if (!MoKarenajData.isValidWord(activity, guess)) {
                showTopMessage("❌ Mo la sa pa adan diktyonè kréyòl")
                return
            }

            val states = MoKarenajData.evaluateGuess(targetWord, guess)
            rows[currentAttempt] = MoKarenajRow(guess.toList(), states)
            renderBoard()
            updateLegend(guess, states)

            val won = states.all { it == LetterState.CORRECT }
            currentAttempt++

            when {
                won -> {
                    gameOver = true
                    endGame(true)
                }
                currentAttempt >= MoKarenajData.MAX_ATTEMPTS -> {
                    gameOver = true
                    endGame(false)
                }
                else -> {
                    editGuess.setText("")
                    tvAttempts.text = "Essai ${currentAttempt + 1}/${MoKarenajData.MAX_ATTEMPTS}"
                }
            }
        }

        // Toast.setGravity() est ignoré depuis Android 11 : ancré en haut via Snackbar
        // pour ne pas se faire masquer par le clavier virtuel (même piste que WordSearchFragment).
        private fun showTopMessage(message: String) {
            Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT).apply {
                (view.layoutParams as? FrameLayout.LayoutParams)?.let {
                    it.gravity = Gravity.TOP
                    view.layoutParams = it
                }
            }.show()
        }

        private fun updateLegend(guess: String, states: List<LetterState>) {
            val activity = requireActivity()
            guess.forEachIndexed { index, letter ->
                val newState = states[index]
                val existing = letterBestState[letter]
                if (existing == null || statePriority(newState) > statePriority(existing)) {
                    letterBestState[letter] = newState
                }
            }

            legendContainer.removeAllViews()
            letterBestState.entries.sortedBy { it.key }.forEach { (letter, state) ->
                val chip = TextView(activity).apply {
                    text = letter.toString().uppercase()
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    setBackgroundColor(state.color())
                    setPadding(24, 16, 24, 16)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 8, 0) }
                }
                legendContainer.addView(chip)
            }
        }

        private fun statePriority(state: LetterState): Int = when (state) {
            LetterState.CORRECT -> 3
            LetterState.PRESENT -> 2
            LetterState.ABSENT -> 1
            LetterState.EMPTY -> 0
        }

        private fun endGame(won: Boolean) {
            editGuess.isEnabled = false
            btnSubmit.isEnabled = false

            // La partie se termine sur le mot ; c'est le seul moment où sa
            // traduction ne gâche rien, et le seul où elle sert à quelque
            // chose.
            val glose = TranslationDictionary.libelle(
                requireContext(), targetWord, prefixe = "\n\nEn français : "
            )

            AlertDialog.Builder(requireContext())
                .setTitle(if (won) "🎉 Bravo !" else "😔 Domaj !")
                .setMessage(
                    (if (won) "Ou touvé mo-a an $currentAttempt èsèy : ${targetWord.uppercase()}"
                    else "Mo la té : ${targetWord.uppercase()}") + glose
                )
                .setPositiveButton("Rejouer") { _, _ -> startNewGame() }
                .setNegativeButton("OK", null)
                .show()
        }

        override fun onDestroyView() {
            super.onDestroyView()
            rootView = null
        }
    }

    // Fragment pour Fraz a twou : une phrase authentique dont un mot
    // manque, et quatre propositions. Les phrases, la réponse et les leurres
    // viennent tels quels de l'actif ; ce fragment ne fait que présenter et
    // compter.
    class ClozeFragment : Fragment() {
        private var rootView: ScrollView? = null

        private lateinit var tvScore: TextView
        private lateinit var tvProgress: TextView
        private lateinit var progressBar: ProgressBar
        private lateinit var tvSentence: TextView
        private lateinit var tvSource: TextView
        private lateinit var tvFeedback: TextView
        private lateinit var optionsContainer: LinearLayout
        private lateinit var btnNext: Button
        private lateinit var difficultyRow: LinearLayout

        private val optionButtons = mutableListOf<Button>()

        private var round: List<ClozeQuestion> = emptyList()
        private var questionIndex = 0
        private var score = 0
        private var answered = false
        private var difficulty = ClozeDifficulty.NORMALE

        private val couleurNeutre = Color.parseColor("#FF8C00")
        private val couleurJuste = Color.parseColor("#4CAF50")
        private val couleurFausse = Color.parseColor("#E53935")
        private val couleurInerte = Color.parseColor("#BDBDBD")

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity

            rootView = ScrollView(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.parseColor("#F5F5F5"))

                val mainLayout = LinearLayout(activity).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.VERTICAL
                    setPadding(32, 16, 32, 16)

                    // Titre et score sur une ligne : la phrase à trous a besoin
                    // de toute la hauteur qu'on peut lui laisser.
                    val headerRow = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 12 }
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL

                        val title = TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                            )
                            text = "📝 Fraz a twou"
                            textSize = 18f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(couleurNeutre)
                        }
                        addView(title)

                        tvScore = TextView(activity).apply {
                            text = "0 / ${ClozeData.QUESTIONS_PER_ROUND}"
                            textSize = 14f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.parseColor("#333333"))
                        }
                        addView(tvScore)
                    }
                    addView(headerRow)

                    // Choix de la difficulté : elle porte sur la fréquence du
                    // mot masqué, pas sur le nombre de propositions.
                    difficultyRow = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 16 }
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                    }
                    ClozeDifficulty.values().forEach { niveau ->
                        val bouton = Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                            ).apply { setMargins(4, 0, 4, 0) }
                            text = niveau.label
                            textSize = 12f
                            isAllCaps = false
                            setTextColor(Color.WHITE)
                            tag = niveau
                            setOnClickListener {
                                difficulty = niveau
                                startNewRound()
                            }
                        }
                        difficultyRow.addView(bouton)
                    }
                    addView(difficultyRow)

                    tvProgress = TextView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 6 }
                        text = "Question 1 / ${ClozeData.QUESTIONS_PER_ROUND}"
                        textSize = 13f
                        setTextColor(Color.parseColor("#666666"))
                    }
                    addView(tvProgress)

                    progressBar = ProgressBar(
                        activity, null, android.R.attr.progressBarStyleHorizontal
                    ).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 16 }
                        max = ClozeData.QUESTIONS_PER_ROUND
                        progress = 0
                    }
                    addView(progressBar)

                    // Carte de la phrase
                    val sentenceCard = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = 20 }
                        orientation = LinearLayout.VERTICAL
                        setPadding(28, 28, 28, 24)
                        background = GradientDrawable().apply {
                            cornerRadius = 12f
                            setColor(Color.WHITE)
                        }

                        tvSentence = TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            textSize = 18f
                            setLineSpacing(0f, 1.25f)
                            setTextColor(Color.parseColor("#212121"))
                        }
                        addView(tvSentence)

                        // La source est affichée phrase par phrase : ce sont des
                        // extraits d'œuvres d'auteurs identifiés, pas du texte
                        // anonyme.
                        tvSource = TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { topMargin = 14 }
                            textSize = 11f
                            setTextColor(Color.parseColor("#9E9E9E"))
                        }
                        addView(tvSource)
                    }
                    addView(sentenceCard)

                    // Les quatre propositions, une par ligne : deux colonnes
                    // couperaient les formes agglutinées comme « an-mwen ».
                    optionsContainer = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.VERTICAL
                    }
                    repeat(4) { position ->
                        val bouton = Button(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { bottomMargin = 12 }
                            textSize = 16f
                            setTextColor(Color.WHITE)
                            setTypeface(null, Typeface.BOLD)
                            isAllCaps = false
                            setOnClickListener { onOptionChosen(position) }
                        }
                        optionButtons.add(bouton)
                        optionsContainer.addView(bouton)
                    }
                    addView(optionsContainer)

                    tvFeedback = TextView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 4; bottomMargin = 8 }
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        visibility = View.GONE
                    }
                    addView(tvFeedback)

                    btnNext = Button(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 4 }
                        text = "➡️ Question suivante"
                        setBackgroundColor(couleurNeutre)
                        setTextColor(Color.WHITE)
                        setTypeface(null, Typeface.BOLD)
                        isAllCaps = false
                        visibility = View.INVISIBLE
                        setOnClickListener { goToNextQuestion() }
                    }
                    addView(btnNext)

                    val btnRestart = Button(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, 16, 0, 8) }
                        text = "🔄 Nouvelle partie"
                        textSize = 14f
                        setBackgroundColor(Color.parseColor("#9C27B0"))
                        setTextColor(Color.WHITE)
                        setTypeface(null, Typeface.BOLD)
                        isAllCaps = false
                        setOnClickListener { startNewRound() }
                    }
                    addView(btnRestart)

                    // Règles + crédits du corpus
                    val rulesCard = LinearLayout(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 16 }
                        orientation = LinearLayout.VERTICAL
                        setPadding(24, 20, 24, 20)
                        background = GradientDrawable().apply {
                            cornerRadius = 12f
                            setColor(Color.WHITE)
                        }

                        val rulesTitle = TextView(activity).apply {
                            text = "📜 Règles du jeu"
                            textSize = 16f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(couleurNeutre)
                            setPadding(0, 0, 0, 12)
                        }
                        addView(rulesTitle)

                        val rulesText = TextView(activity).apply {
                            text = "Chaque phrase est une phrase kréyòl réelle, à laquelle " +
                                "il manque un mot. Parmi les quatre propositions, une seule " +
                                "est celle qu'a écrite l'auteur : les trois autres sont des " +
                                "mots que le corpus atteste au même endroit, elles sonnent " +
                                "donc juste tant qu'on ne lit pas toute la phrase.\n\n" +
                                "La difficulté porte sur la fréquence du mot manquant : " +
                                "courant en « Facile », rare en « Difficile »."
                            textSize = 14f
                            setTextColor(Color.parseColor("#333333"))
                        }
                        addView(rulesText)

                        val creditsText = TextView(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { topMargin = 16 }
                            text = "Phrases extraites du corpus :\n" +
                                ClozeData.attribution(activity)
                            textSize = 11f
                            setTextColor(Color.parseColor("#757575"))
                        }
                        addView(creditsText)
                    }
                    addView(rulesCard)
                }

                addView(mainLayout)

                post {
                    // Même précaution que les autres jeux : ce post() peut
                    // s'exécuter après un changement d'onglet.
                    if (isAdded) {
                        startNewRound()
                    }
                }
            }

            return rootView!!
        }

        private fun startNewRound() {
            val activity = requireActivity()
            round = ClozeData.newRound(activity, difficulty)
            questionIndex = 0
            score = 0
            answered = false
            tvScore.text = "0 / ${ClozeData.QUESTIONS_PER_ROUND}"
            progressBar.max = maxOf(1, round.size)
            progressBar.progress = 0
            highlightDifficulty()

            if (round.isEmpty()) {
                showMissingAsset()
                return
            }
            renderQuestion()
        }

        private fun highlightDifficulty() {
            for (i in 0 until difficultyRow.childCount) {
                val bouton = difficultyRow.getChildAt(i) as Button
                val actif = bouton.tag == difficulty
                bouton.setBackgroundColor(if (actif) couleurNeutre else couleurInerte)
            }
        }

        /**
         * Sans l'actif, le jeu ne se rabat pas sur des phrases de secours : il
         * le dit. Un jeu de dépannage jouable masquerait une livraison cassée.
         */
        private fun showMissingAsset() {
            tvSentence.text = "Les phrases de Fraz a twou n'ont pas pu être chargées."
            tvSource.text = ""
            tvProgress.text = ""
            tvFeedback.visibility = View.GONE
            btnNext.visibility = View.INVISIBLE
            optionButtons.forEach {
                it.visibility = View.GONE
            }
        }

        private fun renderQuestion() {
            val question = round[questionIndex]
            answered = false

            tvProgress.text = "Question ${questionIndex + 1} / ${round.size}"
            tvSource.text = question.source
            tvSentence.text = sentenceWithBlank(question)
            tvFeedback.visibility = View.GONE
            btnNext.visibility = View.INVISIBLE

            optionButtons.forEachIndexed { position, bouton ->
                val proposition = question.options.getOrNull(position)
                if (proposition == null) {
                    bouton.visibility = View.GONE
                } else {
                    bouton.visibility = View.VISIBLE
                    bouton.text = proposition
                    bouton.isEnabled = true
                    bouton.setBackgroundColor(couleurNeutre)
                }
            }
        }

        /** La phrase avec son trou matérialisé, en gras et en couleur. */
        private fun sentenceWithBlank(question: ClozeQuestion): CharSequence {
            val trou = "_____"
            val texte = question.before + trou + question.after
            return SpannableString(texte).apply {
                val debut = question.before.length
                setSpan(
                    ForegroundColorSpan(couleurNeutre),
                    debut, debut + trou.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    StyleSpan(Typeface.BOLD),
                    debut, debut + trou.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        /** La phrase complétée, le mot retrouvé mis en évidence. */
        private fun sentenceWithAnswer(question: ClozeQuestion): CharSequence {
            val texte = question.completed
            return SpannableString(texte).apply {
                val debut = question.before.length
                setSpan(
                    ForegroundColorSpan(couleurJuste),
                    debut, debut + question.answer.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    StyleSpan(Typeface.BOLD),
                    debut, debut + question.answer.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        private fun onOptionChosen(position: Int) {
            if (answered || round.isEmpty()) return
            val question = round[questionIndex]
            val choix = question.options.getOrNull(position) ?: return
            answered = true

            val juste = choix == question.answer
            if (juste) {
                score++
                tvScore.text = "$score / ${round.size}"
            }

            // Toutes les propositions se figent : la bonne en vert, celle qu'on
            // a touchée en rouge si elle était fausse. Voir la bonne réponse
            // compte autant que marquer le point.
            optionButtons.forEachIndexed { i, bouton ->
                val proposition = question.options.getOrNull(i)
                bouton.isEnabled = false
                bouton.setBackgroundColor(
                    when {
                        proposition == question.answer -> couleurJuste
                        i == position -> couleurFausse
                        else -> couleurInerte
                    }
                )
            }

            tvSentence.text = sentenceWithAnswer(question)
            // La réponse est révélée dans les deux cas — juste, elle est déjà en
            // vert dans la grille — donc sa traduction ne dévoile plus rien.
            // Elle n'arrive que si l'actif la connaît : 77 des 240 réponses du
            // jeu sont glosées, les autres s'affichent sans.
            val glose = TranslationDictionary.libelle(
                requireContext(), question.answer, prefixe = " — "
            )
            tvFeedback.apply {
                text = if (juste) "✅ Ou touvé ! ${question.answer}$glose"
                       else "❌ La phrase disait « ${question.answer} »$glose"
                setTextColor(if (juste) couleurJuste else couleurFausse)
                visibility = View.VISIBLE
            }

            progressBar.progress = questionIndex + 1
            btnNext.visibility = View.VISIBLE
            btnNext.text =
                if (questionIndex + 1 >= round.size) "🏁 Voir le résultat"
                else "➡️ Question suivante"
        }

        private fun goToNextQuestion() {
            if (questionIndex + 1 >= round.size) {
                endRound()
                return
            }
            questionIndex++
            renderQuestion()
        }

        private fun endRound() {
            val total = round.size
            val message = when {
                score == total -> "Sans faute : $score sur $total !"
                score == 0 -> "Aucune bonne réponse cette fois. Une autre manche ?"
                score == 1 -> "1 bonne réponse sur $total. Une autre manche ?"
                score * 2 >= total -> "$score bonnes réponses sur $total."
                else -> "$score bonnes réponses sur $total. Une autre manche ?"
            }
            AlertDialog.Builder(requireContext())
                .setTitle(if (score * 2 >= total) "🎉 Bravo !" else "💪 Encore un effort")
                .setMessage(message)
                .setPositiveButton("Rejouer") { _, _ -> startNewRound() }
                .setNegativeButton("OK", null)
                .show()
        }

        override fun onDestroyView() {
            super.onDestroyView()
            optionButtons.clear()
            rootView = null
        }
    }

    /**
     * Onglet « Jé » : le choix des jeux, puis le jeu choisi.
     *
     * Ce fragment ne joue à rien lui-même. Il montre une carte par jeu et, au
     * tap, installe le fragment du jeu dans son propre conteneur. Aucun pager
     * imbriqué : les jeux comportent des grilles qui se manipulent au doigt
     * (le glissé des Mots Mêlés, notamment), et un second ViewPager leur aurait
     * disputé chaque geste horizontal.
     *
     * Le retour au choix passe par [androidx.activity.OnBackPressedCallback]
     * plutôt que par l'override d'`onBackPressed` de l'activité : le rappel
     * n'est actif que pendant qu'un jeu est ouvert, si bien que le bouton
     * Retour continue de quitter l'application partout ailleurs, sans que
     * l'activité ait à savoir ce que ses onglets contiennent.
     */
    class GamesFragment : Fragment() {

        private var rootView: LinearLayout? = null
        private var conteneurJeu: FrameLayout? = null
        private var barreRetour: LinearLayout? = null
        private var grilleChoix: View? = null

        private val retourAuChoix = object : androidx.activity.OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = fermerLeJeu()
        }

        private data class Jeu(
            val emoji: String,
            val nom: String,
            val resume: String,
            val couleur: String,
            val fabrique: () -> Fragment
        )

        private val jeux = listOf(
            Jeu("🎲", "Mots Mêlés", "Retrouvez les mots cachés dans la grille",
                "#9C27B0") { WordSearchFragment() },
            Jeu("🔤", "Mots Mélangés", "Remettez les lettres dans l'ordre",
                "#1976D2") { WordScrambleFragment() },
            Jeu("🟩", "Mo an Karénaj", "Devinez le mot de 5 lettres en 6 essais",
                "#4CAF50") { MoKarenajFragment() },
            Jeu("📝", "Fraz a twou", "Complétez la phrase à laquelle il manque un mot",
                "#FF8C00") { ClozeFragment() }
        )

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity

            val colonne = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#F5F5F5"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }

            barreRetour = construireBarreRetour(activity).also {
                it.visibility = View.GONE
                colonne.addView(it)
            }

            grilleChoix = construireGrilleChoix(activity).also { colonne.addView(it) }

            conteneurJeu = FrameLayout(activity).apply {
                id = View.generateViewId()
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }
            colonne.addView(conteneurJeu)

            requireActivity().onBackPressedDispatcher
                .addCallback(viewLifecycleOwner, retourAuChoix)

            rootView = colonne
            return colonne
        }

        private fun construireBarreRetour(activity: SettingsActivity) =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(16, 12, 16, 12)
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                addView(TextView(activity).apply {
                    text = "‹  Tous les jeux"
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#1976D2"))
                })
                setOnClickListener { fermerLeJeu() }
            }

        private fun construireGrilleChoix(activity: SettingsActivity): View {
            val colonne = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 24, 20, 24)
            }

            colonne.addView(TextView(activity).apply {
                text = "🎮 Jé"
                textSize = 22f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1C1C1C"))
                setPadding(4, 0, 4, 6)
            })
            colonne.addView(TextView(activity).apply {
                text = "Quatre façons de jouer avec les mots du dictionnaire " +
                        "kréyòl du clavier."
                textSize = 14f
                setTextColor(Color.parseColor("#666666"))
                setLineSpacing(0f, 1.25f)
                setPadding(4, 0, 4, 20)
            })

            // Deux cartes par ligne : quatre jeux tombent juste, et une carte
            // pleine largeur pour chacun aurait poussé le quatrième hors de
            // l'écran, là où on ne le découvre plus. Une ligne incomplète
            // reçoit une cale de même poids, faute de quoi sa carte s'étirerait
            // sur toute la largeur.
            jeux.chunked(2).forEach { paire ->
                colonne.addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = 16 }
                    paire.forEachIndexed { rang, jeu ->
                        addView(carteJeu(activity, jeu, marginDroite = rang == 0))
                    }
                    if (paire.size == 1) {
                        addView(View(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
                            )
                        })
                    }
                })
            }

            return colonne
        }

        private fun carteJeu(activity: SettingsActivity, jeu: Jeu, marginDroite: Boolean) =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(16, 24, 16, 24)
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = 16f * resources.displayMetrics.density
                    setStroke(
                        (1.5f * resources.displayMetrics.density).toInt(),
                        activity.avecOpacite(jeu.couleur, 0x55)
                    )
                }
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                ).apply { if (marginDroite) rightMargin = 16 }

                addView(TextView(activity).apply {
                    text = jeu.emoji
                    textSize = 40f
                    gravity = Gravity.CENTER
                })
                addView(TextView(activity).apply {
                    text = jeu.nom
                    textSize = 17f
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor(jeu.couleur))
                    setPadding(0, 8, 0, 4)
                })
                addView(TextView(activity).apply {
                    text = jeu.resume
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(Color.parseColor("#777777"))
                    setLineSpacing(0f, 1.2f)
                })

                isClickable = true
                setOnClickListener { ouvrirLeJeu(jeu) }
            }

        private fun ouvrirLeJeu(jeu: Jeu) {
            val conteneur = conteneurJeu ?: return
            childFragmentManager.beginTransaction()
                .replace(conteneur.id, jeu.fabrique())
                .commit()
            grilleChoix?.visibility = View.GONE
            conteneur.visibility = View.VISIBLE
            barreRetour?.visibility = View.VISIBLE
            retourAuChoix.isEnabled = true
        }

        private fun fermerLeJeu() {
            val conteneur = conteneurJeu ?: return
            childFragmentManager.findFragmentById(conteneur.id)?.let {
                childFragmentManager.beginTransaction().remove(it).commit()
            }
            conteneur.visibility = View.GONE
            barreRetour?.visibility = View.GONE
            grilleChoix?.visibility = View.VISIBLE
            retourAuChoix.isEnabled = false
        }

        override fun onDestroyView() {
            super.onDestroyView()
            rootView = null
            conteneurJeu = null
            barreRetour = null
            grilleChoix = null
        }
    }

    // Fragment « Dictionnaire » : un champ de saisie et une liste de résultats.
    // C'est le seul onglet qui ne joue à rien — on y cherche un mot, dans un
    // sens ou dans l'autre, et on lit ce qu'il veut dire.
    class DictionaryFragment : Fragment() {

        private var rootView: ScrollView? = null
        private lateinit var champRecherche: EditText
        private lateinit var conteneurResultats: LinearLayout
        private lateinit var tvEtat: TextView

        // La recherche parcourt toute la table : à la vitesse de frappe, c'est
        // une dizaine de parcours par mot tapé. On attend 200 ms de silence
        // avant de chercher, ce qui ramène cela à un seul.
        private val delaiRecherche = Handler(Looper.getMainLooper())
        private var rechercheEnAttente: Runnable? = null

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity

            val racine = ScrollView(activity).apply {
                setBackgroundColor(Color.parseColor("#F5F5F5"))
                isFillViewport = true
            }

            val colonne = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
            }

            colonne.addView(TextView(activity).apply {
                text = "📚 Dictionnaire"
                textSize = 22f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#FF8C00"))
                setPadding(0, 0, 0, 8)
            })

            // Le chargement se fait ici et non à la première frappe : analyser
            // l'actif entre la frappe et les résultats se verrait.
            TranslationDictionary.charger(activity)

            colonne.addView(TextView(activity).apply {
                text = "Tapez un mot kréyòl ou un mot français : la recherche " +
                        "fonctionne dans les deux sens.\n" +
                        "Touchez un mot pour ouvrir sa fiche ; appui long pour " +
                        "le copier."
                textSize = 14f
                setTextColor(Color.parseColor("#666666"))
                setLineSpacing(0f, 1.2f)
                setPadding(0, 0, 0, 20)
            })

            champRecherche = EditText(activity).apply {
                hint = "kaz, maison, dlo, eau…"
                textSize = 18f
                // Couleurs explicites : sur fond blanc imposé, la couleur de
                // texte héritée du thème est elle-même claire, et le champ
                // paraissait vide alors qu'il contenait la requête.
                setTextColor(Color.parseColor("#1C1C1C"))
                setHintTextColor(Color.parseColor("#BBBBBB"))
                setSingleLine(true)
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                setPadding(24, 20, 24, 20)
                setBackgroundColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun afterTextChanged(s: android.text.Editable?) {
                        rechercheEnAttente?.let { delaiRecherche.removeCallbacks(it) }
                        val requete = s?.toString() ?: ""
                        val tache = Runnable { if (isAdded) afficherResultats(requete) }
                        rechercheEnAttente = tache
                        delaiRecherche.postDelayed(tache, 200)
                    }

                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                })
            }
            colonne.addView(champRecherche)

            tvEtat = TextView(activity).apply {
                textSize = 15f
                setTextColor(Color.parseColor("#999999"))
                setPadding(4, 20, 4, 8)
                setLineSpacing(0f, 1.25f)
            }
            colonne.addView(tvEtat)

            conteneurResultats = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            colonne.addView(conteneurResultats)

            // Les deux sources sont en CC BY-SA : la citation n'est pas une
            // politesse, c'est la condition de la réutilisation. C'est aussi ce
            // qui dit d'où sort la traduction qu'on lit, et donc jusqu'où on
            // peut lui faire confiance. Le texte vient de l'actif lui-même.
            colonne.addView(TextView(activity).apply {
                text = "Traductions issues de :\n" +
                        TranslationDictionary.attribution(activity) +
                        "\nPartage à l'identique."
                textSize = 12f
                setTextColor(Color.parseColor("#AAAAAA"))
                setLineSpacing(0f, 1.2f)
                setPadding(4, 28, 4, 8)
            })

            racine.addView(colonne)
            rootView = racine

            afficherResultats("")
            return racine
        }

        /**
         * Affiche les résultats d'une requête, ou l'invite quand elle est vide.
         *
         * Le cas « rien trouvé » mérite une explication plutôt qu'un vide : le
         * clavier connaît 5 296 mots et la table n'en glose que 513. Sans ce
         * message, quelqu'un qui cherche « sé » — le troisième mot le plus
         * fréquent du kréyòl — croit l'application cassée, alors qu'aucune
         * source libre ne le traduit.
         */
        private fun afficherResultats(requete: String) {
            val activity = activity as? SettingsActivity ?: return
            conteneurResultats.removeAllViews()

            if (requete.trim().length < 2) {
                tvEtat.text = "Entrez au moins deux lettres. " +
                        "${TranslationDictionary.taille(activity)} mots traduits."
                return
            }

            val resultats = TranslationDictionary.rechercher(activity, requete)
            if (resultats.isEmpty()) {
                tvEtat.text = "Aucun résultat pour « ${requete.trim()} ».\n" +
                        "Le dictionnaire est encore jeune : beaucoup de mots " +
                        "kréyòl n'ont pas encore de traduction publiée sous " +
                        "licence libre."
                return
            }

            tvEtat.text = if (resultats.size == 1) "1 résultat"
                          else "${resultats.size} résultats"

            resultats.forEachIndexed { rang, entree ->
                conteneurResultats.addView(ligneResultat(activity, entree, rang))
            }
        }

        private fun ligneResultat(
            activity: SettingsActivity,
            entree: TranslationDictionary.Entree,
            rang: Int
        ): View = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 16, 20, 16)
            // Une ligne sur deux légèrement teintée : la liste peut compter
            // quarante entrées, et rien d'autre ne sépare une glose du mot
            // suivant.
            setBackgroundColor(
                if (rang % 2 == 0) Color.WHITE else Color.parseColor("#FAFAFA")
            )
            isClickable = true
            setOnClickListener { ouvrirFiche(activity, entree) }
            // L'appui long garde la copie à un seul geste : c'est l'action
            // courante, et la faire passer par la fiche coûterait deux taps à
            // qui veut seulement coller un mot ailleurs.
            setOnLongClickListener { copierMot(activity, entree.mot); true }

            addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                addView(TextView(activity).apply {
                    text = entree.mot
                    textSize = 19f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#1C1C1C"))
                })
                addView(TextView(activity).apply {
                    text = entree.glose
                    textSize = 16f
                    setTextColor(Color.parseColor("#555555"))
                    setPadding(0, 4, 0, 0)
                })
            })

            // Le chevron des liens de référence de l'onglet Démarrage : c'est
            // déjà, ailleurs dans l'application, ce qui annonce qu'une ligne
            // ouvre quelque chose.
            addView(TextView(activity).apply {
                text = "›"
                textSize = 22f
                setTextColor(Color.parseColor("#BBBBBB"))
                setPadding(20, 0, 0, 0)
            })
        }

        /**
         * Fiche d'un mot : son sens, sa prononciation, ses exemples, sa source.
         *
         * Déployée d'emblée et sans position repliée. Une feuille inférieure
         * s'ouvre par défaut à une hauteur calculée sur l'écran : tant que la
         * fiche tient dedans on ne voit rien, mais sur un petit écran — ou avec
         * une police système agrandie, ce qui revient au même — elle s'ouvre en
         * cachant les boutons, sans que rien n'indique qu'il faut la tirer.
         */
        private fun ouvrirFiche(
            activity: SettingsActivity,
            entree: TranslationDictionary.Entree
        ) {
            val dialogue = BottomSheetDialog(activity)
            dialogue.setContentView(contenuFiche(activity, entree, dialogue))
            dialogue.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            dialogue.behavior.skipCollapsed = true
            dialogue.show()
        }

        private fun contenuFiche(
            activity: SettingsActivity,
            entree: TranslationDictionary.Entree,
            dialogue: BottomSheetDialog
        ): View {
            val colonne = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(40, 36, 40, 44)
            }

            colonne.addView(TextView(activity).apply {
                text = entree.mot
                textSize = 30f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1C1C1C"))
            })

            // La prononciation ne vient que de Kreyolopedia, dont les fiches
            // sont écrites à la main. Elle est rare et se pose sous le mot,
            // sans titre : un intitulé de section pour six caractères pèserait
            // plus que ce qu'il annonce.
            if (entree.phonetique.isNotEmpty()) {
                colonne.addView(TextView(activity).apply {
                    text = entree.phonetique
                    textSize = 16f
                    setTextColor(Color.parseColor("#999999"))
                    setPadding(0, 6, 0, 0)
                })
            }

            colonne.addView(titreSection(activity, "EN FRANÇAIS", 30))

            // Le générateur assemble les acceptions avec « , » ; rien ne lui
            // interdit d'en produire une qui contienne elle-même une virgule.
            // Découper là est donc une heuristique : au pire une acception
            // s'affiche sur deux lignes, jamais aucune n'est perdue.
            entree.glose.split(", ").filter { it.isNotBlank() }.forEach { sens ->
                colonne.addView(TextView(activity).apply {
                    text = "•  $sens"
                    textSize = 17f
                    setTextColor(Color.parseColor("#333333"))
                    setPadding(0, 0, 0, 8)
                    setLineSpacing(0f, 1.15f)
                })
            }

            // La définition entière quand la glose l'a raccourcie : « gwoka »
            // se glose « musique » sur une ligne de liste, mais la fiche a la
            // place de dire les chants et les danses. Le champ ne voyage dans
            // l'actif que lorsqu'il apporte quelque chose, la section
            // disparaît donc d'elle-même pour les mille formes dont la glose
            // est déjà toute la définition.
            if (entree.definition.isNotEmpty()) {
                colonne.addView(titreSection(activity, "DÉFINITION", 26))
                colonne.addView(TextView(activity).apply {
                    text = entree.definition
                    textSize = 16f
                    setTextColor(Color.parseColor("#333333"))
                    setLineSpacing(0f, 1.25f)
                })
            }

            // Une glose dit ce qu'un mot veut dire, jamais comment il s'emploie :
            // « kaz = maison » ne fait pas deviner « an ka rété adan an ti kaz ».
            // Ces phrases-là sont écrites pour cela.
            if (entree.exemples.isNotEmpty()) {
                colonne.addView(titreSection(
                    activity,
                    if (entree.exemples.size == 1) "EXEMPLE" else "EXEMPLES",
                    26
                ))
                // Chacune sur son fond, séparées d'un vrai intervalle : côte à
                // côte sur le blanc de la fiche, deux phrases se lisent comme
                // un seul paragraphe.
                entree.exemples.forEachIndexed { rang, phrase ->
                    colonne.addView(TextView(activity).apply {
                        text = phrase
                        textSize = 16f
                        setTextColor(Color.parseColor("#333333"))
                        setLineSpacing(0f, 1.25f)
                        setPadding(24, 20, 24, 20)
                        background = GradientDrawable().apply {
                            cornerRadius = 12f
                            setColor(Color.parseColor("#F6F7F8"))
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            if (rang < entree.exemples.size - 1) bottomMargin = 14
                        }
                    })
                }
            }

            colonne.addView(titreSection(activity, "SOURCE", 26))
            colonne.addView(TextView(activity).apply {
                text = entree.libelleSource + " · CC BY-SA 4.0"
                textSize = 15f
                setTextColor(Color.parseColor("#555555"))
            })

            colonne.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 30, 0, 0)

                addView(boutonFiche(
                    activity, "Copier le mot",
                    Color.parseColor("#FF8C00"), Color.WHITE, null
                ) {
                    copierMot(activity, entree.mot)
                    dialogue.dismiss()
                }.apply {
                    (layoutParams as LinearLayout.LayoutParams).bottomMargin = 20
                })

                // Le lien n'apparaît que si l'on sait sur quel article il
                // tombe. Un mot atteint par les traductions n'a pas de page à
                // lui : l'envoyer sur `wiktionary.org/wiki/<mot>` mènerait à
                // une page absente, ou pire, à un homographe d'une autre
                // langue.
                val url = entree.url
                if (url.isNotEmpty()) {
                    addView(boutonFiche(
                        activity, "Voir sur ${entree.libelleSource} ↗",
                        Color.WHITE, Color.parseColor("#2C7A8C"),
                        Color.parseColor("#B9D6DD")
                    ) {
                        ouvrirLien(activity, url)
                        dialogue.dismiss()
                    })
                }
            })

            return ScrollView(activity).apply { addView(colonne) }
        }

        /**
         * Intitulé d'une section de la fiche. Toutes se ressemblent au pixel
         * près ; seul l'espace au-dessus de la première diffère, la fiche
         * ouvrant sur le mot en 30sp.
         */
        private fun titreSection(
            activity: SettingsActivity,
            libelle: String,
            marge: Int
        ): TextView = TextView(activity).apply {
            text = libelle
            textSize = 11f
            letterSpacing = 0.12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#AAAAAA"))
            setPadding(0, marge, 0, 10)
        }

        /**
         * Bouton de la fiche. `isAllCaps = false` : le thème AppCompat force la
         * capitale sur les boutons d'une activité, ce qui donnerait « COPIER LE
         * MOT » à côté de libellés en casse normale partout ailleurs.
         */
        private fun boutonFiche(
            activity: SettingsActivity,
            libelle: String,
            fond: Int,
            encre: Int,
            bordure: Int?,
            action: () -> Unit
        ): Button = Button(activity).apply {
            text = libelle
            isAllCaps = false
            textSize = 16f
            setTextColor(encre)
            background = GradientDrawable().apply {
                cornerRadius = 14f
                setColor(fond)
                bordure?.let { setStroke(3, it) }
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { action() }
        }

        private fun copierMot(activity: SettingsActivity, mot: String) {
            val presse = activity.getSystemService(Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
            presse.setPrimaryClip(ClipData.newPlainText("Dictionnaire", mot))
            Snackbar.make(requireView(), "« $mot » copié", Snackbar.LENGTH_SHORT).show()
        }

        private fun ouvrirLien(activity: SettingsActivity, url: String) {
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                // Un appareil sans navigateur n'est pas une panne de l'onglet :
                // la fiche reste lisible, seul le lien ne mène nulle part.
                Log.e("SettingsActivity", "Lien injoignable: ${e.message}")
            }
        }

        override fun onDestroyView() {
            super.onDestroyView()
            rechercheEnAttente?.let { delaiRecherche.removeCallbacks(it) }
            rootView = null
        }
    }

    /**
     * Enveloppe plein écran pour les pages de référence — Guide, À Propos —
     * sorties de la barre d'onglets.
     *
     * Un [androidx.fragment.app.DialogFragment] plutôt qu'une Activity : les
     * deux pages existent déjà sous forme de Fragment, et les héberger ici
     * évite deux déclarations de manifeste et deux cycles de vie de plus pour
     * un contenu qu'on ouvre et qu'on referme.
     */
    class SheetFragment : androidx.fragment.app.DialogFragment() {

        companion object {
            private const val ARG_PAGE = "page"
            const val PAGE_GUIDE = "guide"
            const val PAGE_A_PROPOS = "a_propos"

            fun pour(page: String) = SheetFragment().apply {
                arguments = Bundle().apply { putString(ARG_PAGE, page) }
            }
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setStyle(STYLE_NORMAL, android.R.style.Theme_DeviceDefault_Light_NoActionBar)
        }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val activity = requireActivity() as SettingsActivity
            val page = arguments?.getString(ARG_PAGE) ?: PAGE_GUIDE

            val colonne = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
            }

            colonne.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.parseColor("#0080FF"))
                setPadding(16, 14, 16, 14)
                addView(TextView(activity).apply {
                    text = if (page == PAGE_GUIDE) "📖  Guide" else "ℹ️  À propos"
                    textSize = 18f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                })
                addView(TextView(activity).apply {
                    text = "✕"
                    textSize = 22f
                    setTextColor(Color.WHITE)
                    setPadding(20, 0, 8, 0)
                    isClickable = true
                    setOnClickListener { dismiss() }
                })
            })

            val hote = FrameLayout(activity).apply {
                id = View.generateViewId()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }
            colonne.addView(hote)

            if (savedInstanceState == null) {
                childFragmentManager.beginTransaction()
                    .replace(
                        hote.id,
                        if (page == PAGE_GUIDE) GuideFragment() else AboutFragment()
                    )
                    .commit()
            }

            return colonne
        }

        override fun onStart() {
            super.onStart()
            // Sans cela le dialogue s'ajuste à son contenu et laisse le fond de
            // l'activité visible sur les bords : ces deux pages sont de la
            // lecture longue, elles méritent tout l'écran.
            dialog?.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }
}
