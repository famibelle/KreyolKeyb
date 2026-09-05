package com.example.kreyolkeyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.Log

/**
 * Palette du clavier, en clair et en sombre.
 *
 * Elle existe parce que quatre surfaces se partagent les mêmes couleurs : les
 * touches ([KeyboardLayoutManager]), la barre de suggestions (le service), la
 * popup d'appui long ([AccentHandler]) et le panneau emoji ([EmojiPickerView]),
 * et qu'elles les écrivaient chacune en littéral. Un thème n'est pas ajoutable
 * tant qu'une couleur peut être décidée à son point d'usage.
 *
 * ### Les couleurs produit ne s'inversent pas
 *
 * Le vert tropical des touches d'action, l'orange caraïbe de la ponctuation et le
 * bleu caraïbe de la barre d'espace **sont identiques dans les deux thèmes**. Ce
 * sont eux qui rendent ce clavier reconnaissable, et la charte les décrit comme la
 * palette produit, vive parce qu'un clavier se lit en un dixième de seconde : les
 * assombrir donnerait un clavier générique. Ils n'ont pas besoin de bouger, leurs
 * contrastes ne dépendant pas de ce qui les entoure.
 *
 * Ce qui bascule, c'est donc uniquement le **blanc des touches de lettres**, qui
 * devient anthracite, et tout ce qui en découle : l'encre, la bordure, le fond du
 * clavier, la barre de suggestions, la popup, l'onglet emoji actif.
 *
 * ### Contrastes mesurés (WCAG, texte sur fond)
 *
 * | | clair | sombre |
 * |---|---|---|
 * | lettre sur sa touche | 12,63:1 | 12,43:1 |
 * | majuscule enclenchée | 5,10:1 | 4,30:1 |
 * | glyphe sur le vert | 2,71:1 | 2,71:1 |
 * | glyphe sur l'orange | 2,26:1 | 2,26:1 |
 * | glyphe sur le bleu | 3,26:1 | 3,26:1 |
 * | accent dans la popup | 12,63:1 | 11,09:1 |
 * | mot en tête, sur le vert | 4,56:1 | 4,56:1 |
 * | mot en tête, sur le bleu | 4,93:1 | 4,93:1 |
 * | mot nu sur le plateau | 10,69:1 | 17,17:1 |
 *
 * Les trois dernières lignes sont celles de la barre de suggestions (v17.0.0).
 * Elles obéissent à un régime différent des glyphes de touche : un mot de
 * suggestion se lit à 18 sp, ce qui est du texte courant au sens WCAG et non du
 * texte large, donc le seuil applicable est 4,5:1. C'est ce qui a fait
 * assombrir le vert kréyòl, qui n'était qu'à 3,41:1.
 *
 * Les valeurs claires sont celles d'avant ce thème, reprises telles quelles : le
 * clavier clair ne change pas d'un pixel. Les trois lignes des couleurs produit
 * sont donc l'existant, thème ou pas ; leurs glyphes sont larges et gras, régime
 * du texte de grande taille.
 *
 * Les aperçus d'appui long ne figurent pas dans ce tableau : leur encre n'est pas
 * une constante mais le résultat d'un calcul de contraste contre le fond réel de
 * la touche (`hintColorFor` dans [KeyboardLayoutManager]). Ce calcul suit la
 * palette sans rien savoir d'elle, et n'a pas eu à changer.
 */
object KeyboardTheme {

    private const val TAG = "KeyboardTheme"

    /** Ce que l'utilisateur choisit dans l'écran de réglages. */
    enum class Mode(val cle: String, val libelle: String) {
        SYSTEME("systeme", "Comme le téléphone"),
        CLAIR("clair", "Toujours clair"),
        SOMBRE("sombre", "Toujours sombre");

        companion object {
            /** Tolérante : une clé inconnue (préférence d'une version future,
             *  fichier recopié à la main) retombe sur le défaut plutôt que de jeter. */
            fun depuisCle(cle: String?): Mode =
                entries.firstOrNull { it.cle == cle } ?: SYSTEME
        }
    }

    /**
     * Un fond de touche, du haut vers le bas.
     *
     * Les touches de ce clavier sont dégradées depuis l'origine, y compris les
     * blanches : le thème conserve cette forme plutôt que de l'aplatir, sans quoi
     * le clavier clair changerait d'aspect alors qu'il n'a aucune raison de bouger.
     */
    data class Degrade(val haut: Int, val bas: Int) {
        /** Sous la forme qu'attend `GradientDrawable.setColors`. */
        fun couleurs(): IntArray = intArrayOf(haut, bas)
    }

