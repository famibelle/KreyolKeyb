package com.example.kreyolkeyboard.carnet

import java.util.TimeZone

/**
 * Le calendrier de révision des cartes du carnet.
 *
 * Le carnet gardait les mots gagnés dans les cinq jeux, mais rien n'obligeait à
 * rouvrir une carte : une étagère à trophées là où il fallait un paquet qui
 * revient vous voir. Cet objet est ce paquet, et il ne contient que du calcul :
 * pas de `Context`, pas de vue, donc testable sur la JVM comme
 * [com.example.kreyolkeyboard.gamification.CreoleLevels].
 *
 * Quatre choix qui portent le reste :
 *
 * - **Des boîtes de Leitner à intervalles fixes, ni SM-2 ni FSRS.** SM-2 demande
 *   au joueur de noter sa propre réponse de 1 à 4, et cette note alimente un
 *   exposant : c'est une question à laquelle personne ne répond honnêtement, et
 *   ici elle déciderait de quand le mot revient. FSRS, lui, s'ajuste sur un
 *   journal de milliers de révisions, et rien ne sort de l'appareil : nous
 *   livrerions des paramètres ajustés sur les révisions de quelqu'un d'autre,
 *   dans une autre langue. C'est le même refus que celui déjà écrit pour la
 *   rareté des cartes : poser une statistique inventée sur une vraie langue
 *   apprend quelque chose de faux. Un intervalle fixe ne prétend rien.
 * - **Des jours, pas des millisecondes.** Une carte due « demain » doit l'être
 *   demain matin, pas dans vingt-quatre heures à la seconde près : on compte
 *   donc des jours locaux, avec une coupure à [HEURE_COUPURE] pour que la
 *   révision de minuit et demi appartienne encore à la veille.
 * - **Une horloge reculée ne verrouille pas le paquet.** Changement de fuseau,
 *   réglage manuel, et toutes les échéances passent dans un futur qui n'arrive
 *   jamais. Au-delà du plus long intervalle, une échéance est tenue pour
 *   aberrante et la carte redevient due : voir [estDue].
 * - **La file est plafonnée.** Une semaine d'absence fabrique un mur de deux
 *   cents cartes, et le mur ne s'ouvre jamais. [PLAFOND_SESSION] cartes par
 *   session, les plus anciennement dues d'abord.
 */
object Sonje {

    /**
     * Les intervalles, en jours : celui de la boîte *n* est le délai accordé
     * quand une carte vient d'entrer dans cette boîte.
     *
     * 1, 3, 7, 16, 35, 90 font 152 jours de la première rencontre à
     * l'acquisition. Ce sont des valeurs d'usage, pas des valeurs mesurées, et
     * rien ici ne pouvait les mesurer : aucune donnée de rétention ne sort de
     * l'appareil, et c'est délibéré.
     */
    val INTERVALLES = intArrayOf(1, 3, 7, 16, 35, 90)

    /** La boîte dont on ne ressort pas : la carte est acquise, elle quitte la file. */
    val BOITE_ACQUISE = INTERVALLES.size

    /** Cartes par session. Voir la note de classe : la file est plafonnée. */
    const val PLAFOND_SESSION = 12

    /** Heure locale à laquelle un nouveau jour de révision commence. */
    const val HEURE_COUPURE = 4

    /** Échéance d'une carte que personne n'a encore planifiée. */
    const val JAMAIS_PLANIFIEE = -1

    private const val MILLIS_PAR_JOUR = 86_400_000L

    /**
     * Le numéro du jour local d'un instant.
     *
     * [decalageMillis] est le décalage du fuseau à cet instant, passé plutôt que
     * lu, pour que la fonction reste pure et que le test puisse traverser les
     * fuseaux sans toucher à l'horloge de la machine.
     */
    fun jour(instant: Long, decalageMillis: Int): Int {
        val local = instant + decalageMillis - HEURE_COUPURE * 3_600_000L
        // Division entière **plancher**, écrite à la main. `Math.floorDiv` dit
        // exactement cela, mais il est apparu avec l'API 24 alors que le
        // minimum du projet est 21, et rien ici n'active le désucrage des
        // bibliothèques : sur un Android 5 ou 6, l'appel lèverait un
        // `NoSuchMethodError` à l'ouverture du carnet. C'était d'ailleurs la
        // seule API Java 8 de tout le dépôt.
        //
        // Le plancher n'est pas un détail : une division ordinaire tronque vers
        // zéro, si bien qu'un instant local négatif — horloge mal réglée,
        // appareil revenu avant 1970 — rendrait le même numéro de jour que son
        // symétrique positif, et deux jours voisins se confondraient.
        return if (local >= 0) {
            (local / MILLIS_PAR_JOUR).toInt()
        } else {
            -(((-local) + MILLIS_PAR_JOUR - 1) / MILLIS_PAR_JOUR).toInt()
        }
    }

