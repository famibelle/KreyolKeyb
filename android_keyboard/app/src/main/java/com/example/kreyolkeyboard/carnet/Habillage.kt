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
 * - **le fond** change la matière du lieu, pixel par pixel : le carême
 *   réchauffe la lumière et jaunit l'herbe par taches, comme une savane
 *   sèche ; l'hivernage reverdit la végétation et voile le reste d'un gris
 *   de pluie ;
 * - **le dessus** passe devant : les oiseaux du carême, les nuages et
 *   l'averse de l'hivernage, puis la fête du jour s'il y en a une (lucioles
 *   du Nouvel An, fanions et confettis du Carnaval, flambeaux du 27 mai,
 *   bougies de la Toussaint, guirlandes des Chanté Nwèl).
 *
 * **Jamais de feu d'artifice** (propriétaire, 2026-10-19) : il ne respecte pas
 * les sons de la nature. Le Nouvel An est une nuit calme, avec ses lucioles.
 *
 * La mécanique (masque du lieu, végétation lue sur la palette, bruit lissé)
 * vient de LuxKeyb, qui peignait quatre saisons européennes ; les deux
 * saisons et les cinq fêtes sont celles de la Guadeloupe (2026-10-19).
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
    fun appliquer(b: Bitmap, calque: Bitmap, lieu: Lieu, heure: Heure, s: Souvenir, k: FloatArray,
                  averse: Boolean = true) {
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
        dessus(c, w.toFloat(), h.toFloat(), u, heure, s.saison, alea, fleurs, averse = averse)
        s.fete?.let { fete(c, w, h, u, heure, it, mk, alea) }
    }

    /**
     * La même chose sur les trois calques de [Paysages.plans], chacun avec
     * son propre masque et sa propre échelle ([ks]).
     *
     * La matière change plan par plan. Les nuages de l'hivernage vont sur le
     * calque proche, par-dessus tout ; l'averse et les oiseaux du carême n'y
     * sont pas peints, ils bougent dans l'ambiance (voir [Ambiance]). Jamais
     * de fête ici : voir [Paysages.plans].
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
        val paille = arrayOf(intArrayOf(196, 170, 92), intArrayOf(176, 150, 74), intArrayOf(214, 190, 112), intArrayOf(158, 138, 70))
        // Les taches du carême mesurent deux unités et demie de la boîte,
        // quelle que soit la taille du rendu : des blocs de quelques pixels se
        // lisaient en hachures sur la carte et en confettis sur la vignette.
        val tache = max(3f, 12f * u)
        // Les deux bruits de l'automne, tirés une fois par nœud de leur grille
        // plutôt que quatre fois par pixel : la carte ouverte passait la moitié
        // de son habillage d'automne à refaire les mêmes tirages (Galaxy A21s,
        // 2026-10-18). Le résultat est le même au bit près.
        val grilleX = ((w - 1) / tache).toInt() + 2
        val grilleY = ((h - 1) / tache).toInt() + 2
        val bruit17 = if (saison == Saison.CAREME) noeuds(grilleX, grilleY, 17) else FloatArray(0)
        val bruit71 = if (saison == Saison.CAREME) noeuds(grilleX, grilleY, 71) else FloatArray(0)
        val fleurs = ArrayList<Float>()
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val c = px[i]
            val veg = objet(mk, i) && vegetal(c)
            var rr = r(c).toFloat(); var gg = g(c).toFloat(); var bb = bl(c).toFloat()
            when (saison) {
                Saison.CAREME -> {
                    // La lumière sèche : un peu plus chaude partout.
                    rr += 9f; gg += 3f; bb -= 8f
                    if (veg) {
                        // L'herbe qui jaunit, par taches, sans tout griller.
                        val n = bruitGrille(bruit17, grilleX, x / tache, y / tache) * 3f
                        val a = paille[n.toInt().coerceIn(0, 2)]
                        val b = paille[(n.toInt() + 1).coerceIn(0, 3)]
                        val f = n - n.toInt()
                        val t = (0.05f + 0.08f * bruitGrille(bruit71, grilleX, x / tache, y / tache)) * nuit
                        rr += (a[0] + (b[0] - a[0]) * f - rr) * t
                        gg += (a[1] + (b[1] - a[1]) * f - gg) * t
                        bb += (a[2] + (b[2] - a[2]) * f - bb) * t
                    }
                }
                Saison.HIVERNAGE -> if (veg) {
                    // Le vert gorgé d'eau.
                    rr *= 0.93f; gg *= 1.06f; bb *= 0.97f
                } else {
                    // Le reste sous un ciel couvert : un peu de gris, un peu de bleu.
                    val m = (rr + gg + bb) / 3f
                    rr = m + (rr - m) * 0.85f; gg = m + (gg - m) * 0.85f; bb = m + (bb - m) * 0.85f + 5f
                }
            }
            px[i] = (c and 0xFF000000.toInt()) or (canal(rr) shl 16) or (canal(gg) shl 8) or canal(bb)
        }
        return fleurs.toFloatArray()
    }

    // ------------------------------------------------------------ le dessus

    private fun dessus(
        c: Canvas, W: Float, H: Float, u: Float, P: Heure, saison: Saison,
        alea: Paysages.Alea, fleurs: FloatArray, oiseaux: Boolean = true, averse: Boolean = oiseaux
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (saison) {
            Saison.CAREME -> if (oiseaux) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1.3f * u
                p.strokeCap = Paint.Cap.ROUND
                // Des hérons garde-bœufs, blancs, qui rentrent au dortoir ; en
                // pleine nuit ils ne volent plus (les tirages restent les mêmes).
                p.color = argb(0.85f, 255, 255, 255)
                repeat(6) {
                    val x = W * (0.12f + alea() * 0.5f); val y = H * (0.08f + alea() * 0.22f); val t = (3f + alea() * 3f) * u
                    if (P.nuit) return@repeat
                    val oiseau = Path()
                    oiseau.moveTo(x - t, y - t * 0.5f)
                    oiseau.quadTo(x - t * 0.4f, y - t * 0.7f, x, y)
                    oiseau.quadTo(x + t * 0.4f, y - t * 0.7f, x + t, y - t * 0.5f)
                    c.drawPath(oiseau, p)
                }
            }
            Saison.HIVERNAGE -> {
                // Le plafond de nuages, puis l'averse en biais.
                p.shader = LinearGradient(
                    0f, 0f, 0f, H * 0.4f,
                    if (P.nuit) argb(0.45f, 20, 24, 36) else argb(0.42f, 96, 104, 118), 0x00000000,
                    Shader.TileMode.CLAMP
                )
                c.drawRect(0f, 0f, W, H * 0.4f, p)
                p.shader = null
                p.color = if (P.nuit) argb(0.3f, 170, 180, 200) else argb(0.32f, 236, 242, 250)
                repeat(9) {
                    val cx = alea() * W; val cy = H * (0.02f + alea() * 0.14f)
                    ellipse(c, p, cx, cy, (18f + alea() * 22f) * u, (5f + alea() * 4f) * u, 0f)
                }
                // Sur la carte ouverte, en plans ou un jour de fête, l'averse
                // tombe vraiment (voir Ambiance) : peinte, elle resterait figée.
                if (averse) {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 0.7f * u
                    p.strokeCap = Paint.Cap.ROUND
                    p.color = if (P.nuit) argb(0.35f, 200, 210, 230) else argb(0.45f, 236, 242, 250)
                    repeat(80) {
                        val x = alea() * W; val y = alea() * H; val l = (6f + alea() * 8f) * u
                        c.drawLine(x, y, x - l * 0.25f, y + l, p)
                    }
                    p.style = Paint.Style.FILL
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
            Fete.NOUVEL_AN -> lucioles(c, p, W, H, u, alea)
            Fete.CARNAVAL -> {
                val coul = intArrayOf(0xFFD6453A.toInt(), 0xFFE9B02F.toInt(), 0xFF2F9C95.toInt(),
                    0xFF5A6FD1.toInt(), 0xFFD4569A.toInt(), 0xFF3F9A4F.toInt())
                chainette(c, p, W, u, H * 0.06f, 16f * u) { x, y, j -> fanion(c, p, x, y, 6f * u, 13f * u, coul[j % 6]) }
                repeat(60) { k ->
                    p.color = coul[k % 6]
                    ellipse(c, p, alea() * W, H * (0.2f + alea() * 0.75f), 1.6f * u, 0.9f * u, alea() * PI.toFloat())
                }
            }
            Fete.VINGT_SEPT_MAI -> flambeaux(c, p, W, H, u, P, alea)
            Fete.TOUSSAINT -> bougies(c, p, w, h, u, mk, alea)
            Fete.CHANTE_NWEL -> for ((y0, fl) in arrayOf(0.06f to 12f, 0.16f to 18f)) {
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

    /** Les lucioles du Nouvel An : des points vert-jaune et leur halo, dans le bas du paysage. */
    private fun lucioles(c: Canvas, p: Paint, W: Float, H: Float, u: Float, alea: Paysages.Alea) {
        p.style = Paint.Style.FILL
        repeat(22) {
            val x = alea() * W
            val y = H * (0.35f + alea() * 0.6f)
            val l = 0.5f + alea() * 0.5f
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            p.shader = RadialGradient(x, y, 9f * u,
                argb(0.45f * l, 212, 225, 87), argb(0f, 212, 225, 87), Shader.TileMode.CLAMP)
            c.drawRect(x - 9f * u, y - 9f * u, x + 9f * u, y + 9f * u, p)
            p.shader = null
            p.xfermode = null
            p.color = argb(l, 255, 249, 196)
            c.drawCircle(x, y, 1.4f * u, p)
        }
    }

    /**
     * Les flambeaux de la veillée du 27 mai, en rang au premier plan : un
     * manche, une flamme, un halo. Un jour de mémoire : pas de couleur vive,
     * rien qui éclate.
     */
    private fun flambeaux(c: Canvas, p: Paint, W: Float, H: Float, u: Float, P: Heure, alea: Paysages.Alea) {
        val lueur = if (P.rang >= 2) 1f else 0.5f
        p.style = Paint.Style.FILL
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        p.shader = LinearGradient(0f, H, 0f, H * 0.4f, argb(0.2f * lueur, 255, 130, 50), argb(0f, 255, 130, 50), Shader.TileMode.CLAMP)
        c.drawRect(0f, H * 0.4f, W, H, p)
        p.shader = null
        val n = 7
        val haut = 34f * u
        for (k in 0 until n) {
            val x = W * (0.08f + k * 0.84f / (n - 1)) + (alea() - 0.5f) * 6f * u
            val pied = H * (0.93f - 0.04f * alea())
            val tete = pied - haut
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            p.shader = RadialGradient(x, tete - 4f * u, 26f * u,
                argb(0.7f * lueur, 255, 170, 70), argb(0f, 255, 110, 30), Shader.TileMode.CLAMP)
            c.drawRect(x - 28f * u, tete - 32f * u, x + 28f * u, tete + 24f * u, p)
            p.shader = null
            p.xfermode = null
            p.color = 0xFF3A2A20.toInt()
            c.drawRect(x - 1.1f * u, tete, x + 1.1f * u, pied, p)
            p.color = 0xFF5A4030.toInt()
            c.drawRect(x - 2f * u, tete - 1f * u, x + 2f * u, tete + 2f * u, p)
            val hh = (11f + alea() * 5f) * u
            p.shader = LinearGradient(0f, tete, 0f, tete - hh,
                intArrayOf(0xFFFFF2B0.toInt(), 0xFFFF9A2A.toInt(), argb(0f, 220, 40, 10)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            val flamme = Path()
            flamme.moveTo(x - 3.2f * u, tete - 0.5f * u)
            flamme.quadTo(x - 2.4f * u, tete - hh * 0.6f, x + (alea() * 2f - 1f) * u, tete - hh)
            flamme.quadTo(x + 2.4f * u, tete - hh * 0.6f, x + 3.2f * u, tete - 0.5f * u)
            c.drawPath(flamme, p)
            p.shader = null
        }
    }

    /**
     * Les bougies de la Toussaint : des flammes posées sur ce que le lieu a de
     * solide dans sa moitié basse, chacune avec son halo. C'est le masque du
     * lieu qui dit où l'on peut en poser, pas une bande fixe : sans lui, les
     * bougies flottaient sur la mer.
     */
    private fun bougies(c: Canvas, p: Paint, w: Int, h: Int, u: Float, mk: IntArray, alea: Paysages.Alea) {
        p.style = Paint.Style.FILL
        var posees = 0
        var essais = 0
        while (posees < 46 && essais < 400) {
            essais++
            val x = (alea() * (w - 1)).toInt()
            val y = (h * (0.55f + alea() * 0.42f)).toInt().coerceIn(0, h - 1)
            if (!objet(mk, y * w + x)) continue
            posees++
            val fx = x.toFloat(); val fy = y.toFloat()
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            p.shader = RadialGradient(fx, fy - 2f * u, 7f * u,
                argb(0.6f, 255, 200, 110), argb(0f, 255, 160, 60), Shader.TileMode.CLAMP)
            c.drawRect(fx - 7f * u, fy - 9f * u, fx + 7f * u, fy + 5f * u, p)
            p.shader = null
            p.xfermode = null
            p.color = 0xFFF4EEDF.toInt()
            c.drawRect(fx - 0.7f * u, fy - 1.2f * u, fx + 0.7f * u, fy + 1.4f * u, p)
            p.color = 0xFFFFE08A.toInt()
            ellipse(c, p, fx, fy - 2.4f * u, 0.8f * u, 1.4f * u, 0f)
        }
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