    /**
     * Les couleurs dont les quatre surfaces ont besoin, résolues.
     *
     * Des `Int` et non des chaînes hexadécimales : c'est la forme qu'attendent
     * `setColor`, `setTextColor` et `setStroke`, et les touches sont restylées à
     * chaque changement d'état du shift, autant ne pas reparser un littéral à
     * chaque fois.
     */
    data class Palette(
        /** Vert tropical : Entrée et changements de mode. Identique en clair et sombre. */
        val vert: Degrade,
        /** Orange caraïbe : ponctuation. Identique aussi. */
        val orange: Degrade,
        /** Bleu caraïbe : barre d'espace. Identique aussi. */
        val bleu: Degrade,
        /** Fond d'une touche de lettre. C'est lui qui bascule. */
        val touche: Degrade,
        /** Fond des touches accentuées dédiées et du shift au repos. */
        val toucheAccentuee: Degrade,
        /** Fond du shift en majuscule ponctuelle. */
        val toucheMaj: Degrade,
        /** Fond du shift verrouillé en capitales. */
        val toucheCaps: Degrade,
        /** Fond du retour arrière, volontairement translucide sur le fond du clavier. */
        val toucheSuppr: Degrade,
        /** Contour d'une touche, qui la détache du fond du clavier. */
        val bordure: Int,
        /** Encre d'un glyphe posé sur une touche non colorée. */
        val encre: Int,
        /** Encre atténuée : shift enclenché, libellés secondaires. */
        val encreAttenuee: Int,
        /** Encre d'un glyphe posé sur le vert, l'orange ou le bleu. */
        val encreSurCouleur: Int,
        /** Micro-étiquettes KR/FR en tête des rangées de suggestions. */
        val encreEtiquette: Int,
        /** Fond derrière les touches, et derrière le panneau emoji. */
        val fondClavier: Int,
        /**
         * Fond de la barre de suggestions, c'est-à-dire le fond du plateau
         * creusé depuis la v17.0.0. **Plus sombre que [fondClavier]**, dans les
         * deux thèmes : c'est le signal principal du creux, et il est passé du
         * côté opposé en 17.0.0, la barre ayant été plus claire que les touches
         * jusque-là.
         */
        val fondSuggestions: Int,
        /**
         * Ombre interne du bord haut du plateau, et liséré éclairé de son bord
         * bas (v17.0.0). Voir [CuvetteSuggestions].
         *
         * Leur alpha diffère beaucoup d'un thème à l'autre parce que leur
         * support diffère : sur le fond clair, 13 % de noir se voient déjà ;
         * sur le fond sombre il en faut 40 % pour un écart comparable, et le
         * liséré doit y être un blanc translucide là où le thème clair se
         * contente d'un blanc presque plein.
         */
        val ombreCuvette: Int,
        val lisereCuvette: Int,
        /** Popup d'appui long : dégradé du haut, dégradé du bas, contour. */
        val popupHaut: Int,
        val popupBas: Int,
        val popupBordure: Int,
        /** Bouton d'accent de la popup : dégradé haut, bas, contour, encre. */
        val popupAccentHaut: Int,
        val popupAccentBas: Int,
        val popupAccentBordure: Int,
        val popupAccentEncre: Int,
        /** Bouton du caractère de base, volontairement en retrait. */
        val popupBaseHaut: Int,
        val popupBaseBas: Int,
        val popupBaseBordure: Int,
        val popupBaseEncre: Int,
        /** Fond de l'onglet actif du panneau emoji. */
        val emojiOngletActif: Int
    )

    // Les trois couleurs produit, seules communes aux deux thèmes. Nommées ici
    // pour que leur unicité soit visible dans le code, et non seulement dans un
    // commentaire. Valeurs reprises telles quelles de KeyboardLayoutManager.
    private val VERT = Degrade(Color.parseColor("#00C853"), Color.parseColor("#00A843"))
    private val ORANGE = Degrade(Color.parseColor("#FF8C00"), Color.parseColor("#FF7000"))
    private val BLEU = Degrade(Color.parseColor("#1E90FF"), Color.parseColor("#0000FF"))

