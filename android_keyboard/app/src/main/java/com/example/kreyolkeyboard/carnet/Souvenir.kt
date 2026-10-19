package com.example.kreyolkeyboard.carnet

import java.util.Calendar
import java.util.TimeZone

/**
 * Les deux saisons de la Guadeloupe : le carême, sec, et l'hivernage, humide
 * (choix du propriétaire, 2026-10-19).
 *
 * Les noms restent en français, comme ceux des lieux de [Lieu] : aucune forme
 * kréyòl validée n'est disponible dans le dépôt pour l'une ou l'autre.
 */
enum class Saison(val nom: String) {
    CAREME("Carême"),
    HIVERNAGE("Hivernage")
}

/**
 * Les fêtes qu'une carte peut garder, choisies par le propriétaire
 * (2026-10-19) : le Nouvel An, les jours gras du Carnaval, le 27 mai,
 * la Toussaint et ses cimetières illuminés, et la saison des Chanté Nwèl.
 *
 * Le 27 mai commémore l'abolition de l'esclavage en Guadeloupe (1848). Il
 * est peint en flambeaux, jamais en feu d'artifice : c'est un jour de
 * mémoire, pas de réjouissance.
 */
enum class Fete(val nom: String) {
    NOUVEL_AN("Nouvel An"),
    CARNAVAL("Carnaval"),
    VINGT_SEPT_MAI("27 mai"),
    TOUSSAINT("Toussaint"),
    CHANTE_NWEL("Chanté Nwèl")
}

/**
 * Ce que la carte retient du jour où on l'a gagnée : la saison, et la fête
 * s'il y en avait une.
 *
 * ## La date est une donnée technique
 *
 * Le carnet garde l'instant de la capture ([CarteMot.premiereFois]) ; la
 * carte n'en montre **jamais** la valeur. Ce qui compte, c'est la saison :
 * elle habille le paysage (herbes sèches du carême, averses de l'hivernage)
 * et le poinçon de la carte ouverte porte son emblème. Rien d'autre n'entre
 * en jeu, ni météo ni position : la date seule.
 *
 * Les saisons vont par mois entiers, comme on les compte d'ordinaire en
 * Guadeloupe : le carême de janvier à juin, l'hivernage de juillet à
 * décembre. La bascule réelle varie d'une année à l'autre ; une borne fixe
 * garde à une carte la même saison d'une année sur l'autre.
 */
data class Souvenir(val saison: Saison, val fete: Fete?) {

    /** Le nom que porte le cachet : la fête quand il y en a une, la saison sinon. */
    val nom: String get() = fete?.nom ?: saison.nom

    companion object {

        /** Le calendrier de Guadeloupe est choisi (2026-10-19) : il est allumé. */
        internal const val CALENDRIER_ACTIF = true

        /**
         * Le souvenir d'un instant, lu dans le fuseau de l'appareil : la carte
         * gagnée le soir du 31 décembre à Pointe-à-Pitre n'est pas du Nouvel An.
         *
         * `null` pour un instant nul ou négatif, qui n'est pas une date de
         * capture mais un champ absent : un 1er janvier 1970 ferait voler les
         * lucioles du Nouvel An sur une carte qui n'a rien vu.
         */
        fun de(instant: Long, fuseau: TimeZone = TimeZone.getDefault()): Souvenir? {
            if (!CALENDRIER_ACTIF) return null
            if (instant <= 0L) return null
            val cal = Calendar.getInstance(fuseau)
            cal.timeInMillis = instant
            return du(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        }

        /** Le souvenir d'un jour du calendrier, [mois] de 1 à 12. */
        fun du(an: Int, mois: Int, jour: Int): Souvenir {
            val saison = if (mois <= 6) Saison.CAREME else Saison.HIVERNAGE
            val fete = when {
                mois == 1 && jour == 1 -> Fete.NOUVEL_AN
                joursGras(an).any { it[0] == mois && it[1] == jour } -> Fete.CARNAVAL
                mois == 5 && jour == 27 -> Fete.VINGT_SEPT_MAI
                mois == 11 && (jour == 1 || jour == 2) -> Fete.TOUSSAINT
                mois == 12 && jour <= 24 -> Fete.CHANTE_NWEL
                else -> null
            }
            return Souvenir(saison, fete)
        }

        /**
         * Le dimanche de Pâques (algorithme de Meeus pour le calendrier
         * grégorien), en mois de 1 à 12 et jour.
         */
        fun paques(an: Int): IntArray {
            val a = an % 19
            val b = an / 100
            val c = an % 100
            val d = b / 4
            val e = b % 4
            val f = (b + 8) / 25
            val g = (b - f + 1) / 3
            val h = (19 * a + b - d - g + 15) % 30
            val i = c / 4
            val k = c % 4
            val l = (32 + 2 * e + 2 * i - h - k) % 7
            val m = (a + 11 * h + 22 * l) / 451
            val n = h + l - 7 * m + 114
            return intArrayOf(n / 31, n % 31 + 1)
        }

        /**
         * Les quatre jours gras du Carnaval : dimanche gras, lundi gras, mardi
         * gras et mercredi des Cendres, où l'on brûle Vaval. Le mercredi des
         * Cendres tombe 46 jours avant Pâques.
         *
         * Comptés en jours du calendrier et non en millisecondes, sans quoi un
         * changement d'heure les décalerait d'un jour.
         */
        fun joursGras(an: Int): List<IntArray> {
            val p = paques(an)
            return (49 downTo 46).map { avant ->
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                cal.clear()
                cal.set(an, p[0] - 1, p[1])
                cal.add(Calendar.DAY_OF_MONTH, -avant)
                intArrayOf(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
            }
        }
    }
}
