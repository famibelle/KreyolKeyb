package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.kreyolkeyboard.TranslationDictionary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tout ce qu'une carte affiche, rassemblé une fois.
 *
 * Le mot montré est **celui que le joueur a rencontré**, avec la casse du jeu
 * qui l'a donné, et la glose est celle de la table de traductions.
 *
 * Deux champs du carnet luxembourgeois n'ont pas d'équivalent ici et ne sont
 * pas portés : la famille de flexions, parce que le kréyòl fléchit à peine et
 * qu'aucune source ne livre de familles, et la catégorie grammaticale, que le
 * LOD fournit là-bas et que ni Kreyolopedia ni le Wiktionnaire ne donnent en
 * volume. La place qu'ils occupaient dans le panneau revient au crédit de la
 * phrase, qui, lui, est une obligation.
 */
data class ContenuCarte(
    val carte: CarteMot,
    val rarete: Rarete,
    val rang: Int?,
    val glose: String,
    val exemple: String?,
    val blason: Blasonnement = Blasonnement.AUCUN,
    /**
     * L'auteur ou l'œuvre d'où vient [exemple], taillé pour une ligne.
     *
     * Il accompagne la phrase partout où elle s'affiche, et ce n'est pas une
     * décoration : les phrases sont des extraits d'auteurs guadeloupéens
     * vivants et publiés, et la règle du corpus — déjà celle de *Fraz a
     * twou* — est que la source voyage avec l'extrait.
     */
    val creditExemple: String? = null,
    /**
     * Le nom de la source de la glose : « Kreyolopedia » ou « Wiktionnaire ».
     *
     * Vide quand le mot n'a pas de glose du tout, ce qui ne devrait pas
     * arriver — les cinq jeux ne tirent que dans les mots glosés — mais un
     * carnet repris d'une version antérieure peut en porter.
     */
    val sourceGlose: String = ""
)

/**
 * Le rendu d'une carte du carnet.
 *
 * **Pas d'emoji, et une image seulement là où elle est juste.** La mesure qui
 * a fondé cette règle côté luxembourgeois — 1 504 têtes de glose distinctes
 * pour 1 690 substantifs, soit 1,12 mot par dessin — vaut a fortiori sur un
 * vivier de 778 formes. Une
 * bibliothèque d'images ne peut donc pas couvrir la collection, et l'argument
 * qui tenait ici — « un jeu où quelques cartes portent une image et les autres
 * rien du tout se lit comme inachevé » — a été résolu autrement : *toutes* les
 * cartes reçoivent un sujet génératif, le tracé de leur mot, et le dessin est
 * un **surcroît** qui en distingue trois sur cent. Rien n'est inachevé, parce
 * que rien n'est vide ; l'enluminure est une propriété collectionnable de plus,
 * indépendante de la rareté. Voir [Blason] et [Meubles].
 *
 * ## La carte est une carte à jouer, et son ornement monte avec la rareté
 *
 * La disposition suit celle d'une carte de collection, et elle est **fixe** :
 * gemme de coût en débord sur l'angle de l'ouverture, illustration en haut,
 * **plaque du nom en travers du milieu**, agrafe sertie dessous, pastille de
 * nature, panneau de texte, puis un bas de carte qui rassemble ce que le
 * carnet sait de la partie : deux écus, la pastille de provenance et la ligne
 * de série. Rien
 * ne descend quand une phrase du corpus prend trois lignes — c'est ce qui permet
 * de lire une grille de cartes sans en lire aucune.
 *
 * Le mot a mis du temps à trouver sa place. Il a d'abord été une étiquette de
 * métal dans le bandeau du haut, ce qui en faisait la légende de la carte ;
 * il en est le **sujet**. Au milieu, sur une plaque qui mord sur
 * l'illustration, il redevient ce que le joueur a gagné, et tout ce qui
 * l'entoure redevient ce que c'était — une description. Et comme c'est la
 * pièce que l'œil trouve en premier, c'est elle qui porte l'échelle de
 * rareté la plus franche : sa matière, du bois à l'or. Les emplacements sont dans
 * [Ornement] et le tracé dans [CarteOrnee] ; ce fichier ne fait plus que
 * choisir *quoi* poser dans chaque case.
 *
 * Le cadre, lui, s'enrichit palier par palier : étain nu, bronze à rivets,
 * argent à volutes et arche, or à clef de voûte et étincelles. Voir
 * [Ornement] pour la liste exacte de ce que chaque palier ajoute, et pour ce
 * que cette échelle a coûté.
 *
 * ## Ce que la rareté a le droit de dire
 *
 * La règle a **changé**, et il faut le dire franchement. Elle disait : la
 * teinte appartient au mot, donc la rareté ne peut pas prendre une couleur de
 * plus, seulement une matière. Elle dit maintenant : la teinte appartient au
 * mot **à l'intérieur du cadre** — la face et la gemme de coût —, et le métal
 * du cadre appartient au palier.
 *
 * Ce qui autorise les deux à cohabiter, c'est que le métal *encadre* au lieu
 * de recouvrir : quatre valeurs de métal contre trois cent soixante teintes de
 * face, et c'est toujours la face et l'illustration qui remplissent l'œil.
 * Le corollaire d'origine tient toujours, et il porte même plus qu'avant : les
 * communes sont **volontairement nues**. Un palier ne se voit que par
 * contraste, et enrichir les rares sans garder les communes sobres n'aurait
 * déplacé que la moitié de l'écart.
 */
