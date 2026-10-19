package com.example.kreyolkeyboard.carnet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * La saison posée sur un paysage déjà peint : « la carte se souvient du jour
 * où on l'a gagnée » (propriétaire, 2026-10-11).
 *
 * Deux passes, reprises de la maquette validée (artefact « Cartes à
 * collectionner ») :
 *
 * - **le fond** change la matière du lieu, pixel par pixel : l'hiver
 *   désature et blanchit la végétation puis pose la neige sur les crêtes et
 *   les toits ; le printemps verdit et sème des fleurs ; l'été réchauffe ;
 *   l'automne roussit le feuillage par taches ;
 * - **le dessus** passe devant : flocons, pétales, oiseaux, brume et
 *   feuilles, puis la fête du jour s'il y en a une (feu d'artifice, fanions
 *   aux couleurs du drapeau, guirlandes de la Fouer, lumières du marché de
 *   Noël, feu du Buergbrennen).
 *
 * ## La végétation se reconnaît à la palette de l'heure
 *
 * Le paysage n'a pas de calque « arbres » : un vert se reconnaît à sa
 * distance aux segments de couleur que l'heure emploie pour la colline et le
 * premier plan. Un simple seuil de teinte ne marchait pas : l'après-midi peint
 * ses collines en ocre et le soir en violet, la végétation n'y est plus verte.
 *
 * ## Les mesures
 *
 * La maquette travaillait sur 480 pixels de large, soit 4,8 pixels par unité
 * de la boîte 100 × 66. Toutes les tailles sont reprises de là et mises à
 * l'échelle par [u] ; les tirages au sort ont une graine fixe par lieu, heure
 * et souvenir, si bien que deux cartes du même hiver partagent leur rendu.
 */
internal object Habillage {

    private const val SEUIL_MASQUE = 100
    private const val PIXELS_MAQUETTE_PAR_UNITE = 4.8f
    private const val BITS_TABLE = 13
    private const val TABLE = 1 shl BITS_TABLE

    /**
     * Habille [b], le lieu déjà posé sur son ciel. [calque] est le même lieu
     * seul, sur fond transparent : là où son alpha est plein, il y a une
     * colline, un toit, une berge, et c'est ce qui pose la neige sur les
     * crêtes sans fleurir le ciel.
     */
    fun appliquer(b: Bitmap, calque: Bitmap, lieu: Lieu, heure: Heure, s: Souvenir, k: FloatArray) {
        val w = b.width
        val h = b.height
        val u = k[0] / PIXELS_MAQUETTE_PAR_UNITE
        val mk = IntArray(w * h)
        calque.getPixels(mk, 0, w, 0, 0, w, h)
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        val graine = lieu.ordinal * 1009 + s.saison.ordinal * 101 + ((s.fete?.ordinal ?: -1) + 1) * 13 + heure.rang
        val alea = Paysages.Alea(graine)
        val fleurs = fond(px, mk, w, h, u, heure, s.saison, alea)
        b.setPixels(px, 0, w, 0, 0, w, h)
        val c = Canvas(b)
        dessus(c, w.toFloat(), h.toFloat(), u, heure, s.saison, alea, fleurs)
        s.fete?.let { fete(c, w, h, u, heure, it, mk, alea) }
    }

