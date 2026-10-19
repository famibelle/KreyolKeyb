package com.example.kreyolkeyboard.carnet

import java.util.Calendar
import java.util.TimeZone

/**
 * Les quatre saisons, en luxembourgeois.
 *
 * Les noms sont ceux du LOD (WANTER1, FREIJOER1, SUMMER1, HIERSCHT1) et ne se
 * traduisent pas, comme les noms de lieux de [Lieu] : ce sont des noms
 * propres de la carte, pas des libellés d'interface.
 */
enum class Saison(val nom: String) {
    WANTER("Wanter"),
    FREIJOER("Fréijoer"),
    SUMMER("Summer"),
    HIERSCHT("Hierscht")
}

/**
 * Les fêtes du calendrier luxembourgeois qu'une carte peut garder.
 *
 * Toutes attestées au LOD (NEIJOERSCHDAG1, BUERGBRENNEN1, NATIONALFEIERDAG1,
 * SCHUEBERFOUER1, CHRESCHTMAART1). Ce sont des repères civils de l'année, la
 * même ligne que celle de [MotsEcartes], qui garde `Chrëschtdag` et
 * `Ouschteren` pour la même raison.
 */
enum class Fete(val nom: String) {
    NEIJOERSCHDAG("Neijoerschdag"),
    BUERGBRENNEN("Buergbrennen"),
    NATIONALFEIERDAG("Nationalfeierdag"),
    SCHUEBERFOUER("Schueberfouer"),
    CHRESCHTMAART("Chrëschtmaart")
}

/**
 * Ce que la carte retient du jour où on l'a gagnée : la saison, et la fête
 * s'il y en avait une.
 *
 * ## La date est une donnée technique
 *
 * Le carnet garde l'instant de la capture ([CarteMot.premiereFois]) ; la
 * carte n'en montre **jamais** la valeur (propriétaire, 2026-10-11). Ce qui
 * compte, c'est la saison : elle habille le paysage (neige, fleurs, chaleur,
 * feuilles) et le cachet en relief de la carte ouverte porte son nom. Rien
 * d'autre n'entre en jeu, ni météo ni position : la date seule.
 *
 * Les saisons sont les saisons météorologiques, par mois entiers : décembre à
 * février, mars à mai, juin à août, septembre à novembre. Les bornes
 * astronomiques bougent d'un jour selon l'année et feraient changer de saison
 * une carte du 21 mars d'une année à l'autre.
 */
data class Souvenir(val saison: Saison, val fete: Fete?) {

    /** Le nom que porte le cachet : la fête quand il y en a une, la saison sinon. */
    val nom: String get() = fete?.nom ?: saison.nom

    companion object {

        /**
         * Les saisons et les fêtes ci-dessous sont celles du Luxembourg,
         * reprises de LuxKeyb (v23.0.0). Éteintes tant que le calendrier de
         * Guadeloupe (carême, hivernage, fêtes) n'a pas été choisi : sans
         * souvenir, une carte n'a ni saison, ni fête, ni ambiance animée.
         */
        internal const val CALENDRIER_ACTIF = false

        /**
         * Le souvenir d'un instant, lu dans le fuseau de l'appareil : la carte
         * gagnée le soir du 31 décembre à Luxembourg n'est pas du Nouvel An.
         *
         * `null` pour un instant nul ou négatif, qui n'est pas une date de
         * capture mais un champ absent : un 1er janvier 1970 tirerait un feu
         * d'artifice sur une carte qui n'a rien vu.
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
            val saison = when (mois) {
                12, 1, 2 -> Saison.WANTER
                in 3..5 -> Saison.FREIJOER
                in 6..8 -> Saison.SUMMER
                else -> Saison.HIERSCHT
            }
            val brandons = buergbrennen(an)
            val fete = when {
                mois == 1 && jour == 1 -> Fete.NEIJOERSCHDAG
                mois == brandons[0] && jour == brandons[1] -> Fete.BUERGBRENNEN
                mois == 6 && (jour == 22 || jour == 23) -> Fete.NATIONALFEIERDAG
                (mois == 8 && jour >= 22) || (mois == 9 && jour <= 11) -> Fete.SCHUEBERFOUER
                (mois == 11 && jour >= 22) || (mois == 12 && jour <= 24) -> Fete.CHRESCHTMAART
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
         * Le Buergbrennen, premier dimanche de carême : six semaines avant
         * Pâques. Compté en jours du calendrier et non en millisecondes, sans
         * quoi le passage à l'heure d'été le décalait d'un jour.
         */
        fun buergbrennen(an: Int): IntArray {
            val p = paques(an)
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            cal.clear()
            cal.set(an, p[0] - 1, p[1])
            cal.add(Calendar.DAY_OF_MONTH, -42)
            return intArrayOf(cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        }
    }
}
