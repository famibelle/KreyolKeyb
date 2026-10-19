package com.example.kreyolkeyboard.carnet

import com.example.kreyolkeyboard.R
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
import kotlin.math.ceil
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
     * Les quatre rivets de la plaque, dans ses angles : une plaque rivetée,
     * pas une étiquette collée (demande du propriétaire, 2026-09-19).
     *
     * **Ils montent avec la rareté**, comme la matière de la plaque : plus
     * gros ([RIVET_PLAQUE_R]), plus saillants au doigt ([SAILLIE_RIVET]), et
     * posés sur une rondelle à partir de *Rare*. Quatre cartes côte à côte,
     * et les têtes de clou d'une commune deviennent les rivets bombés d'une
     * très rare ; les yeux fermés, la marche qu'on franchit s'allonge.
     *
     * Ils tiennent dans les deux bandes que le mot laisse libres au-dessus et
     * au-dessous de lui : le nom prend toute la largeur de la plaque, mais sa
     * hauteur d'encre reste sous 20 unités même dans la police de *Rare*, soit
     * entre 196 et 216. Le plus gros rivet, rondelle comprise, s'arrête à
     * 195,5 et reprend à 216,5 : plus gros encore, ou plus près du centre, il
     * mordrait sur les lettres d'un mot long.
     */
    const val RIVET_PLAQUE_DX = 9f
    const val RIVET_PLAQUE_DY = 7f
    val RIVET_PLAQUE_R = floatArrayOf(2.6f, 3f, 3.3f, 3.6f)
    /** La rondelle, en rayon de tête : aucune avant *Rare*. */
    private const val RONDELLE = 1.3f
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
    // Large depuis 37.4.8 : elle porte le palier avant le rang (« ✦ Très rare ·
    // 9769ᵉ »). Elle commence après le numéro et le poinçon (x = 64 à 74).
    val SERIE_D = RectF(96f, 428f, 278f, 437.5f)

    /**
     * Le milieu de la bordure basse visible : entre le bas de la face
     * (`haut - bord`) et le bord intérieur du filet de contour (`haut - 2,45`).
     *
     * La bordure s'épaissit avec le palier, de 12 à 18 unités, et la bande
     * ci-dessus, calée une fois pour toutes, tombait au milieu de celle d'une
     * commune seulement : sur une très rare, la ligne de série était trois
     * unités trop bas (propriétaire, 2026-10-10). La ligne et la plume se
     * centrent donc sur la bordure de leur propre palier.
     */
    fun milieuBordureBasse(rarete: Rarete, vignette: Boolean): Float {
        val haut = if (vignette) HAUTEUR_VIGNETTE else HAUTEUR
        val bord = 12f + rarete.ordinal * 2f
        return haut - (bord + 2.45f) / 2f
    }

    /** [SERIE_G], recentrée verticalement sur la bordure du palier. */
    fun serieG(rarete: Rarete): RectF = recentree(SERIE_G, milieuBordureBasse(rarete, false))

    /** [SERIE_D], recentrée verticalement sur la bordure du palier. */
    fun serieD(rarete: Rarete): RectF = recentree(SERIE_D, milieuBordureBasse(rarete, false))

    private fun recentree(r: RectF, milieu: Float): RectF {
        val demi = r.height() / 2f
        return RectF(r.left, milieu - demi, r.right, milieu + demi)
    }
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
     * Une barre de fer forgé : le ruban [chemin], d'épaisseur constante
     * [trait], en relief dans le métal [m], avec son éventuelle pièce pleine
     * [plein]. Rend le dégradé, pour que les pièces rapportées (feuille,
     * collier) prennent la même lumière.
     *
     * Le dessin se fait à l'écran et la lumière vient d'en haut, entre
     * [haut] et [bas] : c'est celle du cadre et des rivets. L'ombre portée
     * d'abord, comme la plaque : sans elle le métal se lirait peint sur la
     * face, pas posé dessus. Puis le contour, que le métal recouvre en
     * laissant déborder sa moitié de chaque côté, et un filet de reflet.
     */
    private fun barre(
        c: Canvas, p: Paint, chemin: Path, trait: Float, m: Metal,
        haut: Float, bas: Float, plein: Path? = null
    ): Shader {
        val lumiere = LinearGradient(
            0f, haut, 0f, bas, intArrayOf(m.hi, m.mid, m.lo),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        c.save()
        c.translate(0.8f, 1.2f)
        p.shader = null
        p.color = 0x38000000
        if (plein != null) {
            p.style = Paint.Style.FILL
            c.drawPath(plein, p)
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = trait + 0.6f
        c.drawPath(chemin, p)
        c.restore()

        p.color = m.trait
        p.style = Paint.Style.STROKE
        p.strokeWidth = trait + 1.4f
        c.drawPath(chemin, p)
        if (plein != null) {
            p.strokeWidth = 1.2f
            c.drawPath(plein, p)
        }
        p.color = Color.BLACK
        p.shader = lumiere
        if (plein != null) {
            p.style = Paint.Style.FILL
            c.drawPath(plein, p)
            p.style = Paint.Style.STROKE
        }
        p.strokeWidth = trait
        c.drawPath(chemin, p)
        p.shader = null

        c.save()
        c.translate(-0.25f, -0.45f)
        p.color = 0x8CFFFFFF.toInt()
        p.strokeWidth = trait * 0.3f
        c.drawPath(chemin, p)
        c.restore()
        p.strokeCap = Paint.Cap.BUTT
        p.strokeJoin = Paint.Join.MITER
        p.style = Paint.Style.FILL
        return lumiere
    }

    /**
     * Une spirale ajoutée à [chemin], de l'angle [de] à l'angle [a] (en
     * degrés, sens de l'écran), le rayon passant de [r0] à [r1].
     *
     * Échantillonnée tous les dix degrés : un arc de cercle ne sait pas
     * resserrer, et c'est le resserrement qui fait la volute.
     */
    private fun spirale(
        chemin: Path, cx: Float, cy: Float, r0: Float, r1: Float, de: Float, a: Float
    ) {
        val n = max(2, (abs(a - de) / 10f).toInt())
        for (i in 0..n) {
            val t = i / n.toFloat()
            val ang = Math.toRadians((de + (a - de) * t).toDouble())
            val r = r0 + (r1 - r0) * t
            chemin.lineTo(cx + r * cos(ang).toFloat(), cy + r * sin(ang).toFloat())
        }
    }

    /**
     * La ferronnerie de l'écoinçon haut droit, sur les cartes à arche.
     *
     * Le vocabulaire est celui du fer forgé (référence donnée par le
     * propriétaire, un panneau de volutes de décoferforge) : une barre
     * d'épaisseur constante, des spirales qui se resserrent jusqu'à leur
     * œil, des crosses secondaires qui naissent de la principale, et des
     * **colliers** qui lient deux barres là où elles se touchent. C'est le
     * collier qui fait « forgé » plutôt que « dessiné » : il dit que les
     * pièces sont assemblées.
     *
     * - *Rare* : la tige principale, qui monte le long du cadre depuis une
     *   petite crosse et s'enroule en une grande volute ; une crosse d'angle
     *   liée à elle par un collier.
     * - *Très rare* : en plus, une branche qui suit l'arche vers la clef de
     *   voûte, un second collier, et des perles à l'œil des volutes.
     *
     * Toutes les cotes sont en unités de carte et tiennent entre l'arche,
     * son jonc compris, et le cadre des deux paliers (bord 16 et 18) : la
     * crosse du bas passe à 1 unité du jonc à hauteur 92, c'est la plus
     * serrée. Qui déplace l'arche doit revoir ces nombres.
     */
    private fun ferronnerie(c: Canvas, p: Paint, palier: Int, m: Metal) {
        val trait = if (palier >= 3) 2.6f else 2.4f

        // La tige : la crosse du bas, contre le cadre, puis la montée le long
        // du bord droit, puis la grande volute qui s'enroule sous l'angle.
        val tige = Path()
        tige.moveTo(277f + 1f, 93f)
        spirale(tige, 277f, 93f, 1f, 3.5f, -360f, 180f)
        tige.cubicTo(273.5f, 80f, 261f, 64f, 265f, 44f)
        spirale(tige, 250f, 44f, 15f, 2.5f, 0f, -540f)

        // La crosse d'angle, qui naît de la volute et part vers le coin.
        val crosse = Path()
        crosse.moveTo(262f, 35f)
        crosse.cubicTo(266f, 32f, 270f, 30f, 270f, 26f)
        spirale(crosse, 275f, 26f, 5f, 1.2f, 180f, 630f)

        val barres = Path()
        barres.addPath(tige)
        barres.addPath(crosse)

        // La branche de l'arche : de la volute vers la clef de voûte, où elle
        // s'enroule à son tour.
        if (palier >= 3) {
            val branche = Path()
            branche.moveTo(203f, 29f)
            spirale(branche, 203f, 30f, 1f, 5.5f, 630f, 90f)
            branche.cubicTo(220f, 37f, 234f, 34f, 243.7f, 30.4f)
            barres.addPath(branche)
        }

        val lumiere = barre(c, p, barres, trait, m, 18f, 100f)

        // Les colliers : une bague de métal en travers des deux barres.
        collier(c, p, 262f, 35f, -53f, trait, m, lumiere)
        if (palier >= 3) collier(c, p, 243.7f, 30.4f, -20f, trait, m, lumiere)

        // L'œil des volutes, à Très rare : une perle du même métal.
        if (palier >= 3) {
            val perle = trait * 0.8f
            joyau(c, p, 250f, 44f, perle, m.hi, 0)
            joyau(c, p, 275f, 26f, perle * 0.8f, m.hi, 0)
            joyau(c, p, 203f, 30f, perle * 0.8f, m.hi, 0)
        }
    }

    /** Un collier de fer forgé, centré en ([x], [y]), en travers d'une barre orientée à [angle]. */
    private fun collier(
        c: Canvas, p: Paint, x: Float, y: Float, angle: Float, trait: Float, m: Metal,
        lumiere: Shader
    ) {
        val bague = RectF(-1.1f, -trait * 1.25f, 1.1f, trait * 1.25f)
        c.save()
        c.translate(x, y)
        c.rotate(angle)
        p.style = Paint.Style.FILL
        p.color = Color.BLACK
        p.shader = lumiere
        c.drawRoundRect(bague, 0.5f, 0.5f, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.8f
        p.color = m.trait
        c.drawRoundRect(bague, 0.5f, 0.5f, p)
        c.restore()
        p.style = Paint.Style.FILL
    }

    /**
     * Un écoinçon : le coin de métal qui tient l'angle de la face, et les
     * deux volutes qui en partent le long des bords.
     *
     * Tout est construit dans le repère de l'angle — `u` le long d'un bord,
     * `v` vers l'intérieur — puis ramené à l'écran par une matrice : la
     * seconde volute est la première réfléchie sur la diagonale, ce qui
     * garantit la symétrie au lieu de l'approcher. Le dessin, lui, se fait à
     * l'écran, pour que la lumière vienne d'en haut quel que soit l'angle :
     * un dégradé tracé dans le repère local s'inverserait dans les coins du
     * bas et creuserait ce qui doit être en relief.
     *
     * L'échelle de rareté ne multiplie pas les spirales, elle enrichit la
     * pièce : deux crosses nues à *Peu commun*, un œil perlé au bout de chacune
     * à *Rare*, une feuille d'acanthe sur la diagonale à *Très rare* — celle
     * des flancs, qui fait écho.
     */
    private fun ecoincon(
        c: Canvas, p: Paint, x: Float, y: Float, sx: Float, sy: Float,
        echelle: Float, palier: Int, m: Metal
    ) {
        val w = floatArrayOf(0f, 2.2f, 2.6f, 3f)[palier]
        val l = floatArrayOf(0f, 13f, 16f, 18f)[palier]
        val r = floatArrayOf(0f, 3.4f, 4f, 4.5f)[palier]
        val k = floatArrayOf(0f, 8f, 10f, 12f)[palier]
        val e = w / 2f + 0.6f

        val versEcran = Matrix()
        versEcran.setScale(sx * echelle, sy * echelle)
        versEcran.postTranslate(x, y)
        val diagonale = Matrix()
        diagonale.setValues(floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f))

        // Une crosse : le long du bord, puis une vraie spirale — deux
        // demi-tours de rayon décroissant, qui finissent en son centre. Une
        // boucle de rayon constant se lisait comme une lettre (« P », « b ») ;
        // c'est l'enroulement qui resserre qui fait la volute.
        val r2 = r * 0.5f
        val bras = Path()
        bras.moveTo(k * 0.55f, e)
        bras.lineTo(l, e)
        bras.arcTo(RectF(l - r, e, l + r, e + 2f * r), -90f, 180f)
        bras.arcTo(RectF(l - r2, e + 2f * r - 2f * r2, l + r2, e + 2f * r), 90f, 180f)
        val deux = Path(bras)
        deux.transform(diagonale)
        bras.addPath(deux)
        bras.transform(versEcran)

        // Le coin : un triangle aux côtés creusés, qui remplit l'arrondi de
        // la face et d'où les deux crosses semblent sortir.
        val ancre = Path()
        ancre.moveTo(0f, 0f)
        ancre.lineTo(k, 0f)
        ancre.quadTo(k * 0.28f, k * 0.28f, 0f, k)
        ancre.close()
        ancre.transform(versEcran)

        val trait = w * echelle
        val lumiere = barre(c, p, bras, trait, m, min(y, y + sy * (l + r) * echelle),
            max(y, y + sy * (l + r) * echelle), ancre)

        // L'œil de chaque volute, à Rare : une perle du même métal, au centre
        // de la spirale, là où elle finit. C'est le point le plus épais, pas
        // le plus fin.
        if (palier >= 2) {
            val bouts = floatArrayOf(l, e + r, e + r, l)
            versEcran.mapPoints(bouts)
            val perle = (w * 0.85f) * echelle
            joyau(c, p, bouts[0], bouts[1], perle, m.hi, 0)
            joyau(c, p, bouts[2], bouts[3], perle, m.hi, 0)
        }

        // La feuille d'acanthe sur la diagonale, à Très rare.
        if (palier >= 3) {
            val f = Path()
            val d = k * 0.5f
            val lf = 11f
            f.moveTo(d, d)
            f.quadTo(d + lf * 0.62f, d + lf * 0.08f, d + lf * 0.71f, d + lf * 0.71f)
            f.quadTo(d + lf * 0.08f, d + lf * 0.62f, d, d)
            f.close()
            f.transform(versEcran)
            p.style = Paint.Style.FILL
            p.color = Color.BLACK
            p.shader = lumiere
            c.drawPath(f, p)
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = 0.9f
            p.color = m.trait
            c.drawPath(f, p)
            // La nervure.
            val n = floatArrayOf(d, d, d + lf * 0.6f, d + lf * 0.6f)
            versEcran.mapPoints(n)
            p.strokeWidth = 0.6f
            c.drawLine(n[0], n[1], n[2], n[3], p)
        }
        p.style = Paint.Style.FILL
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

        // Les rivets, par-dessus le filet : c'est lui qui passe dessous, comme
        // la règle tracée avant que la plaque soit fixée. Ils sont du métal du
        // cadre et non de la matière de la plaque : une planche se cloue au
        // fer, pas au bois. Mêmes positions que [bosses], qui les fait sentir.
        val rayon = RIVET_PLAQUE_R[palier]
        for (x in floatArrayOf(r.left + RIVET_PLAQUE_DX, r.right - RIVET_PLAQUE_DX)) {
            for (y in floatArrayOf(r.top + RIVET_PLAQUE_DY, r.bottom - RIVET_PLAQUE_DY)) {
                // La rondelle : un anneau plat sous la tête, dans le sombre du
                // métal, qui sépare le rivet de la plaque au lieu de l'y planter.
                if (palier >= 2) {
                    p.style = Paint.Style.FILL
                    p.color = m.lo
                    c.drawCircle(x, y, rayon * RONDELLE, p)
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 0.7f
                    p.color = m.trait
                    c.drawCircle(x, y, rayon * RONDELLE, p)
                }
                joyau(c, p, x, y, rayon, m.hi, 0)
            }
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
     * partagent un seul bitmap.
     *
     * Il faisait deux mégaoctets, et la carte ouverte n'y entrait pas : 940 px
     * de large font un cadre de 5 Mo, que `LruCache` évince aussitôt posé. Le
     * métal se redessinait donc à chaque trame de l'inclinaison, sans que rien
     * ne le montre. Depuis que la moulure se calcule au pixel, ce n'est plus
     * tenable.
     *
     * Un huitième du tas, entre 4 et 32 Mo : le métal des quatre paliers de
     * vignettes, la carte ouverte et ses deux voisines préparées d'avance
     * (voir `LecteurCartes.preparerVoisines`), avec leurs rayons. Au seizième,
     * 12 Mo sur un Galaxy A21s, les voisines auraient évincé le métal des
     * vignettes, recalculé ensuite sur le fil principal au retour à la grille.
     */
    private val CACHE = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceIn(4L * 1024 * 1024, 32L * 1024 * 1024).toInt()
    ) {
        override fun sizeOf(cle: String, valeur: Bitmap): Int = valeur.byteCount
    }

    /**
     * Les deux écus portent un libellé (« VUES », « NIVEAU ») : il entre dans
     * la clef du cache, sinon un changement de langue garderait l'ancien.
     */
    fun metal(context: Context, rarete: Rarete, largeurPx: Int, vignette: Boolean): Bitmap? {
        val vues = context.getString(R.string.carte_ecu_vues)
        val niveau = context.getString(R.string.carte_ecu_niveau)
        return couche("m$vues$niveau", rarete, largeurPx, vignette) { c ->
            dessinerMetal(c, rarete, vignette, vues, niveau)
        }
    }

    /**
     * Calcule d'avance le métal et les rayons des vignettes, les quatre
     * paliers, à la largeur [largeurPx]. Fait pour un fil de fond.
     *
     * Le métal d'une vignette se calcule au pixel ([moulure]) : mesuré sur le
     * Galaxy A21s (2026-10-12), 1,1 s pour les quatre paliers, dont la
     * moitié pour le premier calculé, payés d'un bloc par la première image
     * de la grille, qui figeait l'écran autant. Le carnet
     * met plusieurs secondes à charger ses cartes : c'est là que ce calcul se
     * fait désormais, sur un autre cœur. Le dessin du métal n'écrit dans
     * aucun état partagé, et le cache est sûr d'un fil à l'autre.
     */
    fun prechauffer(context: Context, largeurPx: Int) {
        for (r in Rarete.values()) {
            metal(context, r, largeurPx, vignette = true)
            rayons(r, largeurPx, vignette = true)
        }
    }

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
     * Le plateau du cadre, en volume : une moulure de métal et non un aplat.
     *
     * Il était peint d'un seul dégradé en diagonale, cerné d'un filet : à la
     * taille de la carte, une bande de couleur (propriétaire, 2026-10-11,
     * « donner du volume »). Le modèle retenu est le bronze de la maquette
     * des matières, que j'avais proposée et dont la bordure a plu ; chaque
     * palier y a sa façon de travailler le métal. Quatre choses le font :
     *
     * - **le poli** : le dégradé alterne sombre et clair, avec une bande
     *   d'éclat presque blanche, comme un métal qui renvoie la pièce ;
     * - **la moulure bombée**, calculée au pixel : chaque bord du plateau
     *   descend en pente douce, l'extérieur vers la tranche et l'intérieur
     *   vers la face, et la pente prend la lumière d'en haut à gauche. Le
     *   haut et la gauche s'allument dehors, la face paraît posée en creux ;
     * - **la façon du métal**, une par palier, comme dans la maquette :
     *   l'étain **brossé** en stries, le bronze **martelé** en taches
     *   larges, l'argent **ciselé** d'une tresse gravée sur les flancs,
     *   l'or **poli**, sans grain, aux reflets plus francs ;
     * - **les rivets du cadre**, de *Peu commun* à *Rare*, sur l'axe des
     *   flancs. La très rare y porte déjà ses feuilles d'acanthe.
     *
     * Le grènetis, à partir de *Rare*, borde le pied de la moulure, contre
     * la face, sous la ligne de série et le poinçon.
     *
     * Tout tient dans le bitmap mis en cache par palier et par taille : le
     * calcul au pixel est payé une fois, pas à chaque trame.
     */
    private fun reliefDuPlateau(
        c: Canvas, p: Paint, plateau: Path, bord: Float, haut: Float,
        m: Metal, palier: Int, vignette: Boolean
    ) {
        val poli = palier == 3
        val eclat = melange(m.hi, Color.WHITE, if (poli) 0.7f else 0.35f)
        val fond = melange(m.lo, m.trait, 0.35f)
        p.style = Paint.Style.FILL
        p.color = Color.BLACK
        // L'or poli renvoie la pièce en bandes plus nombreuses et plus nettes :
        // c'est ce qui sépare un miroir d'un métal brossé.
        p.shader = if (poli) LinearGradient(
            0f, 0f, LARGEUR, haut,
            intArrayOf(fond, eclat, m.mid, m.lo, m.hi, eclat, m.mid, m.lo, eclat, m.mid),
            floatArrayOf(0f, 0.1f, 0.2f, 0.32f, 0.45f, 0.52f, 0.62f, 0.75f, 0.86f, 1f),
            Shader.TileMode.CLAMP
        ) else LinearGradient(
            0f, 0f, LARGEUR, haut,
            intArrayOf(fond, m.hi, m.mid, eclat, m.lo, melange(m.mid, m.hi, 0.5f)),
            floatArrayOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawPath(plateau, p)
        p.shader = null

        val u = c.width / LARGEUR
        if (u > 0f) {
            val moulure = moulure(c.width, c.height, u, bord, haut, palier)
            c.save()
            c.scale(1f / u, 1f / u)
            c.drawBitmap(moulure, 0f, 0f, null)
            c.restore()
            moulure.recycle()
        }

        if (vignette) {
            p.style = Paint.Style.FILL
            return
        }

        if (palier == 2) ciselure(c, p, bord, haut, m)

        // Les rivets du cadre, sur l'axe de la bande visible des flancs.
        if (palier in 1..2) {
            val x = (2.45f + bord) / 2f
            var y = 70f
            while (y < haut - 60f) {
                rivetDeCadre(c, p, x, y, m)
                rivetDeCadre(c, p, LARGEUR - x, y, m)
                y += 60f
            }
        }

        if (palier < 2) {
            p.style = Paint.Style.FILL
            return
        }

        // Le grènetis : une perle toutes les 3,2 unités, posée au pied de la
        // moulure, ombrée puis éclairée comme les rivets.
        val r = 0.75f + (palier - 2) * 0.1f
        val rang = Path()
        val e = bord - 1.1f
        rang.addRoundRect(
            RectF(e, e, LARGEUR - e, haut - e), 11f + 1.1f, 11f + 1.1f, Path.Direction.CW
        )
        fun perles(rayon: Float, dx: Float, dy: Float, couleur: Int) {
            val perle = Path().apply { addCircle(0f, 0f, rayon, Path.Direction.CW) }
            p.pathEffect = android.graphics.PathDashPathEffect(
                perle, 3.2f, 0f, android.graphics.PathDashPathEffect.Style.TRANSLATE
            )
            p.style = Paint.Style.FILL
            p.color = couleur
            c.save()
            c.translate(dx, dy)
            c.drawPath(rang, p)
            c.restore()
        }
        perles(r, 0.35f, 0.45f, 0x73000000)
        perles(r, 0f, 0f, m.mid)
        perles(r * 0.55f, -r * 0.25f, -r * 0.3f, eclat)
        p.pathEffect = null
        p.style = Paint.Style.FILL
    }

    /** Un rivet bombé, de la matière du cadre, avec son ombre portée. */
    private fun rivetDeCadre(c: Canvas, p: Paint, x: Float, y: Float, m: Metal) {
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = 0x59000000
        c.drawCircle(x + 0.5f, y + 0.8f, 2.7f, p)
        p.color = Color.BLACK
        p.shader = RadialGradient(
            x - 0.9f, y - 1f, 3.4f,
            intArrayOf(melange(m.hi, Color.WHITE, 0.7f), m.mid, m.lo),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(x, y, 2.6f, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.5f
        p.color = avecAlpha(m.trait, 0xB3)
        c.drawCircle(x, y, 2.6f, p)
        p.style = Paint.Style.FILL
    }

    /** La lumière, d'en haut à gauche, dans le repère de l'écran. */
    private const val LUMIERE_X = -0.61f
    private const val LUMIERE_Y = -0.79f

    /** Sur combien d'unités une pente de la moulure s'éteint. */
    private const val PENTE = 2.4f

    /**
     * La moulure du plateau, au pixel : un calque d'ombres et de lumières,
     * blanc ou noir selon le signe, à poser sur le dégradé du métal.
     *
     * Chaque bord du plateau est un rectangle arrondi dont on connaît la
     * distance signée et sa normale. La pente d'un bord s'éteint en
     * exponentielle sur [PENTE] unités ; elle penche vers l'extérieur pour le
     * bord de la tranche, vers la face pour le bord intérieur, et le produit
     * scalaire de cette pente avec la lumière dit si elle s'allume ou
     * s'assombrit. Le grain s'ajoute au même signe : des stries pour un
     * métal brossé, des taches pour un métal martelé.
     */
    private fun moulure(
        largeurPx: Int, hauteurPx: Int, u: Float, bord: Float, haut: Float, palier: Int
    ): Bitmap {
        val px = IntArray(largeurPx * hauteurPx)
        val n = FloatArray(2)
        val clair = if (palier == 3) 0.65f else 0.5f
        val plafond = if (palier == 3) 0.6f else 0.45f
        // Le bord extérieur s'arrête où commence le filet de contour.
        val dehors = 2.45f
        val ocx = LARGEUR / 2f
        val ocy = haut / 2f
        val ohx = LARGEUR / 2f - dehors
        val ohy = haut / 2f - dehors
        val orr = RAYON - dehors
        val ihx = LARGEUR / 2f - bord
        val ihy = haut / 2f - bord
        // Seul l'anneau du cadre compte. Entre les arrondis, une rangée n'a
        // que ses deux flancs à calculer : le reste tombe dans la face, quatre
        // pixels sur cinq qu'il est inutile de mesurer.
        val flanc = ceil(bord * u).toInt() + 1
        val hautFlancs = bord + 11f
        val basFlancs = haut - bord - 11f
        for (j in 0 until hauteurPx) {
            val y = (j + 0.5f) / u
            val flancsSeuls = y > hautFlancs && y < basFlancs
            for (i in 0 until largeurPx) {
                if (flancsSeuls && i >= flanc && i < largeurPx - flanc) continue
                val x = (i + 0.5f) / u
                val dIn = distanceArrondie(x, y, ocx, ocy, ihx, ihy, 11f, n)
                if (dIn <= 0f) continue
                val gix = n[0]
                val giy = n[1]
                val dOut = distanceArrondie(x, y, ocx, ocy, ohx, ohy, orr, n)
                if (dOut > 0.6f) continue
                val a = -dOut
                val pOut = if (a > 0f) exp(-a / PENTE) else 1f
                val pIn = exp(-dIn / PENTE)
                var s = pOut * (n[0] * LUMIERE_X + n[1] * LUMIERE_Y) -
                    pIn * (gix * LUMIERE_X + giy * LUMIERE_Y)
                // Le brillant : la crête d'une pente bien tournée vers la lumière.
                if (s > 0.45f) s += (s - 0.45f) * 1.2f
                s += grain(x, y, palier)
                // La lumière n'éclaircit qu'à moitié : un métal blanchi se lit
                // comme du plâtre, c'est l'ombre qui donne le volume. L'or
                // poli a droit à un peu plus : c'est un miroir.
                val alpha = (if (s > 0f) (s * clair).coerceAtMost(plafond)
                else (-s).coerceAtMost(0.8f)).let { (it * 255f).toInt() }
                if (alpha < 3) continue
                px[j * largeurPx + i] = (alpha shl 24) or (if (s > 0f) 0xFFFFFF else 0)
            }
        }
        return Bitmap.createBitmap(px, largeurPx, hauteurPx, Bitmap.Config.ARGB_8888)
    }

    /**
     * La distance signée d'un point à un rectangle arrondi de centre
     * ([cx], [cy]) et de demi-côtés ([hx], [hy]), négative dedans. La
     * normale sortante est écrite dans [n].
     */
    private fun distanceArrondie(
        x: Float, y: Float, cx: Float, cy: Float, hx: Float, hy: Float, r: Float, n: FloatArray
    ): Float {
        val dx = x - cx
        val dy = y - cy
        val sx = if (dx < 0f) -1f else 1f
        val sy = if (dy < 0f) -1f else 1f
        val qx = abs(dx) - hx + r
        val qy = abs(dy) - hy + r
        if (qx > 0f && qy > 0f) {
            val l = sqrt(qx * qx + qy * qy)
            n[0] = sx * qx / l
            n[1] = sy * qy / l
            return l - r
        }
        if (qx > qy) {
            n[0] = sx; n[1] = 0f
        } else {
            n[0] = 0f; n[1] = sy
        }
        return max(qx, qy) - r
    }

    /**
     * Le grain du métal, centré sur zéro, selon la façon du palier.
     *
     * - Étain, **brossé** : des stries fines en travers, longues dans
     *   l'autre sens.
     * - Bronze, **martelé** : un bruit de valeur lissé à deux octaves, en
     *   taches d'une dizaine d'unités, les coups du marteau.
     * - Argent, **ciselé** : un brossé plus doux, pour que la tresse gravée
     *   de [ciselure] se lise par-dessus.
     * - Or, **poli** : aucun grain. Un miroir n'a pas de texture, il n'a que
     *   des reflets.
     */
    private fun grain(x: Float, y: Float, palier: Int): Float = when (palier) {
        0 -> (bruit(x / 1.1f, y / 60f, 5) * 0.7f + bruit(x / 0.5f, y / 25f, 11) * 0.3f - 0.5f) * 0.26f
        1 -> (bruit(x / 11f, y / 11f, 3) * 0.65f + bruit(x / 4.5f, y / 4.5f, 9) * 0.35f - 0.5f) * 0.42f
        2 -> (bruit(x / 1.1f, y / 60f, 7) * 0.7f + bruit(x / 0.5f, y / 25f, 13) * 0.3f - 0.5f) * 0.12f
        else -> 0f
    }

    /**
     * La ciselure de l'argent : une tresse gravée sur l'axe des deux flancs,
     * deux ondes en opposition qui se croisent toutes les huit unités.
     *
     * Gravée et non peinte : un sillon sombre, et le liseré clair de sa lèvre
     * basse, qui reçoit la lumière d'en haut à gauche. Les rivets du cadre se
     * posent par-dessus, comme dans la maquette.
     */
    private fun ciselure(c: Canvas, p: Paint, bord: Float, haut: Float, m: Metal) {
        val tresse = Path()
        val debut = 40f
        val fin = haut - 40f
        for (x in floatArrayOf((2.45f + bord) / 2f, LARGEUR - (2.45f + bord) / 2f)) {
            for (sens in floatArrayOf(1f, -1f)) {
                tresse.moveTo(x, debut)
                var y = debut
                var s = sens
                while (y < fin) {
                    tresse.quadTo(x + 4f * s, y + 8f, x, y + 16f)
                    s = -s
                    y += 16f
                }
            }
        }
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 0.6f
        c.save()
        c.translate(0.3f, 0.4f)
        p.color = 0x80FFFFFF.toInt()
        c.drawPath(tresse, p)
        c.restore()
        p.color = avecAlpha(m.trait, 0x99)
        c.drawPath(tresse, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
    }

    private fun bruit(x: Float, y: Float, graine: Int): Float {
        val x0 = kotlin.math.floor(x)
        val y0 = kotlin.math.floor(y)
        val ix = x0.toInt()
        val iy = y0.toInt()
        var fx = x - x0
        var fy = y - y0
        fx = fx * fx * (3f - 2f * fx)
        fy = fy * fy * (3f - 2f * fy)
        val a = hache(ix, iy, graine)
        val b = hache(ix + 1, iy, graine)
        val c = hache(ix, iy + 1, graine)
        val d = hache(ix + 1, iy + 1, graine)
        val haut = a + (b - a) * fx
        val bas = c + (d - c) * fx
        return haut + (bas - haut) * fy
    }

    private fun hache(x: Int, y: Int, graine: Int): Float {
        var h = x * 374761393 + y * 668265263 + graine * 144269504
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFF) / 65535f
    }

    private fun melange(a: Int, b: Int, t: Float): Int {
        fun canal(decalage: Int): Int {
            val x = (a shr decalage) and 0xFF
            val y = (b shr decalage) and 0xFF
            return (x + (y - x) * t).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (canal(16) shl 16) or (canal(8) shl 8) or canal(0)
    }

    private fun avecAlpha(couleur: Int, alpha: Int): Int = (couleur and 0x00FFFFFF) or (alpha shl 24)

    /**
     * Tout ce qui ne dépend que du palier : le plateau, la sertissure de
     * l'ouverture, les volutes, la plaque, les pastilles, les écus.
     *
     * Le centre reste transparent — la face teintée et l'illustration sont
     * peintes dessous, en direct, par [CarteOrnee].
     */
    private fun dessinerMetal(c: Canvas, rarete: Rarete, vignette: Boolean, vues: String, niveau: String) {
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
        reliefDuPlateau(c, p, plateau, bord, haut, m, palier, vignette)
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

        // 4. L'écoinçon, en haut à droite, à partir de Peu commun.
        //
        // Il remplace (2026-09-19) des spirales gravées, fines et
        // translucides, qui flottaient dans les angles du bas sans rien
        // toucher : à la taille de la carte, une courbe qui s'amincit jusqu'à
        // rien se lisait comme un cheveu ou une poussière. Ce qui les
        // remplace est l'inverse sur quatre points : ancré dans le cadre,
        // d'épaisseur constante, fait du métal du palier, en relief.
        //
        // Un seul angle, et c'est celui-là : le haut à gauche appartient à la
        // gemme de coût, et l'ornement lui répond en diagonale au lieu de lui
        // disputer la place. À partir de *Rare*, l'arche ouvre au-dessus
        // d'elle le vrai écoinçon d'architecte — le triangle courbe entre
        // l'arc et l'angle du cadre —, assez grand pour une ferronnerie :
        // voir [ferronnerie]. La vignette, trop petite pour la lire, garde le
        // coin simple à tous les paliers.
        if (palier >= 1) {
            if (vignette || palier == 1) {
                ecoincon(
                    c, p, LARGEUR - bord, bord, -1f, 1f,
                    if (vignette) 0.8f else 1f, if (vignette) palier else 1, m
                )
            } else {
                ferronnerie(c, p, palier, m)
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
        c.drawText(vues, ECU_G.centerX(), ECU_G.top + 9f, p)
        c.drawText(niveau, ECU_D.centerX(), ECU_D.top + 9f, p)
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
    fun dessinerMedaillon(context: Context, c: Canvas, p: Paint, jeu: JeuCarte, m: Metal) {
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
        // Une carte cédée n'a pas été gagnée : elle a été reçue.
        val dessus = if (jeu == JeuCarte.CADEAU) R.string.carte_recue_medaillon else R.string.carte_gagne_a_medaillon
        texteSurArc(c, p, context.getString(dessus), cx, cy, R_EMAIL + 2.2f, true)
        texteSurArc(c, p, jeu.libelle(context).uppercase(), cx, cy, R_MEDAILLON - 3.6f, false)
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
            // Des mots placés en croix : la case commune pleine.
            JeuCarte.MO_AN_PLAS -> {
                p.strokeWidth = 1f
                for (i in -1..1) carre(-1.9f + i * 4.2f, -1.9f, 3.8f, i == 0)
                carre(-1.9f, -1.9f - 4.2f, 3.8f, false)
                carre(-1.9f, -1.9f + 4.2f, 3.8f, false)
            }
            // Une bulle de parole : on dit bonjour.
            JeuCarte.ACCUEIL -> {
                p.strokeWidth = 1.3f
                c.drawRoundRect(cx - 6.5f, cy - 5.5f, cx + 6.5f, cy + 3.5f, 3f, 3f, p)
                trait(-2.5f, 3.5f, -4.5f, 7f)
                trait(-4.5f, 7f, 0.5f, 3.5f)
            }
            // Un paquet-cadeau : la boîte, son ruban, le nœud.
            JeuCarte.CADEAU -> {
                p.strokeWidth = 1.2f
                c.drawRect(cx - 6f, cy - 2f, cx + 6f, cy + 6.5f, p)
                c.drawRect(cx - 7f, cy - 4.5f, cx + 7f, cy - 2f, p)
                trait(0f, -4.5f, 0f, 6.5f)
                c.drawOval(cx - 5f, cy - 8f, cx - 0.3f, cy - 4.5f, p)
                c.drawOval(cx + 0.3f, cy - 8f, cx + 5f, cy - 4.5f, p)
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
     * demande de consulter aucun lemme, et le kréyòl n'a de toute façon pas de
     * table de familles (côté LuxKeyb, `luxemburgish_familles.json` laisse 647
     * formes sans rattachement).
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
     * - La capsule de nature, 1,6 ; le disque du médaillon, 1,8 ; les écus, 2 :
     *   des pièces de métal posées, pas toutes de même épaisseur.
     * - La plaque du nom, 2,2 : la plus épaisse des pièces posées, juste sous
     *   les pierres. C'est là qu'est le mot, et c'est ce que le doigt doit
     *   trouver le plus nettement (relevée de 1,8 le 2026-09-19, à la demande
     *   du propriétaire, qui la cherchait les yeux fermés).
     * - Les pierres dépassent davantage que tout ce qui est posé à plat :
     *   l'agrafe 2,4, la gemme de coût 2,8. Les rivets, 2,4 ; ceux de la
     *   plaque la traversent et dépassent de sa face, de 0,3 à Commun à 1,2
     *   à Très rare : 2,5 à 3,4. Les deux paliers hauts passent au-dessus de
     *   la gemme, et c'est voulu : une tête de 3 unités est trop petite pour
     *   se sentir si elle ne dépasse que de peu.
     * - Une griffe tient sa pierre par-dessus : 0,3 de plus qu'elle.
     */
    private const val Z_HORS = 0f
    private const val Z_OUVERTURE = 0.4f
    const val Z_FACE = 1f
    private const val Z_PANNEAU = 1.1f
    private const val Z_PASTILLE = 1.6f
    private const val Z_PLAQUE = 2.2f
    private const val Z_MEDAILLON = 1.8f
    private const val Z_ECU = 2f
    private const val Z_PLATEAU = 2f
    private const val Z_AGRAFE = 2.4f
    private const val Z_RIVET = 2.4f
    /**
     * Ce que les rivets de la plaque dépassent d'elle, par palier. Les écarts
     * sont larges exprès : sur un moteur sans amplitude c'est la durée de
     * l'impulsion qui dit la hauteur, et elle ne monte que d'un échelon par
     * quart d'unité (voir `KeyFeedback.impulsionPour`).
     */
    private val SAILLIE_RIVET = floatArrayOf(0.3f, 0.6f, 0.9f, 1.2f)
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
        val v = ArrayList<Float>(5 * 22)
        fun bosse(cx: Float, cy: Float, rx: Float, ry: Float, z: Float) {
            v.add(cx); v.add(cy); v.add(rx); v.add(ry); v.add(z)
        }
        if (palier >= 1) {
            for (y in floatArrayOf(HAUTEUR * 0.42f, HAUTEUR * 0.70f)) {
                bosse(bord + 6f, y, 3.2f, 3.2f, Z_RIVET)
                bosse(LARGEUR - bord - 6f, y, 3.2f, 3.2f, Z_RIVET)
            }
        }
        for (x in floatArrayOf(PLAQUE.left + RIVET_PLAQUE_DX, PLAQUE.right - RIVET_PLAQUE_DX)) {
            for (y in floatArrayOf(PLAQUE.top + RIVET_PLAQUE_DY, PLAQUE.bottom - RIVET_PLAQUE_DY)) {
                val r = RIVET_PLAQUE_R[palier]
                bosse(x, y, r, r, Z_PLAQUE + SAILLIE_RIVET[palier])
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
        if (x < BORD_CARTE || x > LARGEUR - BORD_CARTE ||
            y < BORD_CARTE || y > HAUTEUR - BORD_CARTE
        ) return Z_HORS
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

    /**
     * Les crans que le pouce franchit à l'abscisse [x], en descendant : la
     * même chose que [aretes], tournée d'un quart de tour.
     *
     * Une rangée ne voit que ce qu'on franchit en balayant de côté. Or la
     * plaque du nom est plus large que le cadre : de gauche à droite, le doigt
     * entre dessus depuis la table et n'en sort qu'à l'autre bord, sans jamais
     * sentir qu'elle dépasse de la face. Son volume ne se lit qu'en venant
     * d'au-dessus ou d'en dessous, et c'est ce que les yeux fermés font
     * d'abord. [Relief.x] y est une ordonnée ; le sens +1 est vers le bas.
     */
    fun aretesColonne(rarete: Rarete, bosses: FloatArray, x: Float): Relief {
        val brut = ArrayList<Arete>(24)
        var avant = cote(rarete, bosses, x, 0.5f)
        var y = 1.5f
        while (y < HAUTEUR) {
            val ici = cote(rarete, bosses, x, y)
            if (ici != avant) brut.add(Arete(y - 0.5f, ici - avant))
            avant = ici
            y += 1f
        }
        return crans(brut)
    }

    /**
     * Une marche : où elle est, et de combien on monte en la franchissant de
     * gauche à droite (de haut en bas, dans une colonne).
     */
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
    /**
     * Le poinçon de la saison où la carte a été gagnée : l'emblème de la
     * saison (flocon, fleur, soleil, feuille) dans un petit cartouche, **gravé
     * dans le métal de la marge basse**, juste après le numéro de série, sur
     * la carte ouverte comme sur la vignette.
     *
     * C'est la marque d'un orfèvre et non un objet posé : un premier essai en
     * médaille sur l'illustration se lisait comme une verrue (propriétaire,
     * 2026-10-11), et la plume avait déjà suivi le même chemin. La gravure est
     * la sienne : une lèvre claire décalée vers le bas, puis le sillon dans le
     * ton sombre du métal.
     *
     * **Jamais la date** : elle reste une donnée technique du carnet, ce qui
     * compte est la saison. La fête du jour, elle, ne se voit que dans le
     * paysage. Voir [Souvenir].
     */
    fun dessinerSaison(c: Canvas, p: Paint, s: Souvenir, rarete: Rarete, vignette: Boolean) {
        val metal = metal(rarete)
        val cx = POINCON_X
        val cy = milieuBordureBasse(rarete, vignette)
        val e = if (vignette) POINCON_VIGNETTE_ECHELLE else 1f
        p.shader = null
        for (passe in 0..1) {
            c.save()
            c.translate(cx, cy)
            c.scale(e, e)
            if (passe == 0) c.translate(0.3f, 0.42f)
            val encre = if (passe == 0) 0x99FFFFFF.toInt() else metal.trait
            p.color = encre
            p.style = Paint.Style.STROKE
            p.strokeWidth = POINCON_TRAIT
            p.strokeJoin = Paint.Join.ROUND
            c.drawRoundRect(RectF(-POINCON_L / 2f, -POINCON_H / 2f, POINCON_L / 2f, POINCON_H / 2f), 2f, 2f, p)
            c.scale(POINCON_EMBLEME, POINCON_EMBLEME)
            // Le cœur de la fleur et la nervure de la feuille sont des
            // réserves : elles prennent le ton du métal autour.
            emblemeDeSaison(c, p, s.saison, encre, if (passe == 0) encre else metal.mid)
            c.restore()
        }
        p.strokeCap = Paint.Cap.BUTT
        p.strokeJoin = Paint.Join.MITER
        p.style = Paint.Style.FILL
    }

    /**
     * Le poinçon, après le numéro de série : « n° 036 », « Nr. 036 » et
     * « n.º 036 » s'arrêtent avant 54 au corps de 7,5. La plume garde le
     * centre de la marge, le rang la droite.
     *
     * Il tient **dans** la bordure basse, comme la plume : celle d'une commune
     * n'a que 9,55 unités entre la face et le filet de contour, et un premier
     * cartouche de 10,5 la débordait des deux côtés (propriétaire,
     * 2026-10-11). 7 de haut, plus le trait et la lèvre, laissent 0,7 de
     * métal au-dessus et au-dessous ; l'emblème suit, à 2,7 de rayon.
     */
    private const val POINCON_X = 64f
    private const val POINCON_L = 10f
    private const val POINCON_H = 7f
    private const val POINCON_TRAIT = 0.7f
    private const val POINCON_EMBLEME = 0.36f
    private const val POINCON_VIGNETTE_ECHELLE = 0.85f

    /** La zone du poinçon sur la carte ouverte, pour les lecteurs d'écran. */
    fun saison(rarete: Rarete): RectF {
        val cy = milieuBordureBasse(rarete, false)
        return RectF(POINCON_X - POINCON_L / 2f, cy - POINCON_H / 2f, POINCON_X + POINCON_L / 2f, cy + POINCON_H / 2f)
    }

    /** L'emblème d'une saison, dans un rayon de 7,5 unités, repris de la maquette. */
    private fun emblemeDeSaison(c: Canvas, p: Paint, saison: Saison, encre: Int, clair: Int) {
        p.color = encre
        when (saison) {
            Saison.WANTER -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1.3f
                p.strokeCap = Paint.Cap.ROUND
                p.strokeJoin = Paint.Join.ROUND
                for (a in floatArrayOf(0f, 60f, 120f)) {
                    c.save()
                    c.rotate(a)
                    c.drawLine(0f, -7f, 0f, 7f, p)
                    val pointes = Path()
                    pointes.moveTo(-2f, -5f); pointes.lineTo(0f, -3f); pointes.lineTo(2f, -5f)
                    pointes.moveTo(-2f, 5f); pointes.lineTo(0f, 3f); pointes.lineTo(2f, 5f)
                    c.drawPath(pointes, p)
                    c.restore()
                }
            }
            Saison.FREIJOER -> {
                p.style = Paint.Style.FILL
                for (k in 0 until 5) {
                    c.save()
                    c.rotate(k * 72f)
                    c.drawOval(RectF(-2.3f, -3.6f - 3.2f, 2.3f, -3.6f + 3.2f), p)
                    c.restore()
                }
                p.color = clair
                c.drawCircle(0f, 0f, 1.8f, p)
            }
            Saison.SUMMER -> {
                p.style = Paint.Style.FILL
                c.drawCircle(0f, 0f, 3.4f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1.2f
                p.strokeCap = Paint.Cap.ROUND
                for (k in 0 until 8) {
                    c.save()
                    c.rotate(k * 45f)
                    c.drawLine(0f, -5f, 0f, -7.2f, p)
                    c.restore()
                }
            }
            Saison.HIERSCHT -> {
                p.style = Paint.Style.FILL
                c.drawPath(FEUILLE, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 0.7f
                p.color = clair
                c.drawLine(0f, -5f, 0f, 7.6f, p)
            }
        }
        p.style = Paint.Style.FILL
    }

    private val FEUILLE: Path = Path().apply {
        moveTo(0f, -7.5f)
        cubicTo(4.5f, -5f, 6f, -1f, 4.4f, 2.6f)
        cubicTo(3.2f, 5.2f, 1f, 6f, 0f, 7f)
        cubicTo(-1f, 6f, -3.2f, 5.2f, -4.4f, 2.6f)
        cubicTo(-6f, -1f, -4.5f, -5f, 0f, -7.5f)
        close()
    }


    /**
     * Le sceau de cire d'une carte apprise : posé sur l'angle bas droit de
     * l'illustration, il mord sur la fenêtre comme un cachet sur un document.
     *
     * Dessiné d'après la maquette du propriétaire (2026-10-10) : cire rouge,
     * le mot en arc dans le haut, une coche frappée au centre. Côté
     * luxembourgeois le mot est « GELÉIERT » ; ici « APPRIS », en français
     * comme le reste de l'interface (décision du propriétaire, 2026-10-19),
     * aucun mot kréyòl validé n'étant disponible pour ce cachet.
     *
     * C'est un **second axe**, et il ne touche à rien du premier : la rareté
     * reste lue sur la fréquence, dans le métal du cadre. Le sceau est le même
     * à tous les paliers ; une commune apprise et une très rare apprise portent
     * le même cachet sur deux cadres différents. Voir `GAMIFICATION-CARNET.md` dans le dépôt LuxKeyb.
     *
     * La cire n'est pas un disque : son bord coule, d'où le rayon modulé par
     * deux sinus de fréquences premières entre elles. Un anneau en biseau (une
     * lèvre sombre, une lèvre éclairée) dit que la cire a été pressée, et la
     * lumière vient d'en haut à gauche comme dans tout le carnet.
     *
     * La vignette n'a que la cire et la coche : à 110 dp, deux lignes de
     * légende seraient une bouillie rouge.
     */
    fun dessinerSceau(c: Canvas, p: Paint, vignette: Boolean) =
        cachet(c, p, vignette, CIRE_ROUGE, "APPRIS") { cv, pi, ombre ->
            pi.style = Paint.Style.STROKE
            pi.strokeCap = Paint.Cap.ROUND
            pi.strokeJoin = Paint.Join.ROUND
            pi.strokeWidth = if (ombre) 0.15f else 0.12f
            cv.drawPath(COCHE, pi)
        }

    /**
     * La plume d'une carte que le joueur a **écrite lui-même** dans un vrai
     * message : le clavier l'a fait monter d'une boîte sans poser de question
     * (voir [PreuveDeFrappe]). Accordée par le propriétaire le 2026-10-10.
     *
     * Elle a d'abord été un second cachet de cire, bleu, en miroir du sceau ;
     * deux cachets sur l'illustration surchargeaient la carte (propriétaire,
     * même jour). Elle est donc **gravée dans le métal du cadre**, petite, au
     * centre de la marge basse, sur la carte ouverte comme sur la vignette :
     * une mention d'inventaire, comme le numéro, et non une
     * décoration de plus.
     *
     * La gravure est celle du reste de la carte : un sillon dans le ton sombre
     * du métal, une lèvre claire décalée vers le bas qui le fait lire en creux.
     */
    fun dessinerPlume(c: Canvas, p: Paint, vignette: Boolean, rarete: Rarete) {
        val metal = metal(rarete)
        val cx = if (vignette) PLUME_VIGNETTE_X else PLUME_X
        val cy = milieuBordureBasse(rarete, vignette)
        val taille = if (vignette) PLUME_VIGNETTE_TAILLE else PLUME_TAILLE
        c.save()
        c.translate(cx, cy)
        c.scale(taille, taille)
        p.shader = null
        for (passe in 0..1) {
            c.save()
            if (passe == 0) {
                c.translate(0.05f, 0.07f)
                p.color = 0xB3FFFFFF.toInt()
            } else {
                p.color = metal.trait
            }
            p.style = Paint.Style.FILL
            c.drawPath(PLUME, p)
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = 0.07f
            c.drawLine(-0.40f, 0.44f, -0.22f, 0.24f, p)
            c.restore()
        }
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
        c.restore()
    }

    /**
     * La plume, au centre de la marge basse : sur l'axe de la carte, comme la
     * clef de voûte, la plaque, l'agrafe et le médaillon. Posée devant le
     * numéro de série, elle touchait l'arrondi du coin et mordait sur le
     * « n° » ; ici, la ligne de série et le rang la laissent libre des deux
     * côtés (propriétaire, 2026-10-10).
     */
    private const val PLUME_X = LARGEUR / 2f
    private const val PLUME_TAILLE = 9f
    private const val PLUME_VIGNETTE_X = LARGEUR / 2f
    private const val PLUME_VIGNETTE_TAILLE = 7.5f

    /**
     * Un cachet de cire : l'ombre, la cire coulée, le biseau, un motif frappé
     * au centre et une légende en arc au-dessus.
     *
     * [motif] est tracé deux fois sur le cercle unité, d'abord dans l'ombre
     * (`ombre` vrai, décalé vers le bas à droite) puis en blanc : c'est ce
     * décalage qui le fait lire comme frappé dans la cire.
     */
    private fun cachet(
        c: Canvas, p: Paint, vignette: Boolean, cire: Cire, legende: String,
        motif: (Canvas, Paint, Boolean) -> Unit
    ) {
        val cx = if (vignette) SCEAU_VIGNETTE_X else SCEAU_X
        val cy = if (vignette) SCEAU_VIGNETTE_Y else SCEAU_Y
        val r = if (vignette) SCEAU_VIGNETTE_R else SCEAU_R
        c.save()
        c.translate(cx, cy)

        // La cire et ses reliefs sont tracés sur le cercle unité.
        c.save()
        c.scale(r, r)
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = 0x59000000
        c.save()
        c.translate(0.07f, 0.11f)
        c.drawPath(BORD_CIRE, p)
        c.restore()
        // Pinceau opaque avant le dégradé : l'alpha de l'ombre, resté sur le
        // pinceau, multiplierait la cire et la rendrait translucide.
        p.color = Color.BLACK
        p.shader = cire.modele
        c.drawPath(BORD_CIRE, p)
        p.shader = null

        // Le biseau : la lèvre basse dans l'ombre, la haute dans la lumière.
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.075f
        p.color = cire.ombre
        c.drawCircle(0.02f, 0.03f, 0.88f, p)
        p.color = 0x55FFFFFF
        p.strokeWidth = 0.04f
        c.drawArc(RectF(-0.88f, -0.88f, 0.88f, 0.88f), 160f, 140f, false, p)

        // Le motif descend sous la légende ; sur la vignette, sans légende, il
        // grandit et se centre.
        val echelle = if (vignette) 1.25f else 1.0f
        c.save()
        if (!vignette) c.translate(0f, 0.12f)
        c.scale(echelle, echelle)
        p.color = cire.ombre
        c.save()
        c.translate(0.025f, 0.04f)
        motif(c, p, true)
        c.restore()
        p.color = Color.WHITE
        motif(c, p, false)
        c.restore()
        p.strokeCap = Paint.Cap.BUTT
        p.strokeJoin = Paint.Join.MITER
        p.style = Paint.Style.FILL
        c.restore()

        // La légende, en unités de carte et non sur le cercle unité : un corps
        // de 0,2 pixel mis à l'échelle ensuite se rend mal.
        if (!vignette) legendeEnArc(c, p, legende, r * 0.50f, r, cire.ombre)
        c.restore()
    }

    /**
     * Une ligne de légende courbée sur le pourtour du sceau.
     *
     * Le chemin tourne dans le sens horaire, le pied des lettres au rayon
     * donné. Le corps rétrécit si la légende ne tient pas dans son arc.
     */
    private fun legendeEnArc(c: Canvas, p: Paint, texte: String, rayon: Float, r: Float, ombre: Int) {
        p.shader = null
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.LEFT
        p.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
        p.letterSpacing = 0.06f
        val arc = 170f
        val longueur = (Math.PI * rayon * arc / 180f).toFloat()
        p.textSize = r * 0.29f
        val large = p.measureText(texte)
        if (large > longueur) p.textSize *= longueur / large
        val chemin = Path().apply {
            val boite = RectF(-rayon, -rayon, rayon, rayon)
            addArc(boite, 270f - arc / 2f, arc)
        }
        val decalage = (longueur - p.measureText(texte)) / 2f
        p.color = ombre
        c.save()
        c.translate(r * 0.02f, r * 0.03f)
        c.drawTextOnPath(texte, chemin, decalage, 0f, p)
        c.restore()
        p.color = LEGENDE
        c.drawTextOnPath(texte, chemin, decalage, 0f, p)
        p.letterSpacing = 0f
        p.typeface = null
    }

    /**
     * Le sceau de la carte ouverte, à cheval sur l'angle bas droit de la
     * fenêtre.
     */
    private const val SCEAU_X = 239f
    private const val SCEAU_Y = 151f
    private const val SCEAU_R = 31f
    /** Sur la vignette, plus gros en proportion : il doit se lire à 110 dp. */
    private const val SCEAU_VIGNETTE_X = 240f
    private const val SCEAU_VIGNETTE_Y = 172f
    private const val SCEAU_VIGNETTE_R = 34f

    /**
     * Une cire : son modelé sur le cercle unité, fabriqué une fois (le cachet
     * se dessine à chaque trame d'une carte qu'on incline), et son ton
     * d'ombre, qui sert au biseau, au motif et à la légende.
     */
    private class Cire(claire: String, base: String, ombre: String) {
        val ombre: Int = Color.parseColor(ombre)
        val modele = RadialGradient(
            -0.30f, -0.38f, 1.4f,
            intArrayOf(Color.parseColor(claire), Color.parseColor(base), this.ombre),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
    }

    /** Le sceau, pris sur le rouge du drapeau. */
    private val CIRE_ROUGE by lazy { Cire("#FF6B6B", "#D7141F", "#7A0B12") }
    private val LEGENDE = Color.parseColor("#F6E3B4")

    /** Le bord coulé de la cire, sur un cercle unité. */
    private val BORD_CIRE: Path = Path().apply {
        val pas = 72
        for (i in 0..pas) {
            val t = (2 * Math.PI * i / pas)
            val r = 1f + 0.055f * sin(7 * t).toFloat() + 0.035f * sin(11 * t + 1.3).toFloat()
            val x = r * cos(t).toFloat()
            val y = r * sin(t).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    /** La coche frappée dans le sceau. */
    private val COCHE: Path = Path().apply {
        moveTo(-0.30f, 0.0f)
        lineTo(-0.08f, 0.22f)
        lineTo(0.32f, -0.22f)
    }

    /** La plume : une barbe en fuseau, la pointe en bas à gauche, sur le cercle unité. */
    private val PLUME: Path = Path().apply {
        moveTo(-0.26f, 0.28f)
        cubicTo(-0.36f, -0.04f, 0.04f, -0.40f, 0.38f, -0.48f)
        cubicTo(0.32f, -0.14f, 0.06f, 0.20f, -0.26f, 0.28f)
        close()
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

    /** Le tangage de l'appareil depuis le repos, dans [-1, 1], tel que dessiné. */
    private var tangage = 0f

    /**
     * D'où l'œil regarde la carte, dans [-1, 1] sur chaque axe : x vers la
     * droite, y vers le bas. C'est ce que lit la profondeur de la fenêtre
     * ([Motif.peindreLieu]). La pesanteur seule, sans le doigt : comme la
     * tranche, c'est l'orientation réelle du carton et non la lumière.
     *
     * Le signe suit [Posture] : bord droit qui descend, le roulis est négatif
     * et le bord droit de la carte vient vers le joueur, qui la voit donc
     * de sa droite.
     *
     * Amplifié de [GAIN_REGARD] : un roulis de 1, c'est le téléphone sur la
     * tranche, et le poignet n'en donne jamais qu'un quart ou un tiers. Sans
     * gain, les plans ne glissaient que d'un pour cent.
     */
    protected val regardX: Float get() = (-pesanteur * GAIN_REGARD).coerceIn(-1f, 1f)
    protected val regardY: Float get() = (tangage * GAIN_REGARD).coerceIn(-1f, 1f)

    /** La même posture que celle qui fait pivoter la carte, voir [Posture]. */
    private val posture = Posture(this, LISSAGE)

    /** Le vrai bas dans le plan de l'écran, en unités de pesanteur : voir [Posture.basX]. */
    protected val basX: Float get() = posture.basX
    protected val basY: Float get() = posture.basY

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

    /** La même chose en colonne, pour ce que le doigt franchit en montant ou en descendant. */
    private var colonne: Ornement.Relief = Ornement.Relief.LISSE
    private var abscisseDeColonne = 0f
    private var derniereY = 0f

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

    /** Le relief à l'abscisse [x], parcouru de haut en bas. Lisse par défaut, pour la même raison. */
    protected open fun aretesColonne(x: Float): Ornement.Relief = Ornement.Relief.LISSE

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
        tangage = 0f
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
        if (abs(posture.roulis - pesanteur) < SEUIL && abs(posture.tangage - tangage) < SEUIL) return
        pesanteur = posture.roulis
        tangage = posture.tangage
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
        colonne = aretesColonne(doigtX)
        abscisseDeColonne = doigtX
        derniereY = doigtY
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
        val ouX = x * u
        val ouY = y * u
        doigtX = ouX
        doigtY = ouY
        if (abs(doigtY - hauteurDuRelief) > Ornement.ECART_MIN) {
            relief = aretes(doigtY)
            hauteurDuRelief = doigtY
        }
        if (abs(doigtX - abscisseDeColonne) > Ornement.ECART_MIN) {
            colonne = aretesColonne(doigtX)
            abscisseDeColonne = doigtX
        }
        franchi = 0f
        dernier = 0f
        touche = false
        if (abs(ouX - derniereX) >= PAS_MIN) {
            franchir(relief, derniereX, ouX)
            derniereX = ouX
        }
        if (abs(ouY - derniereY) >= PAS_MIN) {
            franchir(colonne, derniereY, ouY)
            derniereY = ouY
        }
        if (touche) emettre(if (abs(franchi) < Ornement.DENIVELE_MIN) dernier else franchi)
        if (Pochette.animationsReduites(context)) return
        doigt = lumiereEn(x)
        invalidate()
    }

    // Ce que [franchir] a relevé pendant l'événement en cours. Des champs et
    // non un résultat, pour ne rien allouer à chaque `ACTION_MOVE`.
    private var franchi = 0f
    private var dernier = 0f
    private var touche = false

    /**
     * Les crans de [crans] situés entre [de] et [vers], sommés dans le sens
     * de la marche. Une rangée et une colonne passent par ici : un doigt en
     * diagonale peut franchir les deux dans le même événement, et n'en reçoit
     * toujours qu'une vibration.
     */
    private fun franchir(crans: Ornement.Relief, de: Float, vers: Float) {
        val sens = if (vers > de) 1f else -1f
        val bas = min(de, vers)
        val haut = max(de, vers)
        for (i in 0 until crans.taille) {
            val c = crans.x(i)
            if (c > bas && c <= haut) {
                dernier = crans.ressenti(i, sens)
                franchi += dernier
                touche = true
            }
        }
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
        colonne = Ornement.Relief.LISSE
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

        /** Voir [regardX] : pleine course vers vingt degrés de poignet. */
        const val GAIN_REGARD = 3f

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
    private val intensite: Float = 0.5f,
    /** Le jour où la carte a été gagnée, réduit à sa saison : voir [Souvenir]. */
    private val souvenir: Souvenir? = null
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

    /**
     * Prévenu quand le paysage d'une vignette, rendu en arrière-plan, arrive.
     * Pour qui dessine la vignette ailleurs qu'à l'écran : l'éventail de la
     * boîte en garde une photo, qui sans cela resterait au ciel seul.
     */
    var quandLieuArrive: (() -> Unit)? = null

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
        val redessiner: (() -> Unit)? = if (vignette) fun() { invalidate(); quandLieuArrive?.invoke() } else null
        motif = Motif(mot, rarete, fenetre, blason, souvenir, redessiner)
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

    override fun aretesColonne(x: Float): Ornement.Relief =
        if (vignette) Ornement.Relief.LISSE else Ornement.aretesColonne(rarete, bosses, x)

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
        oublierLesCouches()
        invalidate()
        return this
    }

    /** La carte a été écrite au clavier : voir [Ornement.dessinerPlume]. */
    private var plume = false

    fun avecPlume(valeur: Boolean): CarteOrnee {
        plume = valeur
        oublierLesCouches()
        invalidate()
        return this
    }

    /**
     * Faux tant que le paysage et le métal se préparent hors du fil principal :
     * voir [attendreSaMatiere]. La carte ne se dessine pas d'ici là.
     */
    private var matierePrete = true
    private var quandPrete: (() -> Unit)? = null
    private var largeurPreparee = 0

    /**
     * Pour le lecteur de cartes : le paysage et le métal de la carte ouverte
     * se calculent sur deux fils de fond dès qu'elle connaît sa taille, et
     * [pret] est appelé sur le fil principal quand elle peut se dessiner.
     *
     * Sur un Galaxy A21s (2026-10-12), les deux se calculaient l'un après
     * l'autre à la première image de la carte, 240 ms et 230 ms : l'écran se
     * figeait une demi-seconde au toucher, et le retournement démarrait en
     * retard. Menés de front, ils laissent le voile apparaître tout de suite
     * et la carte se retourner sans à-coup.
     *
     * Le lecteur, la révision et la pochette s'en servent (la révision et la
     * pochette posent la face cachée pendant que le dos est montré, 37.0.1) ;
     * le partage dessine la carte tout de suite et garde le rendu direct.
     */
    fun attendreSaMatiere(pret: () -> Unit): CarteOrnee {
        matierePrete = false
        quandPrete = pret
        return this
    }

    override fun onSizeChanged(w: Int, h: Int, ancienneL: Int, ancienneH: Int) {
        super.onSizeChanged(w, h, ancienneL, ancienneH)
        if (matierePrete || w <= 0 || w == largeurPreparee) return
        largeurPreparee = w
        val principal = android.os.Handler(android.os.Looper.getMainLooper())
        var restants = 2
        val fini: () -> Unit = {
            restants--
            if (restants == 0 && largeurPreparee == w) {
                matierePrete = true
                oublierLesCouches()
                invalidate()
                quandPrete?.invoke()
                quandPrete = null
            }
        }
        val ctx = context
        Thread({
            // Un échec ne bloque pas la carte : elle retombe sur le rendu direct.
            try {
                Ornement.metal(ctx, rarete, w, vignette)
                Ornement.rayons(rarete, w, vignette)
            } catch (e: Throwable) {
                android.util.Log.w("CarteOrnee", "Métal non préparé : ${e.message}")
            }
            principal.post(fini)
        }, "metal").start()
        motif.preparer(w / Ornement.LARGEUR, principal, fini)
    }

    /**
     * L'ordre est celui d'une carte imprimée : la face, ce qui rayonne
     * derrière l'illustration, l'illustration, puis le métal par-dessus.
     *
     * Deux de ces quatre couches — les rayons et le métal — sont des bitmaps
     * partagés par toutes les cartes du même palier et de la même taille. Ce
     * qui reste à tracer réellement à chaque trame, c'est un rectangle
     * dégradé, le motif du mot, et une poignée de joyaux.
     *
     * Sur la carte ouverte, ce qui ne bouge pas passe par [couches].
     */
    override fun onDraw(canvas: Canvas) {
        if (!matierePrete) return
        val u = width / Ornement.LARGEUR
        if (u <= 0f) return
        if (couches(canvas, u)) {
            vivant(canvas, u)
            return
        }

        // La neige passe derrière le dessin du mot : c'est le sujet de la
        // carte, quelques flocons dessus en brouillaient la lecture.
        val anime = ambiance != null && canvas.isHardwareAccelerated
        dessous(canvas, u, avecIris = !anime, avecSujet = !anime, regard = canvas.isHardwareAccelerated)
        if (anime) {
            animer(canvas, u)
            canvas.save()
            canvas.scale(u, u)
            motif.dessusDuLieu(canvas, pinceau, roulis, decoupe, avecSujet = true, avecIris = true)
            canvas.restore()
        }
        dessus(canvas, u)
        vivant(canvas, u)
    }

    /** Ce qui bouge dans la fenêtre : voir [Ambiance]. Réservé au lecteur et à la pochette. */
    private var ambiance: Ambiance? = null
    private var instantPrecedent = 0L

    /**
     * Pour le lecteur de cartes et la pochette : la fenêtre vit au temps de sa
     * saison, et son lieu prend de la profondeur en trois plans qui glissent
     * avec l'inclinaison ([Motif.profondeur]). Rien sur une vignette, rien si
     * le téléphone demande des animations réduites.
     */
    fun avecAmbiance(): CarteOrnee {
        if (vignette || Pochette.animationsReduites(context)) return this
        motif.profondeur = true
        val zone = RectF(fenetre.left, fenetre.top, fenetre.right, min(fenetre.bottom, Ornement.PLAQUE.top + 4f))
        // L'été, l'heure de la carte choisit : aigrettes le jour, lucioles
        // le soir et la nuit, les heures des deux paliers hauts.
        ambiance = Ambiance.pour(
            souvenir, zone, mot.hashCode(), nuit = rarete.ordinal >= Rarete.RARE.ordinal,
            oiseaux = motif.profondeur
        )
        oublierLesCouches()
        if (isAttachedToWindow) armerSecousse()
        return this
    }

    private var secousses: android.hardware.SensorManager? = null
    private var derniereSecousse = 0L

    /**
     * Le geste de la boule à neige : un coup sec soulève les flocons.
     *
     * L'accélération linéaire, sans la pesanteur : celle que la neige suit
     * déjà ne doit pas se lire comme une secousse. Un appareil qui ne l'a pas
     * garde sa neige, sans le tourbillon.
     */
    private val ecouteSecousse = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(e: android.hardware.SensorEvent) {
            val a = ambiance ?: return
            val v = e.values
            val m = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
            if (m < SEUIL_SECOUSSE) return
            val maintenant = android.os.SystemClock.uptimeMillis()
            if (maintenant - derniereSecousse < 120L) return
            derniereSecousse = maintenant
            a.secouer(((m - SEUIL_SECOUSSE) / 20f).coerceIn(0.3f, 1f))
            invalidate()
        }

        override fun onAccuracyChanged(capteur: android.hardware.Sensor?, precision: Int) = Unit
    }

    private fun armerSecousse() {
        if (ambiance == null || secousses != null) return
        val m = context.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager ?: return
        val capteur = m.getDefaultSensor(android.hardware.Sensor.TYPE_LINEAR_ACCELERATION) ?: return
        m.registerListener(ecouteSecousse, capteur, android.hardware.SensorManager.SENSOR_DELAY_GAME)
        secousses = m
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        armerSecousse()
    }

    /**
     * Avance l'ambiance du temps écoulé et la dessine dans la fenêtre, puis
     * redemande une image. La boucle s'arrête d'elle-même quand la carte
     * n'est plus dessinée : cachée, fermée ou détachée.
     */
    private fun animer(canvas: Canvas, u: Float) {
        val a = ambiance ?: return
        // Le partage dessine la carte dans un bitmap : une image figée n'a
        // pas à garder des flocons au hasard de l'instant.
        if (!canvas.isHardwareAccelerated) return
        val maintenant = System.nanoTime()
        val dt = if (instantPrecedent == 0L) 0f
        else ((maintenant - instantPrecedent) / 1e9f).coerceAtMost(0.05f)
        instantPrecedent = maintenant
        a.orienter(basX, basY)
        a.avancer(dt)
        canvas.save()
        canvas.scale(u, u)
        canvas.clipPath(decoupe)
        a.dessiner(canvas, pinceau)
        canvas.restore()
        // Une image sur deux : trente par seconde suffisent à des flocons
        // lents, et la batterie tient deux fois mieux qu'à soixante.
        if (!relancePrevue) {
            relancePrevue = true
            postOnAnimationDelayed(relance, 1000L / IMAGES_PAR_SECONDE - 8)
        }
    }

    private var relancePrevue = false
    private val relance = Runnable {
        relancePrevue = false
        invalidate()
    }

    /** La face, les rayons et le motif : tout ce que le métal recouvre. */
    private fun dessous(
        canvas: Canvas, u: Float, avecIris: Boolean, avecSujet: Boolean = true,
        avecLieu: Boolean = true, regard: Boolean = false
    ) {
        canvas.save()
        canvas.scale(u, u)
        Ornement.dessinerFace(canvas, pinceau, degradeFace, rarete, vignette)
        canvas.restore()

        Ornement.rayons(rarete, width, vignette)?.let { canvas.drawBitmap(it, 0f, 0f, null) }

        canvas.save()
        canvas.scale(u, u)
        // Le partage dessine la carte au repos : une image figée n'a pas à
        // garder l'angle de l'instant.
        motif.peindre(
            canvas, pinceau, roulis, decoupe, u, avecIris, avecSujet, avecLieu,
            if (regard) regardX else 0f, if (regard) regardY else 0f
        )
        canvas.restore()
    }

    /** Le métal et ce qui s'y pose : joyaux, médaillon, sceau, plume, saison, semis. */
    private fun dessus(canvas: Canvas, u: Float) {
        Ornement.metal(context, rarete, width, vignette)?.let { canvas.drawBitmap(it, 0f, 0f, null) }

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
            jeu?.let { Ornement.dessinerMedaillon(context, canvas, pinceau, it, Ornement.metal(rarete)) }
            pivoterLesBosses(canvas, pinceau)
        }
        if (boite >= Sonje.BOITE_ACQUISE) Ornement.dessinerSceau(canvas, pinceau, vignette)
        if (plume) Ornement.dessinerPlume(canvas, pinceau, vignette, rarete)
        souvenir?.let { Ornement.dessinerSaison(canvas, pinceau, it, rarete, vignette) }
        Ornement.dessinerSemis(canvas, pinceau, mot, rarete, vignette)
        canvas.restore()
    }

    /** Ce qui suit la main ou le doigt, et se trace donc à chaque trame. */
    private fun vivant(canvas: Canvas, u: Float) {
        canvas.save()
        canvas.scale(u, u)
        if (!vignette) ombreDeContact(canvas, pinceau)
        // La tranche par-dessus le métal : c'est le bord du carton, et le
        // cadre s'arrête dessus comme l'impression s'arrête sur la coupe.
        Ornement.dessinerTranche(canvas, pinceau, assiette, hauteurUnites)
        // Le reflet balaie le métal et la face, pas le paysage : sur un ciel
        // de nuit, une bande blanche se lit comme un brouillard. La fenêtre a
        // sa propre brillance, plus discrète (voir [Motif]).
        canvas.save()
        if (android.os.Build.VERSION.SDK_INT >= 26) canvas.clipOutPath(decoupe)
        else @Suppress("DEPRECATION") canvas.clipPath(decoupe, android.graphics.Region.Op.DIFFERENCE)
        Ornement.dessinerReflet(canvas, pinceau, rarete, roulis, vignette)
        canvas.restore()
        canvas.restore()
    }

    /**
     * Les deux couches fixes de la carte ouverte, et l'irisation entre elles.
     *
     * Posée immobile, la carte ouverte se redessinait environ vingt fois par
     * seconde sur un Galaxy A21s (2026-10-12) : le capteur d'inclinaison ne se
     * tait jamais tout à fait, et chaque écart, même invisible, faisait
     * rejouer les quelques centaines d'ordres de tracé du cadre, du motif et
     * des joyaux. 53 ms par image, dont 2 ms de carte graphique : le reste
     * était le processeur qui renvoyait les mêmes tracés. La carte tenue en
     * main tournait donc vers 18 images par seconde.
     *
     * Ce qui ne dépend ni de la main ni du doigt est enregistré une fois dans
     * deux couches, que la carte graphique garde prêtes : le dessous du métal,
     * puis le métal et ce qu'il porte. L'ambiance et l'irisation d'une très
     * rare passent entre les deux, à leur place ; la tranche, le reflet et l'ombre du
     * doigt se tracent par-dessus, comme avant.
     *
     * Rend faux quand il faut tout tracer en direct : canevas logiciel (le
     * partage dessine la carte dans un bitmap), vignette, Android avant 10,
     * ou doigt posé, puisque les joyaux et les rivets tournent alors vers lui.
     * Les couches enregistrées doigt levé restent bonnes, et resservent dès
     * qu'il repart.
     */
    private fun couches(canvas: Canvas, u: Float): Boolean {
        if (vignette || android.os.Build.VERSION.SDK_INT < 29 || !canvas.isHardwareAccelerated) return false
        if (emprise >= 0.01f) return false
        if (!motif.aSonLieu || Ornement.metal(context, rarete, width, vignette) == null) return false
        val anime = ambiance != null
        // Le lieu en plans glisse à chaque trame : il sort de la couche fixe,
        // et le dessin du mot avec lui, qui doit passer devant.
        val profond = motif.enPlans
        val vivantDessous = anime || profond
        var bas = coucheDessous
        var haut = coucheDessus
        var sujet = coucheSujet
        // Une couche qu'Android n'a pas affichée pendant quelques images est
        // vidée de son contenu : c'est le cas après un appui du doigt, où la
        // carte se trace en direct. Sur la 36.3.4, la carte revenait alors
        // sans son corps, texte et reflets seuls sur la grille (Galaxy A21s,
        // 2026-10-12). Elle se réenregistre donc dès qu'une couche est vide.
        if (bas == null || haut == null || (vivantDessous && sujet == null) ||
            !bas.hasDisplayList() || !haut.hasDisplayList() || (vivantDessous && sujet?.hasDisplayList() != true) ||
            largeurDesCouches != width || hauteurDesCouches != height || profondDesCouches != profond
        ) {
            bas = (bas ?: android.graphics.RenderNode("carte-dessous")).also {
                it.setPosition(0, 0, width, height)
                it.setUseCompositingLayer(true, null)
                val c = it.beginRecording(width, height)
                try { dessous(c, u, avecIris = false, avecSujet = !vivantDessous, avecLieu = !profond) } finally { it.endRecording() }
            }
            // Avec une ambiance, le dessin du mot a sa propre couche, posée
            // par-dessus la neige.
            if (vivantDessous) sujet = (sujet ?: android.graphics.RenderNode("carte-sujet")).also {
                it.setPosition(0, 0, width, height)
                it.setUseCompositingLayer(true, null)
                val c = it.beginRecording(width, height)
                try {
                    c.scale(u, u)
                    motif.dessusDuLieu(c, pinceau, roulis, decoupe, avecSujet = true, avecIris = false)
                } finally { it.endRecording() }
            }
            haut = (haut ?: android.graphics.RenderNode("carte-dessus")).also {
                it.setPosition(0, 0, width, height)
                it.setUseCompositingLayer(true, null)
                val c = it.beginRecording(width, height)
                try { dessus(c, u) } finally { it.endRecording() }
            }
            coucheDessous = bas
            coucheDessus = haut
            coucheSujet = sujet
            largeurDesCouches = width
            hauteurDesCouches = height
            profondDesCouches = profond
        }
        canvas.drawRenderNode(bas)
        if (profond) {
            canvas.save()
            canvas.scale(u, u)
            motif.peindreLieu(canvas, decoupe, u, regardX, regardY)
            canvas.restore()
        }
        if (anime) animer(canvas, u)
        if (vivantDessous) sujet?.let { canvas.drawRenderNode(it) }
        if (motif.irise) {
            canvas.save()
            canvas.scale(u, u)
            motif.dessusDuLieu(canvas, pinceau, roulis, decoupe, avecSujet = false, avecIris = true)
            canvas.restore()
        }
        canvas.drawRenderNode(haut)
        return true
    }

    private var coucheDessous: android.graphics.RenderNode? = null
    private var coucheDessus: android.graphics.RenderNode? = null
    private var coucheSujet: android.graphics.RenderNode? = null
    private var largeurDesCouches = 0
    private var hauteurDesCouches = 0
    private var profondDesCouches = false

    /** À appeler quand une partie fixe change : les couches se réenregistrent. */
    private fun oublierLesCouches() {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            coucheDessous?.discardDisplayList()
            coucheDessus?.discardDisplayList()
            coucheSujet?.discardDisplayList()
        }
        coucheDessous = null
        coucheDessus = null
        coucheSujet = null
    }

    override fun onDetachedFromWindow() {
        oublierLesCouches()
        instantPrecedent = 0L
        removeCallbacks(relance)
        relancePrevue = false
        secousses?.unregisterListener(ecouteSecousse)
        secousses = null
        super.onDetachedFromWindow()
    }

    /*
     * Points de départ, à régler au pouce sur un appareil : aucun n'a encore
     * été vérifié ailleurs qu'à l'œil sur le papier.
     */
    private companion object {
        /** La cadence de l'ambiance : voir [animer]. */
        const val IMAGES_PAR_SECONDE = 30

        /**
         * L'accélération, sans la pesanteur, au-delà de laquelle on secoue,
         * en m/s² : nettement au-dessus d'une marche ou d'un téléphone qu'on
         * repose (3 à 6), atteinte par tout coup de poignet volontaire.
         */
        const val SEUIL_SECOUSSE = 12f

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
 * L'illustration d'une carte : un lieu du pays à l'heure de sa rareté, et le
 * sujet du mot posé devant.
 *
 * ## Ce qui a remplacé la matière et la partition
 *
 * La fenêtre portait une matière teintée par le champ, divisée selon la nature
 * du mot (coupé, tranché, chevron), puis le sujet. Elle porte maintenant un
 * **lieu** par champ ([Lieu]) et une **heure** par rareté ([Heure]) :
 *
 * - le lieu dit le champ par une forme et plus seulement par une teinte, ce
 *   qui était justement le service que la partition rendait ; la nature, elle,
 *   reste écrite en toutes lettres dans la pastille sous la plaque ;
 * - l'heure dit la rareté d'un coup d'œil : matin, après-midi, soir, nuit ;
 * - la saison dit le jour où la carte a été gagnée ([Souvenir], [Habillage]) :
 *   neige, fleurs, chaleur ou feuilles, et la fête du jour s'il y en avait une.
 *
 * Le lieu est un bitmap partagé ([Paysages.image]) : il ne dépend pas du mot,
 * trente vignettes d'un même tiroir n'en paient qu'un. Le sujet, lui, se trace
 * à chaque trame, dans la boîte 100 × 66 du paysage, et l'irisation d'une très
 * rare passe par-dessus le tout quand l'appareil tourne.
 */
class Motif(
    mot: String,
    rarete: Rarete,
    private val zone: RectF,
    blason: Blasonnement = Blasonnement.AUCUN,
    private val souvenir: Souvenir? = null,
    /**
     * Pour une vignette : de quoi se redessiner quand son paysage, rendu en
     * arrière-plan, arrive (voir [Paysages.demander]). Sans lui, le rendu est
     * synchrone, comme sur la carte ouverte.
     */
    private val surPaysage: (() -> Unit)? = null
) {

    private val teinteChamp: Float? = blason.champ?.hue
    private val lieu = Lieu.de(blason.champ)
    private val heure = Heure.de(rarete, teinteChamp)
    /**
     * La part de la fenêtre qu'on voit vraiment. Sur la carte ouverte, la
     * plaque du nom recouvre le bas de l'ouverture : le lieu se cadre sur ce
     * qui reste, sinon la cascade et le dessin passeraient sous la plaque.
     */
    private val visible = if (zone == Ornement.FENETRE)
        RectF(zone.left, zone.top, zone.right, min(zone.bottom, Ornement.PLAQUE.top + 4f)) else RectF(zone)
    private val cadrage = Paysages.cadrage(visible.width(), visible.height())
    private val iris = Irisation.pour(rarete, zone, Ornement.teinteDe(mot, blason.champ))
    private val peinture = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var rendu: Bitmap? = null
    private var demande = ""

    /**
     * Vrai pour la carte ouverte qui vit (lecteur, pochette) : le lieu se
     * rend alors en trois plans qui glissent avec l'inclinaison, voir
     * [Paysages.plans]. À poser avant [preparer].
     */
    var profondeur = false
    private var plans: Array<Bitmap>? = null
    private var plansManques = false
    private val ouPlan = RectF()

    /** Vrai quand le lieu est en plans, et donc à peindre à chaque trame. */
    val enPlans: Boolean get() = profondeur && plans != null
    private val ciel: LinearGradient by lazy {
        LinearGradient(
            0f, visible.top, 0f, visible.bottom,
            intArrayOf(heure.cielHaut, heure.cielMilieu ?: heure.cielBas, heure.cielBas),
            null, Shader.TileMode.CLAMP
        )
    }

    /**
     * Rend le lieu à l'échelle [echelle] sur un fil de fond, puis appelle
     * [pret] par [principal]. Voir [CarteOrnee.attendreSaMatiere].
     */
    fun preparer(echelle: Float, principal: android.os.Handler, pret: () -> Unit) {
        Thread({
            val etages = if (profondeur) rendrePlans(echelle) else null
            val image = if (etages == null) rendreLieu(echelle) else null
            principal.post {
                if (etages != null) plans = etages
                if (image != null) rendu = image
                pret()
            }
        }, "paysage").start()
    }

    private fun rendrePlans(echelle: Float): Array<Bitmap>? {
        val l = (visible.width() * echelle).toInt()
        val h = (visible.height() * echelle).toInt()
        return try {
            Paysages.plans(lieu, heure, teinteChamp, l, h, souvenir)
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /**
     * Demande le lieu à l'échelle [echelle] aux fils de fond de [Paysages],
     * depuis n'importe quel fil, et appelle [pret] quand il est en cache.
     * Une vignette qui se dessine ensuite le trouve, ou rejoint la demande en
     * vol au lieu d'en lancer une seconde.
     */
    fun demanderLieu(echelle: Float, pret: () -> Unit) {
        val l = (visible.width() * echelle).toInt()
        val h = (visible.height() * echelle).toInt()
        if (Paysages.enCache(lieu, heure, teinteChamp, l, h, souvenir) != null) {
            pret()
            return
        }
        Paysages.demander(lieu, heure, teinteChamp, l, h, souvenir) { pret() }
    }

    /**
     * Rend le lieu à l'échelle [echelle] sur le fil appelant, et le met dans
     * le cache de [Paysages]. `null` si la mémoire manque.
     */
    fun rendreLieu(echelle: Float): Bitmap? {
        val l = (visible.width() * echelle).toInt()
        val h = (visible.height() * echelle).toInt()
        return try {
            Paysages.image(lieu, heure, teinteChamp, l, h, souvenir)
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    /** Le lieu rendu à [l] × [h], ou `null` s'il est en route vers une vignette. */
    private fun lieuAPeindre(l: Int, h: Int): Bitmap? {
        val signal = surPaysage ?: return Paysages.image(lieu, heure, teinteChamp, l, h, souvenir)
        Paysages.enCache(lieu, heure, teinteChamp, l, h, souvenir)?.let { return it }
        val taille = "${l}x$h"
        if (demande == taille) return null
        demande = taille
        Paysages.demander(lieu, heure, teinteChamp, l, h, souvenir) { bitmap ->
            if (bitmap.width == l && bitmap.height == h) rendu = bitmap
            signal()
        }
        return null
    }

    /**
     * Le meuble s'il y en a un, le tracé sinon — et jamais les deux.
     *
     * Un nom de meuble que [Meubles] ne connaît pas retombe sur le tracé sans
     * bruit, ce qui permet à l'actif et à la bibliothèque d'avancer chacun à
     * son rythme.
     */
    private val sujet: Sujet =
        blason.meuble?.let { Enluminure.pour(it, heure) } ?: Trace.pour(mot, heure)

    /**
     * Peint le motif dans son ouverture. [echelle] est le nombre de pixels par
     * unité de carte : le lieu est rendu à la taille réelle de la fenêtre,
     * sinon il serait flou sur un grand écran.
     */
    fun peindre(
        c: Canvas, p: Paint, roulis: Float, decoupe: Path, echelle: Float,
        avecIris: Boolean = true, avecSujet: Boolean = true,
        avecLieu: Boolean = true, regardX: Float = 0f, regardY: Float = 0f
    ) {
        c.save()
        c.clipPath(decoupe)
        val l = (visible.width() * echelle).toInt()
        val h = (visible.height() * echelle).toInt()
        // Sous la plaque, le sol du lieu : rien ne doit percer entre ses bords.
        peinture.color = heure.pres
        c.drawRect(zone, peinture)
        val etages = if (profondeur && avecLieu) plansAPeindre(l, h) else null
        // La carte garde son paysage : le cache peut l'évincer pendant qu'une
        // grille défile, la carte ne le redessine pas pour autant.
        val image = if (!avecLieu || etages != null) null
            else rendu?.takeIf { it.width == l && it.height == h && !it.isRecycled }
            ?: lieuAPeindre(l, h)?.also { rendu = it }
        if (!avecLieu) {
            // Le lieu viendra par [peindreLieu], à chaque trame.
        } else if (etages != null) {
            peindrePlans(c, etages, l, h, regardX, regardY)
        } else if (image != null) {
            c.drawBitmap(image, null, visible, peinture)
        } else {
            // Le ciel de l'heure, le temps que le lieu arrive.
            peinture.shader = ciel
            c.drawRect(visible, peinture)
            peinture.shader = null
        }
        if (avecSujet) peindreSujet(c)
        if (avecIris) iris?.dessus(c, p, roulis)
        c.restore()
    }

    /** Les plans à la bonne taille, rendus sur place s'il le faut, ou `null` s'ils n'existent pas (fête). */
    private fun plansAPeindre(l: Int, h: Int): Array<Bitmap>? {
        plans?.takeIf {
            it[0].width == l + 2 * Paysages.marge(0, l) && it[0].height == h + 2 * Paysages.marge(0, h) &&
                it.none { b -> b.isRecycled }
        }?.let { return it }
        if (plansManques) return null
        return try {
            Paysages.plans(lieu, heure, teinteChamp, l, h, souvenir)?.also { plans = it }
        } catch (e: OutOfMemoryError) {
            // La carte retombe sur son lieu à plat, sans réessayer à chaque trame.
            plansManques = true
            null
        }
    }

    /**
     * Chaque calque déborde de la fenêtre ([Paysages.marge]) : on le peint
     * centré sur elle, à l'échelle de la fenêtre, puis décalé selon le regard.
     */
    private fun peindrePlans(c: Canvas, etages: Array<Bitmap>, l: Int, h: Int, regardX: Float, regardY: Float) {
        val sx = visible.width() / l
        val sy = visible.height() / h
        for (i in etages.indices) {
            val dx = Paysages.GLISSEMENT[i] * visible.width() * regardX
            val dy = Paysages.GLISSEMENT[i] * visible.height() * regardY
            val mx = Paysages.marge(i, l) * sx
            val my = Paysages.marge(i, h) * sy
            ouPlan.set(
                visible.left - mx + dx, visible.top - my + dy,
                visible.right + mx + dx, visible.bottom + my + dy
            )
            c.drawBitmap(etages[i], null, ouPlan, peinture)
        }
    }

    /**
     * Le lieu seul, en plans décalés selon le regard : ce que [peindre] tait
     * quand `avecLieu` est faux. Rien si le lieu n'est pas en plans.
     */
    fun peindreLieu(c: Canvas, decoupe: Path, echelle: Float, regardX: Float, regardY: Float) {
        val l = (visible.width() * echelle).toInt()
        val h = (visible.height() * echelle).toInt()
        val etages = plansAPeindre(l, h) ?: return
        c.save()
        c.clipPath(decoupe)
        peindrePlans(c, etages, l, h, regardX, regardY)
        c.restore()
    }

    private fun peindreSujet(c: Canvas) {
        c.save()
        c.translate(visible.left + cadrage[1], visible.top + cadrage[2])
        c.scale(cadrage[0], cadrage[0])
        sujet.peindre(c)
        c.restore()
    }

    /** Vrai pour une très rare : son irisation suit l'inclinaison, le reste du motif non. */
    val irise: Boolean get() = iris != null

    /** Vrai quand le lieu est là : sans lui, le motif n'est qu'un ciel d'attente. */
    val aSonLieu: Boolean get() = rendu != null || plans != null

    /**
     * Ce que [peindre] tait quand `avecSujet` ou `avecIris` est faux : le
     * sujet, puis l'irisation, dans l'ouverture et à leur ordre d'origine.
     */
    fun dessusDuLieu(c: Canvas, p: Paint, roulis: Float, decoupe: Path, avecSujet: Boolean, avecIris: Boolean) {
        if (!avecSujet && (!avecIris || iris == null)) return
        c.save()
        c.clipPath(decoupe)
        if (avecSujet) peindreSujet(c)
        if (avecIris) iris?.dessus(c, p, roulis)
        c.restore()
    }
}

/**
 * Ce que la **très rare** pose par-dessus la nuit : une irisation et une
 * brillance qui suivent l'inclinaison de l'appareil.
 *
 * C'est ce qui reste de l'ancienne matière : le grain de la peu commune et le
 * halo de la rare sont devenus l'après-midi et le soir du paysage.
 */
private class Irisation private constructor(private val zone: RectF, private val teinte: Float) {

    private val brillance: LinearGradient
    private val matrice = Matrix()
    private val disque = RectF()

    init {
        brillance = LinearGradient(
            zone.left, zone.bottom, zone.right, zone.top,
            intArrayOf(Color.TRANSPARENT, Color.argb(48, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0.38f, 0.50f, 0.62f), Shader.TileMode.CLAMP
        )
        val rayon = Math.hypot(zone.width() / 2.0, zone.height() / 2.0).toFloat()
        disque.set(zone.centerX() - rayon, zone.centerY() - rayon, zone.centerX() + rayon, zone.centerY() + rayon)
    }

    fun dessus(c: Canvas, p: Paint, roulis: Float) {
        c.save()
        c.clipRect(zone)
        // La roue commence à la teinte du mot : c'est la roue commune, tournée
        // d'autant dans l'autre sens.
        c.rotate(roulis * 55f - teinte, zone.centerX(), zone.centerY())
        p.shader = null
        p.style = Paint.Style.FILL
        // Faible et saturée : sur la nuit, une irisation pâle se lit comme
        // un brouillard blanc, pas comme un reflet.
        p.alpha = 22
        p.isFilterBitmap = true
        c.drawBitmap(IRIS, null, disque, p)
        p.isFilterBitmap = false
        p.alpha = 255
        c.restore()
        matrice.setTranslate(roulis * zone.width() * 0.45f, 0f)
        brillance.setLocalMatrix(matrice)
        p.shader = brillance
        c.drawRect(zone, p)
        p.shader = null
    }

    companion object {
        /**
         * Un tour complet du cercle, saturation basse, la teinte 0 à droite.
         * Calculé pixel par pixel et non en SweepGradient : sa couture
         * laissait une rangée de pixels corrompus, un trait jaune du centre
         * vers le bord.
         *
         * Une seule roue pour toutes les cartes. Chaque très rare calculait la
         * sienne, décalée de sa teinte, et la grille du carnet en construisait
         * neuf d'un coup : 367 ms sur le fil principal d'un Galaxy A21s
         * (2026-10-12), l'essentiel de l'à-coup à l'apparition de la grille.
         */
        private val IRIS: Bitmap by lazy {
            val cote = 96
            val demi = cote / 2f
            val pixels = IntArray(cote * cote)
            val hsv = floatArrayOf(0f, 0.75f, 1f)
            for (y in 0 until cote) {
                for (x in 0 until cote) {
                    val angle = Math.atan2((y + 0.5f - demi).toDouble(), (x + 0.5f - demi).toDouble())
                    val tour = ((angle / (2.0 * Math.PI)) + 1.0) % 1.0
                    hsv[0] = (360f * tour.toFloat()) % 360f
                    pixels[y * cote + x] = Color.HSVToColor(hsv)
                }
            }
            Bitmap.createBitmap(pixels, cote, cote, Bitmap.Config.ARGB_8888)
        }

        fun pour(rarete: Rarete, zone: RectF, teinte: Float): Irisation? =
            if (rarete == Rarete.TRES_RARE) Irisation(zone, teinte) else null
    }
}

/**
 * Ce que le **mot** met dans la fenêtre, devant le paysage, dans la boîte
 * 100 × 66 de [Paysages].
 *
 * Les deux sujets partagent leur lumière : un halo derrière eux, qui les
 * détache du lieu, une ombre portée vers le bas à droite (la lumière du carnet
 * vient d'en haut à gauche), et une **nacre** dont la couleur suit l'heure,
 * blanche le matin, dorée la nuit.
 */
private abstract class Sujet(protected val heure: Heure, cadre: RectF) {

    protected val plume = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val nacre: LinearGradient
    private val halo: RadialGradient

    /** L'ombre portée : plus franche la nuit, où le sujet se détache sur le sombre. */
    protected val ombre = argb(if (heure.nuit) 150 else 97, 0, 0, 0)

    init {
        val b = cadre
        nacre = LinearGradient(
            b.left, b.top, b.left + b.width() * 0.7f, b.bottom,
            heure.nacre, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        val couleurHalo = when {
            heure.nuit -> 0xFFE9A8
            heure.rang == 2 -> 0xFFE0B0
            else -> 0xFFFFFF
        }
        val opacite = intArrayOf(107, 115, 110, 64)[heure.rang]
        halo = RadialGradient(
            50f, 38f, 27f,
            intArrayOf((opacite shl 24) or couleurHalo, couleurHalo),
            null, Shader.TileMode.CLAMP
        )
    }

    protected abstract fun tracer(c: Canvas)

    fun peindre(c: Canvas) {
        plume.style = Paint.Style.FILL
        plume.shader = halo
        c.save()
        c.scale(1f, 24f / 27f, 50f, 38f)
        c.drawCircle(50f, 38f, 27f, plume)
        c.restore()
        plume.shader = null
        tracer(c)
    }

    protected companion object {
        fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}

/**
 * L'enluminure : le meuble du mot, en nacre, posé au sol devant le paysage.
 *
 * C'est le sujet des cartes qu'on collectionne pour elles-mêmes — cent vingt
 * mots sur deux mille huit cents, à peu près quatre sur cent. La rareté
 * n'y est pour rien : elle est fixée par le rang de fréquence, alors que
 * l'enluminure est un second axe de désirabilité, qui peut échoir à une
 * commune comme à une très rare.
 *
 * Un liseré sombre la sépare du ciel : sans lui, une nacre blanche se perd
 * dans le ciel clair du matin.
 */
private class Enluminure private constructor(
    private val trace: Path,
    private val nom: String,
    heure: Heure
) : Sujet(heure, RectF(CX - TAILLE / 2f, CY - TAILLE / 2f, CX + TAILLE / 2f, CY + TAILLE / 2f)) {

    override fun tracer(c: Canvas) {
        // L'ombre au sol, qui pose le dessin dans le lieu au lieu de le coller dessus.
        plume.style = Paint.Style.FILL
        plume.color = argb(if (heure.nuit) 115 else 64, 0, 0, 0)
        c.drawOval(CX - 13f, SOL - 1.8f, CX + 13f, SOL + 1.8f, plume)

        c.save()
        c.translate(0.6f, 1f)
        plume.color = ombre
        c.drawPath(trace, plume)
        c.restore()

        plume.color = Color.BLACK
        plume.shader = nacre
        c.drawPath(trace, plume)
        plume.shader = null

        plume.style = Paint.Style.STROKE
        plume.strokeWidth = 0.35f
        plume.strokeJoin = Paint.Join.ROUND
        plume.color = heure.encre
        plume.alpha = 200
        c.drawPath(trace, plume)
        plume.style = Paint.Style.FILL
        plume.alpha = 255

        // Le chat de la nuit a les yeux qui brillent.
        if (nom == "chat" && heure.nuit) {
            val e = TAILLE / Meubles.COTE
            for (s in floatArrayOf(-1f, 1f)) {
                val x = CX + s * 13f * e
                val y = CY - 2f * e
                plume.color = argb(64, 0xFF, 0xD3, 0x4D)
                c.drawCircle(x, y, 2.6f, plume)
                plume.color = argb(255, 0xFF, 0xD3, 0x4D)
                c.drawOval(x - 1.1f, y - 0.75f, x + 1.1f, y + 0.75f, plume)
            }
        }
    }

    companion object {
        /** Le dessin tient dans un carré de [TAILLE] centré en ([CX], [CY]), posé sur [SOL]. */
        private const val TAILLE = 31f
        private const val CX = 50f
        private const val CY = 43f
        private const val SOL = 59f

        /** `null` si la bibliothèque ne connaît pas ce meuble. */
        fun pour(nom: String, heure: Heure): Enluminure? {
            val forme = Meubles.chemin(nom) ?: return null
            val m = Matrix()
            m.setScale(TAILLE / Meubles.COTE, TAILLE / Meubles.COTE)
            m.postTranslate(CX, CY)
            val trace = Path()
            forme.transform(m, trace)
            // Sans « pair-impair », la porte d'une maison cesse d'être un trou.
            trace.fillType = Path.FillType.EVEN_ODD
            return Enluminure(trace, nom, heure)
        }
    }
}

/**
 * Le tracé du mot : sa sonorité, lue lettre à lettre, posée en ruban de nacre
 * devant le paysage.
 *
 * ## La règle
 *
 * Voyelle en haut, consonne en bas, la hauteur affinée par le code du
 * caractère ; un nœud sur chaque voyelle, qui donne la scansion. Le pas est
 * **constant et calé à gauche**, jamais étiré sur la largeur : deux formes qui
 * partagent leur début partagent alors leurs points *exactement*, au lieu de
 * seulement se ressembler. Dans une grille, `Woch`, `Wochen` et `Woche` se
 * lisent comme un seul mot à trois états.
 *
 * La règle n'a pas changé avec les paysages, seule la matière du trait a
 * changé : nacre de l'heure, ombre portée, fil de lumière, et à partir de
 * *Rare* une lueur de la couleur des fenêtres allumées. Le geste du doigt sur
 * le clavier a été essayé à la place, et écarté : il dit où sont les touches,
 * la sonorité dit comment sonne le mot.
 *
 * Rien ne coûte : pas un octet d'actif, et le tracé vaut pour toutes les
 * formes du carnet. C'est un monogramme, pas
 * une illustration : il montre **le mot**, pas **la chose**.
 */
private class Trace private constructor(
    private val xs: FloatArray,
    private val ys: FloatArray,
    private val voyelle: BooleanArray,
    heure: Heure
) : Sujet(heure, boite(xs, ys)) {

    private val chemin = Path()
    private val luit = heure.rang >= 2

    /**
     * Un mot long doit maigrir, sinon ses nœuds se recouvrent. Sept lettres
     * est la longueur de référence.
     */
    private val maigreur = (7f / maxOf(xs.size, 7)).coerceAtMost(1f)
    private val epaisseur = 3.2f * maigreur
    private val rayon = 2.5f * maigreur
    private val coeur = 1.05f * maigreur

    init {
        val n = xs.size
        if (n >= 2) {
            // Deux quadratiques par segment, par le milieu : la courbe passe
            // par chaque lettre sans le dépassement qu'une seule donnerait.
            chemin.moveTo(xs[0], ys[0])
            for (i in 0 until n - 1) {
                val ax = xs[i]; val ay = ys[i]
                val bx = xs[i + 1]; val by = ys[i + 1]
                chemin.quadTo(ax + (bx - ax) * 0.55f, ay, (ax + bx) / 2f, (ay + by) / 2f)
                chemin.quadTo(bx - (bx - ax) * 0.55f, by, bx, by)
            }
        }
        plume.strokeCap = Paint.Cap.ROUND
        plume.strokeJoin = Paint.Join.ROUND
    }

    override fun tracer(c: Canvas) {
        val n = xs.size
        if (n == 0) return
        plume.shader = null
        plume.style = Paint.Style.STROKE

        if (luit && n >= 2) {
            plume.color = heure.fenetre
            plume.alpha = 71
            plume.strokeWidth = epaisseur * 2.1f
            c.drawPath(chemin, plume)
            plume.alpha = 255
        }

        // L'ombre, puis le ruban, puis son fil de lumière.
        c.save()
        c.translate(0.5f, 0.9f)
        plume.color = ombre
        plume.strokeWidth = epaisseur
        if (n >= 2) c.drawPath(chemin, plume)
        plume.style = Paint.Style.FILL
        for (i in 0 until n) if (voyelle[i] || n == 1) c.drawCircle(xs[i], ys[i], rayon, plume)
        c.restore()

        if (n >= 2) {
            plume.style = Paint.Style.STROKE
            plume.color = Color.BLACK
            plume.shader = nacre
            c.drawPath(chemin, plume)
            plume.shader = null
            c.save()
            c.translate(-0.4f, -0.5f)
            plume.color = argb(191, 255, 255, 255)
            plume.strokeWidth = 0.8f * maigreur
            c.drawPath(chemin, plume)
            c.restore()
        }

        // Un nœud sur chaque voyelle : la scansion du mot.
        plume.style = Paint.Style.FILL
        for (i in 0 until n) {
            if (!voyelle[i] && n > 1) continue
            plume.color = Color.BLACK
            plume.shader = nacre
            c.drawCircle(xs[i], ys[i], rayon, plume)
            plume.shader = null
            plume.color = heure.encre
            c.drawCircle(xs[i], ys[i], coeur, plume)
        }
    }

    companion object {
        /**
         * Les voyelles, diacritiques compris.
         *
         * Les accents du kréyòl s'ajoutent à ceux du luxembourgeois : `ò` est
         * la voyelle accentuée la plus courante de la langue (`kréyòl`,
         * `mòso`), et sans elle le tracé la prendrait pour une consonne — le
         * monogramme de `kréyòl` s'en trouverait faux.
         */
        private const val VOYELLES = "aeiouyàáâäèéêëìíîïòóôöùúûü"

        /**
         * Où tombe le tracé dans la boîte 100 × 66 du paysage : entre l'arche
         * des paliers hauts et le sol, et dans la largeur qu'une vignette
         * garde une fois rognée sur les côtés.
         */
        private const val X0 = 16f
        private const val LARGEUR = 68f
        private const val MILIEU = 36f
        private const val AMPLITUDE = 13f

        fun pour(mot: String, heure: Heure): Trace {
            val lettres = mot.toCharArray()
            val n = lettres.size
            // Un mot de sept lettres occupe toute la largeur ; un plus court
            // s'arrête avant, un plus long resserre son pas.
            val pas = LARGEUR / (maxOf(n, 7) - 1).toFloat()
            val xs = FloatArray(n)
            val ys = FloatArray(n)
            val voyelle = BooleanArray(n)
            for (i in 0 until n) {
                val ch = lettres[i]
                voyelle[i] = ch.lowercaseChar() in VOYELLES
                xs[i] = if (n == 1) 50f else X0 + pas * i
                ys[i] = MILIEU + (if (voyelle[i]) -1f else 1f) *
                    AMPLITUDE * (0.42f + 0.58f * ((ch.code % 7) / 6f))
            }
            return Trace(xs, ys, voyelle, heure)
        }

        /** La boîte des lettres, qui oriente la nacre du ruban. */
        private fun boite(xs: FloatArray, ys: FloatArray): RectF {
            if (xs.isEmpty()) return RectF(40f, 30f, 60f, 50f)
            val r = RectF(xs[0], ys[0], xs[0], ys[0])
            for (i in 1 until xs.size) r.union(xs[i], ys[i])
            r.inset(-2f, -2f)
            return r
        }
    }
}
