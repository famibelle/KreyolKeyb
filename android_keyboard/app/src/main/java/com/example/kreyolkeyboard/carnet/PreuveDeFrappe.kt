package com.example.kreyolkeyboard.carnet

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Le clavier comme examen.
 *
 * C'est ce qu'aucune application de cartes ne peut faire et que celle-ci peut :
 * avoir écrit `Forschett` dans un vrai message est une preuve de mémoire plus
 * forte que n'importe quelle carte retournée. Une carte dont le compteur
 * d'usage a monté depuis la dernière révision monte donc d'une boîte **sans que
 * la question soit posée**.
 *
 * Rien de nouveau n'est collecté : `CreoleDictionaryWithUsage` compte déjà, par
 * mot et pour les seuls mots du dictionnaire, derrière `isSensitiveInput()`. Ce
 * qui est nouveau, et ce que cet objet existe pour tenir, c'est **la frontière
 * entre les deux domaines de stockage** :
 *
 * - `creole_dict_with_usage.json` vit dans `filesDir`, que
 *   `backup_rules.xml` et `data_extraction_rules.xml` excluent tous les deux.
 *   C'est ce qui rend vraie la confidentialité publiée : ce qui a été tapé ne
 *   quitte jamais l'appareil, pas même vers la sauvegarde du constructeur.
 * - Le carnet, lui, est un `SharedPreferences` : aucune sauvegarde cloud ne
 *   l'emporte (`BackupRulesTest`), mais le transfert direct d'appareil à appareil
 *   le suit d'un téléphone à l'autre.
 *
 * La référence de comptage est donc écrite **ici, dans `filesDir`**, à côté du
 * fichier dont elle dérive, et jamais dans les préférences. Le carnet, lui, ne
 * reçoit qu'une échéance, indiscernable de celle qu'aurait produite une carte
 * réussie. Déplacer [FICHIER_REFERENCE] vers les préférences pour « simplifier »
 * ferait sortir un historique de frappe vers le nuage : ne pas le faire.
 *
 * Tout ici lit et écrit des fichiers : **à appeler hors du fil principal.**
 */
object PreuveDeFrappe {

    private const val TAG = "PreuveDeFrappe"
    private const val FICHIER_USAGE = "creole_dict_with_usage.json"
    private const val FICHIER_REFERENCE = "carnet_vu.json"

    /**
     * Parmi [formes], celles que le joueur a écrites au clavier depuis le
     * dernier passage ici.
     *
     * **Le premier appel ne renvoie jamais rien**, et c'est voulu : sans
     * référence, tout mot déjà tapé une fois dans la vie de l'installation
     * passerait pour une preuve fraîche, et un carnet entier monterait d'une
     * boîte le jour où la fonction est livrée. Le premier appel pose la
     * référence, les suivants mesurent l'écart.
     *
     * Les clés du fichier d'usage sont en minuscules (`trackWordUsage`
     * normalise avant d'écrire), la forme du carnet garde sa majuscule de
     * substantif : la comparaison passe donc par `lowercase()`.
     */
    fun ecritesDepuisLaDerniereFois(context: Context, formes: Collection<String>): Set<String> {
        if (formes.isEmpty()) return emptySet()
        val usages = lireUsages(context) ?: return emptySet()

        val fichierReference = File(context.filesDir, FICHIER_REFERENCE)
        val premiereFois = !fichierReference.exists()
        val reference = lireReference(fichierReference)

        val ecrites = HashSet<String>()
        val nouvelleReference = JSONObject()
        for (forme in formes) {
            val cle = forme.lowercase()
            val compteur = usages[cle] ?: 0
            nouvelleReference.put(cle, compteur)
            if (!premiereFois && compteur > (reference[cle] ?: 0)) ecrites.add(forme)
        }

        try {
            fichierReference.writeText(nouvelleReference.toString())
        } catch (e: Exception) {
            // Sans référence écrite, la même preuve sera comptée deux fois à la
            // prochaine session. Une carte montée d'une boîte de trop n'est pas
            // une raison d'interrompre une révision.
            Log.e(TAG, "Référence non écrite: ${e.message}", e)
        }
        return ecrites
    }

    /**
     * Les compteurs d'usage, lus à même le fichier.
     *
     * `CreoleDictionaryWithUsage` saurait le faire, mais l'instancier charge
     * tout le dictionnaire d'usage en mémoire et en migre le format : c'est
     * l'affaire du service de saisie, pas d'une révision qui a besoin de
     * quelques dizaines de compteurs. Les deux formats d'entrée sont ceux que
     * l'onglet de statistiques lit déjà : l'entier nu des premières versions et
     * l'objet `{"frequency", "user_count"}`.
     *
     * `null` veut dire « pas de fichier ni de lecture possible », donc aucune
     * preuve : c'est différent d'une table vide, qui serait une preuve que rien
     * n'a été écrit.
     */
    private fun lireUsages(context: Context): Map<String, Int>? {
        val fichier = File(context.filesDir, FICHIER_USAGE)
        if (!fichier.exists()) return null
        return try {
            val racine = JSONObject(fichier.readText())
            val compteurs = HashMap<String, Int>()
            val cles = racine.keys()
            while (cles.hasNext()) {
                val cle = cles.next()
                val valeur = when (val brut = racine.get(cle)) {
                    is Int -> brut
                    is JSONObject -> brut.optInt("user_count", 0)
                    else -> 0
                }
                if (valeur > 0) compteurs[cle] = valeur
            }
            compteurs
        } catch (e: Exception) {
            Log.e(TAG, "Fichier d'usage illisible: ${e.message}", e)
            null
        }
    }

    private fun lireReference(fichier: File): Map<String, Int> {
        if (!fichier.exists()) return emptyMap()
        return try {
            val racine = JSONObject(fichier.readText())
            val compteurs = HashMap<String, Int>()
            val cles = racine.keys()
            while (cles.hasNext()) {
                val cle = cles.next()
                compteurs[cle] = racine.optInt(cle, 0)
            }
            compteurs
        } catch (e: Exception) {
            Log.e(TAG, "Référence illisible: ${e.message}", e)
            emptyMap()
        }
    }

    /** Efface la référence. N'existe que pour les tests et le débogage. */
    fun oublier(context: Context) {
        File(context.filesDir, FICHIER_REFERENCE).delete()
    }
}