    /** Le jour local courant, fuseau de l'appareil compris. */
    fun aujourdHui(instant: Long = System.currentTimeMillis()): Int =
        jour(instant, TimeZone.getDefault().getOffset(instant))

    /** La boîte d'une carte réussie. Au sommet, la carte est acquise. */
    fun apresReussite(boite: Int): Int = (boite + 1).coerceAtMost(BOITE_ACQUISE)

    /**
     * La boîte d'une carte ratée : retour au départ.
     *
     * Redescendre d'un cran seulement laisserait un mot qu'on ne sait pas
     * revenir dans seize jours. Le paquet n'a aucune raison de ménager une
     * carte que le joueur vient d'échouer.
     */
    fun apresEchec(boite: Int): Int = 0

    /** La boîte d'une carte selon ce que le joueur a répondu. */
    fun apresVerdict(boite: Int, verdict: Verdict): Int = when (verdict) {
        Verdict.EXACT -> apresReussite(boite)
        Verdict.FAUX -> apresEchec(boite)
    }

    /**
     * L'échéance d'une carte qui vient d'entrer dans [boite].
     *
     * Une carte acquise reçoit tout de même une date, le plus long intervalle :
     * elle ne sert à rien tant que la carte est acquise, mais elle évite une
     * échéance vide dont [estDue] devrait se défier.
     */
    fun echeance(aujourdHui: Int, boite: Int): Int =
        aujourdHui + INTERVALLES[boite.coerceIn(0, INTERVALLES.size - 1)]

    /**
     * Cette carte est-elle à revoir aujourd'hui ?
     *
     * Trois sorties avant la comparaison de dates : une carte acquise ne revient
     * pas, une carte jamais planifiée est due (c'est le cas d'une carte gagnée
     * depuis la dernière session), et une échéance aberrante est traitée comme
     * due plutôt que d'attendre un jour qui n'arrivera pas.
     */
    fun estDue(boite: Int, jourEcheance: Int, aujourdHui: Int): Boolean = when {
        boite >= BOITE_ACQUISE -> false
        jourEcheance == JAMAIS_PLANIFIEE -> true
        jourEcheance > aujourdHui + INTERVALLES.last() -> true
        else -> jourEcheance <= aujourdHui
    }

    /**
     * La file d'une session : les cartes dues, les plus anciennes d'abord.
     *
     * À égalité d'échéance, la boîte la plus basse passe devant : ce sont les
     * mots les moins sûrs, et ce sont eux qu'il faut avoir vus si la session est
     * interrompue. L'ordre de capture départage le reste, pour qu'une même
     * journée serve toujours la même file.
     */
    fun file(
        cartes: List<CarteMot>,
        aujourdHui: Int,
        plafond: Int = PLAFOND_SESSION
    ): List<CarteMot> = cartes
        .filter { estDue(it.boite, it.jourEcheance, aujourdHui) }
        .sortedWith(
            compareBy<CarteMot> { if (it.jourEcheance == JAMAIS_PLANIFIEE) Int.MIN_VALUE else it.jourEcheance }
                .thenBy { it.boite }
                .thenBy { it.numero }
        )
        .take(plafond)

    /**
     * L'échéance à donner aux cartes qu'aucune session n'a jamais planifiées.
     *
     * Le piège que cette fonction existe pour désamorcer : un joueur qui a déjà
     * deux cents cartes trouverait, à la première ouverture, deux cents cartes
     * dues le même jour. C'est le mur décrit dans la note de classe, et il
     * suffit à condamner la fonction entière.
     *
     * Les cartes sont donc **étalées** par paquets de [plafond] sur les jours
     * suivants, dans leur ordre de capture : la collection revient au rythme où
     * elle a été faite. [position] est le rang de la carte parmi celles qui
     * restent à planifier.
     */
    fun echeanceDEtalement(aujourdHui: Int, position: Int, plafond: Int = PLAFOND_SESSION): Int =
        aujourdHui + position / plafond.coerceAtLeast(1)
}
