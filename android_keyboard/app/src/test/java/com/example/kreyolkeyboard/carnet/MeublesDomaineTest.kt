package com.example.kreyolkeyboard.carnet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les meubles tirés du domaine public (`MeublesDomaine.kt`, généré par
 * `scripts/meubles_domaine.py`) : chaque chemin livré se relit, et tient dans
 * le carré de cent unités.
 *
 * Un chemin mal écrit ne casserait rien de visible au test d'actif : la carte
 * dessinerait une silhouette tronquée, ou plus rien. Le JVM des tests n'a pas
 * de `Path` (il n'y a que des bouchons), d'où [Meubles.anneaux], qui fait la
 * lecture sans lui.
 */
class MeublesDomaineTest {

    @Test
    fun `chaque chemin se relit en anneaux fermés`() {
        for ((nom, svg) in MeublesDomaine.TRACES) {
            val anneaux = Meubles.anneaux(svg)
            assertTrue("« $nom » n'a aucun anneau", anneaux.isNotEmpty())
            assertEquals("« $nom » : autant d'anneaux que de M", svg.count { it == 'M' }, anneaux.size)
            for (a in anneaux) {
                assertTrue("« $nom » : un anneau de moins de trois points", a.size >= 6)
                assertEquals("« $nom » : une coordonnée sans sa paire", 0, a.size % 2)
            }
        }
    }

    @Test
    fun `chaque silhouette tient dans le carre`() {
        val bord = Meubles.COTE / 2 + 1f
        for ((nom, svg) in MeublesDomaine.TRACES) {
            for (a in Meubles.anneaux(svg)) for (v in a) {
                assertTrue("« $nom » sort du carré ($v)", v in -bord..bord)
            }
        }
    }

    @Test
    fun `ce qui se pose touche le sol`() {
        // Le générateur pose au sol à y = 47 tout ce qui ne vole pas : si une
        // bête flottait au-dessus de son paysage, c'est ici qu'on le verrait.
        for (nom in listOf("chat", "chien", "cheval", "lion", "maison", "voiture", "arbre", "famille", "chaise")) {
            val bas = Meubles.anneaux(MeublesDomaine.TRACES.getValue(nom))
                .maxOf { a -> (1 until a.size step 2).maxOf { a[it] } }
            assertEquals("« $nom » ne touche pas le sol", 47f, bas, 0.2f)
        }
    }

    @Test
    fun `les meubles dessines a la main ne doublent pas ceux du domaine public`() {
        assertEquals(
            setOf("coffre", "miroir", "rue", "beurre", "framboise", "alphabet"),
            Meubles.noms() - MeublesDomaine.TRACES.keys
        )
    }
}