object CarteCarnet {

    private val FORMAT_DATE = SimpleDateFormat("dd.MM.yy", Locale.FRENCH)

    private const val ENCRE = 0xFF1B1610.toInt()
    private const val ENCRE_DOUCE = 0xFF4A4234.toInt()
    private const val ENCRE_PALE = 0xFF7A7160.toInt()

    /** Le corps des étiquettes de type, et le plancher où il cesse de céder. */
    private const val TYPE_CORPS = 11f
    private const val TYPE_CORPS_MIN = 9f

    fun contenu(context: Context, carte: CarteMot): ContenuCarte {
        val entree = TranslationDictionary.entree(context, carte.forme)
        val exemple = TranslationDictionary.exemples(context, carte.forme).firstOrNull()
        return ContenuCarte(
            carte = carte,
            rarete = Carnet.rarete(context, carte),
            rang = Carnet.rang(context, carte.forme),
            glose = entree?.glose.orEmpty(),
            exemple = exemple?.phrase,
            // La clé du blason est la forme elle-même : sans familles de
            // flexions, il n'y a pas de représentant à qui déléguer le
            // rangement, contrairement au carnet luxembourgeois.
            blason = Armorial.pour(context, carte.forme, carte.forme),
            creditExemple = exemple?.credit?.ifEmpty { null },
            sourceGlose = entree?.libelleSource.orEmpty()
        )
    }

    /**
     * La vignette de la grille du carnet : le cadre, l'illustration, le mot.
     *
     * Elle ne porte **pas** le texte de la carte ouverte, et c'est délibéré :
     * à 160 dp, un panneau de glose, une phrase et une ligne de série
     * deviendraient six lignes de quatre pixels que personne ne lit, tout en
     * écrasant l'ornement qui, lui, se lit encore très bien à cette taille.
     * Une très rare se reconnaît à sa dorure, pas à une pastille.
     *
     * Ce qui reste, c'est le strict nécessaire pour choisir quelle carte
     * ouvrir : le mot, son sens en une ligne coupée, et le ou les jeux qui
     * l'ont donnée. La barre de Leitner est peinte sur le cadre.
     */
    fun vignette(context: Context, c: ContenuCarte, cote: Int): View {
        val carte = CarteOrnee(context, c.carte.forme, c.rarete, vignette = true, blason = c.blason)
            .avecBoite(c.carte.boite)

        carte.posee(
            ligne(context, c.carte.forme, taille = 18f, couleur = ENCRE, gras = true),
            Ornement.NOM_VIGNETTE
        )

        // L'emoji du jeu plutôt que son nom : à cette taille, un nom de jeu
        // prendrait toute la ligne et chasserait le sens, qui est ce que la
        // carte apprend.
        val provenance = c.carte.jeux.joinToString("") { it.emoji }
        carte.posee(
            ligne(
                context,
                "$provenance ${c.glose.ifEmpty { "—" }}",
                taille = 11.5f, couleur = ENCRE_DOUCE, gras = false
            ),
            Ornement.GLOSE_VIGNETTE
        )
        return carte
    }

