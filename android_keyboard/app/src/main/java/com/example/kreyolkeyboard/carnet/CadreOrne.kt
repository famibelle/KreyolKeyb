package com.example.kreyolkeyboard.carnet

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.LruCache
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import com.example.kreyolkeyboard.KeyFeedback
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Le cadre d'une carte du carnet, et **l'échelle d'ornement** qui le fait
 * grandir avec la rareté.
 *
 * ## Pourquoi une échelle, et pas quatre couleurs
 *
 * Le carnet distinguait déjà ses paliers par la matière de l'illustration :
 * une commune mate, un grain oblique, un halo, une irisation. C'était juste,
 * mais ça se jouait entièrement **dans** le panneau, sur 130 dp de hauteur.
 * Une carte à collectionner se reconnaît d'abord à son cadre, et un cadre
 * identique pour les quatre paliers annulait la moitié du travail.
 *
 * Ici, chaque palier **hérite de tout le précédent** et ajoute des éléments
 * nommés. C'est une liste, pas une impression, et c'est ce qui rend l'écart
 * lisible même quand deux cartes ne sont pas côte à côte :
 *
 * - **Commun** — cadre d'étain, ouverture rectangulaire, **plaque de nom en
 *   bois** en travers du milieu, gemme de coût, agrafe sertie, panneau de
 *   texte, écus. Volontairement nu : sans commune nue, aucun des trois autres
 *   paliers ne se verrait.
 * - **Peu commun** — bronze, **plaque de parchemin**, rivets sertis, filet
 *   clair sur l'ouverture.
 * - **Rare** — argent, **plaque d'argent à filet réglé**, ouverture **en
 *   arche**, rayons en éventail, volutes aux quatre angles, pastilles de type
 *   à filet clair, couronne de griffes autour de la gemme de coût, double
 *   filet.
 * - **Très rare** — or, **plaque d'or aux pans repliés**, clef de voûte
 *   sertie, griffes à l'agrafe, huit volutes, feuilles d'acanthe sur les
 *   flancs, joyaux satellites, semis d'étincelles, et un reflet spéculaire qui
 *   suit l'inclinaison du téléphone.
 *
 * La **plaque de nom** est la marche la plus lourde des quatre, et c'est
 * délibéré : elle est au centre, elle porte le mot, et sa matière se nomme
 * d'un regard. Voir [Support].
 *
 * ## Ce que l'ornement n'a pas le droit de faire
 *
 * Il ne dit rien de neuf. Toute l'information reste celle que
 * [ContenuCarte] portait déjà, et la **teinte du mot** garde ce qui lui
 * appartient — la face intérieure et la gemme de coût. Le métal encadre, il
 * ne recouvre pas. C'est la seule raison pour laquelle deux échelles de
 * couleur peuvent cohabiter sans que la collection cesse d'être variée.
 *
 * ## Le coût, et comment il est payé
 *
 * Une commune demande une trentaine d'ordres de tracé, une très rare près de
 * trois cents. À raison d'un `onDraw` par vignette et de deux colonnes qui
 * défilent, ce serait intenable. Le métal est donc **rendu une fois dans un
 * bitmap mis en cache** ([metal]), partagé par toutes les cartes d'un même
 * palier et d'une même taille : il ne dépend pas du mot. Seuls la face, la
 * gemme, l'illustration et les étincelles se retracent, et ils sont
 * bon marché.
 */
object Ornement {

    /** La carte est dessinée en unités de carte, puis mise à l'échelle. */
    const val LARGEUR = 300f
    const val HAUTEUR = 440f
    const val HAUTEUR_VIGNETTE = 300f

    /**
     * Le rayon des coins, en unités de carte.
     *
     * Il était écrit en clair aux trois endroits qui tracent le bord, et
     * [Booster] en avait un quatrième, en dp, qui ne lui correspondait pas.
     * Un dos et une face qui ne s'arrondissent pas pareil se retournent comme
     * deux objets qui se remplacent, pas comme un carton.
     */
    const val RAYON = 18f

    /**
     * L'épaisseur apparente du carton à plein roulis, en unités de carte.
     * Voir [dessinerTranche] : elle est volontairement plus grande que la
     * vérité.
     */
    const val EPAISSEUR = 5f

    /** Le cœur du carton, que l'impression ne recouvre pas. */
    private const val TRANCHE = 0xFFFBF6EA.toInt()

    /**
     * Le parchemin : la deuxième marche du nom, et le fond du panneau.
     *
     * La plaque d'une peu commune le prend en plein ; le panneau de texte, sur
     * les quatre paliers, en garde une version délavée. L'un porte un mot de
     * vingt et un points, l'autre trois lignes de douze, et la même valeur
     * sous les deux aurait fait du panneau un second titre.
     */
    private const val PARCHEMIN_HAUT = 0xFFFBF1D8.toInt()
    private const val PARCHEMIN_MI = 0xFFF0E1BB.toInt()
    private const val PARCHEMIN_BAS = 0xFFDCC69B.toInt()

    /** Le pli d'une pointe : de l'encre diluée, pas une arête de métal. */
    private const val PARCHEMIN_PLI = 0x66705B2E

    /** La ligne où la face s'arrête sur la tranche. */
    private const val TRANCHE_FIL = 0xFF6E6559.toInt()

    /** Sur quelle largeur le bord fuyant tombe dans l'ombre. */
    private const val LARGEUR_OMBRE = 28f

    /**
     * De combien un roulis de 1 déplace le balayage, en largeurs de carte.
     *
     * Nommé plutôt qu'écrit dans [refletBalaye] parce que [Carton] a besoin de
     * l'**inverser** : pour poser la lumière exactement sous le pouce, il faut
     * savoir quel roulis l'y amène. Les deux formules doivent donc lire le
     * même nombre, sinon le reflet suivrait le doigt de loin.
     */
    const val ETALEMENT = 0.55f

    /**
     * Un palier, un alliage.
     *
     * C'est la convention de tous les jeux de collection, et c'est ce qui se
     * reconnaît à travers la pièce sans lire une étiquette. Les quatre
     * alliages montent en clarté et en chaleur : l'étain est froid et sourd,
     * l'or est chaud et lumineux.
     */
    class Metal(val hi: Int, val mid: Int, val lo: Int, val trait: Int, val joyau: Int)

    private val METAUX = arrayOf(
        Metal(0xFFB9C0C4.toInt(), 0xFF7E878C.toInt(), 0xFF454C50.toInt(), 0xFF2E3437.toInt(), 0xFF9AA6AD.toInt()),
        Metal(0xFFE3B475.toInt(), 0xFFB07C3C.toInt(), 0xFF68441A.toInt(), 0xFF3E2910.toInt(), 0xFF5BB55F.toInt()),
        Metal(0xFFF2F6F9.toInt(), 0xFFB9C4CE.toInt(), 0xFF6E7C88.toInt(), 0xFF3D474F.toInt(), 0xFF3D9BF0.toInt()),
        Metal(0xFFFFF0BC.toInt(), 0xFFE0B74E.toInt(), 0xFF8E6216.toInt(), 0xFF4A3208.toInt(), 0xFFC558E8.toInt())
    )

    fun metal(rarete: Rarete): Metal = METAUX[rarete.ordinal]

    /**
     * Le support du nom : ce sur quoi le mot est écrit, palier par palier.
     *
     * L'échelle d'ornement faisait pousser le cadre, les angles et les
     * joyaux ; elle ne disait rien de la pièce que l'œil lit en premier.
     * Depuis que le nom est au centre, c'est la plus grosse omission de la
     * carte : on pouvait poser une commune et une très rare côte à côte sans
     * qu'il se passe quoi que ce soit là où les deux se regardent.
     *
     * Le nom monte donc lui aussi, et il monte en **matière** plutôt qu'en
     * décor — une planche, une feuille, une plaque d'argent, une plaque d'or.
     * C'est la seule échelle qui se lise sans comparer : personne n'a besoin
     * d'une seconde carte pour savoir que ce qu'il tient est en or.
     *
     * Les deux paliers hauts reprennent le métal du cadre ([METAUX]) ; les
     * deux bas ne le peuvent pas. Une plaque d'étain sur un cadre d'étain
     * n'apprend rien, et surtout le bas d'une échelle doit rester humble pour
     * que le haut se voie : le bois et le parchemin ne sont pas des métaux
     * ratés, ce sont les deux marches qui rendent l'or lisible comme de l'or.
     *
     * L'encre est celle du support et non du palier — elle sert au fil du
     * bois, aux plis des pans et au filet réglé, et un trait clair sur du
     * chêne ou sombre sur de l'or ne se verrait ni l'un ni l'autre.
     */
    class Support(val hi: Int, val mi: Int, val lo: Int, val encre: Int, val fil: Boolean)

    private val SUPPORTS = arrayOf(
        // Chêne clair : la planche d'un établi, pas un meuble de salon.
        Support(0xFFD9C098.toInt(), 0xFFC2A67A.toInt(), 0xFF9A7C52.toInt(), 0x59513A1E, true),
        // Le parchemin, un cran plus haut : une feuille, faite pour l'encre.
        Support(PARCHEMIN_HAUT, PARCHEMIN_MI, PARCHEMIN_BAS, PARCHEMIN_PLI, false),
        // Argent, puis or : le métal du palier, celui-là même que le cadre.
        Support(METAUX[2].hi, METAUX[2].mid, METAUX[2].lo, 0x8C3D474F.toInt(), false),
        Support(METAUX[3].hi, METAUX[3].mid, METAUX[3].lo, 0x8C4A3208.toInt(), false)
    )

    fun support(rarete: Rarete): Support = SUPPORTS[rarete.ordinal]

    /**
     * Le fil du bois : hauteur dans la planche, puis départ et arrivée en
     * largeurs de plaque. Trois traits inégaux — trois traits centrés
     * feraient une grille, et une grille n'est pas du bois.
     */
    private val FILS = arrayOf(
        floatArrayOf(0.30f, 0.06f, 0.72f),
        floatArrayOf(0.52f, 0.22f, 0.94f),
        floatArrayOf(0.74f, 0.10f, 0.63f)
    )

    // ---------------------------------------------------------------- slots

    /**
     * Les emplacements, en unités de carte.
     *
     * Ils sont ici et nulle part ailleurs : le tracé du cadre et la pose du
     * texte doivent lire les mêmes nombres, sinon un bandeau finit décalé
     * d'un pixel sous son libellé et personne ne comprend pourquoi.
     */
    val FENETRE = RectF(32f, 36f, 268f, 208f)
    val FENETRE_VIGNETTE = RectF(26f, 26f, 274f, 212f)
    /**
     * La plaque de nom, **posée en travers du milieu**.
     *
     * Elle était une barre de métal dans le bandeau du haut, à côté de la
     * gemme de coût, et le mot y passait pour une étiquette. C'est l'inverse
     * de ce qu'une carte doit faire : le mot **est** la pièce, tout le reste
     * la décrit. Le porter au centre, sur la seule surface écrite de la carte
     * avec le panneau, le remet au milieu du regard — c'est la disposition de
     * toutes les cartes à collectionner, et elle ne coûte rien puisque le haut
     * n'avait besoin de rien d'autre que l'illustration et la gemme.
     *
     * Trois conséquences, toutes voulues :
     *
     * - **Sa matière monte avec la rareté** : bois, parchemin, argent, or.
     *   C'est l'échelle la plus visible de la carte, et la seule qui se lise
     *   sans en poser une seconde à côté. Voir [Support].
     * - **Elle mord sur l'ouverture** de 24 unités. C'est ce recouvrement qui
     *   la pose *sur* la carte au lieu de la ranger dans une bande de plus, et
     *   c'est lui qui vaut l'ombre portée de [plaque].
     * - **Elle est plus large que l'ouverture**, de six unités de chaque côté.
     *   À deux unités près de la même largeur, ses bords tombaient juste en
     *   dedans de ceux de la fenêtre et la carte devenait ambiguë : on ne
     *   voyait plus une plaque posée devant une ouverture, mais une ouverture
     *   dont le bas manquait. Un ruban ne passe devant que s'il dépasse.
     * - **Ses pointes débordent du cadre**, à 9,5 et 290,5. Elles le faisaient
     *   déjà en haut, mais d'un seul côté — l'autre disparaissait sous la
     *   gemme de coût, et un ruban qui ne dépasse qu'à droite se lit comme un
     *   défaut de calage. Au centre, les deux pans sortent de la même
     *   longueur, et le débord redevient ce qu'il est sur un vrai ruban.
     */
    val PLAQUE = RectF(28f, 184f, 272f, 228f)
    /**
     * La gemme de coût, à 11 unités des deux bords de l'angle.
     *
     * Elle y était centrée sur la plaque de nom, qui occupait le bandeau du
     * haut ; la plaque est partie au milieu, le calage reste. Il ne dépendait
     * en réalité que de l'angle, et c'est le seul repère qui ne bouge pas
     * quand la marge du cadre passe de 12 à 18 selon le palier.
     */
    val GEMME = RectF(11f, 11f, 65f, 65f)
    /**
     * La gemme sertie sur la clef de voûte, au sommet de l'arche : elle
     * tenait la plaque sous le nom avant de monter prendre la place du petit
     * joyau de la clef.
     *
     * Elle ne compte rien et n'ouvre rien — c'est une pièce d'orfèvrerie, au
     * même titre que les rivets des flancs ou les volutes des angles, et elle
     * a le droit d'exister pour la même raison : le carnet n'a jamais dit que
     * l'ornement devait porter une donnée, seulement qu'il ne devait pas en
     * **inventer** une. Elle prend donc la teinte du mot, comme la face et la
     * gemme de coût, plutôt qu'une couleur à elle : deux pierres de la même
     * eau aux deux bouts de l'illustration, et rien de neuf à apprendre.
     *
     * Ovale et non ronde, pour ne pas se lire comme une petite gemme de coût,
     * et centrée sur l'axe de la carte à hauteur de la clef de voûte
     * (`FENETRE.top + 2`), dont le métal est dessiné pour l'accueillir.
     */
    val GEMME_CENTRE = RectF(141f, 27f, 159f, 53f)

    /**
     * L'épaisseur du chaton de l'agrafe, contre quatre pour la gemme de coût.
     * Le même creux sur une pierre de treize unités de haut ne lui aurait plus
     * laissé qu'un noyau : une sertissure se mesure à sa pierre.
     */
    const val CREUX_AGRAFE = 3f

    /** Le disque du médaillon, l'émail qu'il tient, et le corps de sa légende. */
    private const val R_MEDAILLON = 23f
    private const val R_EMAIL = 13f
    private const val TAILLE_LEGENDE = 5.6f
    private const val INTERLETTRAGE = 0.06f
    /**
     * Ce qu'est le mot : un **onglet** posé sur le bord haut du panneau, sur
     * l'axe de la carte.
     *
     * Une seule bande portait « Substantif · le jeu », deux informations
     * qui n'ont ni la même source ni la même durée de vie — la nature vient du
     * mot, la provenance de la partie qui l'a donné — séparées par un point
     * médian qui ne disait pas laquelle est laquelle. Deux capsules le disent
     * sans ponctuation, et elles ont fini par se séparer aussi dans l'espace :
     * la nature est une propriété du mot, elle reste sous lui ; la provenance
     * est une mention d'inventaire, elle est descendue entre les écus, avec le
     * reste de ce que le carnet sait de la partie. Voir [PROVENANCE].
     *
     * Centrée, donc, et non plus calée à gauche d'une rangée : elle prolonge
     * l'aplomb de la clef de voûte et de la plaque. Ses 92 unités de texte
     * tiennent « Kreyolopedia », le plus large des libellés de source une fois
     * les trois genres composés abrégés.
     *
     * Elle mord de 10 unités sur le panneau, comme le médaillon mord sur son
     * bord bas : le panneau est tenu par deux pièces, l'une en haut qui dit ce
     * qu'est le mot, l'autre en bas qui dit d'où il vient. Elle flottait
     * auparavant à 26 unités de la plaque et 6 du panneau, dans la bande que
     * la gemme avait quittée ; le panneau a remonté d'autant, et gagné 21
     * unités de texte.
     */
    val NATURE = RectF(96f, 244f, 204f, 272f)
    /**
     * Où le mot a été gagné : un **médaillon** dans le bas de carte, entre les
     * écus.
     *
     * C'était une capsule, « gagné à Mokwaré », et la rangée de la nature
     * ne pouvait pas la tenir : les deux libellés demandaient ensemble 192
     * unités de texte là où 184 étaient disponibles. Le bas de carte avait 144
     * unités inoccupées entre les écus, et c'est là que la mention est allée.
     *
     * Elle y est devenue un sceau : le jeu s'y reconnaît à son **emblème** sur
     * un émail de sa couleur, et le libellé passe en légende sur le pourtour,
     * « GAGNÉ À » au-dessus et le nom du jeu au-dessous. Le disque est du
     * métal de la carte, donc du bitmap partagé ; l'émail, l'emblème et la
     * légende dépendent du jeu et se tracent en direct : voir
     * [dessinerMedaillon].
     *
     * Centré sur les écus (392) et non sur la bande qu'ils laissent libre :
     * il mord donc sur le bas du panneau exactement comme eux. Le rectangle
     * est le carré qui circonscrit le disque, pour le semis d'étincelles et
     * les arêtes.
     */
    val PROVENANCE = RectF(127f, 369f, 173f, 415f)