    private val CLAIR = Palette(
        vert = VERT,
        orange = ORANGE,
        bleu = BLEU,
        touche = Degrade(Color.parseColor("#FFFFFF"), Color.parseColor("#F5F5F5")),
        toucheAccentuee = Degrade(Color.parseColor("#FFFFFF"), Color.parseColor("#F8F8F8")),
        toucheMaj = Degrade(Color.parseColor("#F0F0F0"), Color.parseColor("#E0E0E0")),
        toucheCaps = Degrade(Color.parseColor("#E8E8E8"), Color.parseColor("#D0D0D0")),
        toucheSuppr = Degrade(Color.parseColor("#CCFFFFFF"), Color.parseColor("#C0F0F0F0")),
        bordure = Color.parseColor("#D0D0D0"),
        encre = Color.parseColor("#333333"),
        encreAttenuee = Color.parseColor("#666666"),
        encreSurCouleur = Color.WHITE,
        encreEtiquette = Color.parseColor("#6C757D"),
        fondClavier = Color.parseColor("#F5F5F5"),
        fondSuggestions = Color.parseColor("#ECECEC"),
        ombreCuvette = Color.parseColor("#22000000"),
        lisereCuvette = Color.parseColor("#CCFFFFFF"),
        popupHaut = Color.parseColor("#FFFFFF"),
        popupBas = Color.parseColor("#F8F8F8"),
        popupBordure = Color.parseColor("#E0E0E0"),
        popupAccentHaut = Color.parseColor("#FFFFFF"),
        popupAccentBas = Color.parseColor("#F0F0F0"),
        popupAccentBordure = Color.parseColor("#C0C0C0"),
        popupAccentEncre = Color.parseColor("#333333"),
        popupBaseHaut = Color.parseColor("#F5F5F5"),
        popupBaseBas = Color.parseColor("#E8E8E8"),
        popupBaseBordure = Color.parseColor("#D0D0D0"),
        popupBaseEncre = Color.parseColor("#666666"),
        emojiOngletActif = Color.parseColor("#E0F2E9")
    )

    // Le fond du clavier est plus sombre que les touches, et non l'inverse comme
    // en clair : c'est ce qui fait lire les touches comme posées dessus. Une
    // touche plus sombre que son fond se lirait comme un trou.
    //
    // Le retour arrière garde sa translucidité (les deux premiers octets sont
    // inchangés) : c'est ce qui le distingue de ses voisines sans lui donner une
    // couleur à lui, et le fond qu'il laisse transparaître est désormais sombre.
    private val SOMBRE = Palette(
        vert = VERT,
        orange = ORANGE,
        bleu = BLEU,
        touche = Degrade(Color.parseColor("#2B2B2B"), Color.parseColor("#242424")),
        toucheAccentuee = Degrade(Color.parseColor("#2B2B2B"), Color.parseColor("#262626")),
        toucheMaj = Degrade(Color.parseColor("#3A3A3A"), Color.parseColor("#333333")),
        toucheCaps = Degrade(Color.parseColor("#4A4A4A"), Color.parseColor("#404040")),
        toucheSuppr = Degrade(Color.parseColor("#CC2B2B2B"), Color.parseColor("#C0242424")),
        bordure = Color.parseColor("#4A4A4A"),
        encre = Color.parseColor("#F0F0F0"),
        encreAttenuee = Color.parseColor("#B4B4B4"),
        encreSurCouleur = Color.WHITE,
        encreEtiquette = Color.parseColor("#9AA3AB"),
        fondClavier = Color.parseColor("#131313"),
        fondSuggestions = Color.parseColor("#0C0C0C"),
        ombreCuvette = Color.parseColor("#66000000"),
        lisereCuvette = Color.parseColor("#26FFFFFF"),
        popupHaut = Color.parseColor("#2B2B2B"),
        popupBas = Color.parseColor("#242424"),
        popupBordure = Color.parseColor("#454545"),
        popupAccentHaut = Color.parseColor("#333333"),
        popupAccentBas = Color.parseColor("#2B2B2B"),
        popupAccentBordure = Color.parseColor("#4A4A4A"),
        popupAccentEncre = Color.parseColor("#F0F0F0"),
        popupBaseHaut = Color.parseColor("#232323"),
        popupBaseBas = Color.parseColor("#1E1E1E"),
        popupBaseBordure = Color.parseColor("#3A3A3A"),
        popupBaseEncre = Color.parseColor("#909090"),
        emojiOngletActif = Color.parseColor("#26382D")
    )

    // Résolue une fois par prise de focus plutôt qu'à chaque touche : le service
    // survit au passage dans l'écran de réglages, donc la valeur ne peut pas être
    // figée au démarrage, mais elle n'a pas non plus à relire les préférences
    // soixante fois par reconstruction du clavier. Même politique que KeyFeedback.
    @Volatile
    private var courante: Palette = CLAIR