    /**
     * La carte entière, telle qu'on la regarde quand on l'a choisie.
     *
     * L'ordre est celui d'une carte de collection, et chaque case dit une
     * chose que le carnet connaissait déjà :
     *
     * - la **gemme de coût**, c'est la longueur du mot — le seul chiffre qui
     *   mesure un effort réel ;
     * - la **pastille de source**, sous la plaque, dit d'où vient le sens :
     *   « Kreyolopedia » ou « Wiktionnaire ». Le carnet luxembourgeois y met
     *   la catégorie grammaticale du LOD ; voir [complete] pour pourquoi elle
     *   n'a pas d'équivalent kréyòl et pourquoi c'est l'attribution qui prend
     *   la place ;
     * - le **médaillon de provenance**, entre les écus, dit la partie qui a
     *   donné la carte : l'emblème du jeu sur un émail de sa couleur, « gagné
     *   à » et son nom en légende. C'est une mention d'inventaire, comme le
     *   numéro et la date de la ligne juste dessous. Il est tracé par la carte
     *   elle-même (voir [Ornement.dessinerMedaillon]), pas posé comme une vue.
     *   Le corps de l'étiquette de nature s'ajuste : voir [corpsDeLEtiquette] ;
     * - les deux **écus** comptent les rencontres et la boîte de révision ;
     * - la **ligne de série** situe la carte dans la collection : son numéro
     *   d'entrée, le jour de la capture, et le rang de fréquence qui a
     *   décidé de sa rareté.
     */
    fun complete(context: Context, c: ContenuCarte): View {
        val jeu = c.carte.origine
        val metal = Ornement.metal(c.rarete)
        val carte = CarteOrnee(
            context, c.carte.forme, c.rarete, vignette = false, blason = c.blason, jeu = jeu,
            intensite = Rarete.intensitePourRang(c.rang)
        )

        carte.posee(
            ligne(context, "${c.carte.forme.length}", taille = 25f, couleur = Color.WHITE, gras = true),
            Ornement.GEMME
        )
        // Le nom prend toute la plaque, sans rattrapage : elle est centrée sur
        // l'axe de la carte, comme la clef de voûte, l'agrafe et le joyau de
        // rareté. C'est le décalage de 19 unités de l'ancienne plaque du haut
        // qui demandait un emplacement calculé.
        // La police monte avec la rareté, comme la matière de la plaque : voir
        // [PolicesCarnet].
        // Gravé dans la plaque, pas imprimé dessus : voir [MotGrave].
        carte.posee(
            MotGrave(context, c.carte.forme, Ornement.support(c.rarete)).apply {
                setTypeface(PolicesCarnet.pour(context, c.rarete), Typeface.NORMAL)
                tag = floatArrayOf(21f * PolicesCarnet.echelle(c.rarete), 0f)
            },
            Ornement.PLAQUE
        )

        // La pastille dit d'où vient le sens, et non ce qu'est le mot.
        //
        // C'est un écart assumé avec le carnet luxembourgeois, où elle porte
        // la catégorie grammaticale du LOD. Ici cette catégorie n'existe pas,
        // et la deviner est hors de portée : le kréyòl ne capitalise pas ses
        // substantifs, et lire la nature sur la glose française échoue une
        // fois sur trois (« té » se glose « terre », « manman » « mère »,
        // « kat » « quatre » — le premier mot de la glose finit en -re et
        // aucun des trois n'est un verbe). Une pastille qui dirait « Mot »
        // sur toutes les cartes ne dirait rien.
        //
        // La source, elle, est connue carte par carte, et elle est due : les
        // gloses sont sous CC BY-SA 4.0, donc l'attribution doit accompagner
        // ce qu'elle couvre. C'est la même règle que le crédit sous la
        // phrase, en haut de carte plutôt qu'en bas.
        val vueNature = ligne(context, c.sourceGlose, TYPE_CORPS, ENCRE, gras = true)
        vueNature.tag = floatArrayOf(corpsDeLEtiquette(vueNature), 0f)
        carte.posee(vueNature, Ornement.NATURE_TEXTE)
        // La provenance n'est pas une vue : c'est le médaillon, tracé par la
        // carte elle-même à partir du jeu. Une vue vide le double pour les
        // lecteurs d'écran, qui ne lisent pas une légende dessinée.
        carte.posee(
            View(context).apply {
                contentDescription = "gagné à ${jeu.nom}"
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            },
            Ornement.PROVENANCE
        )

        // Les écus mordent sur le bas du panneau (375 contre 378) : le texte
        // s'arrête au-dessus, sinon sa dernière ligne passe sous « VUES ».
        carte.posee(
            panneau(context, c),
            RectF(
                Ornement.PANNEAU_TEXTE.left, Ornement.PANNEAU_TEXTE.top,
                Ornement.PANNEAU_TEXTE.right, Ornement.ECU_G.top - 3f
            )
        )

        carte.posee(
            ligne(context, "${c.carte.rencontres}", taille = 15f, couleur = Color.WHITE, gras = true),
            Ornement.ECU_G_TEXTE
        )
        carte.posee(
            ligne(
                context,
                if (c.carte.acquise) "✓" else "${c.carte.boite + 1}",
                taille = 15f, couleur = Color.WHITE, gras = true
            ),
            Ornement.ECU_D_TEXTE
        )

        // Le corps de 7,5 est imposé par la bande, qui vit maintenant dans la
        // marge : voir [Ornement.SERIE_G]. C'est celui des libellés d'écu.
        // Le jeu n'y figure plus : le médaillon le dit déjà, par son emblème
        // et son nom.
        val serie = "n° %03d · %s".format(
            Locale.FRENCH, c.carte.numero, FORMAT_DATE.format(Date(c.carte.premiereFois))
        )
        carte.posee(
            ligne(context, serie, taille = 7.5f, couleur = metal.trait, gras = true, ou = Gravity.START),
            Ornement.SERIE_G
        )
        carte.posee(
            ligne(
                context,
                c.rang?.let { "${it + 1}ᵉ" } ?: "hors corpus",
                taille = 7.5f, couleur = metal.trait, gras = true, ou = Gravity.END
            ),
            Ornement.SERIE_D
        )
        return carte
    }