    /** Le texte de la nature, en retrait des bouts ronds de la capsule. */
    val NATURE_TEXTE = RectF(104f, 244f, 196f, 272f)
    val PANNEAU = RectF(38f, 262f, 262f, 384f)
    /**
     * Le texte, en retrait du panneau : le double filet passe entre les deux.
     * Il commence sous l'onglet de nature (bas à 272), à 5 unités de lui ; sur
     * le dos de révision, où l'ardoise réutilise ce rectangle, l'onglet n'existe
     * pas et le texte s'y centre simplement.
     */
    val PANNEAU_TEXTE = RectF(48f, 277f, 252f, 378f)
    /**
     * Les écus, alignés sur les bords de la plaque (28 et 272) : à 26 et 274,
     * deux unités de décalage se lisaient comme une erreur plutôt que comme un
     * choix.
     */
    val ECU_G = RectF(28f, 375f, 80f, 410f)
    val ECU_D = RectF(220f, 375f, 272f, 410f)
    /** Le chiffre d'un écu : sous le libellé gravé, pas par-dessus. */
    val ECU_G_TEXTE = RectF(28f, 382f, 80f, 408f)
    val ECU_D_TEXTE = RectF(220f, 382f, 272f, 408f)
    /**
     * La ligne de série est passée **dans la marge**, où ce genre de mention
     * vit sur une carte imprimée : numéro, date, rang — de
     * l'administratif, qui n'a pas à disputer sa place au contenu.
     *
     * Sur le plateau elle ne manquait pas seulement d'air, elle **traversait
     * les volutes**. À `bord = 18`, les spirales du bas sont centrées en
     * (23, 417) et (277, 417) sur 22 unités, et l'or en pose deux secondes en
     * x = 39 et x = 261 : quatre spirales sous un texte qui allait de 30 à
     * 270, et l'exposant du rang illisible à droite.
     *
     * La bande retenue est la même sur les quatre paliers, parce qu'elle est
     * ancrée au bord bas et non à la marge, qui varie de 12 à 18 :
     *
     * - **428 en haut.** Une volute a perdu 62 % de son rayon quand elle passe
     *   à l'aplomb de son centre (`r = taille · exp(-1,75 t)`, et l'angle bas
     *   tombe à t ≈ 0,56) : aucune ne descend plus bas que ~427, or et rare
     *   confondus, et les secondes spirales de l'or s'arrêtent vers 420.
     * - **437,5 en bas.** Le filet de contour extérieur commence là —
     *   `RectF(1.2, …, haut - 1.2)` tracé en 2,5 d'épaisseur.
     *
     * Neuf unités et demie, donc, et le corps descend à 7,5 : c'est celui des
     * libellés gravés dans les écus, pas une taille inventée pour l'occasion.
     * Serré, et c'est le prix — en échange la ligne se lit d'un bloc.
     *
     * Le texte reste en `trait`, le ton sombre du métal, et non en `hi` : au
     * bas du bandeau le dégradé est entre `lo` et `mid`, où le sombre tient
     * 4,4:1 sur l'or et 4,0:1 sur l'argent, contre 2,4:1 et 2,2:1 au clair.
     */
    val SERIE_G = RectF(22f, 428f, 176f, 437.5f)
    val SERIE_D = RectF(176f, 428f, 278f, 437.5f)
    /**
     * L'énoncé d'une question, sur le dos de révision.
     *
     * Le dos pose son texte dans les emplacements de la face — c'est ce qui
     * fait que l'ardoise apparaît exactement là où le sens attend le joueur de
     * l'autre côté. Mais [FENETRE] et [PLAQUE] se **recouvrent** depuis que le
     * plaque est au milieu, et ce recouvrement n'est lisible que sur la
     * face, où la plaque est opaque. Sur le dos, deux textes s'y
     * marcheraient dessus : l'énoncé s'arrête donc où la consigne commence.
     */
    val ENONCE_DOS = RectF(38f, 42f, 262f, 178f)
    val NOM_VIGNETTE = RectF(20f, 218f, 280f, 248f)
    val GLOSE_VIGNETTE = RectF(20f, 249f, 280f, 269f)
    val BOITE_VIGNETTE = RectF(30f, 277f, 270f, 281f)

    // ----------------------------------------------------------- primitives

    /**
     * Une volute **gravée** : un sillon creusé dans la matière, pas un trait
     * posé dessus.
     *
     * La lumière du cadre vient d'en haut à gauche (son dégradé va de `hi` à
     * `lo`). Un creux y montre donc sa paroi haute dans l'ombre et sa lèvre
     * basse dans la lumière ; l'inverse se lit comme un relief. Le fond est
     * translucide pour que la matière, métal ou face teintée, reste visible
     * au fond du sillon. Chaque passe va dans sa propre couche : des segments
     * translucides aux bouts ronds se superposeraient en chapelet aux jointures.
     */
    private fun volute(
        c: Canvas, p: Paint, x: Float, y: Float, taille: Float,
        sx: Float, sy: Float, tours: Float, epais: Float, m: Metal
    ) {
        val marge = epais + 2f
        val cadre = RectF(x - taille - marge, y - taille - marge, x + taille + marge, y + taille + marge)
        passe(c, p, cadre, 0x9E, Color.WHITE, x + 0.45f, y + 0.85f, taille, sx, sy, tours, epais)
        passe(c, p, cadre, 0x6B, Color.BLACK, x, y, taille, sx, sy, tours, epais * 0.8f)
        passe(c, p, cadre, 0x5C, m.trait, x - 0.25f, y - 0.45f, taille, sx, sy, tours, epais * 0.35f)
    }

    /** Une passe de [volute], opaque dans une couche rendue à [alpha]. */
    private fun passe(
        c: Canvas, p: Paint, cadre: RectF, alpha: Int, couleur: Int,
        x: Float, y: Float, taille: Float, sx: Float, sy: Float, tours: Float, epais: Float
    ) {
        c.saveLayerAlpha(cadre, alpha)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = couleur
        p.shader = null
        var px = taille
        var py = 0f
        val n = 40
        for (i in 1..n) {
            val t = i / n.toFloat()
            val ang = t * tours * 2f * Math.PI.toFloat()
            val r = taille * exp(-1.75f * t)
            val nx = cos(ang) * r
            val ny = sin(ang) * r
            p.strokeWidth = epais * (1f - t * 0.8f)
            c.drawLine(x + px * sx, y + py * sy, x + nx * sx, y + ny * sy, p)
            px = nx
            py = ny
        }
        c.restore()
    }

    /**
     * Une feuille d'acanthe **gravée** dans la bande de métal, comme les
     * volutes : lèvre claire en bas à droite, creux sombre. Posée en aplat
     * pâle à cheval sur le bord du cadre, elle se lisait comme une tache.
     */
    private fun feuille(c: Canvas, p: Paint, x: Float, y: Float, l: Float, angle: Float) {
        val chemin = Path()
        chemin.moveTo(0f, 0f)
        chemin.quadTo(l * 0.45f, -l * 0.38f, l, 0f)
        chemin.quadTo(l * 0.45f, l * 0.38f, 0f, 0f)
        p.style = Paint.Style.FILL
        p.shader = null
        for ((dx, dy, couleur) in arrayOf(
            Triple(0.45f, 0.85f, 0x8CFFFFFF.toInt()),
            Triple(0f, 0f, 0x4D000000)
        )) {
            c.save()
            c.translate(x + dx, y + dy)
            c.rotate(angle)
            p.color = couleur
            c.drawPath(chemin, p)
            c.restore()
        }
    }

    /** Un joyau serti : facette claire en haut à gauche, creux sombre en bas. */
    private fun joyau(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, couleur: Int, facettes: Int) {
        p.style = Paint.Style.FILL
        // Opaque d'abord : le liseré du joyau précédent laissait 55 % d'alpha
        // au pinceau, et sept griffes sur huit montraient l'anneau au travers.
        p.color = Color.BLACK
        p.shader = RadialGradient(
            cx - r * 0.35f, cy - r * 0.4f, r * 1.35f,
            intArrayOf(0xF2FFFFFF.toInt(), couleur, 0x8C000000.toInt()),
            floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP
        )
        if (facettes >= 3) {
            val chemin = Path()
            for (i in 0 until facettes) {
                val a = -Math.PI.toFloat() / 2f + i.toFloat() / facettes * 2f * Math.PI.toFloat()
                val px = cx + cos(a) * r
                val py = cy + sin(a) * r
                if (i == 0) chemin.moveTo(px, py) else chemin.lineTo(px, py)
            }
            chemin.close()
            c.drawPath(chemin, p)
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = r * 0.14f
            p.color = 0x8CFFFFFF.toInt()
            c.drawPath(chemin, p)
        } else {
            c.drawCircle(cx, cy, r, p)
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = r * 0.14f
            p.color = 0x8CFFFFFF.toInt()
            c.drawCircle(cx, cy, r, p)
        }
        p.shader = null
        p.style = Paint.Style.FILL
    }

    /** Une étincelle à quatre branches : deux courbes en losange étiré. */
    private fun etincelle(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, alpha: Int) {
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = Color.WHITE
        p.alpha = alpha
        val chemin = Path()
        chemin.moveTo(cx, cy - r)
        chemin.quadTo(cx + r * 0.16f, cy - r * 0.16f, cx + r, cy)
        chemin.quadTo(cx + r * 0.16f, cy + r * 0.16f, cx, cy + r)
        chemin.quadTo(cx - r * 0.16f, cy + r * 0.16f, cx - r, cy)
        chemin.quadTo(cx - r * 0.16f, cy - r * 0.16f, cx, cy - r)
        c.drawPath(chemin, p)
        p.alpha = 255
    }