    /**
     * La même chose sur les trois calques de [Paysages.plans], chacun avec
     * son propre masque et sa propre échelle ([ks]).
     *
     * La matière change plan par plan, et les fleurs du printemps restent sur
     * le plan qui les porte. La neige se pose sur le haut de chaque plan, si
     * bien que les toits du milieu en prennent aussi quand ils passent devant
     * une colline. Ce qui flotte (flocons, pétales, feuilles, oiseaux) va sur
     * le calque proche, par-dessus tout, comme sur le rendu à plat. Jamais de
     * fête ici : voir [Paysages.plans].
     */
    fun appliquerPlans(
        couches: Array<Bitmap>, masques: Array<Bitmap>, lieu: Lieu, heure: Heure, s: Souvenir, ks: Array<FloatArray>
    ) {
        val graine = lieu.ordinal * 1009 + s.saison.ordinal * 101 + heure.rang
        val alea = Paysages.Alea(graine)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in couches.indices) {
            val b = couches[i]
            val w = b.width
            val h = b.height
            val u = ks[i][0] / PIXELS_MAQUETTE_PAR_UNITE
            val mk = IntArray(w * h)
            masques[i].getPixels(mk, 0, w, 0, 0, w, h)
            val px = IntArray(w * h)
            b.getPixels(px, 0, w, 0, 0, w, h)
            val fleurs = fond(px, mk, w, h, u, heure, s.saison, alea)
            b.setPixels(px, 0, w, 0, 0, w, h)
            fleurir(Canvas(b), p, u, alea, fleurs)
        }
        val devant = couches.last()
        // Sans les oiseaux : sur la carte en plans, ils volent (voir Ambiance).
        dessus(Canvas(devant), devant.width.toFloat(), devant.height.toFloat(), ks.last()[0] / PIXELS_MAQUETTE_PAR_UNITE,
            heure, s.saison, alea, FloatArray(0), oiseaux = false)
    }

    private fun objet(mk: IntArray, i: Int) = (mk[i] ushr 24) > SEUIL_MASQUE

    // ------------------------------------------------------------ le fond

    private fun fond(
        px: IntArray, mk: IntArray, w: Int, h: Int, u: Float,
        P: Heure, saison: Saison, alea: Paysages.Alea
    ): FloatArray {
        val nuit = if (P.nuit) 0.55f else 1f
        val sg = FloatArray(18)
        intArrayOf(
            P.milieu, P.pres, P.loin, P.milieu,
            Heure.melange(P.milieu, 0xFF6F8F3E.toInt(), 0.35f), Heure.melange(P.milieu, 0xFF4F7D3A.toInt(), 0.35f)
        ).forEachIndexed { j, col ->
            sg[j * 3] = r(col).toFloat(); sg[j * 3 + 1] = g(col).toFloat(); sg[j * 3 + 2] = bl(col).toFloat()
        }
        fun calculer(rr: Float, gg: Float, bb: Float): Boolean {
            for (o in 0 until 18 step 6) {
                val ux = sg[o + 3] - sg[o]; val uy = sg[o + 4] - sg[o + 1]; val uz = sg[o + 5] - sg[o + 2]
                val uu = (ux * ux + uy * uy + uz * uz).takeIf { it > 0f } ?: 1f
                val t = (((rr - sg[o]) * ux + (gg - sg[o + 1]) * uy + (bb - sg[o + 2]) * uz) / uu).coerceIn(0f, 1f)
                val dx = sg[o] + ux * t - rr; val dy = sg[o + 1] + uy * t - gg; val dz = sg[o + 2] + uz * t - bb
                if (dx * dx + dy * dy + dz * dz < 900f) return true
            }
            return false
        }
        // Le paysage est fait d'aplats : quelques centaines de couleurs pour des
        // dizaines de milliers de pixels. La réponse se garde par couleur, dans
        // une table à adressage ouvert, sans objet par pixel.
        val cles = IntArray(TABLE)
        val reponses = BooleanArray(TABLE)
        var remplies = 0
        fun vegetal(c: Int): Boolean {
            val cle = (c and 0xFFFFFF) or 0x1000000
            var h = (cle * -0x61C88647) ushr (32 - BITS_TABLE)
            while (true) {
                val k = cles[h]
                if (k == cle) return reponses[h]
                if (k == 0) {
                    val v = calculer(r(c).toFloat(), g(c).toFloat(), bl(c).toFloat())
                    if (remplies < TABLE * 3 / 4) { cles[h] = cle; reponses[h] = v; remplies++ }
                    return v
                }
                h = (h + 1) and (TABLE - 1)
            }
        }
        val rouille = arrayOf(intArrayOf(214, 118, 38), intArrayOf(186, 64, 36), intArrayOf(230, 176, 58), intArrayOf(160, 92, 40))
        // Les taches de l'automne mesurent deux unités et demie de la boîte,
        // quelle que soit la taille du rendu : des blocs de quelques pixels se
        // lisaient en hachures sur la carte et en confettis sur la vignette.
        val tache = max(3f, 12f * u)
        // Les deux bruits de l'automne, tirés une fois par nœud de leur grille
        // plutôt que quatre fois par pixel : la carte ouverte passait la moitié
        // de son habillage d'automne à refaire les mêmes tirages (Galaxy A21s,
        // 2026-10-18). Le résultat est le même au bit près.
        val grilleX = ((w - 1) / tache).toInt() + 2
        val grilleY = ((h - 1) / tache).toInt() + 2
        val bruit17 = if (saison == Saison.HIERSCHT) noeuds(grilleX, grilleY, 17) else FloatArray(0)
        val bruit71 = if (saison == Saison.HIERSCHT) noeuds(grilleX, grilleY, 71) else FloatArray(0)
        // Le printemps et l'automne ne touchent que la végétation : le reste
        // du paysage, ciel compris, se saute sans rien recalculer.
        val vegetationSeule = saison == Saison.FREIJOER || saison == Saison.HIERSCHT
        // La densité des fleurs se règle sur la surface : sans cela, un rendu
        // deux fois plus grand en porterait quatre fois plus, deux fois plus gros.
        val probaFleur = 0.009f / (u * u).coerceAtLeast(0.25f)
        val fleurs = ArrayList<Float>()
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val c = px[i]
            val veg = objet(mk, i) && vegetal(c)
            if (vegetationSeule && !veg) continue
            var rr = r(c).toFloat(); var gg = g(c).toFloat(); var bb = bl(c).toFloat()
            when (saison) {
                Saison.WANTER -> {
                    val m = (rr + gg + bb) / 3f
                    rr = m + (rr - m) * 0.65f; gg = m + (gg - m) * 0.65f; bb = m + (bb - m) * 0.65f + 10f
                    if (veg) {
                        val t = 0.62f * nuit
                        rr += (238f - rr) * t; gg += (243f - gg) * t; bb += (250f - bb) * t
                    }
                }
                Saison.FREIJOER -> if (veg) {
                    gg *= 1.07f
                    if (alea() < probaFleur) { fleurs.add(x.toFloat()); fleurs.add(y.toFloat()) }
                }
                Saison.SUMMER -> { rr += 9f; gg += 3f; bb -= 8f }
                Saison.HIERSCHT -> if (veg) {
                    val n = bruitGrille(bruit17, grilleX, x / tache, y / tache) * 3f
                    val a = rouille[n.toInt().coerceIn(0, 2)]
                    val b = rouille[(n.toInt() + 1).coerceIn(0, 3)]
                    val f = n - n.toInt()
                    val t = 0.38f + 0.2f * bruitGrille(bruit71, grilleX, x / tache, y / tache)
                    rr += (a[0] + (b[0] - a[0]) * f - rr) * t
                    gg += (a[1] + (b[1] - a[1]) * f - gg) * t
                    bb += (a[2] + (b[2] - a[2]) * f - bb) * t
                }
            }
            px[i] = (c and 0xFF000000.toInt()) or (canal(rr) shl 16) or (canal(gg) shl 8) or canal(bb)
        }
        // La neige se pose sur les crêtes et les toits : là où un objet
        // commence, sous le ciel.
        if (saison == Saison.WANTER) {
            val blanc = if (P.nuit) 0xFFBECCE4.toInt() else 0xFFFAFCFF.toInt()
            for (x in 0 until w) {
                var avant = false
                for (y in 1 until h) {
                    val a = objet(mk, y * w + x)
                    if (a && !avant) {
                        val ep = max(1, ((3f + (alea() * 2f).roundToInt()) * u).roundToInt())
                        for (k in 0 until min(ep, h - y)) px[(y + k) * w + x] = blanc
                    }
                    avant = a
                }
            }
        }
        return fleurs.toFloatArray()
    }

    // ------------------------------------------------------------ le dessus

    private fun dessus(
        c: Canvas, W: Float, H: Float, u: Float, P: Heure, saison: Saison,
        alea: Paysages.Alea, fleurs: FloatArray, oiseaux: Boolean = true
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (saison) {
            Saison.WANTER -> {
                p.shader = LinearGradient(
                    0f, H * 0.86f, 0f, H, 0x00FFFFFF,
                    if (P.nuit) argb(0.75f, 200, 212, 236) else argb(0.9f, 255, 255, 255), Shader.TileMode.CLAMP
                )
                c.drawRect(0f, H * 0.86f, W, H, p)
                p.shader = null
                repeat(90) {
                    val o = 0.55f + alea() * 0.45f
                    val x = alea() * W; val y = alea() * H; val rr = (0.7f + alea() * 1.8f) * u
                    p.color = argb(o, 255, 255, 255)
                    c.drawCircle(x, y, rr, p)
                }
            }
            Saison.FREIJOER -> {
                fleurir(c, p, u, alea, fleurs)
                p.color = argb(0.85f, 0xFF, 0xB8, 0xD6)
                repeat(22) { ellipse(c, p, alea() * W, alea() * H * 0.8f, 2.6f * u, 1.3f * u, alea() * PI.toFloat()) }
            }
            Saison.SUMMER -> if (oiseaux) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1.3f * u
                p.strokeCap = Paint.Cap.ROUND
                p.color = if (P.nuit) argb(0.6f, 255, 255, 255) else argb(0.7f, 30, 26, 30)
                repeat(6) {
                    val x = W * (0.12f + alea() * 0.5f); val y = H * (0.08f + alea() * 0.22f); val t = (3f + alea() * 3f) * u
                    val oiseau = Path()
                    oiseau.moveTo(x - t, y - t * 0.5f)
                    oiseau.quadTo(x - t * 0.4f, y - t * 0.7f, x, y)
                    oiseau.quadTo(x + t * 0.4f, y - t * 0.7f, x + t, y - t * 0.5f)
                    c.drawPath(oiseau, p)
                }
            }
            Saison.HIERSCHT -> {
                p.shader = LinearGradient(
                    0f, H * 0.55f, 0f, H * 0.8f,
                    intArrayOf(0x00FFFFFF, argb(if (P.nuit) 0.1f else 0.22f, 255, 250, 240), 0x00FFFFFF),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
                )
                c.drawRect(0f, H * 0.55f, W, H * 0.8f, p)
                p.shader = null
                val tons = intArrayOf(0xE2862F, 0xC9452B, 0xF2B544, 0xA85A2A)
                for (k in 0 until 26) {
                    p.color = (0xEB shl 24) or tons[k % 4]
                    ellipse(c, p, alea() * W, alea() * H * 0.85f, 3.4f * u, 1.7f * u, alea() * PI.toFloat())
                }
            }
        }
    }

    /** Les fleurs que [fond] a semées, aux pixels qu'il a retenus. */
    private fun fleurir(c: Canvas, p: Paint, u: Float, alea: Paysages.Alea, fleurs: FloatArray) {
        var i = 0
        while (i < fleurs.size) {
            p.color = if (alea() < 0.7f) argb(0.95f, 0xFF, 0x9E, 0xC8) else argb(0.95f, 255, 255, 255)
            c.drawCircle(fleurs[i], fleurs[i + 1], (2f + alea() * 1.8f) * u, p)
            i += 2
        }
    }

    // ------------------------------------------------------------ les fêtes

    private fun fete(c: Canvas, w: Int, h: Int, u: Float, P: Heure, f: Fete, mk: IntArray, alea: Paysages.Alea) {
        val W = w.toFloat()
        val H = h.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val soir = P.rang >= 2
        when (f) {
            Fete.NEIJOERSCHDAG -> feuDArtifice(c, W, H, u, alea, true)
            Fete.NATIONALFEIERDAG -> {
                if (soir) feuDArtifice(c, W, H, u, alea, true)
                val drapeau = intArrayOf(0xFFED2939.toInt(), 0xFFFFFFFF.toInt(), 0xFF00A1DE.toInt())
                chainette(c, p, W, u, H * 0.07f, 14f * u) { x, y, j -> fanion(c, p, x, y, 6f * u, 14f * u, drapeau[j % 3]) }
            }
            Fete.SCHUEBERFOUER -> {
                val coul = intArrayOf(0xFFD6453A.toInt(), 0xFFE9B02F.toInt(), 0xFF2F9C95.toInt(),
                    0xFF5A6FD1.toInt(), 0xFFD4569A.toInt(), 0xFF3F9A4F.toInt())
                chainette(c, p, W, u, H * 0.06f, 16f * u) { x, y, j -> fanion(c, p, x, y, 6f * u, 13f * u, coul[j % 6]) }
            }
            Fete.CHRESCHTMAART -> for ((y0, fl) in arrayOf(0.06f to 12f, 0.16f to 18f)) {
                chainette(c, p, W, u, H * y0, fl * u) { x, y, _ ->
                    val r = 9f * u
                    p.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
                    p.shader = RadialGradient(x, y + 3f * u, r,
                        argb(0.95f, 255, 214, 120), argb(0f, 255, 180, 60), Shader.TileMode.CLAMP)
                    c.drawRect(x - r, y - 6f * u, x + r, y + 12f * u, p)
                    p.shader = null
                    p.xfermode = null
                    p.color = 0xFFFFE7A8.toInt()
                    c.drawCircle(x, y + 3f * u, 1.8f * u, p)
                }
            }
            Fete.BUERGBRENNEN -> buergbrennen(c, p, w, h, u, P, mk, alea)
        }
    }

    /** Un fanion triangulaire, pointe en bas. */
    private fun fanion(c: Canvas, p: Paint, x: Float, y: Float, demi: Float, haut: Float, col: Int) {
        p.style = Paint.Style.FILL
        p.color = col
        val t = Path()
        t.moveTo(x - demi, y); t.lineTo(x + demi, y); t.lineTo(x, y + haut); t.close()
        c.drawPath(t, p)
    }

    /** Une cordelette tendue d'un bord à l'autre, et ce qu'on y accroche tous les 22 pixels de maquette. */
    private fun chainette(c: Canvas, p: Paint, W: Float, u: Float, y0: Float, fleche: Float,
                          dessin: (Float, Float, Int) -> Unit) {
        val corde = Path()
        corde.moveTo(-10f * u, y0)
        corde.quadTo(W / 2f, y0 + fleche * 2f, W + 10f * u, y0)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.4f * u
        p.color = argb(0.75f, 30, 24, 20)
        c.drawPath(corde, p)
        p.style = Paint.Style.FILL
        val pas = 22f * u
        var x = 4f * u
        while (x < W) {
            val t = (x + 10f * u) / (W + 20f * u)
            val y = (1 - t) * (1 - t) * y0 + 2 * (1 - t) * t * (y0 + fleche * 2f) + t * t * y0
            dessin(x, y, (x / pas).roundToInt())
            x += pas
        }
    }

    private fun feuDArtifice(c: Canvas, W: Float, H: Float, u: Float, alea: Paysages.Alea, fort: Boolean) {
        val couleurs = intArrayOf(0xFF5A7A, 0xFFD45A, 0x7AE0FF, 0xB98CFF, 0x8CFFB0, 0xFFFFFF)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        val gerbes = arrayOf(floatArrayOf(0.22f, 0.2f, 44f), floatArrayOf(0.55f, 0.12f, 56f), floatArrayOf(0.82f, 0.24f, 38f))
        gerbes.forEachIndexed { j, (fx, fy, rayon) ->
            val cx = fx * W; val cy = fy * H; val R = rayon * u
            val col = couleurs[(j * 2 + 1) % couleurs.size]
            val opaque = (0xFF shl 24) or col
            val a = (if (fort) 0.9f else 0.5f)
            for (k in 0 until 28) {
                val ang = k / 28f * PI.toFloat() * 2f + alea() * 0.1f
                val r = R * (0.75f + alea() * 0.3f)
                val ex = cx + cos(ang) * r; val ey = cy + sin(ang) * r
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1.3f * u
                p.color = 0xFF000000.toInt()
                p.alpha = (a * 255f).roundToInt()
                p.shader = LinearGradient(cx, cy, ex, ey, 0x00FFFFFF, opaque, Shader.TileMode.CLAMP)
                c.drawLine(cx + cos(ang) * r * 0.25f, cy + sin(ang) * r * 0.25f, ex, ey, p)
                p.shader = null
                p.style = Paint.Style.FILL
                p.color = opaque
                p.alpha = (a * 255f).roundToInt()
                c.drawCircle(ex, ey + 2f * u, 1.6f * u, p)
            }
            p.color = 0xFF000000.toInt()
            p.alpha = ((if (fort) 0.25f else 0.12f) * 255f).roundToInt()
            p.shader = RadialGradient(cx, cy, R * 1.2f, opaque, 0x00000000, Shader.TileMode.CLAMP)
            c.drawRect(cx - R * 1.3f, cy - R * 1.3f, cx + R * 1.3f, cy + R * 1.3f, p)
            p.shader = null
        }
    }

    /** Le grand feu du premier dimanche de carême, sur la ligne d'horizon, à gauche. */
    private fun buergbrennen(c: Canvas, p: Paint, w: Int, h: Int, u: Float, P: Heure, mk: IntArray, alea: Paysages.Alea) {
        val W = w.toFloat(); val H = h.toFloat()
        // Le feu se pose sur l'horizon du tiers gauche, au point le plus bas de
        // la ligne de crête sans descendre sous les trois quarts : un feu
        // posé à une abscisse fixe tombait au Mëllerdall sur le haut d'un
        // rocher, et flottait devant la paroi une fois abaissé.
        var xi = (W * 0.16f).roundToInt().coerceIn(0, w - 1)
        var y0 = -1f
        val pas = max(1, w / 120)
        var x = (W * 0.08f).toInt()
        while (x < (W * 0.42f).toInt()) {
            var y = 0
            while (y < h && !objet(mk, y * w + x)) y++
            if (y < H * 0.75f && y > y0) { y0 = y.toFloat(); xi = x }
            x += pas
        }
        if (y0 < 0f) y0 = H * 0.5f
        // En plein jour le halo additif blanchit un ciel déjà clair : il ne
        // garde sa force que le soir et la nuit.
        val lueur = if (P.rang >= 2) 1f else 0.45f
        val x0 = xi.toFloat()
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, H, 0f, 0f, argb(0.22f * lueur, 255, 120, 40), argb(0f, 255, 120, 40), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, H, p)
        p.shader = RadialGradient(x0, y0 - 20f * u, 120f * u, argb(0.75f * lueur, 255, 170, 60), argb(0f, 255, 90, 20), Shader.TileMode.CLAMP)
        c.drawRect(x0 - 130f * u, y0 - 150f * u, x0 + 130f * u, y0 + 110f * u, p)
        // Les flammes en mélange normal : additives, elles viraient au blanc
        // sur un ciel de matin et le feu disparaissait.
        p.xfermode = null
        for (k in 0 until 7) {
            val dx = (k - 3) * 7f * u
            val hh = (42f + alea() * 38f) * u
            p.shader = LinearGradient(0f, y0, 0f, y0 - hh,
                intArrayOf(0xFFFFF2B0.toInt(), 0xFFFF9A2A.toInt(), argb(0f, 220, 40, 10)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            val flamme = Path()
            flamme.moveTo(x0 + dx - 10f * u, y0)
            flamme.quadTo(x0 + dx - 6f * u, y0 - hh * 0.6f, x0 + dx + (alea() * 4f - 2f) * u, y0 - hh)
            flamme.quadTo(x0 + dx + 6f * u, y0 - hh * 0.6f, x0 + dx + 10f * u, y0)
            c.drawPath(flamme, p)
        }
        p.shader = null
        // Le bûcher, sombre, sous les flammes.
        p.color = 0xFF3A2A20.toInt()
        ellipse(c, p, x0, y0 + 1.5f * u, 14f * u, 4f * u, 0f)
        p.color = argb(0.9f, 255, 210, 120)
        repeat(30) {
            val x = x0 + (alea() - 0.5f) * 50f * u
            val y = y0 - 20f * u - alea() * 70f * u
            c.drawCircle(x, y, (0.6f + alea()) * u, p)
        }
        p.xfermode = null
    }

    // ------------------------------------------------------------ outils

    private fun ellipse(c: Canvas, p: Paint, x: Float, y: Float, rx: Float, ry: Float, angle: Float) {
        c.save()
        c.translate(x, y)
        c.rotate(Math.toDegrees(angle.toDouble()).toFloat())
        // Sans RectF partagé : les vignettes se rendent sur deux fils à la fois.
        c.drawOval(-rx, -ry, rx, ry, p)
        c.restore()
    }

    /** La valeur du bruit de graine [graine] au nœud entier (i, j). */
    private fun noeud(i: Int, j: Int, graine: Int) =
        (melanger((i + graine) * 73856093 xor (j + 31) * 19349663) % 1024).toFloat() / 1023f

    /** Les nœuds d'une grille de [nx] × [ny], rangée par rangée, pour [bruitGrille]. */
    private fun noeuds(nx: Int, ny: Int, graine: Int) =
        FloatArray(nx * ny) { noeud(it % nx, it / nx, graine) }

    /**
     * Un bruit de valeur lissé, de 0 à 1 : un tirage par nœud d'une grille
     * entière, interpolé entre les quatre nœuds voisins. C'est ce qui fait des
     * taches de feuillage plutôt qu'une mosaïque. Les nœuds sont tirés
     * d'avance par [noeuds].
     */
    private fun bruitGrille(grille: FloatArray, nx: Int, x: Float, y: Float): Float {
        val x0 = x.toInt(); val y0 = y.toInt()
        val fx = lisse(x - x0); val fy = lisse(y - y0)
        val o = y0 * nx + x0
        val haut = grille[o] + (grille[o + 1] - grille[o]) * fx
        val bas = grille[o + nx] + (grille[o + nx + 1] - grille[o + nx]) * fx
        return haut + (bas - haut) * fy
    }

    private fun lisse(t: Float) = t * t * (3f - 2f * t)

    /**
     * Brasse un entier : les bits bas d'un produit par une constante impaire
     * varient linéairement, et un modulo dessinait des hachures en diagonale.
     */
    private fun melanger(v: Int): Long {
        var h = v
        h = h xor (h ushr 16)
        h *= 0x45D9F3B
        h = h xor (h ushr 16)
        h *= 0x45D9F3B
        h = h xor (h ushr 16)
        return h.toLong() and 0xFFFFFFFFL
    }

    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun bl(c: Int) = c and 0xFF
    private fun canal(v: Float) = v.roundToInt().coerceIn(0, 255)
    private fun argb(a: Float, r: Int, g: Int, b: Int) =
        ((a * 255f).roundToInt().coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b
}