    /**
     * Le corps de l'étiquette de nature : onze, et moins si elle déborde.
     *
     * Elle porte ici un nom de source, « Kreyolopedia » ou « Wiktionnaire »,
     * dont le plus large demande 84 unités pour les 92 de la pastille : elle
     * ne descend donc jamais en pratique. Le rattrapage reste parce qu'il est
     * gratuit et qu'une troisième source, un jour, n'aura pas à se mesurer
     * avant d'être ajoutée.
     */
    private fun corpsDeLEtiquette(vue: TextView): Float {
        // Le pinceau de la vue elle-même, et non un neuf : les tailles sont
        // en unités de carte, donc la largeur mesurée l'est aussi.
        val large = android.text.TextPaint(vue.paint)
            .apply { textSize = TYPE_CORPS }
            .measureText(vue.text.toString())
        val boite = Ornement.NATURE_TEXTE.width()
        val corps = if (large > boite) TYPE_CORPS * boite / large else TYPE_CORPS
        return corps.coerceAtLeast(TYPE_CORPS_MIN)
    }

    /**
     * Le panneau de texte : le sens, la phrase du corpus, son crédit.
     *
     * C'est la boîte de texte d'une carte à jouer, et c'est le cœur de
     * celle-ci : sur une carte de jeu ce cadre dit ce que la carte *fait*, ici
     * il dit ce que le mot *veut dire*. Les trois lignes se coupent plutôt que
     * de déborder — la carte a une taille fixe, et une glose à rallonge ne
     * peut pas pousser les écus hors du cadre.
     *
     * **Le crédit ne cède jamais sa place.** C'est la seule des trois lignes
     * qui soit une obligation et non un contenu : une phrase d'auteur affichée
     * sans son auteur n'est pas une carte dégradée, c'est une citation non
     * attribuée. Comme [PanneauTexte] coupe par le bas quand la place manque,
     * la glose est bornée à une ligne dès qu'il y a une phrase, pour que le
     * crédit reste sous celle qu'il crédite.
     */
    private fun panneau(context: Context, c: ContenuCarte): View =
        PanneauTexte(context).apply {
            orientation = LinearLayout.VERTICAL
            // Un texte court se centre plutôt que de laisser une bande vide en bas.
            gravity = Gravity.CENTER_VERTICAL

            val credit = c.creditExemple?.takeIf { c.exemple != null }

            // Le panneau a une hauteur fixe. Avec une phrase et son crédit, la
            // glose se contente d'une ligne ; sans phrase, elle a les deux.
            addView(bloc(context, c.glose.ifEmpty { "sens non répertorié" }, 15f, ENCRE, 0f).apply {
                setTypeface(null, Typeface.BOLD)
                maxLines = if (c.exemple != null) 1 else 2
            })

            c.exemple?.let { phrase ->
                // Espaces insécables à l'intérieur des guillemets, sinon le
                // chevron fermant part seul à la ligne.
                addView(bloc(context, "« $phrase »", 12f, ENCRE_DOUCE, 2f).apply {
                    setTypeface(null, Typeface.ITALIC)
                    maxLines = 2
                })
            }

            if (credit != null) {
                // Trois unités d'air : collé à la phrase, le crédit se lisait
                // comme sa troisième ligne.
                addView(bloc(context, credit, 9f, ENCRE_PALE, 3f).apply { maxLines = 1 })
            }
        }