    /**
     * La plaque de nom, dans la matière de son palier.
     *
     * Elle prend tout ce que [Support] lui donne — le bois, le parchemin,
     * l'argent, l'or — et n'ajoute que ce qui dépend de la forme : l'ombre
     * qu'elle porte sur l'illustration qu'elle recouvre, le fil du bois quand
     * c'en est, les plis de ses pans, et le filet réglé des deux paliers
     * hauts. Rien ici ne décide de la matière, et c'est voulu : l'échelle est
     * une table, pas une suite de `if`.
     *
     * Les pointes n'arrivent qu'à *Très rare*. C'est le genre de détail qui ne
     * se remarque jamais seul et qui fait toute la différence en série : quatre
     * cartes alignées, deux à bords droits et deux à pans repliés, et l'échelle
     * se lit sans lire un mot.
     */
    private fun plaque(c: Canvas, p: Paint, r: RectF, rarete: Rarete) {
        val palier = rarete.ordinal
        val s = support(rarete)
        val m = metal(rarete)
        val pointes = palier >= 3
        // Le pan s'arrête avant le filet extérieur : à 0,55 hauteur, celui
        // d'une plaque large venait buter contre le bord de la carte. La règle
        // est « neuf unités du bord », pas un rapport accordé à une largeur —
        // elle survit donc à la prochaine plaque qu'on élargira.
        val q = min(r.height() * 0.55f, min(r.left, LARGEUR - r.right) - 9f)
        val chemin = Path()
        if (pointes) {
            chemin.moveTo(r.left - q, r.centerY())
            chemin.lineTo(r.left, r.top)
            chemin.lineTo(r.right, r.top)
            chemin.lineTo(r.right + q, r.centerY())
            chemin.lineTo(r.right, r.bottom)
            chemin.lineTo(r.left, r.bottom)
            chemin.close()
        } else {
            chemin.addRoundRect(r, 3f, 3f, Path.Direction.CW)
        }
        // L'ombre portée. La plaque mord sur l'ouverture de vingt-quatre
        // unités, et sans elle ce recouvrement se lirait comme une découpe
        // dans l'illustration plutôt que comme une plaque posée dessus.
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = 0x38000000
        c.save()
        c.translate(1.5f, 2.5f)
        c.drawPath(chemin, p)
        c.restore()

        // Opaque avant le dégradé : celui-ci est multiplié par l'alpha du
        // pinceau, que l'ombre vient de descendre à 22 %.
        p.color = Color.BLACK
        p.shader = LinearGradient(
            0f, r.top, 0f, r.bottom,
            intArrayOf(s.hi, s.mi, s.lo), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawPath(chemin, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f
        p.color = m.trait
        c.drawPath(chemin, p)

        // Le fil du bois, dans le sens de la planche. Trois traits suffisent,
        // et ils ne sont pas décoratifs : sans eux la matière se lit comme du
        // cuir ou de la terre cuite, et l'échelle commence sur rien.
        if (s.fil) {
            p.strokeWidth = 1f
            p.color = s.encre
            for (fil in FILS) {
                val y = r.top + r.height() * fil[0]
                c.drawLine(r.left + r.width() * fil[1], y, r.left + r.width() * fil[2], y, p)
            }
        }

        // Les plis : deux traits du sommet vers les coins qu'il a quittés,
        // pour lire une pointe qui replie la plaque plutôt qu'une flèche.
        if (pointes) {
            p.strokeWidth = 1f
            p.color = s.encre
            c.drawLine(r.left - q, r.centerY(), r.left, r.top, p)
            c.drawLine(r.left - q, r.centerY(), r.left, r.bottom, p)
            c.drawLine(r.right + q, r.centerY(), r.right, r.top, p)
            c.drawLine(r.right + q, r.centerY(), r.right, r.bottom, p)
        }

        // Le filet réglé, à partir de *Rare*. Depuis que le mot est au centre,
        // la plaque est la première chose que l'œil lit : la laisser
        // rigoureusement identique sur les quatre paliers revenait à retirer
        // l'échelle d'ornement de l'endroit le plus regardé de la carte. Il ne
        // court que sur le corps, jamais sur les pans — une règle tracée
        // suit le bord de la feuille, pas ses plis.
        if (palier >= 2) {
            p.strokeWidth = 0.8f
            p.color = s.encre
            c.drawRoundRect(
                RectF(r.left + 3.5f, r.top + 3.5f, r.right - 3.5f, r.bottom - 3.5f), 2f, 2f, p
            )
        }
    }

    /**
     * Une pastille : la capsule des deux étiquettes de la ligne de type.
     *
     * Elle n'a pas de pointes, et ce n'est pas un oubli : deux pastilles
     * séparées de huit unités se toucheraient par leurs pointes dès que le
     * palier les fait pousser. L'échelle passe donc ici par le filet clair,
     * celui que l'ouverture reçoit déjà à *Peu commun*.
     */
    private fun pastille(c: Canvas, p: Paint, r: RectF, filet: Boolean, m: Metal) {
        val rayon = r.height() / 2f
        p.style = Paint.Style.FILL
        // Opaque d'abord : les griffes de la sertissure précédente ont laissé
        // leur liseré à 55 % dans le pinceau.
        p.color = Color.BLACK
        p.shader = LinearGradient(
            0f, r.top, 0f, r.bottom,
            intArrayOf(m.hi, m.mid, m.lo), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(r, rayon, rayon, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f
        p.color = m.trait
        c.drawRoundRect(r, rayon, rayon, p)
        if (filet) {
            p.strokeWidth = 0.9f
            p.color = 0x8CFFFFFF.toInt()
            val dedans = RectF(r.left + 2.5f, r.top + 2.5f, r.right - 2.5f, r.bottom - 2.5f)
            c.drawRoundRect(dedans, rayon - 2.5f, rayon - 2.5f, p)
        }
    }

    /**
     * La sertissure d'une pierre : le chaton de métal qui la tient.
     *
     * Elle est tracée avec le cadre et non avec la pierre, parce qu'elle ne
     * dépend pas du mot — c'est ce qui laisse trente cartes d'un même palier
     * partager un seul bitmap. La pierre, elle, est peinte par-dessus en
     * direct, à la teinte du mot : voir [dessinerGemme].
     *
     * L'ovale passe par une mise à l'échelle autour du centre plutôt que par
     * un `drawOval` : le même code tient alors les deux pierres — la ronde du
     * coût et l'ovale de l'agrafe — et les griffes tombent sur le contour réel
     * au lieu d'un cercle circonscrit.
     */
    private fun sertissure(c: Canvas, p: Paint, r: RectF, m: Metal, griffes: Int) {
        val cx = r.centerX()
        val cy = r.centerY()
        val ry = r.height() / 2f
        c.save()
        c.scale(r.width() / r.height(), 1f, cx, cy)
        p.style = Paint.Style.FILL
        // Opaque d'abord, comme [joyau] : l'encre de la plaque ou le liseré
        // d'une griffe laissent un alpha partiel au pinceau, et un dégradé en
        // est multiplié.
        p.color = Color.BLACK
        p.shader = LinearGradient(cx, cy - ry, cx, cy + ry, m.hi, m.lo, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, ry, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.4f
        p.color = m.trait
        c.drawCircle(cx, cy, ry, p)
        // Une couronne de griffes : c'est ce qui fait « serti » plutôt
        // que « posé ».
        for (i in 0 until griffes) {
            val a = i / griffes.toFloat() * 2f * Math.PI.toFloat()
            joyau(c, p, cx + cos(a) * ry, cy + sin(a) * ry, ry * 0.09f, m.hi, 0)
        }
        c.restore()
    }

    /** L'écu d'une statistique : un blason à base arrondie. */
    private fun ecu(c: Canvas, p: Paint, r: RectF, m: Metal, filet: Boolean) {
        val chemin = Path()
        chemin.moveTo(r.left, r.top)
        chemin.lineTo(r.right, r.top)
        chemin.lineTo(r.right, r.top + r.height() * 0.5f)
        chemin.quadTo(r.centerX(), r.bottom + r.height() * 0.16f, r.left, r.top + r.height() * 0.5f)
        chemin.close()
        p.style = Paint.Style.FILL
        // Un dégradé est multiplié par l'alpha du pinceau : sans ce retour à
        // l'opaque, l'écu héritait du filet tracé juste avant et devenait
        // transparent (16 % à gauche, 50 % à droite).
        p.color = Color.BLACK
        p.shader = LinearGradient(
            0f, r.top, 0f, r.bottom,
            intArrayOf(m.hi, m.mid, m.lo), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        c.drawPath(chemin, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.4f
        p.color = m.trait
        c.drawPath(chemin, p)
        if (filet) {
            p.strokeWidth = 0.9f
            p.color = 0x80FFFFFF.toInt()
            c.drawPath(chemin, p)
        }
    }

    /**
     * L'ouverture de l'illustration : rectangle arrondi, ou plein cintre.
     *
     * L'arche est l'ajout le plus visible de *Rare*, et c'est voulu : c'est
     * la seule modification qui change la **silhouette** de la carte plutôt
     * que d'ajouter un détail à sa surface.
     */
    fun cheminFenetre(r: RectF, arche: Boolean): Path {
        val chemin = Path()
        if (!arche) {
            chemin.addRoundRect(r, 10f, 10f, Path.Direction.CW)
            return chemin
        }
        val fleche = r.width() * 0.30f
        val rayon = 8f
        chemin.moveTo(r.left, r.bottom - rayon)
        chemin.lineTo(r.left, r.top + fleche)
        chemin.arcTo(RectF(r.left, r.top, r.right, r.top + fleche * 2f), 180f, 180f)
        chemin.lineTo(r.right, r.bottom - rayon)
        chemin.arcTo(RectF(r.right - rayon * 2f, r.bottom - rayon * 2f, r.right, r.bottom), 0f, 90f)
        chemin.lineTo(r.left + rayon, r.bottom)
        chemin.arcTo(RectF(r.left, r.bottom - rayon * 2f, r.left + rayon * 2f, r.bottom), 90f, 90f)
        chemin.close()
        return chemin
    }

    // ------------------------------------------------------------- le métal

    /**
     * Le cache des cadres.
     *
     * La clef ne contient **pas le mot** : c'est tout l'intérêt. Le métal ne
     * dépend que du palier et de la taille, donc trente cartes très rares
     * partagent un seul bitmap. Deux mégaoctets suffisent largement pour les
     * huit combinaisons que l'application demande réellement.
     */
    private val CACHE = object : LruCache<String, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(cle: String, valeur: Bitmap): Int = valeur.byteCount
    }

    fun metal(rarete: Rarete, largeurPx: Int, vignette: Boolean): Bitmap? =
        couche("m", rarete, largeurPx, vignette) { c -> dessinerMetal(c, rarete, vignette) }

    /**
     * Les rayons en éventail, à partir de *Rare*.
     *
     * Ils passent **sous** l'illustration, donc ils ne peuvent pas voyager
     * dans le bitmap du métal — mais ils ne dépendent pas davantage du mot,
     * et vingt-quatre secteurs retracés à chaque trame pour chacune des
     * trente vignettes d'une grille qui défile étaient de loin le premier
     * poste de dépense. D'où une seconde couche, mise en cache de la même
     * façon.
     */
    fun rayons(rarete: Rarete, largeurPx: Int, vignette: Boolean): Bitmap? {
        if (rarete.ordinal < 2) return null
        return couche("r", rarete, largeurPx, vignette) { c -> dessinerRayons(c, rarete, vignette) }
    }

    private fun couche(
        prefixe: String,
        rarete: Rarete,
        largeurPx: Int,
        vignette: Boolean,
        tracer: (Canvas) -> Unit
    ): Bitmap? {
        if (largeurPx <= 0) return null
        val cle = "${prefixe}_${rarete.ordinal}_${largeurPx}_$vignette"
        CACHE.get(cle)?.let { if (!it.isRecycled) return it }

        val u = largeurPx / LARGEUR
        val hautUnites = if (vignette) HAUTEUR_VIGNETTE else HAUTEUR
        val hauteurPx = (hautUnites * u).toInt()
        if (hauteurPx <= 0) return null

        val bitmap = Bitmap.createBitmap(largeurPx, hauteurPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.scale(u, u)
        tracer(c)
        CACHE.put(cle, bitmap)
        return bitmap
    }

    /**
     * Tout ce qui ne dépend que du palier : le plateau, la sertissure de
     * l'ouverture, les volutes, la plaque, les pastilles, les écus.
     *
     * Le centre reste transparent — la face teintée et l'illustration sont
     * peintes dessous, en direct, par [CarteOrnee].
     */
    private fun dessinerMetal(c: Canvas, rarete: Rarete, vignette: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val palier = rarete.ordinal
        val m = metal(rarete)
        val haut = if (vignette) HAUTEUR_VIGNETTE else HAUTEUR
        val bord = 12f + palier * 2f
        val fenetre = if (vignette) FENETRE_VIGNETTE else FENETRE
        val arche = palier >= 2

        // 1. Le plateau : le métal du palier, sur tout le pourtour.
        val plateau = Path()
        plateau.addRoundRect(RectF(0f, 0f, LARGEUR, haut), 18f, 18f, Path.Direction.CW)
        val creux = Path()
        creux.addRoundRect(RectF(bord, bord, LARGEUR - bord, haut - bord), 11f, 11f, Path.Direction.CW)
        plateau.op(creux, Path.Op.DIFFERENCE)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            0f, 0f, LARGEUR * 0.6f, haut,
            intArrayOf(m.hi, m.mid, m.lo, m.mid),
            floatArrayOf(0f, 0.28f, 0.62f, 1f), Shader.TileMode.CLAMP
        )
        c.drawPath(plateau, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.5f
        p.color = m.trait
        c.drawRoundRect(RectF(1.2f, 1.2f, LARGEUR - 1.2f, haut - 1.2f), RAYON, RAYON, p)

        // 2. La sertissure de l'ouverture : un jonc, doublé d'un filet clair
        //    à partir de Peu commun.
        val ouverture = cheminFenetre(fenetre, arche)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f + palier
        p.color = m.mid
        c.drawPath(ouverture, p)
        p.strokeWidth = 1.4f
        p.color = m.trait
        c.drawPath(ouverture, p)
        if (palier >= 1) {
            c.save()
            c.translate(0f, -1.5f)
            p.strokeWidth = 1.2f
            p.color = 0x8CFFFFFF.toInt()
            c.drawPath(ouverture, p)
            c.restore()
        }

        // 3. La clef de voûte, au sommet de l'arche (Très rare).
        if (palier >= 3) {
            val kx = fenetre.centerX()
            val ky = fenetre.top + 2f
            val clef = Path()
            clef.moveTo(kx - 18f, ky + 16f)
            clef.lineTo(kx - 11f, ky - 11f)
            clef.lineTo(kx + 11f, ky - 11f)
            clef.lineTo(kx + 18f, ky + 16f)
            clef.close()
            p.style = Paint.Style.FILL
            p.color = Color.BLACK
            p.shader = LinearGradient(0f, ky - 11f, 0f, ky + 16f, m.hi, m.lo, Shader.TileMode.CLAMP)
            c.drawPath(clef, p)
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.2f
            p.color = m.trait
            c.drawPath(clef, p)
            // La pierre de la clef est [GEMME_CENTRE], peinte par-dessus ; la
            // vignette, qui n'a pas de gemme teintée, garde son petit joyau.
            if (vignette) joyau(c, p, kx, ky + 2f, 5.5f, m.joyau, 6)
        }

        // 4. Les volutes d'angle : aucune, puis deux, quatre, huit.
        //
        // Sur la grande carte, les angles du haut appartiennent à la gemme et
        // à la pointe de la plaque, peintes par-dessus : la volute n'y montrait
        // que des bouts de spirale. Seuls les angles du bas en reçoivent, plus
        // petites et logées dans le coin, sous la courbe des écus, au-dessus
        // de la ligne de série (428). Le nombre visible par palier est inchangé.
        val volutes = intArrayOf(0, 2, 4, 8)[palier]
        if (volutes > 0) {
            val coins = if (vignette) arrayOf(
                floatArrayOf(bord + 5f, bord + 5f, 1f, 1f),
                floatArrayOf(LARGEUR - bord - 5f, bord + 5f, -1f, 1f),
                floatArrayOf(bord + 5f, haut - bord - 5f, 1f, -1f),
                floatArrayOf(LARGEUR - bord - 5f, haut - bord - 5f, -1f, -1f)
            ) else arrayOf(
                floatArrayOf(0f, 0f, 0f, 0f),
                floatArrayOf(0f, 0f, 0f, 0f),
                floatArrayOf(bord + 5f, haut - bord - 3f, 1f, -1f),
                floatArrayOf(LARGEUR - bord - 5f, haut - bord - 3f, -1f, -1f)
            )
            val taille = (13f + palier * 3f) * (if (vignette) 1f else 0.65f)
            for (i in 0 until volutes) {
                val coin = coins[i % 4]
                if (coin[2] == 0f) continue
                val x = coin[0]
                val y = coin[1]
                val sx = coin[2]
                val sy = coin[3]
                // Au-delà de quatre, la seconde volute d'un angle se pose en
                // retrait sur le flanc : deux spirales concentriques feraient
                // une tache, deux spirales décalées font une frise.
                val seconde = i >= 4
                val dx = if (seconde) sx * 16f else 0f
                val dy = if (seconde) sy * 3f else 0f
                val t = if (seconde) taille * 0.6f else taille
                volute(c, p, x + dx, y + dy, t, sx, sy, 1.35f, if (vignette) 3.2f else 2.6f, m)
            }
        }

        // 5. Les feuilles d'acanthe sur les flancs (Très rare).
        if (palier >= 3) {
            val depart = fenetre.top + 30f
            val pas = (haut - depart - 80f) / 5f
            for (i in 0 until 5) {
                val y = depart + i * pas
                feuille(c, p, bord / 2f - 3f, y, 9f, -28f)
                feuille(c, p, LARGEUR - bord / 2f + 3f, y, 9f, 208f)
            }
        }

        // 6. Les rivets, au milieu des flancs — pas dans les angles, les
        //    volutes y sont déjà.
        if (palier >= 1) {
            for (y in floatArrayOf(haut * 0.42f, haut * 0.70f)) {
                joyau(c, p, bord + 6f, y, 3.2f, m.hi, 0)
                joyau(c, p, LARGEUR - bord - 6f, y, 3.2f, m.hi, 0)
            }
        }

        if (vignette) return

        // 7. La plaque de nom, en travers du milieu.
        plaque(c, p, PLAQUE, rarete)

        // 8. Les deux sertissures : la gemme de coût sur l'angle de
        //    l'ouverture, et la gemme de la clef de voûte.
        sertissure(c, p, GEMME, m, if (palier >= 2) 8 else 0)
        sertissure(c, p, GEMME_CENTRE, m, if (palier >= 3) 6 else 0)

        // 9. Le panneau de texte.
        p.style = Paint.Style.FILL
        // Opaque d'abord : les griffes de la sertissure précédente ont laissé
        // leur liseré à 55 % dans le pinceau.
        p.color = Color.BLACK
        p.shader = LinearGradient(
            0f, PANNEAU.top, 0f, PANNEAU.bottom,
            0xF7FFFCF2.toInt(), 0xF7F0EADA.toInt(), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(PANNEAU, 9f, 9f, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.4f
        p.color = m.mid
        c.drawRoundRect(PANNEAU, 9f, 9f, p)
        p.strokeWidth = 1f
        p.color = m.trait
        c.drawRoundRect(PANNEAU, 9f, 9f, p)
        if (palier >= 2) {
            // Le double filet de l'encadreur, déjà utilisé par les cartes
            // rares du carnet : de la profondeur sans une couleur de plus.
            p.strokeWidth = 0.8f
            p.color = 0x29000000
            c.drawRoundRect(
                RectF(PANNEAU.left + 4f, PANNEAU.top + 4f, PANNEAU.right - 4f, PANNEAU.bottom - 4f),
                5f, 5f, p
            )
        }

        // 10. L'onglet de nature, par-dessus le bord haut du panneau qu'il mord.
        pastille(c, p, NATURE, palier >= 2, m)

        // 11. Les deux écus, et leur libellé gravé.
        //
        // Sans libellé, deux chiffres nus dans deux blasons ne veulent rien
        // dire — et comme ils ne dépendent pas du mot, ils entrent dans le
        // bitmap mis en cache au même titre que le métal.
        ecu(c, p, ECU_G, m, palier >= 3)
        ecu(c, p, ECU_D, m, palier >= 3)
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = m.trait
        p.alpha = 190
        p.textAlign = Paint.Align.CENTER
        p.textSize = 7.5f
        p.typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD
        )
        c.drawText("VUES", ECU_G.centerX(), ECU_G.top + 9f, p)
        c.drawText("NIVEAU", ECU_D.centerX(), ECU_D.top + 9f, p)
        p.alpha = 255

        // 12. Le disque du médaillon de provenance, par-dessus le panneau
        //     qu'il mord, entre les deux écus.
        disqueMedaillon(c, p, palier, m)
    }

    /**
     * Le disque du médaillon : du métal, plus clair en haut qu'en bas, avec
     * la lunette sombre qui tient l'émail.
     *
     * Le dégradé s'arrête à `mid` et n'atteint pas `lo` : la légende y est
     * gravée en `trait`, et sur le ton le plus sombre du métal elle aurait
     * disparu au bas du disque.
     */
    private fun disqueMedaillon(c: Canvas, p: Paint, palier: Int, m: Metal) {
        val cx = PROVENANCE.centerX()
        val cy = PROVENANCE.centerY()
        p.style = Paint.Style.FILL
        p.color = Color.BLACK
        p.shader = LinearGradient(0f, cy - R_MEDAILLON, 0f, cy + R_MEDAILLON, m.hi, m.mid, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, R_MEDAILLON, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.4f
        p.color = m.trait
        c.drawCircle(cx, cy, R_MEDAILLON, p)
        // La lunette de l'émail : un anneau plein, plus épais que le contour.
        p.strokeWidth = 1.6f
        c.drawCircle(cx, cy, R_EMAIL + 0.8f, p)
        if (palier >= 2) {
            p.strokeWidth = 0.9f
            p.color = 0x8CFFFFFF.toInt()
            c.drawCircle(cx, cy, R_MEDAILLON - 1.7f, p)
        }
        if (palier >= 3) {
            // Le grènetis d'une pièce : des perles sur le bord.
            for (i in 0 until 24) {
                val a = i / 24f * 2f * Math.PI.toFloat()
                joyau(c, p, cx + cos(a) * (R_MEDAILLON - 0.6f), cy + sin(a) * (R_MEDAILLON - 0.6f), 0.8f, m.hi, 0)
            }
        }
    }

    /**
     * Le médaillon de provenance, sa partie vivante : l'émail à la couleur du
     * jeu, son emblème, et la légende en arc.
     *
     * L'emblème dit le jeu sans le nommer, la légende le nomme, et les deux
     * sont des faits que le carnet possède, pas de la décoration : l'ornement
     * n'a jamais eu le droit d'inventer une donnée. La couleur du mot reste
     * aux gemmes ; l'émail prend celle du **jeu**, pour que les deux ne se
     * confondent pas.
     */
    fun dessinerMedaillon(c: Canvas, p: Paint, jeu: JeuCarte, m: Metal) {
        val cx = PROVENANCE.centerX()
        val cy = PROVENANCE.centerY()
        val hsv = FloatArray(3)
        Color.colorToHSV(jeu.couleur, hsv)
        val clair = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1] * 0.55f, min(1f, hsv[2] + 0.25f)))
        val sombre = Color.HSVToColor(floatArrayOf(hsv[0], min(1f, hsv[1] + 0.1f), hsv[2] * 0.55f))

        p.style = Paint.Style.FILL
        p.color = Color.BLACK
        p.shader = RadialGradient(
            cx - R_EMAIL * 0.3f, cy - R_EMAIL * 0.35f, R_EMAIL * 1.5f,
            intArrayOf(clair, jeu.couleur, sombre), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, R_EMAIL, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        p.color = 0x40000000
        c.drawCircle(cx, cy, R_EMAIL - 0.5f, p)

        emblemeDuJeu(c, p, jeu, cx, cy)

        // La légende : sur le disque, entre l'émail et le bord.
        p.style = Paint.Style.FILL
        p.color = m.trait
        p.typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD
        )
        texteSurArc(c, p, "GAGNÉ À", cx, cy, R_EMAIL + 2.2f, true)
        texteSurArc(c, p, jeu.nom.uppercase(), cx, cy, R_MEDAILLON - 3.6f, false)
        p.typeface = android.graphics.Typeface.DEFAULT
        p.strokeCap = Paint.Cap.BUTT
        p.strokeJoin = Paint.Join.MITER
    }

    /**
     * Un texte suivant l'arc du haut ou du bas du disque, centré, **lettre par
     * lettre**.
     *
     * En haut les lettres se dressent vers l'extérieur ; en bas, sous le
     * centre, elles se dressent vers le centre, pour se lire à l'endroit : d'où
     * les deux rayons différents, qui sont ceux de la ligne de base.
     *
     * Chaque lettre est posée à la main, à l'angle que lui donne la somme des
     * chasses qui la précèdent. L'interlettrage est ajouté ici plutôt que par
     * `letterSpacing`, dont l'effet sur un texte sur chemin n'est pas garanti.
     * Trop long, le texte se serre plutôt que de sortir de son demi-cercle.
     */
    private fun texteSurArc(c: Canvas, p: Paint, texte: String, cx: Float, cy: Float, rayon: Float, haut: Boolean) {
        // La légende est tracée à 5,6 unités sur un canvas agrandi trois fois :
        // sans texte linéaire, les chasses sont arrondies au pixel à 5,6 px puis
        // agrandies, et l'arrondi devient un trou visible (devant le « É » de
        // « GAGNÉ »).
        val lineaire = p.isLinearText
        val sousPixel = p.isSubpixelText
        p.isLinearText = true
        p.isSubpixelText = true
        p.textSize = TAILLE_LEGENDE
        p.textAlign = Paint.Align.CENTER
        val chasses = FloatArray(texte.length)
        fun mesurer(): Float {
            p.getTextWidths(texte, chasses)
            val espace = p.textSize * INTERLETTRAGE
            for (k in chasses.indices) chasses[k] += espace
            return chasses.sum() - espace
        }
        var largeur = mesurer()
        val dispo = Math.PI.toFloat() * rayon * 0.9f
        if (largeur > dispo) {
            p.textSize *= dispo / largeur
            largeur = mesurer()
        }
        // Angles en radians, 0 à droite, sens horaire (l'axe y descend).
        val centre = if (haut) -Math.PI.toFloat() / 2f else Math.PI.toFloat() / 2f
        val sens = if (haut) 1f else -1f
        var parcouru = 0f
        for (k in texte.indices) {
            val a = centre + sens * ((parcouru + chasses[k] / 2f) - largeur / 2f) / rayon
            c.save()
            c.translate(cx + cos(a) * rayon, cy + sin(a) * rayon)
            c.rotate(Math.toDegrees(a.toDouble()).toFloat() + if (haut) 90f else -90f)
            c.drawText(texte[k].toString(), 0f, 0f, p)
            c.restore()
            parcouru += chasses[k]
        }
        p.textAlign = Paint.Align.LEFT
        p.isLinearText = lineaire
        p.isSubpixelText = sousPixel
    }

    /** L'emblème d'un jeu, en blanc, dans un rayon de huit unités. */
    private fun emblemeDuJeu(c: Canvas, p: Paint, jeu: JeuCarte, cx: Float, cy: Float) {
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.color = 0xF2FFFFFF.toInt()
        fun trait(x1: Float, y1: Float, x2: Float, y2: Float) = c.drawLine(cx + x1, cy + y1, cx + x2, cy + y2, p)
        fun carre(x: Float, y: Float, t: Float, plein: Boolean) {
            p.style = if (plein) Paint.Style.FILL else Paint.Style.STROKE
            c.drawRect(cx + x, cy + y, cx + x + t, cy + y + t, p)
        }
        fun fleche(x1: Float, y1: Float, x2: Float, y2: Float) {
            trait(x1, y1, x2, y2)
            val a = Math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble()).toFloat()
            for (d in floatArrayOf(2.5f, -2.5f)) {
                trait(x2, y2, x2 + cos(a + Math.PI.toFloat() + d * 0.35f) * 3f, y2 + sin(a + Math.PI.toFloat() + d * 0.35f) * 3f)
            }
        }
        when (jeu) {
            // Une loupe.
            JeuCarte.MOTS_MELES -> {
                c.drawCircle(cx - 1.6f, cy - 1.6f, 4.4f, p)
                trait(1.6f, 1.6f, 6.4f, 6.4f)
            }
            // Deux flèches qui se croisent : le mélange.
            JeuCarte.MOTS_MELANGES -> {
                fleche(-6f, -3.8f, 6f, 3.8f)
                fleche(-6f, 3.8f, 6f, -3.8f)
            }
            // Une grille d'essais : la dernière rangée pleine.
            JeuCarte.MO_AN_KARENAJ -> {
                p.strokeWidth = 1f
                for (i in 0 until 3) carre(-6.4f + i * 4.6f, -4.6f, 3.6f, i == 0)
                for (i in 0 until 3) carre(-6.4f + i * 4.6f, 0.4f, 3.6f, true)
            }
            // Une ligne de texte avec sa case vide.
            JeuCarte.FRAZ_A_TWOU -> {
                p.strokeWidth = 1.3f
                trait(-7f, -4.5f, 7f, -4.5f)
                trait(-7f, 1.5f, -3.6f, 1.5f)
                trait(3.6f, 1.5f, 7f, 1.5f)
                p.strokeWidth = 1f
                carre(-2f, -1.2f, 4f, false)
            }
            // Une grille de mots croisés, trois cases noires.
            JeuCarte.MOKWARE -> {
                p.strokeWidth = 0.9f
                for (i in 0 until 3) for (j in 0 until 3) {
                    carre(-6f + i * 4f, -6f + j * 4f, 4f, (i == 0 && j == 0) || (i == 2 && j == 1) || (i == 1 && j == 2))
                }
            }
        }
        p.style = Paint.Style.FILL
    }

    // -------------------------------------------------------------- le vif

    /**
     * La teinte d'un mot : le seul endroit qui la décide.
     *
     * Elle se lit sur les **trois premières lettres**, et non sur le mot
     * entier. Le mot entier donnait à `Woch`, `Wochen` et `Woche` trois
     * couleurs sans rapport : le carnet cachait activement qu'il s'agit d'un
     * seul mot à trois états, alors que c'est exactement ce qu'un carnet de
     * vocabulaire devrait montrer. Le préfixe suffit à les réunir, et il ne
     * demande de consulter aucun lemme — donc il vaut aussi pour les 647
     * formes que `luxemburgish_familles.json` ne rattache à rien.
     *
     * Le prix est l'homonymie de préfixe : `Stad` et `Statist` tomberont sur
     * la même teinte. C'est sans conséquence, parce qu'une teinte n'identifie
     * pas une carte — la plaque porte le mot — elle en rapproche.
     *
     * La face, la gemme et le motif la partagent : une carte dont la fenêtre
     * jurerait avec son carton se lirait comme un défaut d'impression.
     */
    fun teinteDe(mot: String): Float {
        val condense = condense(mot)
        return ((condense % 360) + 360) % 360f
    }

    /**
     * La teinte d'un mot **qui a un champ** : la couleur du champ, écartée.
     *
     * Sans champ, on retombe sur [teinteDe] et rien ne change. Avec, la carte
     * prend la teinte de son domaine — et c'est ce qui fait qu'un tiroir du
     * carnet cesse d'être un nuancier aléatoire pour devenir un rangement.
     *
     * L'écart de ±12° n'est pas une décoration : huit teintes strictement
     * identiques feraient de chaque champ un aplat, et deux cartes voisines du
     * même domaine deviendraient indiscernables l'une de l'autre. Il est tiré
     * du même condensé que [teinteDe], donc des trois premières lettres, ce qui
     * garde `Woch`, `Wochen` et `Woche` exactement sur la même couleur.
     *
     * Douze degrés, enfin, parce que c'est moins que la moitié du plus petit
     * intervalle entre deux champs (34°, de 20 à 52) : deux domaines ne peuvent
     * donc jamais se recouvrir, quel que soit le mot.
     */
    fun teinteDe(mot: String, champ: Champ?): Float {
        if (champ == null) return teinteDe(mot)
        val ecart = (((condense(mot) shr 3) % 25) + 25) % 25 - 12
        return ((champ.hue + ecart) % 360f + 360f) % 360f
    }

    /** Le condensé des trois premières lettres, seule source des teintes. */
    private fun condense(mot: String): Int =
        mot.lowercase().take(3).fold(7919) { acc, c -> acc * 31 + c.code }

    /**
     * La face intérieure : la teinte du mot, et rien d'autre.
     *
     * C'est la pièce qui garantit que la collection reste variée. Le métal
     * dit le palier — quatre valeurs possibles — et la face dit le mot, qui
     * en a trois cent soixante.
     */
    fun degradeFace(teinte: Float, rarete: Rarete, vignette: Boolean): LinearGradient {
        val haut = if (vignette) HAUTEUR_VIGNETTE else HAUTEUR
        val bord = 12f + rarete.ordinal * 2f
        return LinearGradient(
            0f, bord, 0f, haut - bord,
            Color.HSVToColor(floatArrayOf(teinte, 0.10f, 0.99f)),
            Color.HSVToColor(floatArrayOf(teinte, 0.22f, 0.88f)),
            Shader.TileMode.CLAMP
        )
    }

    fun dessinerFace(c: Canvas, p: Paint, degrade: LinearGradient, rarete: Rarete, vignette: Boolean) {
        val haut = if (vignette) HAUTEUR_VIGNETTE else HAUTEUR
        val bord = 12f + rarete.ordinal * 2f
        p.style = Paint.Style.FILL
        p.shader = degrade
        c.drawRoundRect(RectF(bord, bord, LARGEUR - bord, haut - bord), 11f, 11f, p)
        p.shader = null
    }

    /** Le tracé des rayons, appelé une seule fois par palier et par taille. */
    private fun dessinerRayons(c: Canvas, rarete: Rarete, vignette: Boolean) {
        val palier = rarete.ordinal
        if (palier < 2) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val haut = if (vignette) HAUTEUR_VIGNETTE else HAUTEUR
        val bord = 12f + palier * 2f
        val fenetre = if (vignette) FENETRE_VIGNETTE else FENETRE
        c.save()
        c.clipPath(Path().apply {
            addRoundRect(RectF(bord, bord, LARGEUR - bord, haut - bord), 11f, 11f, Path.Direction.CW)
        })
        val cx = LARGEUR / 2f
        val cy = fenetre.top + fenetre.height() * 0.45f
        val n = if (palier >= 3) 24 else 16
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = if (palier >= 3) 0x4DFFEEBE else 0x57FFFFFF
        val secteur = 360f / n
        for (i in 0 until n step 2) {
            val chemin = Path()
            chemin.moveTo(cx, cy)
            chemin.arcTo(RectF(cx - 340f, cy - 340f, cx + 340f, cy + 340f), i * secteur, secteur)
            chemin.close()
            c.drawPath(chemin, p)
        }
        c.restore()
    }

    /**
     * Une pierre taillée : la teinte du mot, dans son chaton.
     *
     * Deux l'appellent. La **gemme de coût** mord sur l'angle de l'ouverture,
     * comme la gemme de mana d'une carte de jeu — c'est ce débord qui donne
     * l'impression d'épaisseur ; elle le prenait sur la plaque de nom tant que
     * celle-ci était en haut. L'**agrafe** pend sous la plaque, en plus
     * petit, en ovale, et avec un chaton plus mince : voir [CREUX_AGRAFE].
     */
    fun degradeGemme(
        teinte: Float, r: RectF = GEMME, creux: Float = 4f, intensite: Float = 0.5f
    ): RadialGradient {
        val gx = r.centerX()
        val gy = r.centerY()
        val gr = r.height() / 2f - creux
        // L'intensité ne touche que la saturation, jamais la teinte (c'est celle
        // du champ) ni la valeur : le chiffre blanc du coût est posé sur le ton
        // moyen, et une pierre plus claire le lirait mal.
        return RadialGradient(
            gx - gr * 0.3f, gy - gr * 0.35f, gr * 1.4f,
            intArrayOf(
                Color.HSVToColor(floatArrayOf(teinte, lerp(0.18f, 0.34f, intensite), 1f)),
                Color.HSVToColor(floatArrayOf(teinte, lerp(0.50f, 0.88f, intensite), 0.72f)),
                Color.HSVToColor(floatArrayOf(teinte, lerp(0.70f, 0.95f, intensite), 0.34f))
            ),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

    /**
     * D'où vient la lumière cuite dans le métal, normalisée : en haut à gauche,
     * celle du `RadialGradient` de [joyau]. C'est la direction de repos de
     * tout ce qui tourne vers le doigt, pour qu'une carte non touchée reste
     * exactement celle d'avant.
     */
    const val CUITE_X = -0.66f
    const val CUITE_Y = -0.75f

    /**
     * [lumX], [lumY] : d'où vient la lumière sur cette pierre, normalisé. Seul
     * le point spéculaire la suit ; le dégradé est construit une fois par carte
     * et ne bouge pas, ce qui suffit à faire tourner la pierre et évite d'en
     * refaire un par trame.
     */
    fun dessinerGemme(
        c: Canvas, p: Paint, degrade: RadialGradient, r: RectF = GEMME, creux: Float = 4f,
        intensite: Float = 0.5f, lumX: Float = CUITE_X, lumY: Float = CUITE_Y
    ) {
        val gx = r.centerX()
        val gy = r.centerY()
        val gr = r.height() / 2f - creux
        c.save()
        c.scale(r.width() / r.height(), 1f, gx, gy)
        p.style = Paint.Style.FILL
        // Deux pierres par trame : la seconde héritait du point de lumière de
        // la première, à 90 % d'alpha, et son dégradé s'en trouvait délavé.
        p.color = Color.BLACK
        p.shader = degrade
        c.drawCircle(gx, gy, gr, p)
        p.shader = null

        // Le filet interne, plus sombre que la sertissure : sans lui le
        // dégradé s'arrête à plat sur le métal au lieu de sembler taillé.
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.1f
        p.color = 0x40000000
        c.drawCircle(gx, gy, gr - 0.6f, p)

        p.style = Paint.Style.FILL
        // Le point de lumière suit l'intensité : une pierre rare brille plus.
        p.color = (lerp(0x58.toFloat(), 0xC0.toFloat(), intensite).toInt() shl 24) or 0xFFFFFF
        c.save()
        c.rotate(-28f, gx - gr * 0.28f, gy - gr * 0.38f)
        c.drawOval(
            RectF(
                gx - gr * 0.74f, gy - gr * 0.66f,
                gx + gr * 0.18f, gy - gr * 0.10f
            ), p
        )
        c.restore()

        // Le point de lumière : une facette taillée, posée sur le halo.
        p.color = 0xE6FFFFFF.toInt()
        c.drawCircle(gx + lumX * gr * 0.64f, gy + lumY * gr * 0.64f, gr * 0.11f, p)
        c.restore()
    }


    /**
     * Le semis d'étincelles d'une très rare.
     *
     * Tiré du mot, donc **toujours le même pour une carte donnée** : deux
     * ouvertures de la même carte montrent les mêmes étincelles aux mêmes
     * endroits, ce qui en fait une propriété de la pièce et non un effet.
     */
    fun dessinerSemis(c: Canvas, p: Paint, mot: String, rarete: Rarete, vignette: Boolean) {
        if (rarete != Rarete.TRES_RARE) return
        val haut = if (vignette) HAUTEUR_VIGNETTE else HAUTEUR
        var g = mot.fold(7919) { acc, ch -> acc * 31 + ch.code }
        val n = if (vignette) 7 else 11
        // Le semis est peint après le métal : tiré sur toute la carte, il
        // tombait sur le cadre. Il reste dans la face, rayon maximal compris.
        val marge = 12f + rarete.ordinal * 2f + 7f
        // Ni sur le texte ni sur le métal posé sur la face : une étincelle y
        // passe pour un défaut d'impression. On retire, au même générateur.
        val interdits = if (vignette) arrayOf(NOM_VIGNETTE, GLOSE_VIGNETTE, BOITE_VIGNETTE)
        else arrayOf(
            GEMME, PLAQUE, GEMME_CENTRE, NATURE, PANNEAU, ECU_G, ECU_D, PROVENANCE
        )
        var poses = 0
        var essais = 0
        while (poses < n && essais < n * 8) {
            essais++
            g = g * 1103515245 + 12345
            val x = marge + ((g ushr 8) % 1000) / 1000f * (LARGEUR - 2f * marge)
            val y = marge + ((g ushr 18) % 1000) / 1000f * (haut - 2f * marge)
            val r = 2.5f + ((g ushr 4) % 5)
            if (interdits.any { x > it.left - r && x < it.right + r && y > it.top - r && y < it.bottom + r }) continue
            // Ni sur la sertissure de la fenêtre : dedans ou dehors, pas à cheval.
            val m = r + 5f
            val f = if (vignette) FENETRE_VIGNETTE else FENETRE
            val dehors = !dansArche(x, y, RectF(f.left - m, f.top - m, f.right + m, f.bottom + m))
            val dedans = dansArche(x, y, RectF(f.left + m, f.top + m, f.right - m, f.bottom - m))
            if (!dehors && !dedans) continue
            etincelle(c, p, x, y, r, 90 + ((g ushr 12) % 100))
            poses++
        }
    }

    /** Le point est-il dans l'ouverture en plein cintre de [cheminFenetre] ? */
    private fun dansArche(x: Float, y: Float, r: RectF): Boolean {
        if (x < r.left || x > r.right || y > r.bottom) return false
        val fleche = r.width() * 0.30f
        val base = r.top + fleche
        if (y >= base) return true
        val dx = (x - r.centerX()) / (r.width() / 2f)
        val dy = (y - base) / fleche
        return dx * dx + dy * dy <= 1f
    }

    /**
     * Le reflet spéculaire qui traverse le métal quand on incline l'appareil.
     *
     * Réservé aux deux paliers hauts, et calé sur le **même roulis** que
     * l'irisation de l'illustration : les deux doivent glisser ensemble,
     * sinon la carte se lit comme deux objets superposés.
     */
    fun dessinerReflet(c: Canvas, p: Paint, rarete: Rarete, roulis: Float, vignette: Boolean) {
        if (!rarete.distinguee) return
        val haut = if (vignette) HAUTEUR_VIGNETTE else HAUTEUR
        refletBalaye(c, p, roulis, haut, if (rarete == Rarete.TRES_RARE) 0x66 else 0x2E)
    }

    /**
     * L'épaisseur du carton : ce qui le sépare d'une découpe de papier.
     *
     * ## Pourquoi c'est le manque le plus criant
     *
     * Une carte inclinée montre sa tranche. C'est la chose qu'on ne remarque
     * jamais consciemment et qui décide pourtant, à elle seule, si le cerveau
     * range ce qu'il voit dans les objets ou dans les images. Le carnet
     * savait déjà pencher ses cartes et y faire glisser une lumière ; ce
     * qu'il ne savait pas, c'est leur donner un bord.
     *
     * ## Quel bord, et pourquoi celui-là
     *
     * Un `rotationY` positif fait **fuir le bord droit** — c'est la
     * convention d'Android, et c'est celle sur laquelle [Inclinaison] a réglé
     * son contre-pivot. Or la tranche qu'on voit n'est pas celle du bord qui
     * s'éloigne mais celle du bord qui **s'approche** : c'est sa face
     * latérale qui tourne vers l'œil, l'autre tournant le dos. Un roulis
     * positif se dessine donc avec la tranche à gauche et l'ombre à droite,
     * ce qui est l'inverse de ce que la main écrit spontanément.
     *
     * ## Une épaisseur assumée comme fausse
     *
     * Une carte à jouer fait trois dixièmes de millimètre, soit une unité et
     * demie de carte, et sa projection à sept degrés vaut deux dixièmes
     * d'unité — invisible. [EPAISSEUR] est donc un mensonge délibéré, et la
     * racine appliquée au roulis en est un second : sans elle, la tranche ne
     * se déplierait que dans le dernier quart du débattement, alors que le
     * téléphone passe sa vie dans le premier.
     */
    fun dessinerTranche(c: Canvas, p: Paint, roulis: Float, haut: Float) {
        val ouverture = abs(roulis)
        if (ouverture < 0.02f) return
        val e = EPAISSEUR * ouverture.pow(0.55f)
        val contour = RectF(0f, 0f, LARGEUR, haut)
        val fuiteADroite = roulis > 0f

        // Le bord qui s'éloigne tombe dans l'ombre. Un dégradé, et non un
        // aplat : une arête franche se lirait comme un trait d'encre.
        c.save()
        if (fuiteADroite) c.clipRect(LARGEUR - LARGEUR_OMBRE, 0f, LARGEUR, haut)
        else c.clipRect(0f, 0f, LARGEUR_OMBRE, haut)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            if (fuiteADroite) LARGEUR else 0f, 0f,
            if (fuiteADroite) LARGEUR - LARGEUR_OMBRE else LARGEUR_OMBRE, 0f,
            ((0x44 * ouverture).toInt() shl 24), 0x00000000, Shader.TileMode.CLAMP
        )
        c.drawRoundRect(contour, RAYON, RAYON, p)
        p.shader = null
        c.restore()

        // Le bord qui vient vers le joueur montre son cœur. La découpe est
        // rectangulaire et parallèle aux pixels, donc sans crénelage : les
        // coins arrondis viennent du rectangle tracé dedans, lui antialiasé.
        c.save()
        if (fuiteADroite) c.clipRect(0f, 0f, e, haut) else c.clipRect(LARGEUR - e, 0f, LARGEUR, haut)
        p.color = TRANCHE
        c.drawRoundRect(contour, RAYON, RAYON, p)
        c.restore()

        // Là où l'impression s'arrête sur la tranche, il reste une ligne. Elle
        // s'arrête avant les coins : à cette hauteur, la tranche a déjà tourné.
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.9f
        p.color = TRANCHE_FIL
        p.alpha = (230f * ouverture).toInt().coerceAtMost(230)
        val x = if (fuiteADroite) e else LARGEUR - e
        c.drawLine(x, RAYON * 0.8f, x, haut - RAYON * 0.8f, p)
        p.alpha = 255
    }

    // ------------------------------------------------------------ les cotes

    /*
     * Le modèle de hauteurs de la face, en unités de carte.
     *
     * Ce sont des cotes **relatives**, pas des millimètres : ce qui compte est
     * leur ordre et leurs écarts, que le vibreur lit comme des amplitudes et
     * le pinceau comme des bosses. Chacune doit rester d'accord avec ce que le
     * dessin montre déjà, sinon le doigt et l'œil décrivent deux objets.
     *
     * - Hors carte, la table : zéro.
     * - Le plateau de métal, 2 : c'est le carton plus son cadre, et c'est la
     *   première marche. La seconde est sa lèvre intérieure, à `bord`, qui
     *   redescend sur la face : 12 unités à Commun, 18 à Très rare. L'échelle
     *   de rareté a donc déjà une composante tactile, et elle est juste.
     * - La face, 1 ; le panneau de texte à fleur, 1,1.
     * - L'ouverture, 0,4 : une **découpe**, que le dessin creuse déjà par
     *   son ombre. On y descend en entrant, on en remonte en sortant.
     * - La capsule de nature, 1,6 ; la plaque et le disque du médaillon, 1,8 ;
     *   les écus, 2 : des pièces de métal posées, pas toutes de même épaisseur.
     * - Les pierres dépassent davantage que tout ce qui est posé à plat :
     *   l'agrafe 2,4, la gemme de coût 2,8. Les rivets, 2,4.
     * - Une griffe tient sa pierre par-dessus : 0,3 de plus qu'elle.
     */
    private const val Z_HORS = 0f
    private const val Z_OUVERTURE = 0.4f
    const val Z_FACE = 1f
    private const val Z_PANNEAU = 1.1f
    private const val Z_PASTILLE = 1.6f
    private const val Z_PLAQUE = 1.8f
    private const val Z_MEDAILLON = 1.8f
    private const val Z_ECU = 2f
    private const val Z_PLATEAU = 2f
    private const val Z_AGRAFE = 2.4f
    private const val Z_RIVET = 2.4f
    private const val Z_GEMME = 2.8f
    private const val Z_GRIFFE = 0.3f

    /** Le rayon du chaton de la gemme de coût, celui de [sertissure]. */
    private val R_GEMME get() = GEMME.height() / 2f

    /**
     * Les points en relief de la face : rivets et griffes, par palier.
     *
     * Quintuplets `cx, cy, rx, ry, z`. C'est **la** liste : [cote] la lit pour
     * le doigt, `CarteOrnee` pour faire tourner leur lumière vers lui. Les
     * positions sont celles que [dessinerMetal] et [sertissure] tracent ; qui
     * déplace un rivet là-bas doit le déplacer ici, et c'est pourquoi les deux
     * lisent les mêmes constantes plutôt que de recopier des nombres.
     */
    fun bosses(rarete: Rarete): FloatArray {
        val palier = rarete.ordinal
        val bord = 12f + palier * 2f
        val v = ArrayList<Float>(5 * 18)
        fun bosse(cx: Float, cy: Float, rx: Float, ry: Float, z: Float) {
            v.add(cx); v.add(cy); v.add(rx); v.add(ry); v.add(z)
        }
        if (palier >= 1) {
            for (y in floatArrayOf(HAUTEUR * 0.42f, HAUTEUR * 0.70f)) {
                bosse(bord + 6f, y, 3.2f, 3.2f, Z_RIVET)
                bosse(LARGEUR - bord - 6f, y, 3.2f, 3.2f, Z_RIVET)
            }
        }
        griffes(GEMME, if (palier >= 2) 8 else 0, Z_GEMME + Z_GRIFFE, ::bosse)
        griffes(GEMME_CENTRE, if (palier >= 3) 6 else 0, Z_AGRAFE + Z_GRIFFE, ::bosse)
        return v.toFloatArray()
    }

    private fun griffes(
        r: RectF, n: Int, z: Float, bosse: (Float, Float, Float, Float, Float) -> Unit
    ) {
        val ry = r.height() / 2f
        val sx = r.width() / r.height()
        for (i in 0 until n) {
            val a = i / n.toFloat() * 2f * Math.PI.toFloat()
            bosse(
                r.centerX() + cos(a) * ry * sx, r.centerY() + sin(a) * ry,
                ry * 0.09f * sx, ry * 0.09f, z
            )
        }
    }

    /**
     * La hauteur de la face en ([x], [y]), dans le modèle ci-dessus.
     *
     * L'ordre des tests est celui de l'empilement, du plus haut au plus bas :
     * une pièce posée cache ce qu'elle recouvre, au doigt comme à l'œil. C'est
     * ce qui fait disparaître d'eux-mêmes les flancs de l'ouverture sous la
     * plaque, que l'ancienne liste de positions faisait sentir au travers.
     */
    fun cote(rarete: Rarete, bosses: FloatArray, x: Float, y: Float): Float {
        if (x < BORD_CARTE || x > LARGEUR - BORD_CARTE || y < 0f || y > HAUTEUR) return Z_HORS
        var i = 0
        while (i < bosses.size) {
            if (dansOvale(x, y, bosses[i], bosses[i + 1], bosses[i + 2], bosses[i + 3])) {
                return bosses[i + 4]
            }
            i += 5
        }
        if (dansOvale(x, y, GEMME.centerX(), GEMME.centerY(), R_GEMME, R_GEMME)) return Z_GEMME
        if (dansOvale(
                x, y, GEMME_CENTRE.centerX(), GEMME_CENTRE.centerY(),
                GEMME_CENTRE.width() / 2f, GEMME_CENTRE.height() / 2f
            )
        ) return Z_AGRAFE
        if (dansOvale(
                x, y, PROVENANCE.centerX(), PROVENANCE.centerY(), R_MEDAILLON, R_MEDAILLON
            )
        ) return Z_MEDAILLON
        if (ECU_G.contains(x, y) || ECU_D.contains(x, y)) return Z_ECU
        if (NATURE.contains(x, y)) return Z_PASTILLE
        if (PLAQUE.contains(x, y)) return Z_PLAQUE
        val bord = 12f + rarete.ordinal * 2f
        if (x < bord || x > LARGEUR - bord || y < bord || y > HAUTEUR - bord) return Z_PLATEAU
        if (PANNEAU.contains(x, y)) return Z_PANNEAU
        val dansFenetre = if (rarete.ordinal >= 2) dansArche(x, y, FENETRE)
        else FENETRE.contains(x, y)
        return if (dansFenetre) Z_OUVERTURE else Z_FACE
    }

    private fun dansOvale(x: Float, y: Float, cx: Float, cy: Float, rx: Float, ry: Float): Boolean {
        val dx = (x - cx) / rx
        val dy = (y - cy) / ry
        return dx * dx + dy * dy <= 1f
    }

    /**
     * Les crans que le pouce franchit à la hauteur [y] : où, et de combien.
     *
     * ## Ce que le doigt est censé sentir
     *
     * Une carte de collection est gravée : le cadre est en relief sur la
     * face, l'ouverture est creusée dedans, les écus dépassent. Un pouce qui
     * la traverse franchit donc une poignée de marches, et leur **rythme**
     * dépend de la hauteur à laquelle il passe. Mais le rythme seul ne dit
     * pas le volume : monter sur la plaque et en redescendre donnaient deux
     * tics identiques, une perle et une capsule aussi. Chaque cran porte donc
     * le dénivelé franchi, et c'est lui qui laisse un doigt aveugle dire « là
     * c'est creux, là il y a une petite bosse ».
     *
     * La rangée est **échantillonnée** sur [cote] à l'unité, plutôt que
     * reconstruite à partir des bords des rectangles : une seule source de
     * vérité, et les formes rondes (pierres, médaillon, arche) y sont rondes
     * sans une ligne de géométrie de plus. Trois cents appels par changement
     * de bande, jamais par trame.
     *
     * ## Pourquoi la rareté a le droit d'y être
     *
     * Sur la face, oui : le palier est déjà sous les yeux, une main qui le
     * confirme n'apprend rien à personne. Sur le dos de révision, non — et
     * c'est pourquoi [DosRevision] ne passe pas par ici mais donne sa propre
     * géométrie, la même pour les douze cartes d'une session.
     */
    fun aretes(rarete: Rarete, bosses: FloatArray, y: Float): Relief {
        val brut = ArrayList<Arete>(24)
        var avant = cote(rarete, bosses, 0.5f, y)
        var x = 1.5f
        while (x < LARGEUR) {
            val ici = cote(rarete, bosses, x, y)
            if (ici != avant) brut.add(Arete(x - 0.5f, ici - avant))
            avant = ici
            x += 1f
        }
        return crans(brut)
    }

    /** Une marche : où elle est, et de combien on monte en la franchissant de gauche à droite. */
    class Arete(val x: Float, val denivele: Float)

    /**
     * Les crans d'une rangée, triés : `x`, dénivelé net, amplitude, par triplet.
     *
     * Un seul tableau et non deux, pour qu'une position ne puisse jamais se
     * retrouver avec le dénivelé d'une autre. L'amplitude existe pour les
     * arêtes fines : une bosse plus étroite que le doigt monte puis descend
     * dans le même cran, son net est nul, et pourtant le doigt l'accroche.
     */
    class Relief internal constructor(private val v: FloatArray) {
        val taille: Int get() = v.size / 3
        fun x(i: Int): Float = v[3 * i]

        /**
         * Ce que ressent un doigt qui franchit le cran [i] dans le [sens]
         * donné (+1 vers la droite). Une marche change de signe avec lui ;
         * un accroc, non : on le sent comme une montée dans les deux sens.
         */
        fun ressenti(i: Int, sens: Float): Float {
            val net = v[3 * i + 1]
            return if (abs(net) < DENIVELE_MIN) v[3 * i + 2] else net * sens
        }

        companion object {
            /** Un carton lisse. Partagé : il est vide et personne n'y écrit. */
            val LISSE = Relief(FloatArray(0))
        }
    }

    /**
     * Trie des arêtes et fond celles qui se touchent.
     *
     * La plaque de nom et l'ouverture se croisent en hauteur, et leurs flancs
     * finissent à deux unités l'un de l'autre : deux vibrations séparées par
     * un demi-millimètre ne se sentent pas comme deux marches, elles se
     * sentent comme un défaut.
     *
     * Deux arêtes fondues **somment** leurs dénivelés : deux marches d'un
     * millimètre à un demi-millimètre l'une de l'autre, c'est une marche de
     * deux. Une montée suivie de sa descente s'annule, et devient l'accroc de
     * [Relief], d'amplitude sa plus forte composante.
     */
    fun crans(brut: MutableList<Arete>): Relief {
        if (brut.isEmpty()) return Relief.LISSE
        brut.sortBy { it.x }
        val net = ArrayList<Float>(brut.size * 3)
        var debut = brut[0].x
        var somme = 0f
        var plusFort = 0f
        fun clore() {
            if (abs(somme) < DENIVELE_MIN && plusFort < DENIVELE_MIN) return
            net.add(debut); net.add(somme); net.add(max(abs(somme), plusFort))
        }
        for (a in brut) {
            if (a.x - debut >= ECART_MIN) {
                clore()
                debut = a.x
                somme = 0f
                plusFort = 0f
            }
            somme += a.denivele
            plusFort = max(plusFort, abs(a.denivele))
        }
        clore()
        return Relief(net.toFloatArray())
    }

    /** En deçà, deux arêtes n'en font qu'une sous le doigt (≈ 1,2 mm à l'écran). */
    const val ECART_MIN = 6f

    /** Sous ce dénivelé, un cran n'a plus de sens de marche : c'est un accroc. */
    const val DENIVELE_MIN = 0.05f

    /**
     * Où se trouve le bord du carton pour le doigt.
     *
     * Deux unités en dedans du bord géométrique : c'est le moment où le pouce
     * quitte la carte, et une carte qui se termine sans qu'on la sente finir
     * est une carte qui n'avait pas de bord.
     */
    const val BORD_CARTE = 2f

    /**
     * Le balayage lui-même, sans la question de savoir qui y a droit.
     *
     * Extrait de [dessinerReflet] parce que le dos de révision l'utilise
     * aussi, et qu'il n'a pas de rareté : un dos est le même pour toutes les
     * cartes du paquet, c'est même sa raison d'être. Une seule implémentation,
     * donc, sinon les deux faces d'un même carton finiraient par accrocher la
     * lumière selon deux angles différents.
     */
    fun refletBalaye(c: Canvas, p: Paint, roulis: Float, haut: Float, force: Int) {
        val dx = roulis * LARGEUR * ETALEMENT
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            dx, haut, LARGEUR + dx, 0f,
            intArrayOf(0x00FFFFFF, (force shl 24) or 0xFFFFFF, 0x00FFFFFF),
            floatArrayOf(0.32f, 0.5f, 0.68f), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(RectF(0f, 0f, LARGEUR, haut), RAYON, RAYON, p)
        p.shader = null
    }

    /** Les six paliers de Leitner, en pastilles, pour la vignette. */
    fun dessinerBoite(c: Canvas, p: Paint, boite: Int) {
        val r = BOITE_VIGNETTE
        val large = (r.width() - 5f * 2f) / Sonje.BOITE_ACQUISE
        p.style = Paint.Style.FILL
        p.shader = null
        for (i in 0 until Sonje.BOITE_ACQUISE) {
            p.color = if (i < boite) Carnet.COULEUR else 0x40000000
            val x = r.left + i * (large + 2f)
            c.drawRoundRect(RectF(x, r.top, x + large, r.bottom), 2f, 2f, p)
        }
    }
}

/**
 * Le carton : ce que les deux faces d'une carte ont en commun.
 *
 * ## Pourquoi un ViewGroup et pas un empilement de LinearLayout
 *
 * La disposition d'une carte de jeu n'est pas un flux : la plaque de nom, la
 * fenêtre et les écus sont **toujours au même endroit**, quelle que soit la
 * longueur de la glose. Un flux vertical ferait descendre les écus quand une
 * phrase du corpus prend trois lignes, et l'échelle d'ornement — qui suppose que
 * le cadre et le texte coïncident au pixel près — s'effondrerait.
 *
 * Les emplacements vivent dans [Ornement] et sont exprimés en unités de carte
 * (300 × 440). La vue les met à l'échelle de sa largeur réelle, ce qui rend la
 * même disposition valable en vignette de 160 dp et en carte ouverte.
 *
 * ## Pourquoi la géométrie a quitté [CarteOrnee]
 *
 * Depuis que la révision retourne ses cartes, une carte a un recto **et** un
 * verso, et l'illusion du retournement ne tient qu'à une condition : que les
 * deux faces soient le même objet vu des deux côtés. Même rectangle, même
 * rapport, même rayon de coin, mêmes unités.
 *
 * Laisser le dos naître ailleurs aurait suffi à tout perdre : [DosDeCarte],
 * écrit pour la pochette, arrondit ses coins à 14 dp fixes quand la face les
 * arrondit à [Ornement.RAYON] unités. Deux faces qui ne s'arrondissent pas
 * pareil se retournent comme deux objets qui se remplacent. La mesure, la mise
 * à l'échelle et la pose sont donc ici, en amont des deux.
 *
 * ## Pourquoi la lumière et la main sont ici aussi
 *
 * Le carton répondait au téléphone et pas à la main. On pouvait passer le
 * pouce dessus dix secondes sans que rien n'arrive, ce qui suffisait à le
 * ranger parmi les images : un objet réel réagit d'abord à ce qui le touche,
 * et seulement ensuite à la façon dont on le penche.
 *
 * Trois choses le sortent de là, et elles vivent toutes ici parce qu'elles
 * valent pour les deux faces :
 *
 * - **La tranche** ([Ornement.dessinerTranche]) — l'épaisseur qui se déplie
 *   sur le bord qui s'approche. C'est le seul des trois qui ne demande pas
 *   qu'on touche la carte, et probablement celui qui compte le plus.
 * - **Le doigt prend la lumière** — tant que le pouce est posé, c'est lui et
 *   non la pesanteur qui dit où tombe le reflet. Il n'a fallu inventer aucun
 *   tracé : [Ornement.refletBalaye] et [Motif.peindre] lisaient déjà un
 *   roulis, il leur en est simplement donné un autre.
 * - **L'appui** — le carton s'enfonce du côté pressé et remonte en dépassant
 *   légèrement son aplomb. C'est ce dépassement, et non l'enfoncement, qui se
 *   lit comme de la masse.
 *
 * ## Deux roulis, et pourquoi ils ne peuvent pas n'en faire qu'un
 *
 * [roulis] dit **où tombe la lumière**, [orientation] dit **comment le carton
 * est tourné**. Le doigt n'a le droit d'écrire que dans le premier : un pouce
 * posé au bord droit déplace un reflet, il ne fait pas pivoter la carte de
 * quarante degrés. Les confondre donnerait une tranche de cinq unités sur un
 * carton parfaitement à plat, c'est-à-dire l'exact contraire de l'effet
 * cherché.
 *
 * ## Ce que la main n'a pas le droit de faire
 *
 * Elle ne descend pas dans la grille, pour la même raison que l'inclinaison :
 * une vignette de 160 dp n'a la place ni d'une tranche ni d'un reflet, et un
 * `ACTION_DOWN` capté par chaque carte se battrait avec le défilement.
 * [sensibleAuDoigt] est donc faux par défaut et s'allume à la main, là où un
 * carton occupe l'écran pour lui seul.
 */
abstract class Carton(context: Context) : ViewGroup(context), SensorEventListener {

    private val emplacements = ArrayList<RectF>()

    /** La hauteur de ce carton-là, en unités de carte. */
    protected abstract val hauteurUnites: Float

    /**
     * Le carton doit-il rétrécir pour tenir dans la hauteur qu'on lui donne ?
     *
     * Faux partout où la carte vit dans un flux vertical — la grille du
     * carnet, la pochette : là, la largeur commande et la hauteur suit, et
     * c'est le défilement qui absorbe le reste.
     *
     * Vrai en révision, où le carton partage l'écran avec ses boutons et doit
     * se contenter de ce qui reste. C'est une option et non la règle
     * parce qu'un carton qui rétrécirait partout rétrécirait aussi dans un
     * `ScrollView`, dont la hauteur proposée ne veut rien dire.
     */
    var ajusteALaHauteur = false

    fun posee(vue: View, ou: RectF): Carton {
        addView(vue)
        emplacements.add(ou)
        return this
    }

    /**
     * Met les corps de texte à l'échelle du carton.
     *
     * Un `TextView` porte dans son `tag` sa taille et sa marge haute **en
     * unités de carte**. C'est la seule façon qu'une même disposition tienne
     * à 160 dp de vignette et à 340 dp de carte ouverte sans qu'il faille
     * écrire deux jeux de tailles et les tenir synchronisés.
     */
    private fun mettreALEchelle(vue: View, u: Float) {
        (vue.tag as? FloatArray)?.let { t ->
            (vue as? TextView)?.let { tv ->
                tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, t[0] * u)
                tv.setPadding(0, (t[1] * u).toInt(), 0, 0)
            }
        }
        if (vue is ViewGroup) {
            for (i in 0 until vue.childCount) mettreALEchelle(vue.getChildAt(i), u)
        }
    }

    override fun onMeasure(largeurSpec: Int, hauteurSpec: Int) {
        var largeur = MeasureSpec.getSize(largeurSpec)
        if (ajusteALaHauteur) {
            val mode = MeasureSpec.getMode(hauteurSpec)
            val plafond = MeasureSpec.getSize(hauteurSpec)
            if (mode != MeasureSpec.UNSPECIFIED && plafond > 0) {
                val tenable = (plafond * Ornement.LARGEUR / hauteurUnites).toInt()
                if (tenable < largeur) largeur = tenable
            }
        }
        val u = largeur / Ornement.LARGEUR
        val hauteur = (hauteurUnites * u).toInt()
        for (i in 0 until childCount) {
            val r = emplacements[i]
            mettreALEchelle(getChildAt(i), u)
            getChildAt(i).measure(
                MeasureSpec.makeMeasureSpec((r.width() * u).toInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((r.height() * u).toInt(), MeasureSpec.EXACTLY)
            )
        }
        setMeasuredDimension(largeur, hauteur)
    }

    override fun onLayout(change: Boolean, g: Int, h: Int, d: Int, b: Int) {
        val u = (d - g) / Ornement.LARGEUR
        for (i in 0 until childCount) {
            val r = emplacements[i]
            getChildAt(i).layout(
                (r.left * u).toInt(), (r.top * u).toInt(),
                (r.right * u).toInt(), (r.bottom * u).toInt()
            )
        }
    }

    // ------------------------------------------------- la lumière et la main

    /**
     * Ce carton-là suit-il la pesanteur ?
     *
     * Faux par défaut, c'est-à-dire pour une vignette de grille : autant
     * d'abonnements au capteur que de cartes visibles dans une colonne qui
     * défile serait absurde, et à 160 dp ni la tranche ni le reflet n'ont la
     * place d'exister.
     */
    protected open val suitLaLumiere: Boolean get() = false

    /**
     * Ce carton-là répond-il au doigt ?
     *
     * Allumé là où un carton occupe l'écran pour lui seul — la carte ouverte
     * du carnet, les deux faces de la révision. **Pas** dans la pochette : le
     * voile y prend l'appui pour passer à la carte suivante, et un carton qui
     * consommerait le geste supprimerait cette navigation-là. La révélation
     * d'un tirage est déjà un spectacle ; le pouce n'y a rien à ajouter.
     *
     * À allumer **une fois la mise en place finie**, comme
     * [Inclinaison.suivre] et pour la même raison : tant qu'un
     * `ViewPropertyAnimator` fait entrer la carte, il est seul à avoir le
     * droit d'écrire dans `rotationY`.
     */
    var sensibleAuDoigt = false
        set(valeur) {
            field = valeur
            if (valeur) {
                Inclinaison.perspective(this)
                // Le retour tactile du système a pu changer depuis la dernière
                // sonde, et c'est lui qui décide si la voie riche sera jetée.
                // Une fois par carte ouverte, jamais par geste.
                KeyFeedback.refresh(context)
            }
        }

    private var capteurs: SensorManager? = null

    /** Le roulis de l'appareil depuis le repos, dans [-1, 1], tel que dessiné. */
    private var pesanteur = 0f

    /** La même posture que celle qui fait pivoter la carte, voir [Posture]. */
    private val posture = Posture(this, LISSAGE)

    /** Le roulis que dicte le doigt, dans [-1, 1]. */
    private var doigt = 0f

    /** La part du doigt dans la lumière : 0 la pesanteur, 1 le pouce. */
    private var main = 0f

    /** L'enfoncement, dans [0, 1] — et un peu en dessous au rebond. */
    private var profondeur = 0f
    private var appuiX = 0f
    private var appuiY = 0f

    private var fonduMain: ValueAnimator? = null
    private var fonduAppui: ValueAnimator? = null

    /**
     * Les crans de la rangée où passe le doigt, en unités de carte.
     *
     * Recalculés quand le doigt **change de bande**, pas à chaque
     * `ACTION_MOVE`. Ils étaient gelés à la pose pour tout le geste, au motif
     * qu'un pouce qui traverse une carte suit une horizontale. Les yeux ouverts,
     * à peu près ; les yeux fermés, personne ne balaie droit, et un balayage
     * en diagonale faisait sentir la rangée de départ d'un bout à l'autre, ce
     * qui est un mensonge sur la carte. Le seuil est [Ornement.ECART_MIN], le
     * pouvoir séparateur du doigt : en dessous, la nouvelle rangée serait la
     * même, et on ne refait pas une liste par trame pour un balayage
     * horizontal où la hauteur ne bouge pratiquement pas.
     */
    private var relief: Ornement.Relief = Ornement.Relief.LISSE
    private var hauteurDuRelief = 0f
    private var derniereX = 0f

    /**
     * Où est le doigt, en unités de carte : ce que lisent l'ombre de contact et
     * le pivot des points en relief.
     *
     * Ils ne retombent pas à zéro au lâcher, et c'est voulu : l'ombre et les
     * reflets s'effacent avec [emprise] pendant [RETOUR], et doivent le faire
     * **là où était le pouce**. Remis au coin de la carte, ils y sauteraient
     * pour s'y éteindre. Ce qui dit qu'il n'y a plus de doigt, c'est [emprise].
     */
    protected var doigtX = 0f
        private set
    protected var doigtY = 0f
        private set

    /** L'autorité du doigt, 0..1 : la même que celle qui déplace la lumière. */
    protected val emprise: Float get() = main

    /** Quand est parti le dernier cran, et ce qu'on a dû taire depuis. */
    private var dernierCran = 0L
    private var report = 0f

    /**
     * Où tombe la lumière, dans [-1, 1].
     *
     * C'est ce que lisent le reflet et l'irisation du motif. Le doigt
     * l'emporte tant qu'il est posé, puis la pesanteur la reprend en une
     * seconde environ : une lumière qui sauterait au relâchement dirait que
     * le pouce était un mode, pas une main.
     */
    protected val roulis: Float get() = pesanteur + (doigt - pesanteur) * main

    /**
     * Comment le carton est réellement tourné, dans les mêmes unités.
     *
     * C'est ce que lit la tranche, et le doigt n'y écrit pas — sauf par
     * l'appui, qui fait pivoter le carton pour de bon et mérite donc que son
     * bord s'épaississe. [Inclinaison.AMPLITUDE] est le dénominateur commun
     * qui ramène des degrés à un roulis.
     */
    protected val assiette: Float
        get() = pesanteur + profondeur * appuiY / Inclinaison.AMPLITUDE

    /**
     * Le relief de ce carton-là à la hauteur [y], en unités de carte.
     *
     * Vide par défaut : un carton qui n'annonce rien est lisse, et le doigt
     * n'y sentira que le contact. C'est le bon comportement pour une vignette
     * — qui d'ailleurs ne reçoit jamais de doigt — et le seul honnête pour
     * une face qu'on ajouterait sans lui dessiner de gravure.
     */
    protected open fun aretes(y: Float): Ornement.Relief = Ornement.Relief.LISSE

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!suitLaLumiere || Pochette.animationsReduites(context)) return
        val manager =
            context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        // L'accéléromètre brut mélange la pesanteur et l'accélération
        // linéaire : marcher suffisait à faire trembler le reflet. Le capteur
        // fusionné n'en garde que la pesanteur, et n'existe pas partout.
        val capteur = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return
        manager.registerListener(this, capteur, SensorManager.SENSOR_DELAY_UI)
        capteurs = manager
    }

    override fun onDetachedFromWindow() {
        capteurs?.unregisterListener(this)
        capteurs = null
        posture.oublier()
        pesanteur = 0f
        // Un carton qui reviendrait à l'écran encore enfoncé se lirait comme
        // un bogue : l'appui appartient au geste, pas à la vue. Le test évite
        // au passage de fabriquer un état pour chacune des vignettes d'une
        // grille, qui n'ont jamais été touchées et ne le seront jamais.
        if (sensibleAuDoigt) reposer()
        super.onDetachedFromWindow()
    }

    /**
     * Rend le carton à son aplomb, tout de suite et sans transition.
     *
     * À appeler avant de lui faire jouer une animation qui écrit dans
     * `rotationY` — un retournement, typiquement. Le `ViewPropertyAnimator`
     * ne connaît pas l'arbitrage d'[Inclinaison] et écrirait dans la même
     * propriété que le rebond de l'appui : c'est exactement le tremblement à
     * deux écrivains que cet arbitrage existe pour empêcher.
     */
    fun reposer() {
        fonduMain?.cancel()
        fonduAppui?.cancel()
        fonduMain = null
        fonduAppui = null
        main = 0f
        profondeur = 0f
        appuiX = 0f
        appuiY = 0f
        scaleX = 1f
        scaleY = 1f
        relief = Ornement.Relief.LISSE
        report = 0f
        Inclinaison.appui(this, 0f, 0f)
        invalidate()
    }

    override fun onAccuracyChanged(capteur: Sensor?, precision: Int) = Unit

    /**
     * Le roulis, lissé, et seulement quand il a vraiment changé.
     *
     * Le filtre passe-bas rend la lumière lourde, ce qui est exactement
     * l'effet voulu : une carte, ça a du poids. Le seuil évite de rejouer
     * trois cents ordres de tracé pour un dixième de degré.
     */
    override fun onSensorChanged(evenement: SensorEvent) {
        posture.echantillon(evenement.values)
        if (abs(posture.roulis - pesanteur) < SEUIL) return
        pesanteur = posture.roulis
        invalidate()
    }

    /**
     * Le geste, en trois temps : saisir, glisser, lâcher.
     *
     * `super` voit tout, y compris l'appui qu'on réclame ensuite, pour que la
     * machinerie de clic d'Android continue de fonctionner : la carte ouverte
     * du carnet est `clickable` uniquement pour empêcher le voile de se
     * fermer sous elle, et il n'y a aucune raison de lui retirer ça.
     *
     * Les animations réduites ne coupent plus le geste entier, seulement ce
     * qui bouge : le retour tactile passe outre. Il n'a jamais gêné personne,
     * il n'occupe pas l'écran, et pour qui coupe les animations c'est
     * précisément le seul retour qui reste — le supprimer avec elles serait
     * l'exact contraire de ce que ce réglage demande.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val herite = super.onTouchEvent(event)
        if (!sensibleAuDoigt) return herite
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                saisir(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                glisser(event.x, event.y)
                return true
            }
            // Le `CANCEL` compte autant que le `UP` : dans la fiche du carnet,
            // c'est le `ScrollView` qui reprend le geste dès qu'il devient
            // vertical, et le carton doit alors se relever comme s'il avait
            // été lâché.
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> lacher()
        }
        return herite
    }

    private fun saisir(x: Float, y: Float) {
        if (width <= 0 || height <= 0) return
        val u = Ornement.LARGEUR / width
        doigtX = x * u
        doigtY = y * u
        relief = aretes(doigtY)
        hauteurDuRelief = doigtY
        derniereX = doigtX
        report = 0f
        // Le contact lui-même. Un carton posé ne claque pas quand on le
        // touche, mais un écran qui ne répond pas à un doigt posé n'a rien
        // touché du tout.
        KeyFeedback.onCardRidge(this)
        dernierCran = SystemClock.uptimeMillis()
        if (Pochette.animationsReduites(context)) return
        doigt = lumiereEn(x)
        // La lumière arrive sous le pouce dans le temps que met le carton à
        // s'enfoncer, et n'y saute pas : le doigt n'est pas une lampe qu'on
        // allume, c'est une surface qui bascule vers lui.
        animerMain(1f, ENFONCEMENT)
        // Le point pressé s'enfonce : à droite, c'est le bord droit qui part
        // en arrière (`rotationY` positif) ; en bas, c'est le bord bas, donc
        // le haut qui revient (`rotationX` négatif).
        val demiL = width / 2f
        val demiH = height / 2f
        appuiY = APPUI * ((x - demiL) / demiL).coerceIn(-1f, 1f)
        appuiX = -APPUI * ((y - demiH) / demiH).coerceIn(-1f, 1f)
        animerAppui(1f, ENFONCEMENT, DecelerateInterpolator())
    }

    /**
     * Le doigt avance : on regarde ce qu'il vient de franchir, puis on
     * déplace la lumière.
     *
     * Une seule vibration par événement, même quand plusieurs arêtes ont été
     * franchies d'un coup. C'est ce que fait une vraie surface gravée : un
     * balayage lent donne des marches distinctes parce que les événements
     * arrivent plus serrés que les arêtes, un balayage rapide donne un
     * frottement. Le pas minimum, lui, empêche un pouce immobile posé
     * exactement sur une arête de la franchir cent fois par tremblement.
     *
     * Les crans franchis d'un coup **somment** leur dénivelé, dans le sens de
     * la marche : monter puis redescendre dans le même événement ne vaut rien
     * de net, et c'est alors le dernier franchi qui parle.
     */
    private fun glisser(x: Float, y: Float) {
        if (width <= 0) return
        val u = Ornement.LARGEUR / width
        val ou = x * u
        doigtX = ou
        doigtY = y * u
        if (abs(doigtY - hauteurDuRelief) > Ornement.ECART_MIN) {
            relief = aretes(doigtY)
            hauteurDuRelief = doigtY
        }
        if (abs(ou - derniereX) >= PAS_MIN) {
            val sens = if (ou > derniereX) 1f else -1f
            val bas = min(derniereX, ou)
            val haut = max(derniereX, ou)
            derniereX = ou
            var franchi = 0f
            var dernier = 0f
            var touche = false
            for (i in 0 until relief.taille) {
                val c = relief.x(i)
                if (c > bas && c <= haut) {
                    dernier = relief.ressenti(i, sens)
                    franchi += dernier
                    touche = true
                }
            }
            if (touche) emettre(if (abs(franchi) < Ornement.DENIVELE_MIN) dernier else franchi)
        }
        if (Pochette.animationsReduites(context)) return
        doigt = lumiereEn(x)
        invalidate()
    }

    /**
     * Demande un cran, sauf s'il arriverait avant la fin du précédent.
     *
     * Sur une pastille ou un moteur lent, un cran dure plus longtemps que le
     * trajet d'une arête à la suivante sur la rangée des écus ; les enchaîner
     * donne une bouillie où plus rien ne se distingue. La carte éclaircit
     * donc sa partition : un cran qui tombe dans la fenêtre du précédent est
     * tu, et son dénivelé **reporté** sur le suivant. Le perdre ferait mentir
     * la carte sur sa hauteur cumulée : on aurait monté trois marches et n'en
     * redescendrait qu'une. On perd du détail, on garde le rythme et la
     * cohérence des volumes. Sur un bon moteur, la fenêtre est plus courte
     * que le trajet et rien ne change.
     */
    private fun emettre(franchi: Float) {
        val maintenant = SystemClock.uptimeMillis()
        val total = report + franchi
        if (maintenant - dernierCran < KeyFeedback.dureeDuCran(context)) {
            report = total
            return
        }
        report = 0f
        dernierCran = maintenant
        // Un report qui annule ce cran-ci : il reste qu'on a franchi quelque
        // chose, et ce cran-là le dit mieux que rien.
        KeyFeedback.onCardRidge(this, if (abs(total) < Ornement.DENIVELE_MIN) franchi else total)
    }

    private fun lacher() {
        relief = Ornement.Relief.LISSE
        report = 0f
        if (Pochette.animationsReduites(context)) return
        // Le dépassement est tout l'intérêt : le carton remonte, passe son
        // aplomb et revient. C'est la seule chose de la liste qui se lise
        // comme de la masse plutôt que comme une animation.
        animerAppui(0f, REBOND, OvershootInterpolator(2.2f))
        animerMain(0f, RETOUR)
    }

    private fun animerMain(vers: Float, duree: Long) {
        fonduMain?.cancel()
        fonduMain = ValueAnimator.ofFloat(main, vers).apply {
            duration = duree
            addUpdateListener {
                main = it.animatedValue as Float
                this@Carton.invalidate()
            }
            start()
        }
    }

    /**
     * Quel roulis pose la tache de lumière exactement sous [x].
     *
     * L'inverse de ce que fait [Ornement.refletBalaye], à mi-hauteur — le
     * balayage étant diagonal, la tache dérive un peu vers les bords haut et
     * bas, et c'est très bien ainsi : une lumière qui collerait au doigt au
     * pixel près serait un curseur, pas un reflet.
     */
    private fun lumiereEn(x: Float): Float =
        ((x / width) - 0.5f).div(Ornement.ETALEMENT).coerceIn(-1f, 1f)

    private fun animerAppui(vers: Float, duree: Long, courbe: TimeInterpolator) {
        fonduAppui?.cancel()
        fonduAppui = ValueAnimator.ofFloat(profondeur, vers).apply {
            duration = duree
            interpolator = courbe
            addUpdateListener {
                profondeur = it.animatedValue as Float
                Inclinaison.appui(this@Carton, appuiX * profondeur, appuiY * profondeur)
                val echelle = 1f - CREUX * profondeur
                this@Carton.scaleX = echelle
                this@Carton.scaleY = echelle
                // La tranche dépend de l'assiette, qui vient de bouger.
                this@Carton.invalidate()
            }
            start()
        }
    }

    private companion object {
        /** Le lissage du roulis du capteur, et le seuil sous lequel on l'ignore. */
        const val LISSAGE = 0.20f
        // Sur le roulis déjà lissé, qui avance d'un cinquième de l'écart par
        // échantillon : 0,04 sur la cible d'avant vaut 0,008 ici.
        const val SEUIL = 0.008f

        /** L'enfoncement maximum, en degrés, au bord du carton. */
        const val APPUI = 2.6f

        /** Ce que le carton perd en taille quand on appuie dessus. */
        const val CREUX = 0.015f

        const val ENFONCEMENT = 90L
        const val REBOND = 300L

        /** Le temps que met la pesanteur à reprendre la lumière au doigt. */
        const val RETOUR = 700L

        /** De combien le doigt doit avancer avant qu'on regarde le relief. */
        const val PAS_MIN = 2f
    }
}

/**
 * Le recto : le cadre orné, l'illustration du mot, et le texte de la carte.
 *
 * C'est la face que le joueur a gagnée et que le carnet conserve. Tout ce qui
 * relève de la géométrie du carton est dans [Carton] ; ce qui reste ici est le
 * tracé, l'échelle d'ornement, et le reflet des deux paliers hauts.
 */
class CarteOrnee(
    context: Context,
    private val mot: String,
    private val rarete: Rarete,
    private val vignette: Boolean,
    private val blason: Blasonnement = Blasonnement.AUCUN,
    private val jeu: JeuCarte? = null,
    /** De 0 (mot fréquent) à 1 (mot rare) : voir [Rarete.intensitePourRang]. */
    private val intensite: Float = 0.5f
) : Carton(context) {

    override val hauteurUnites: Float =
        if (vignette) Ornement.HAUTEUR_VIGNETTE else Ornement.HAUTEUR

    /**
     * Toute carte ouverte suit la pesanteur, et plus seulement les deux
     * paliers hauts.
     *
     * Le reflet, lui, reste un privilège de rareté ; la tranche n'en est pas
     * un. Une commune qui resterait plate pendant qu'une rare prend de
     * l'épaisseur ne se lirait pas comme moins précieuse, mais comme moins
     * réelle — et c'est précisément la distinction qu'[Inclinaison] refuse de
     * faire depuis qu'elle existe.
     */
    override val suitLaLumiere: Boolean get() = !vignette

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val teinte: Float
    private var boite = 0

    /**
     * Tout ce qui se calcule une fois pour la vie de la vue.
     *
     * La carte est tracée en **unités de carte**, jamais en pixels : les
     * dégradés, l'ouverture et le motif ne dépendent donc pas de la taille à
     * l'écran, et il n'y a aucune raison de les refabriquer à chaque trame.
     * C'est la même discipline que l'ancienne illustration s'imposait dans
     * `onSizeChanged`, rendue simplement inutile par le changement d'unité.
     */
    private val fenetre = if (vignette) Ornement.FENETRE_VIGNETTE else Ornement.FENETRE
    private val decoupe = Ornement.cheminFenetre(fenetre, rarete.ordinal >= 2)
    private val degradeFace: android.graphics.LinearGradient
    private val degradeGemme: android.graphics.RadialGradient
    private val degradeAgrafe: android.graphics.RadialGradient
    private val motif: Motif

    init {
        // La face, la gemme et la fenêtre prennent la teinte du champ quand le
        // mot en a un : c'est ce qui fait qu'un tiroir du carnet se lit comme
        // un rangement et non comme un nuancier. Sans champ, rien ne change.
        teinte = Ornement.teinteDe(mot, blason.champ)
        degradeFace = Ornement.degradeFace(teinte, rarete, vignette)
        degradeGemme = Ornement.degradeGemme(teinte, intensite = intensite)
        degradeAgrafe = Ornement.degradeGemme(
            teinte, Ornement.GEMME_CENTRE, Ornement.CREUX_AGRAFE, intensite
        )
        motif = Motif(mot, rarete, fenetre, blason)
        setWillNotDraw(false)
        clipChildren = false
    }

    /**
     * Le relief de la face, celui du cadre orné.
     *
     * Une vignette n'en a pas : elle ne reçoit jamais de doigt, et à 160 dp
     * ses arêtes seraient plus serrées que le seuil de perception.
     */
    override fun aretes(y: Float): Ornement.Relief =
        if (vignette) Ornement.Relief.LISSE else Ornement.aretes(rarete, bosses, y)

    /** Les rivets et les griffes : ceux que le doigt sent et ceux qu'il fait tourner. */
    private val bosses = if (vignette) FloatArray(0) else Ornement.bosses(rarete)

    // Tout ce que l'ombre et le pivot tracent à chaque trame est fabriqué ici,
    // une fois : des dégradés de rayon 1 qu'on place par leur matrice locale.
    private val ombre = RadialGradient(
        0f, 0f, RAYON_OMBRE, 0x38000000, 0x00000000, Shader.TileMode.CLAMP
    )
    private val eclat = RadialGradient(
        0f, 0f, 1f, 0x99FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP
    )
    private val sombre = RadialGradient(
        0f, 0f, 1f, 0x66000000, 0x00000000, Shader.TileMode.CLAMP
    )
    private val place = Matrix()
    private val ovale = RectF()

    /** La direction calculée par [orienter], sans allouer de paire. */
    private var dirX = Ornement.CUITE_X
    private var dirY = Ornement.CUITE_Y

    /**
     * Vers où tourne la lumière d'un point en relief en ([px], [py]), et avec
     * quelle force : rend l'influence du doigt, 0..1, et pose [dirX]/[dirY].
     *
     * ## Le cratère
     *
     * Si la lumière passe sous l'horizon d'un point, le préjugé « la lumière
     * vient d'en haut » retourne la bosse en trou (la photo de cratère qui
     * devient un dôme). Il est plus fort que la logique, et le doigt dirait
     * « bosse » au même instant : la direction est donc bridée à l'hémisphère
     * haut **avant** d'être renormalisée (y croît vers le bas), quelle que
     * soit la place du pouce.
     *
     * ## La portée
     *
     * Seuls les points proches du doigt tournent. Sans cette atténuation la
     * carte entière pivote d'un bloc, ce qui se relit aussitôt comme une image
     * qu'on fait glisser.
     */
    private fun orienter(px: Float, py: Float): Float {
        val dx = doigtX - px
        val dy = doigtY - py
        val distance = sqrt(dx * dx + dy * dy)
        val infl = (1f - distance / PORTEE).coerceIn(0f, 1f) * emprise
        var nx = Ornement.CUITE_X
        var ny = Ornement.CUITE_Y
        if (distance > 0.5f) {
            nx = dx / distance
            ny = min(dy / distance, BRIDE)
            val n = sqrt(nx * nx + ny * ny)
            nx /= n
            ny /= n
        }
        val mx = Ornement.CUITE_X + (nx - Ornement.CUITE_X) * infl
        val my = Ornement.CUITE_Y + (ny - Ornement.CUITE_Y) * infl
        // Les deux vecteurs sont dans l'hémisphère haut : leur mélange ne
        // s'annule jamais, my reste sous -0,25.
        val m = sqrt(mx * mx + my * my)
        dirX = mx / m
        dirY = my / m
        return infl
    }

    /**
     * Les points en relief tournent vers le doigt.
     *
     * Ils lisent [bosses], la liste que [Ornement.cote] lit aussi pour le
     * vibreur : une seule source, et c'est la condition de l'effet. Ce qui fait
     * l'objet est la congruence entre ce qu'on sent et ce qu'on voit, pas la
     * qualité de l'un des deux ; qu'ils divergent et l'illusion meurt.
     *
     * ## 0,30 contre 0,55
     *
     * C'est le cœur de l'effet, ne pas les égaliser. Sur une sphère, le point
     * brillant suit le vecteur médian entre la lumière et l'œil : il se déplace
     * de moitié, quand la frontière d'ombre suit la lumière en entier. S'ils
     * glissent ensemble, le cerveau lit une texture qui coulisse ; désolidarisés,
     * il lit une surface courbe et vernie.
     *
     * Rien n'est tracé quand le doigt n'a pas d'influence : la lumière du
     * repos est déjà cuite dans le bitmap du métal, et une carte non touchée
     * doit rester exactement celle d'avant. Ce qui est tracé ici passe par
     * dessus ce bitmap et n'y entre jamais : il est partagé par trente cartes.
     */
    private fun pivoterLesBosses(c: Canvas, p: Paint) {
        var i = 0
        while (i < bosses.size) {
            val cx = bosses[i]
            val cy = bosses[i + 1]
            val rx = bosses[i + 2]
            val ry = bosses[i + 3]
            i += 5
            val infl = orienter(cx, cy)
            if (infl < 0.01f) continue
            // Ici la multiplication d'un dégradé par l'alpha du pinceau est
            // voulue : c'est le fondu, porté par `emprise` au travers d'infl.
            val alpha = (infl * 255f).toInt()
            ovale.set(cx - rx, cy - ry, cx + rx, cy + ry)
            p.style = Paint.Style.FILL
            val ox = dirX * rx * DECALAGE_OMBRE
            val oy = dirY * ry * DECALAGE_OMBRE
            eclairer(c, p, eclat, cx + ox, cy + oy, rx, ry, alpha)
            eclairer(c, p, sombre, cx - ox, cy - oy, rx, ry, alpha)
            p.color = Color.WHITE
            p.alpha = alpha
            c.drawCircle(
                cx + dirX * rx * DECALAGE_BRILLANT, cy + dirY * ry * DECALAGE_BRILLANT,
                min(rx, ry) * 0.22f, p
            )
        }
        p.shader = null
        p.color = Color.BLACK
    }

    private fun eclairer(
        c: Canvas, p: Paint, degrade: RadialGradient, x: Float, y: Float,
        rx: Float, ry: Float, alpha: Int
    ) {
        place.setScale(rx, ry)
        place.postTranslate(x, y)
        degrade.setLocalMatrix(place)
        p.color = Color.BLACK
        p.alpha = alpha
        p.shader = degrade
        c.drawOval(ovale, p)
        p.shader = null
    }

    /**
     * L'ombre de contact : un disque sombre et flou sous la pulpe.
     *
     * Le doigt **ajoutait** de la lumière, ce qui est l'inverse de la
     * physique, et c'est pourquoi il pouvait se lire comme une lueur plutôt
     * que comme un contact : un vrai doigt bouche la lumière. Elle apparaît et
     * s'efface avec [emprise], sans fondu à elle.
     *
     * Elle passe **sous** le texte : `onDraw` précède les enfants. Par-dessus
     * serait plus juste, et coûterait de la lisibilité au mot, qui est le sujet.
     */
    private fun ombreDeContact(c: Canvas, p: Paint) {
        val e = emprise
        if (e < 0.01f) return
        c.save()
        c.translate(doigtX, doigtY)
        p.style = Paint.Style.FILL
        p.color = Color.BLACK
        p.alpha = (e * 255f).toInt()
        p.shader = ombre
        c.drawCircle(0f, 0f, RAYON_OMBRE, p)
        p.shader = null
        p.color = Color.BLACK
        c.restore()
    }

    fun avecBoite(valeur: Int): CarteOrnee {
        boite = valeur
        return this
    }

    /**
     * L'ordre est celui d'une carte imprimée : la face, ce qui rayonne
     * derrière l'illustration, l'illustration, puis le métal par-dessus.
     *
     * Deux de ces quatre couches — les rayons et le métal — sont des bitmaps
     * partagés par toutes les cartes du même palier et de la même taille. Ce
     * qui reste à tracer réellement à chaque trame, c'est un rectangle
     * dégradé, le motif du mot, et une poignée de joyaux.
     */
    override fun onDraw(canvas: Canvas) {
        val u = width / Ornement.LARGEUR
        if (u <= 0f) return

        canvas.save()
        canvas.scale(u, u)
        Ornement.dessinerFace(canvas, pinceau, degradeFace, rarete, vignette)
        canvas.restore()

        Ornement.rayons(rarete, width, vignette)?.let { canvas.drawBitmap(it, 0f, 0f, null) }

        canvas.save()
        canvas.scale(u, u)
        motif.peindre(canvas, pinceau, roulis, decoupe)
        canvas.restore()

        Ornement.metal(rarete, width, vignette)?.let { canvas.drawBitmap(it, 0f, 0f, null) }

        canvas.save()
        canvas.scale(u, u)
        if (vignette) {
            Ornement.dessinerBoite(canvas, pinceau, boite)
        } else {
            val gemme = Ornement.GEMME
            orienter(gemme.centerX(), gemme.centerY())
            Ornement.dessinerGemme(
                canvas, pinceau, degradeGemme, intensite = intensite, lumX = dirX, lumY = dirY
            )
            val agrafe = Ornement.GEMME_CENTRE
            orienter(agrafe.centerX(), agrafe.centerY())
            Ornement.dessinerGemme(
                canvas, pinceau, degradeAgrafe, agrafe, Ornement.CREUX_AGRAFE,
                intensite, dirX, dirY
            )
            jeu?.let { Ornement.dessinerMedaillon(canvas, pinceau, it, Ornement.metal(rarete)) }
            pivoterLesBosses(canvas, pinceau)
        }
        Ornement.dessinerSemis(canvas, pinceau, mot, rarete, vignette)
        if (!vignette) ombreDeContact(canvas, pinceau)
        // La tranche par-dessus le métal : c'est le bord du carton, et le
        // cadre s'arrête dessus comme l'impression s'arrête sur la coupe.
        Ornement.dessinerTranche(canvas, pinceau, assiette, hauteurUnites)
        Ornement.dessinerReflet(canvas, pinceau, rarete, roulis, vignette)
        canvas.restore()
    }

    /*
     * Points de départ, à régler au pouce sur un appareil : aucun n'a encore
     * été vérifié ailleurs qu'à l'œil sur le papier.
     */
    private companion object {
        /** Le rayon de l'ombre sous la pulpe, en unités de carte (~5 mm). */
        const val RAYON_OMBRE = 26f

        /** Jusqu'où un point en relief sent le doigt, en unités de carte. */
        const val PORTEE = 70f

        /** La composante verticale la plus basse qu'on laisse à la lumière. */
        const val BRIDE = -0.25f

        /** Où tombent l'éclat et l'ombre d'un point, en rayons ; puis le brillant. */
        const val DECALAGE_OMBRE = 0.55f
        const val DECALAGE_BRILLANT = 0.30f
    }
}

/**
 * L'illustration d'une carte : la matière de son palier, le sujet de son mot.
 *
 * ## Pourquoi deux objets là où il n'y en avait qu'un
 *
 * L'ancien `Motif` peignait tout dans une seule méthode : le grain du bronze
 * et l'initiale du mot s'y suivaient à quelques lignes d'écart, et l'on ne
 * pouvait toucher à l'un sans relire l'autre. Or la fenêtre dit **deux**
 * choses, qui n'ont ni la même source ni la même durée de vie :
 *
 * - **ce que vaut la carte** — le palier, d'où viennent le grain, le halo et
 *   l'irisation, mesurés et défendus de longue date ;
 * - **quel mot elle porte** — le sujet, qui est la partie qu'on cherche encore.
 *
 * Elles sont désormais [Matiere] et [Sujet], et [Motif] n'est plus que leur
 * assemblage dans l'ordre d'une carte imprimée : la matière dessous, le sujet
 * au milieu, ce que la matière pose par-dessus. Essayer un autre sujet — un
 * meuble héraldique, un poinçon — ne demande donc plus d'ouvrir le grain.
 *
 * ## Ce que le sujet est devenu
 *
 * Il était un anneau par lettre, plus l'initiale en filigrane. Les anneaux
 * tiraient leur position du code des caractères : c'était un bruit stable et
 * unique par mot, mais un bruit — rien n'y disait le mot, et deux formes d'un
 * même lemme n'y avaient aucun air de famille. Le sujet est maintenant le
 * **tracé** du mot, lettre à lettre : voir [Trace].
 *
 * L'initiale disparaît sans être remplacée. Elle se justifiait par « la carte
 * dit son mot même en vignette » — mais [Ornement.PLAQUE] le porte déjà en
 * toutes lettres, à la vignette comme à la carte ouverte, et une lettre géante
 * derrière un mot lisible n'ajoutait qu'un doublon.
 */
class Motif(
    mot: String,
    rarete: Rarete,
    zone: RectF,
    blason: Blasonnement = Blasonnement.AUCUN
) {

    private val teinte = Ornement.teinteDe(mot, blason.champ)
    private val matiere = Matiere(rarete, zone, teinte)
    private val partition = Partition(blason.nature, zone)

    /**
     * Le meuble s'il y en a un, le tracé sinon — et jamais les deux.
     *
     * Le repli n'est pas un pis-aller : le tracé porte 97 % du carnet, et
     * c'est le meuble qui est l'exception. Un nom de meuble que
     * [Meubles] ne connaît pas retombe ici sans bruit, ce qui permet à
     * l'actif et à la bibliothèque d'avancer chacun à son rythme.
     */
    private val sujet: Sujet =
        blason.meuble?.let { Enluminure.pour(it, zone, rarete) } ?: Trace(mot, zone, teinte)

    /**
     * Peint le motif dans son ouverture.
     *
     * L'ordre dit d'où vient chaque chose : le palier pose sa matière, le sens
     * la divise, le mot s'inscrit dedans, et le palier reprend la main pour ce
     * qui doit briller par-dessus.
     *
     * `roulis` est l'inclinaison de l'appareil ramenée dans [-1, 1] : elle ne
     * fait tourner que deux matrices, ce qui rend le suivi du capteur
     * pratiquement gratuit. Sans découpe, le tracé et la brillance
     * déborderaient sur le cadre — et l'arche cesserait d'être une arche.
     */
    fun peindre(c: Canvas, p: Paint, roulis: Float, decoupe: Path) {
        c.save()
        c.clipPath(decoupe)
        matiere.dessous(c, p)
        partition.peindre(c, p)
        sujet.peindre(c)
        matiere.dessus(c, p, roulis)
        c.restore()
    }
}

/**
 * La partition : la division du champ, avant qu'on y pose quoi que ce soit.
 *
 * L'héraldique divise l'écu — plein, coupé, tranché — et c'est ici la seule
 * information de la fenêtre qui **ne coûte ni donnée ni dessin** : elle se lit
 * sur la forme du mot. En kréyòl elle vaut toujours « plein » : voir [Nature],
 * la forme ne porte aucun signal de nature.
 *
 * Elle rend un service qu'on n'attendait pas d'elle. Huit teintes sur une face
 * désaturée, ça se confond : deux verts voisins ne se distinguent pas à 160 dp,
 * et encore moins en deutéranopie. Une forme qui double la couleur rétablit la
 * lecture — c'est le raisonnement des insignes de rareté, qui comptent des
 * symboles au lieu de se fier au vert et au bleu-gris.
 *
 * Deux couches, et il faut les deux : un aplat à 26 % ne se voit pas sur une
 * face déjà claire, et c'est le filet qui donne la ligne de partage. Sans lui,
 * la partition ne servirait justement plus la lisibilité qui la justifie.
 */
private class Partition(nature: Nature, zone: RectF) {

    private val aplat = Path()
    private val ligne = Path()

    init {
        val h = zone.height()
        when (nature) {
            // Coupé : une bande en chef, la forme la plus franche, pour la
            // nature la plus nombreuse après les noms.
            Nature.VERBE -> {
                val y = zone.top + h * 0.40f
                aplat.addRect(zone.left, zone.top, zone.right, y, Path.Direction.CW)
                ligne.moveTo(zone.left, y)
                ligne.lineTo(zone.right, y)
            }
            // Tranché : une diagonale. Ce qui n'est ni nom ni verbe est
            // hétéroclite, et la diagonale est la division qui ne prétend rien.
            Nature.AUTRE -> {
                aplat.moveTo(zone.left, zone.bottom)
                aplat.lineTo(zone.right, zone.top)
                aplat.lineTo(zone.right, zone.bottom)
                aplat.close()
                ligne.moveTo(zone.left, zone.bottom)
                ligne.lineTo(zone.right, zone.top)
            }
            // Chevron : la division la plus stable, pour le substantif, qui
            // est aussi ce que le carnet porte le plus.
            Nature.NOM -> {
                val faite = zone.top + h * 0.14f
                aplat.moveTo(zone.left, zone.bottom)
                aplat.lineTo(zone.centerX(), faite)
                aplat.lineTo(zone.right, zone.bottom)
                aplat.close()
                ligne.moveTo(zone.left, zone.bottom)
                ligne.lineTo(zone.centerX(), faite)
                ligne.lineTo(zone.right, zone.bottom)
            }
        }
    }

    fun peindre(c: Canvas, p: Paint) {
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        p.alpha = 66
        c.drawPath(aplat, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.6f
        p.alpha = 107
        c.drawPath(ligne, p)

        p.alpha = 255
        p.style = Paint.Style.FILL
    }
}

/**
 * Ce que le **palier** met dans la fenêtre : commune mate, grain oblique,
 * halo, irisation.
 *
 * Le tracé n'a pas bougé d'un trait depuis qu'il a été mesuré ; il a seulement
 * changé de maison. Il se peint en deux temps parce que le sujet s'intercale :
 * [dessous] pose la face et sa texture, [dessus] pose ce qui doit passer
 * par-dessus le sujet — l'irisation d'une très rare et sa brillance.
 *
 * La teinte lui est donnée plutôt que calculée : c'est la même que celle de la
 * face et de la gemme, et une carte dont la fenêtre jurerait avec son carton
 * se lirait comme un défaut d'impression.
 */
private class Matiere(private val rarete: Rarete, private val zone: RectF, teinte: Float) {

    private val commun = rarete == Rarete.COMMUN
    private val fond: LinearGradient
    private val hachures = Path()
    private val halo: RadialGradient?
    private val iris: Bitmap?
    private val brillance: LinearGradient?
    private val matrice = Matrix()

    init {
        fun couleur(s: Float, v: Float) = Color.HSVToColor(floatArrayOf(teinte, s, v))
        val w = zone.width()
        val h = zone.height()

        // Une commune reste dans un mouchoir de poche — deux valeurs proches,
        // peu de saturation ; les autres gardent le dégradé d'origine.
        fond = LinearGradient(
            zone.left, zone.top, zone.left + w * 0.35f, zone.bottom,
            if (commun) couleur(0.16f, 0.93f) else couleur(0.30f, 0.96f),
            if (commun) couleur(0.24f, 0.85f) else couleur(0.55f, 0.72f),
            Shader.TileMode.CLAMP
        )

        // Le grain d'une peu commune : des obliques régulières d'un bord à
        // l'autre, le papier, pas un motif qu'on cherche à lire.
        if (rarete == Rarete.PEU_COMMUN) {
            var x = -h
            while (x < w) {
                hachures.moveTo(zone.left + x, zone.bottom)
                hachures.lineTo(zone.left + x + h, zone.top)
                x += 9f
            }
        }

        halo = if (rarete == Rarete.RARE) RadialGradient(
            zone.centerX(), zone.centerY(), h * 0.72f,
            intArrayOf(
                Color.argb(120, 255, 255, 255),
                Color.argb(40, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        ) else null

        if (rarete == Rarete.TRES_RARE) {
            // Un tour complet du cercle depuis la teinte du mot, saturation
            // basse, sinon l'arc-en-ciel mange le mot. Calculé pixel par pixel
            // et non en SweepGradient : sa couture laissait une rangée de
            // pixels corrompus, un trait jaune du centre vers le bord.
            val cote = 96
            val demi = cote / 2f
            val pixels = IntArray(cote * cote)
            val hsv = floatArrayOf(0f, 0.45f, 1f)
            for (y in 0 until cote) {
                for (x in 0 until cote) {
                    val angle = Math.atan2((y + 0.5f - demi).toDouble(), (x + 0.5f - demi).toDouble())
                    val tour = ((angle / (2.0 * Math.PI)) + 1.0) % 1.0
                    hsv[0] = ((teinte + 360f * tour.toFloat()) % 360f + 360f) % 360f
                    pixels[y * cote + x] = Color.HSVToColor(hsv)
                }
            }
            iris = Bitmap.createBitmap(pixels, cote, cote, Bitmap.Config.ARGB_8888)
            brillance = LinearGradient(
                zone.left, zone.bottom, zone.right, zone.top,
                intArrayOf(Color.TRANSPARENT, Color.argb(96, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0.30f, 0.50f, 0.70f), Shader.TileMode.CLAMP
            )
        } else {
            iris = null
            brillance = null
        }
    }

    /** La face de la fenêtre et sa texture, sous le sujet. */
    fun dessous(c: Canvas, p: Paint) {
        p.style = Paint.Style.FILL
        p.shader = fond
        c.drawRect(zone, p)
        p.shader = null

        if (rarete == Rarete.PEU_COMMUN) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1f
            p.color = Color.WHITE
            p.alpha = 22
            c.drawPath(hachures, p)
            p.alpha = 255
        }

        // Le halo d'une rare, posé avant le sujet pour rester derrière lui.
        halo?.let {
            p.style = Paint.Style.FILL
            p.shader = it
            c.drawRect(zone, p)
            p.shader = null
        }
        p.style = Paint.Style.FILL
    }

    /** Ce qui glisse par-dessus le sujet quand l'appareil tourne. */
    fun dessus(c: Canvas, p: Paint, roulis: Float) {
        iris?.let {
            val rayon = Math.hypot(zone.width() / 2.0, zone.height() / 2.0).toFloat()
            c.save()
            c.clipRect(zone)
            c.rotate(roulis * 55f, zone.centerX(), zone.centerY())
            p.shader = null
            p.alpha = 52
            p.isFilterBitmap = true
            c.drawBitmap(
                it, null,
                RectF(zone.centerX() - rayon, zone.centerY() - rayon, zone.centerX() + rayon, zone.centerY() + rayon),
                p
            )
            p.isFilterBitmap = false
            p.alpha = 255
            c.restore()
        }
        brillance?.let {
            matrice.setTranslate(roulis * zone.width() * 0.45f, 0f)
            it.setLocalMatrix(matrice)
            p.shader = it
            c.drawRect(zone, p)
            p.shader = null
        }
    }
}

/**
 * Ce que le **mot** met dans la fenêtre.
 *
 * Une seule implémentation aujourd'hui, [Trace], et c'est tout l'intérêt de
 * l'interface : la question « quelle image pour quel mot » n'est pas tranchée,
 * et le jour où elle le sera, c'est ici que la réponse se branchera — sans
 * qu'un seul trait de la matière change.
 */
private interface Sujet {
    fun peindre(c: Canvas)
}

/**
 * L'enluminure : le meuble du mot, gravé dans la matière.
 *
 * C'est le sujet des cartes qu'on collectionne pour elles-mêmes — cent vingt
 * mots sur deux mille huit cents, à peu près quatre sur cent. La rareté
 * n'y est pour rien : elle est fixée par le rang de fréquence et n'est pas
 * négociable, alors que l'enluminure est un second axe de désirabilité, qui
 * peut échoir à une commune comme à une très rare.
 *
 * La gravure est celle du reste du carnet — une ombre décalée vers le bas à
 * droite, puis la matière claire — parce que la lumière du carnet vient d'en
 * haut à gauche depuis les volutes. Un meuble posé à plat aurait l'air collé.
 *
 * Le plein cintre des deux paliers hauts mange le haut de la fenêtre : le
 * meuble y rentre d'un cran et descend, au lieu d'être rogné par la découpe.
 */
private class Enluminure private constructor(chemin: Path, matrice: Matrix) : Sujet {

    private val plume = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /**
     * Le meuble déjà mis à l'échelle et posé : la carte se trace en unités de
     * carte, donc la transformation n'a aucune raison d'être refaite à chaque
     * trame — et le chemin de [Meubles] reste intact pour les autres cartes.
     */
    private val trace = Path().also {
        chemin.transform(matrice, it)
        // `transform` recopie la règle de remplissage, mais on ne la laisse pas
        // à la charge d'un détail d'implémentation : sans « pair-impair », la
        // porte d'une maison cesse d'être un trou et la silhouette se bouche.
        it.fillType = Path.FillType.EVEN_ODD
    }

    override fun peindre(c: Canvas) {
        c.save()
        c.translate(DECALAGE_X, DECALAGE_Y)
        plume.color = OMBRE
        c.drawPath(trace, plume)
        c.restore()
        plume.color = TRAIT
        c.drawPath(trace, plume)
    }

    companion object {
        private val OMBRE = Color.argb(77, 0, 0, 0)
        private val TRAIT = Color.argb(235, 255, 255, 255)
        private const val DECALAGE_X = 2.2f
        private const val DECALAGE_Y = 2.8f

        /** `null` si la bibliothèque ne connaît pas ce meuble. */
        fun pour(nom: String, zone: RectF, rarete: Rarete): Enluminure? {
            val forme = Meubles.chemin(nom) ?: return null
            val arche = rarete.ordinal >= 2
            val taille = zone.height() * (if (arche) 0.60f else 0.72f)
            val m = Matrix()
            m.setScale(taille / Meubles.COTE, taille / Meubles.COTE)
            m.postTranslate(
                zone.centerX(),
                zone.centerY() + zone.height() * (if (arche) 0.09f else 0.02f)
            )
            return Enluminure(forme, m)
        }
    }
}

/**
 * Le tracé du mot : une signature gravée, lue lettre à lettre.
 *
 * ## La règle
 *
 * Voyelle en haut, consonne en bas, la hauteur affinée par le code du
 * caractère ; un nœud sur chaque voyelle, qui donne la scansion. Le pas est
 * **constant et calé à gauche**, jamais étiré sur la largeur : c'est ce détail
 * qui fait tout le travail, parce que deux formes qui partagent leur début
 * partagent alors leurs points *exactement*, au lieu de seulement se
 * ressembler. Dans une grille, `Woch`, `Wochen` et `Woche` se lisent enfin
 * comme un seul mot à trois états.
 *
 * La teinte va dans le même sens : [Ornement.teinteDe] la lit sur les trois
 * premières lettres, si bien que les formes d'une même famille tombent sur la
 * même couleur **sans qu'on ait eu à consulter le moindre lemme**. Le prix est
 * l'homonymie de préfixe, et il est modeste : une teinte n'identifie rien,
 * elle rapproche.
 *
 * ## Ce que ça coûte
 *
 * Rien. Pas un octet d'actif, pas une ligne de données, aucune couverture à
 * atteindre — le tracé vaut pour toute forme du carnet, y compris pour les
 * formes que le corpus de fréquences ne connaît même pas. Ce
 * qu'il ne fait pas, il faut le dire aussi : il montre **le mot**, pas **la
 * chose**. C'est un monogramme, pas une illustration.
 *
 * Comme la carte se trace en unités de carte, le chemin et les nœuds sont
 * construits **une fois**, ici, et [peindre] n'alloue rien.
 */
private class Trace(mot: String, zone: RectF, teinte: Float) : Sujet {

    private val chemin = Path()
    private val noeudsX: FloatArray
    private val noeudsY: FloatArray
    private val rayon: Float
    private val coeur: Float
    private val couleurCoeur: Int
    private val plume = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        val lettres = mot.toCharArray()
        val n = lettres.size
        val w = zone.width()
        val h = zone.height()

        // Un mot de sept lettres occupe toute la largeur utile ; un plus court
        // s'arrête avant, un plus long resserre son pas. Le pas ne dépend donc
        // jamais de la longueur du mot voisin, ce qui est la condition pour
        // que deux préfixes identiques se superposent.
        val pas = w * 0.76f / (maxOf(n, 7) - 1).toFloat()
        val x0 = zone.left + w * 0.12f
        val cy = zone.centerY()
        val amp = h * 0.30f

        val xs = FloatArray(n)
        val ys = FloatArray(n)
        var voyelles = 0
        for (i in 0 until n) {
            val ch = lettres[i]
            val estVoyelle = ch.lowercaseChar() in VOYELLES
            if (estVoyelle) voyelles++
            // Un mot d'une seule lettre n'a pas de tracé : on le centre.
            xs[i] = if (n == 1) zone.centerX() else x0 + pas * i
            ys[i] = cy + (if (estVoyelle) -1f else 1f) *
                amp * (0.42f + 0.58f * ((ch.code % 7) / 6f))
        }

        // Deux quadratiques par segment, par le milieu : la courbe passe par
        // chaque lettre sans le dépassement qu'une seule donnerait.
        if (n >= 2) {
            chemin.moveTo(xs[0], ys[0])
            for (i in 0 until n - 1) {
                val ax = xs[i]; val ay = ys[i]
                val bx = xs[i + 1]; val by = ys[i + 1]
                chemin.quadTo(ax + (bx - ax) * 0.55f, ay, (ax + bx) / 2f, (ay + by) / 2f)
                chemin.quadTo(bx - (bx - ax) * 0.55f, by, bx, by)
            }
        }

        noeudsX = FloatArray(voyelles)
        noeudsY = FloatArray(voyelles)
        var k = 0
        for (i in 0 until n) {
            if (lettres[i].lowercaseChar() in VOYELLES) {
                noeudsX[k] = xs[i]; noeudsY[k] = ys[i]; k++
            }
        }

        // Un mot long doit maigrir, sinon ses nœuds se recouvrent : à seize
        // lettres, un pas fait onze unités et un nœud d'origine en ferait dix
        // de rayon. Sept lettres est la longueur de référence.
        val maigreur = (7f / maxOf(n, 7)).coerceAtMost(1f)
        val epaisseur = h * 0.055f * maigreur
        rayon = h * 0.055f * maigreur
        coeur = h * 0.024f * maigreur
        couleurCoeur = Color.HSVToColor(floatArrayOf(teinte, 0.55f, 0.62f))

        plume.strokeWidth = epaisseur
        plume.strokeCap = Paint.Cap.ROUND
        plume.strokeJoin = Paint.Join.ROUND
    }

    /**
     * Le tracé se pose deux fois : une ombre décalée, puis le trait clair.
     *
     * C'est la gravure du reste du carnet — la lumière vient d'en haut à
     * gauche, comme pour les volutes et les écus — et c'est ce qui empêche un
     * trait blanc de disparaître sur la face claire d'une commune.
     */
    override fun peindre(c: Canvas) {
        plume.style = Paint.Style.STROKE
        c.save()
        c.translate(1.4f, 1.8f)
        plume.color = OMBRE
        c.drawPath(chemin, plume)
        c.restore()
        plume.color = TRAIT
        c.drawPath(chemin, plume)

        plume.style = Paint.Style.FILL
        for (i in noeudsX.indices) {
            plume.color = OMBRE
            c.drawCircle(noeudsX[i] + 1.4f, noeudsY[i] + 1.8f, rayon, plume)
            plume.color = TRAIT
            c.drawCircle(noeudsX[i], noeudsY[i], rayon, plume)
            plume.color = couleurCoeur
            c.drawCircle(noeudsX[i], noeudsY[i], coeur, plume)
        }
    }

    private companion object {
        /**
         * Les voyelles, diacritiques compris.
         *
         * Les accents du kréyòl s'ajoutent à ceux du luxembourgeois : `ò` est
         * la voyelle accentuée la plus courante de la langue (`kréyòl`,
         * `mòso`), et sans elle le tracé la prendrait pour une consonne — le
         * monogramme de `kréyòl` s'en trouverait faux.
         */
        const val VOYELLES = "aeiouyàáâäèéêëìíîïòóôöùúûü"
        val OMBRE = Color.argb(72, 0, 0, 0)
        val TRAIT = Color.argb(235, 255, 255, 255)
    }
}