    /** La palette en vigueur. Sûre à appeler depuis le dessin d'une touche. */
    fun palette(): Palette = courante

    /**
     * Les trois cotes du plateau creusé (v17.0.0).
     *
     * L'ombre interne tient dans les 4 dp que la première rangée de
     * suggestions réserve déjà au-dessus de sa première puce, et le liséré dans
     * le rembourrage symétrique du bas : le creux ne coûte donc pas un point de
     * hauteur, ce qui compte, `computeAvailableRowsHeight()` comptant le budget
     * vertical au pixel près.
     *
     * L'encastrement latéral, lui, est posé par le service sur les marges de la
     * barre : c'est une affaire de mise en page, pas de peinture.
     */
    private const val OMBRE_INTERNE_DP = 4
    private const val LISERE_DP = 1
    private const val RAYON_CUVETTE_DP = 10

    /**
     * Le fond de la barre de suggestions, creusé.
     *
     * Rendu ici plutôt que dans le service pour que les trois cotes vivent à
     * côté des trois couleurs qu'elles dosent : c'est l'ensemble qui fait
     * l'effet, et les séparer garantirait qu'un réglage de l'un se fasse sans
     * voir les autres.
     */
    fun cuvetteSuggestions(context: Context): Drawable {
        val densite = context.resources.displayMetrics.density
        val p = palette()
        return CuvetteSuggestions(
            fond = p.fondSuggestions,
            ombre = p.ombreCuvette,
            lisere = p.lisereCuvette,
            ombrePx = (OMBRE_INTERNE_DP * densite).toInt(),
            // Au moins un pixel : sous mdpi le liséré s'arrondirait à zéro et
            // le creux perdrait le signal qui fait basculer sa lecture.
            liserePx = maxOf(1, (LISERE_DP * densite).toInt()),
            rayonPx = RAYON_CUVETTE_DP * densite
        )
    }

    /**
     * Relit le réglage et la configuration système.
     *
     * Ne dit délibérément **pas** si la palette a changé. Une première version
     * renvoyait ce booléen et le service s'en servait pour décider de reconstruire
     * sa vue ; le clavier restait sombre après un passage en clair. L'écran de
     * réglages vit dans le même processus que le service, donc
     * [KeyboardPreferences.setThemeMode] appelait déjà cette méthode au moment du
     * clic : elle consommait le changement, et le `refresh` suivant, celui du
     * service, ne voyait plus rien à signaler.
     *
     * La question « faut-il reconstruire ? » ne se pose pas ici : elle se pose par
     * rapport à la palette avec laquelle les vues **ont été construites**, que seul
     * le service connaît. Il compare donc [palette] à celle qu'il a mémorisée.
     */
    fun refresh(context: Context) {
        val avant = courante
        courante = resoudre(KeyboardPreferences.themeMode(context), systemeEnSombre(context))
        if (avant !== courante) {
            Log.d(TAG, "Palette : ${if (courante === SOMBRE) "sombre" else "claire"}")
        }
    }

    /**
     * La table de décision, séparée de la lecture du contexte pour être
     * vérifiable hors appareil (voir `KeyboardThemeTest`).
     *
     * Un test JVM ne peut ni fabriquer un `Context` ni obtenir de vraies couleurs
     * (`android.graphics.Color` n'est pas implémenté hors appareil, et
     * `unitTests.returnDefaultValues` fait renvoyer 0 à `parseColor`) : les deux
     * palettes y sont donc indiscernables champ par champ. Elles restent deux
     * objets distincts, et c'est leur **identité** que le test compare, ce qui
     * suffit à couvrir les six cases de cette table.
     */
    internal fun resoudre(mode: Mode, systemeEnSombre: Boolean): Palette =
        when (mode) {
            Mode.CLAIR -> CLAIR
            Mode.SOMBRE -> SOMBRE
            Mode.SYSTEME -> if (systemeEnSombre) SOMBRE else CLAIR
        }

    /**
     * Le téléphone est-il en mode sombre ?
     *
     * `UI_MODE_NIGHT_UNDEFINED` compte comme clair : c'est ce que renvoient les
     * appareils antérieurs au mode sombre système (minSdk 21, le mode nuit n'est
     * arrivé qu'en 29), pour lesquels « comme le téléphone » ne peut vouloir dire
     * que clair.
     */
    private fun systemeEnSombre(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
}