    /**
     * Une ligne posée dans une case : centrée, sur une seule ligne, coupée.
     *
     * La taille est exprimée **en unités de carte** et voyage dans le `tag` ;
     * c'est [CarteOrnee] qui la convertit en pixels une fois qu'il connaît sa
     * largeur réelle. Sans ça, la même carte serait illisible en vignette ou
     * ridicule en grand.
     */
    private fun ligne(
        context: Context,
        contenu: String,
        taille: Float,
        couleur: Int,
        gras: Boolean,
        ou: Int = Gravity.CENTER
    ): TextView = TextView(context).apply {
        text = contenu
        setTextColor(couleur)
        if (gras) setTypeface(null, Typeface.BOLD)
        gravity = ou or Gravity.CENTER_VERTICAL
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
        tag = floatArrayOf(taille, 0f)
    }

    /**
     * Le panneau retire ses dernières lignes quand elles ne tiennent pas.
     *
     * Un `LinearLayout` écrase le dernier enfant dans la place restante, qui
     * se retrouve coupé à mi-hauteur. La hauteur réelle d'une ligne dépend de
     * la police du téléphone : mieux vaut perdre la famille (ou la traduction)
     * entière qu'en montrer une demi-ligne.
     */
    private class PanneauTexte(context: Context) : LinearLayout(context) {
        override fun onMeasure(largeurSpec: Int, hauteurSpec: Int) {
            val plafond = MeasureSpec.getSize(hauteurSpec)
            val largeur = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(largeurSpec), MeasureSpec.EXACTLY)
            val libre = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            var total = 0
            var deborde = false
            for (i in 0 until childCount) {
                val vue = getChildAt(i)
                if (vue.visibility == GONE) continue
                vue.measure(largeur, libre)
                if (deborde || (i > 0 && total + vue.measuredHeight > plafond)) {
                    deborde = true
                    vue.visibility = GONE
                } else {
                    total += vue.measuredHeight
                }
            }
            super.onMeasure(largeurSpec, hauteurSpec)
        }
    }

    /** Une ligne du panneau de texte : elle, a le droit de revenir à la ligne. */
    private fun bloc(
        context: Context,
        contenu: String,
        taille: Float,
        couleur: Int,
        margeHaute: Float
    ): TextView = TextView(context).apply {
        text = contenu
        setTextColor(couleur)
        setLineSpacing(0f, 1.12f)
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        tag = floatArrayOf(taille, margeHaute)
    }
}
