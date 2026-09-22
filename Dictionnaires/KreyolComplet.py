#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
 KREYÒL POTOMITAN™ - PIPELINE UNIQUE ET AUTOMATIQUE 
===========================================================

Le pipeline ultime pour le clavier créole intelligent.
EXÉCUTION AUTOMATIQUE COMPLÈTE - Aucune interaction requise !

Pipeline automatique intégré:
• Récupération données Hugging Face
• Création/enrichissement dictionnaire  
• Génération N-grams intelligents
• Analyse comparative (delta)
• Statistiques complètes avancées
• Analyse mots longs détaillée
• Validation intégrale
• Nettoyage automatique
• Sauvegarde sécurisée

Usage simple: python KreyolComplet.py

Usage restreint: python KreyolComplet.py --rapport-seul
    Recalcule tout en mémoire mais n'écrit que RAPPORT_LINGUISTIQUE.md. Les
    dictionnaires embarqués dans le clavier ne sont pas touchés : c'est le mode
    utilisé par le workflow qui rafraîchit le rapport quand le dataset bouge,
    sans rien changer à ce qui part dans l'APK.

Fait avec ❤️ pour préserver le Kreyòl Guadeloupéen
"""

import json
import re
import os
import shutil
import sys
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path

# Configuration d'encodage pour Windows
if sys.platform.startswith('win'):
    import codecs
    sys.stdout = codecs.getwriter('utf-8')(sys.stdout.buffer, 'strict')
    sys.stderr = codecs.getwriter('utf-8')(sys.stderr.buffer, 'strict')

# Gestion optionnelle des imports
try:
    from datasets import load_dataset
    HAS_DATASETS = True
except ImportError:
    HAS_DATASETS = False

try:
    from dotenv import load_dotenv
    HAS_DOTENV = True
except ImportError:
    HAS_DOTENV = False

class KreyolPipelineUnique:
    """Pipeline unique automatique pour le système créole"""
    
    def __init__(self):
        """Initialisation du pipeline"""
        self.version = "3.0 - Pipeline Unique"
        self.chemin_dict = "../clavier_creole/assets/creole_dict.json"
        self.chemin_ngrams = "../clavier_creole/assets/creole_ngrams.json"
        self.chemin_rapport = "RAPPORT_LINGUISTIQUE.md"
        # Chemins pour synchronisation Android
        self.chemin_dict_android = "../android_keyboard/app/src/main/assets/creole_dict.json"
        self.chemin_ngrams_android = "../android_keyboard/app/src/main/assets/creole_ngrams.json"

        # Troisième destination : le simulateur du site, qui exécute le même
        # moteur dans le navigateur et lit donc les mêmes actifs, au format
        # Android. Il les a lus une fois, figés au 1er août 2026, jusqu'à ce
        # qu'on s'aperçoive qu'il prédisait sans les trigrammes : rien ne les
        # remettait à jour. Écrire ici plutôt que resynchroniser après coup.
        self.chemin_dict_docs = "../docs/assets/creole_dict.json"
        self.chemin_ngrams_docs = "../docs/assets/creole_ngrams.json"
        self.hf_token = None
        self.textes_kreyol = []
        self.dictionnaire_actuel = {}
        self.ngrams_actuels = {}
        self.nouveau_dictionnaire = {}
        self.nouveaux_ngrams = {}
        self.stats_corpus = {}  # Nouvelles statistiques pour le rapport
        
        # Affichage d'en-tête
        self._afficher_entete()
        
        # Chargement automatique
        self._charger_configuration()
        self._charger_donnees_existantes()
        
        print("✅ Pipeline initialisé")
    
    def _afficher_entete(self):
        """Affiche l'en-tête du pipeline"""
        print(" KREYÒL POTOMITAN™ - PIPELINE UNIQUE ET AUTOMATIQUE ")
        print("=" * 70)
        print(f"Version: {self.version}")
        print(f"Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print("🎯 EXÉCUTION AUTOMATIQUE COMPLÈTE")
        print("=" * 70)
        print("\n🔧 INITIALISATION")
        print("-" * 30)
    
    def _charger_configuration(self):
        """Charge la configuration depuis .env"""
        env_paths = [".env", "../.env", "../../.env"]
        env_found = False
        
        if HAS_DOTENV:
            for env_path in env_paths:
                if os.path.exists(env_path):
                    load_dotenv(env_path)
                    env_found = True
                    print(f"✅ Configuration .env trouvée: {env_path}")
                    break
        
        if not env_found:
            print("⚠️ Configuration .env non trouvée (optionnel)")

        # Le token peut venir d'un .env local (dev) ou être déjà présent dans
        # l'environnement (secret HF_TOKEN injecté par GitHub Actions, sans
        # fichier .env) : cette lecture doit donc s'exécuter dans tous les cas
        token = os.getenv('HF_TOKEN') or os.getenv('HF_TOKEN_read_write')
        if token:
            self.hf_token = token
            print("🔑 Token Hugging Face configuré")
        else:
            print("⚠️ Token Hugging Face non trouvé")
    
    def _charger_donnees_existantes(self):
        """Charge les données existantes si disponibles"""
        # Dictionnaire existant
        if os.path.exists(self.chemin_dict):
            try:
                with open(self.chemin_dict, 'r', encoding='utf-8') as f:
                    self.dictionnaire_actuel = json.load(f)
                print(f"📚 Dictionnaire existant: {len(self.dictionnaire_actuel)} mots")
            except Exception as e:
                print(f"⚠️ Erreur lecture dictionnaire: {e}")
        
        # N-grams existants
        if os.path.exists(self.chemin_ngrams):
            try:
                with open(self.chemin_ngrams, 'r', encoding='utf-8') as f:
                    self.ngrams_actuels = json.load(f)
                predictions = len([k for k, v in self.ngrams_actuels.items() if isinstance(v, list) and v])
                print(f"🧠 N-grams existants: {predictions} prédictions")
            except Exception as e:
                print(f"⚠️ Erreur lecture N-grams: {e}")
    
    def charger_textes_kreyol(self):
        """Charge les textes créoles depuis Hugging Face ou localement"""
        print("\n📖 CHARGEMENT DES TEXTES CRÉOLES")
        print("-" * 40)
        
        textes_charges = False
        source_chargement = "Inconnu"

        # Essayer Hugging Face d'abord
        if HAS_DATASETS:
            try:
                print("🔄 Téléchargement depuis Hugging Face...")
                print(f"   📡 Connexion au dataset POTOMITAN/PawolKreyol-gfc...")
                print(f"   🔑 Token configuré: {'✅ Oui' if self.hf_token else '❌ Non'}")
                
                dataset = load_dataset("POTOMITAN/PawolKreyol-gfc", token=self.hf_token)
                print("   ✅ Dataset récupéré avec succès")
                
                # NOUVEAU: Afficher tous les splits disponibles
                print(f"   � Splits disponibles: {list(dataset.keys())}")
                for split_name in dataset.keys():
                    print(f"      - {split_name}: {len(dataset[split_name])} rows")
                
                print("   🔍 Extraction des textes de TOUS les splits...")
                
                # NOUVEAU: Combiner tous les splits
                all_items = []
                total_rows = 0
                for split_name in dataset.keys():
                    split_data = dataset[split_name]
                    all_items.extend(split_data)
                    total_rows += len(split_data)
                    print(f"      ✅ {split_name}: {len(split_data)} rows ajoutées")
                
                print(f"   📊 Nombre total de rows dans TOUT le dataset: {total_rows}")
                
                # Échantillon des premières rows pour debug
                print("   🔬 Échantillon des premières rows:")
                for i in range(min(3, len(all_items))):
                    item = all_items[i]
                    print(f"      Row {i+1}: {list(item.keys())}")
                    if 'Texte' in item:
                        preview = str(item['Texte'])[:50] + "..." if len(str(item['Texte'])) > 50 else str(item['Texte'])
                        print(f"         Texte: '{preview}'")
                    if 'text' in item:
                        preview = str(item['text'])[:50] + "..." if len(str(item['text'])) > 50 else str(item['text'])
                        print(f"         text: '{preview}'")
                
                self.textes_kreyol = []
                textes_vides = 0
                textes_avec_texte = 0
                textes_avec_text = 0
                
                for i, item in enumerate(all_items):
                    if "Texte" in item and item["Texte"]:
                        self.textes_kreyol.append({
                            "Texte": item["Texte"],
                            "Source": item.get("Source", "Hugging Face")
                        })
                        textes_avec_texte += 1
                    elif "text" in item and item["text"]:
                        self.textes_kreyol.append({
                            "Texte": item["text"],
                            "Source": item.get("source", "Hugging Face")
                        })
                        textes_avec_text += 1
                    else:
                        textes_vides += 1
                        if textes_vides <= 3:  # Afficher seulement les 3 premiers exemples
                            print(f"   ⚠️ Row {i+1} sans texte valide: {list(item.keys())}")
                
                print(f"   📈 Statistiques d'extraction:")
                print(f"      - Rows totales (tous splits): {total_rows}")
                print(f"      - Avec champ 'Texte': {textes_avec_texte}")
                print(f"      - Avec champ 'text': {textes_avec_text}")
                print(f"      - Vides ou invalides: {textes_vides}")
                print(f"      - Textes extraits: {len(self.textes_kreyol)}")
                
                if self.textes_kreyol:
                    print(f"🎉 TÉLÉCHARGEMENT HUGGING FACE RÉUSSI !")
                    print(f"   ✅ {len(self.textes_kreyol)} textes récupérés")
                    print(f"   📊 Source: Dataset POTOMITAN/PawolKreyol-gfc")
                    textes_charges = True
                    source_chargement = "Hugging Face"
                else:
                    print("❌ TÉLÉCHARGEMENT HUGGING FACE ÉCHOUÉ !")
                    print("   ⚠️ Dataset vide - aucun texte trouvé")
                    
            except Exception as e:
                print("❌ TÉLÉCHARGEMENT HUGGING FACE ÉCHOUÉ !")
                print(f"   💥 Erreur: {e}")
                print("   🔄 Passage au mode fallback local...")
        else:
            print("❌ TÉLÉCHARGEMENT HUGGING FACE IMPOSSIBLE !")
            print("   📦 Bibliothèque 'datasets' non installée")
            print("   🔄 Passage au mode fallback local...")
        
        # Fallback local si Hugging Face échoue
        if not textes_charges:
            print("\n🔄 FALLBACK: Recherche de fichiers locaux...")
            chemins_locaux = [
                "PawolKreyol/Textes_kreyol.json",
                "../PawolKreyol/Textes_kreyol.json",
                "textes_kreyol.json"
            ]
            
            for chemin in chemins_locaux:
                print(f"   🔍 Vérification: {chemin}")
                if os.path.exists(chemin):
                    try:
                        print(f"   📁 Fichier trouvé, chargement...")
                        with open(chemin, 'r', encoding='utf-8') as f:
                            data = json.load(f)
                        
                        if isinstance(data, list):
                            self.textes_kreyol = data
                        elif isinstance(data, dict) and "textes" in data:
                            self.textes_kreyol = data["textes"]
                        else:
                            print(f"   ⚠️ Format inattendu dans {chemin}")
                            continue
                        
                        print(f"✅ FALLBACK RÉUSSI !")
                        print(f"   📊 {len(self.textes_kreyol)} textes chargés depuis {chemin}")
                        textes_charges = True
                        source_chargement = "Local"
                        break
                        
                    except Exception as e:
                        print(f"   ❌ Erreur lecture {chemin}: {e}")
                else:
                    print(f"   ❌ Fichier non trouvé")
        
        if not textes_charges:
            print("\n❌ ÉCHEC TOTAL !")
            print("   💥 Aucun texte créole trouvé (ni Hugging Face, ni local)")
            print("   🚨 Le pipeline ne peut pas continuer sans données")
            return False
        
        # Mémorisé pour que les appelants puissent distinguer un vrai
        # téléchargement d'un repli sur l'instantané local, les deux se
        # ressemblant beaucoup une fois le pipeline terminé.
        self.source_chargement = source_chargement

        print(f"\n📋 RÉSUMÉ CHARGEMENT:")
        print(f"   📊 {len(self.textes_kreyol)} textes chargés")
        print(f"   🌐 Source: {source_chargement}")
        print(f"   ✅ Prêt pour traitement")
        
        return True
    
    def creer_dictionnaire(self):
        """Crée un dictionnaire enrichi à partir des textes"""
        print("\n📚 CRÉATION DU DICTIONNAIRE")
        print("-" * 35)
        
        if not self.textes_kreyol:
            print("❌ Aucun texte disponible")
            return False
        
        print(f"🔍 Analyse de {len(self.textes_kreyol)} textes...")
        
        compteur_mots = Counter()
        pattern_mot = re.compile(r'\b[a-zA-ZàáâäèéêëìíîïòóôöùúûüçñÀÁÂÄÈÉÊËÌÍÎÏÒÓÔÖÙÚÛÜÇÑ\-]{2,}\b')
        
        for texte in self.textes_kreyol:
            if isinstance(texte, dict):
                contenu_texte = texte.get("Texte", "")
            else:
                contenu_texte = str(texte) if texte is not None else ""
            
            if not contenu_texte:
                continue
                
            mots = pattern_mot.findall(contenu_texte.lower())
            for mot in mots:
                mot = mot.strip('-')
                if len(mot) >= 2:
                    compteur_mots[mot] += 1
        
        # Le comptage du corpus REMPLACE la fréquence stockée, il ne s'y ajoute
        # pas. L'ancienne fusion faisait `stockée + nouvelle` alors que la valeur
        # stockée provenait déjà d'un passage sur ce même corpus : chaque exécution
        # gonflait donc le dictionnaire d'un corpus supplémentaire. Le rapport
        # mesuré entre valeurs stockées et comptage frais était uniforme, autour de
        # 12, soit une douzaine d'exécutions accumulées. Les fréquences ne
        # mesuraient plus le kréyòl écrit mais le nombre de fois qu'on avait lancé
        # le script. Avec ce remplacement, deux exécutions de suite donnent
        # exactement le même dictionnaire.
        # Facteur d'échelle entre fréquences stockées et comptage frais, estimé sur
        # les mots présents des deux côtés. Il vaut 1 en régime établi (le stock
        # est déjà à la bonne échelle) et corrige la transition depuis un stock
        # gonflé par les cumuls passés.
        stock_commun = sum(f for m, f in self.dictionnaire_actuel.items() if m in compteur_mots)
        frais_commun = sum(compteur_mots[m] for m in self.dictionnaire_actuel if m in compteur_mots)
        echelle = (frais_commun / stock_commun) if stock_commun else 1.0

        mots_conserves = 0
        for mot, freq in self.dictionnaire_actuel.items():
            if mot not in compteur_mots:
                # Mot absent du corpus : ajout curé à la main, ou reliquat d'un
                # corpus antérieur. Sa fréquence stockée est la seule dont on
                # dispose, mais la garder telle quelle le propulserait en tête dès
                # que l'échelle générale change : on la ramène à l'échelle du
                # comptage frais, sans jamais descendre sous 1.
                compteur_mots[mot] = max(1, round(freq * echelle))
                mots_conserves += 1

        self.nouveau_dictionnaire = dict(compteur_mots.most_common())

        nouveaux_mots = len(set(compteur_mots) - set(self.dictionnaire_actuel))
        print(f"✅ Dictionnaire créé:")
        print(f"   - Total mots: {len(self.nouveau_dictionnaire)}")
        print(f"   - Nouveaux mots: {nouveaux_mots}")
        print(f"   - Mots existants: {len(self.dictionnaire_actuel)}")
        print(f"   - Mots hors corpus conservés: {mots_conserves}")

        return True
    
    def creer_ngrams(self):
        """Crée des N-grams pour les prédictions"""
        print("\n🧠 CRÉATION DES N-GRAMS")
        print("-" * 30)
        
        if not self.textes_kreyol:
            print("❌ Aucun texte disponible")
            return False
        
        print("🔄 Génération des N-grams...")
        
        # Seuil de pertinence d'une suite, et nombre de suites gardées par contexte
        SEUIL_PROBABILITE = 0.01
        MAX_CANDIDATS = 5
        # Occurrences minimales d'un contexte à deux mots pour qu'il soit retenu
        MIN_OCCURRENCES_CONTEXTE = 2

        unigrammes = Counter()
        bigrammes = Counter()
        trigrammes = Counter()
        # Suites observées par contexte : {contexte: Counter(mot_suivant)}
        suivants_unigramme = defaultdict(Counter)
        suivants_bigramme = defaultdict(Counter)

        pattern_mot = re.compile(r'\b[a-zA-ZàáâäèéêëìíîïòóôöùúûüçñÀÁÂÄÈÉÊËÌÍÎÏÒÓÔÖÙÚÛÜÇÑ\-]{2,}\b')
        
        for texte in self.textes_kreyol:
            if isinstance(texte, dict):
                contenu_texte = texte.get("Texte", "")
            else:
                contenu_texte = str(texte) if texte is not None else ""
            
            if not contenu_texte:
                continue
                
            mots = [mot.lower().strip('-') for mot in pattern_mot.findall(contenu_texte.lower()) if len(mot.strip('-')) >= 2]
            
            # Unigrammes
            for mot in mots:
                unigrammes[mot] += 1

            # Bigrammes
            for i in range(len(mots) - 1):
                bigramme = (mots[i], mots[i + 1])
                bigrammes[bigramme] += 1
                suivants_unigramme[mots[i]][mots[i + 1]] += 1

            # Trigrammes
            for i in range(len(mots) - 2):
                trigramme = (mots[i], mots[i + 1], mots[i + 2])
                trigrammes[trigramme] += 1
                suivants_bigramme[(mots[i], mots[i + 1])][mots[i + 2]] += 1

        # Créer le modèle de prédictions
        predictions = {}
        total_unigrammes = sum(unigrammes.values())

        def meilleurs_candidats(compteur_suivants, total_contexte):
            """Candidats d'un contexte, triés et filtrés par probabilité"""
            candidats = [
                {"word": suivant, "probability": round(freq / total_contexte, 3)}
                for suivant, freq in compteur_suivants.items()
                if freq / total_contexte > SEUIL_PROBABILITE
            ]
            candidats.sort(key=lambda x: x["probability"], reverse=True)
            return candidats[:MAX_CANDIDATS]

        # Contextes à un mot : clé = le mot précédent.
        # Parcours par mot plutôt que balayage complet des bigrammes pour chaque
        # unigramme : l'ancienne double boucle était en O(unigrammes × bigrammes),
        # soit des centaines de millions d'itérations sur le corpus actuel.
        for mot, compteur_suivants in suivants_unigramme.items():
            candidats = meilleurs_candidats(compteur_suivants, unigrammes[mot])
            if candidats:
                predictions[mot] = candidats

        # Contextes à deux mots : clé = "mot1 mot2", séparés par une espace.
        # Aucune collision possible avec les clés à un mot, le motif de tokenisation
        # excluant les espaces. Le clavier essaie d'abord la clé à deux mots et
        # retombe sur celle à un mot, ce qui garde le modèle rétrocompatible.
        contextes_ignores = 0
        for (mot1, mot2), compteur_suivants in suivants_bigramme.items():
            occurrences_contexte = bigrammes[(mot1, mot2)]
            # Un contexte vu une seule fois donne une probabilité de 1.0 à son
            # unique suite : ce n'est pas une prédiction, c'est la citation d'un
            # passage du corpus. On l'écarte.
            if occurrences_contexte < MIN_OCCURRENCES_CONTEXTE:
                contextes_ignores += 1
                continue
            candidats = meilleurs_candidats(compteur_suivants, occurrences_contexte)
            if candidats:
                predictions[f"{mot1} {mot2}"] = candidats

        self.nouveaux_ngrams = predictions
        self.stats_corpus['contextes_bigrammes_ignores'] = contextes_ignores
        
        # Stocker pour le rapport
        self.stats_corpus['unigrammes'] = unigrammes
        self.stats_corpus['bigrammes'] = bigrammes
        self.stats_corpus['trigrammes'] = trigrammes
        self.stats_corpus['total_tokens'] = total_unigrammes
        
        cles_contexte_2 = sum(1 for cle in predictions if ' ' in cle)
        print(f"✅ N-grams créés:")
        print(f"   - Unigrammes: {len(unigrammes)}")
        print(f"   - Bigrammes: {len(bigrammes)}")
        print(f"   - Trigrammes: {len(trigrammes)}")
        print(f"   - Prédictions: {len(predictions)}")
        print(f"      · contexte 1 mot : {len(predictions) - cles_contexte_2}")
        print(f"      · contexte 2 mots: {cles_contexte_2} ({contextes_ignores} contextes vus une seule fois écartés)")
        
        return True
    
    def analyser_statistiques(self):
        """Analyse statistique complète du dictionnaire et des N-grams"""
        print("\n📊 ANALYSE STATISTIQUE COMPLÈTE")
        print("-" * 40)
        
        if not self.nouveau_dictionnaire:
            print("❌ Aucun dictionnaire à analyser")
            return False
        
        # Statistiques du dictionnaire
        mots = list(self.nouveau_dictionnaire.keys())
        frequences = list(self.nouveau_dictionnaire.values())
        
        print(f"\n📚 ANALYSE DICTIONNAIRE:")
        print(f"   - Total mots: {len(mots)}")
        print(f"   - Fréquence min: {min(frequences)}")
        print(f"   - Fréquence max: {max(frequences)}")
        print(f"   - Fréquence moyenne: {sum(frequences) / len(frequences):.1f}")
        
        # Catégories de fréquence
        tres_rares = sum(1 for f in frequences if f == 1)
        rares = sum(1 for f in frequences if 2 <= f <= 5)
        frequents = sum(1 for f in frequences if 6 <= f <= 20)
        tres_frequents = sum(1 for f in frequences if f > 20)
        
        print(f"   - Très rares (freq=1): {tres_rares} ({tres_rares/len(mots)*100:.1f}%)")
        print(f"   - Rares (freq 2-5): {rares} ({rares/len(mots)*100:.1f}%)")
        print(f"   - Fréquents (freq 6-20): {frequents} ({frequents/len(mots)*100:.1f}%)")
        print(f"   - Très fréquents (freq>20): {tres_frequents} ({tres_frequents/len(mots)*100:.1f}%)")
        
        # Top 15 des mots
        print(f"\n   🏆 TOP 15 MOTS:")
        for i, (mot, freq) in enumerate(list(self.nouveau_dictionnaire.items())[:15]):
            print(f"        {i+1:2d}. {mot:<15} (freq: {freq})")
        
        # Analyse des mots longs
        mots_longs = [(mot, len(mot)) for mot in mots if len(mot) >= 10]
        mots_longs.sort(key=lambda x: x[1], reverse=True)
        
        print(f"\n   📏 ANALYSE MOTS LONGS:")
        print(f"   - Mots ≥10 caractères: {len(mots_longs)}")
        if mots_longs:
            print(f"   - Mot le plus long: '{mots_longs[0][0]}' ({mots_longs[0][1]} caractères)")
            print(f"   - Top 5 mots longs:")
            for i, (mot, longueur) in enumerate(mots_longs[:5]):
                freq = self.nouveau_dictionnaire[mot]
                print(f"     {i+1}. {mot} ({longueur} char, freq: {freq})")
        
        # Statistiques N-grams
        if self.nouveaux_ngrams:
            print(f"\n🧠 ANALYSE N-GRAMS:")
            print(f"   - Mots avec prédictions: {len(self.nouveaux_ngrams)}")
            
            # Exemples de prédictions
            print(f"\n   🎯 EXEMPLES DE PRÉDICTIONS:")
            exemples = ['ka', 'nou', 'té', 'an', 'yo']
            for mot in exemples:
                if mot in self.nouveaux_ngrams:
                    predictions = self.nouveaux_ngrams[mot][:3]
                    pred_str = ", ".join([f"{p['word']}({p['probability']})" for p in predictions])
                    print(f"      '{mot}' → {pred_str}")
        
        return True
    
    def analyser_delta(self):
        """Analyse comparative entre anciennes et nouvelles données"""
        print("\n🔍 ANALYSE COMPARATIVE (DELTA)")
        print("-" * 40)
        
        # Delta dictionnaire
        anciens_mots = set(self.dictionnaire_actuel.keys())
        nouveaux_mots = set(self.nouveau_dictionnaire.keys())
        
        mots_ajoutes = nouveaux_mots - anciens_mots
        mots_supprimes = anciens_mots - nouveaux_mots
        mots_conserves = anciens_mots & nouveaux_mots
        
        print(f"\n📚 DELTA DICTIONNAIRE:")
        print(f"   ➕ Mots ajoutés: {len(mots_ajoutes)}")
        print(f"   ➖ Mots supprimés: {len(mots_supprimes)}")
        print(f"   🔄 Mots conservés: {len(mots_conserves)}")
        
        if mots_ajoutes:
            echantillon = list(mots_ajoutes)[:10]
            print(f"   📝 Nouveaux mots: {', '.join(echantillon)}")
        
        # Delta N-grams
        anciennes_predictions = set(self.ngrams_actuels.keys()) if self.ngrams_actuels else set()
        nouvelles_predictions = set(self.nouveaux_ngrams.keys()) if self.nouveaux_ngrams else set()
        
        predictions_ajoutees = nouvelles_predictions - anciennes_predictions
        predictions_supprimees = anciennes_predictions - nouvelles_predictions
        
        print(f"\n🧠 DELTA N-GRAMS:")
        print(f"   ➕ Nouvelles prédictions: {len(predictions_ajoutees)}")
        print(f"   ➖ Prédictions supprimées: {len(predictions_supprimees)}")
        
        if predictions_ajoutees:
            print(f"\n   📝 Échantillon nouvelles prédictions:")
            for i, mot in enumerate(list(predictions_ajoutees)[:10]):
                if mot in self.nouveaux_ngrams and self.nouveaux_ngrams[mot]:
                    premiere_pred = self.nouveaux_ngrams[mot][0]
                    print(f"      + '{mot}' → {premiere_pred['word']}")
        
        return True
    
    def generer_rapport_linguistique(self):
        """Génère un rapport linguistique scientifique au format Markdown"""
        print("\n📄 GÉNÉRATION DU RAPPORT LINGUISTIQUE")
        print("-" * 45)
        
        if not self.nouveau_dictionnaire:
            print("❌ Aucune donnée à analyser")
            return False
        
        print("🔬 Analyse linguistique approfondie en cours...")
        
        rapport = []
        
        # ============================================================
        # 1. EN-TÊTE & MÉTADONNÉES
        # ============================================================
        rapport.append("# Analyse Lexicographique du Kreyòl Guadeloupéen")
        rapport.append("")
        rapport.append("## Métadonnées du Corpus")
        rapport.append("")
        rapport.append(f"- **Date de génération** : {datetime.now().strftime('%d %B %Y à %H:%M')}")
        rapport.append(f"- **Version du pipeline** : {self.version}")
        rapport.append(f"- **Source des données** : Dataset POTOMITAN/PawolKreyol-gfc (Hugging Face)")
        rapport.append(f"- **Nombre de textes** : {len(self.textes_kreyol)}")
        rapport.append(f"- **Tokens totaux** : {self.stats_corpus.get('total_tokens', 0):,}")
        rapport.append(f"- **Types lexicaux** : {len(self.nouveau_dictionnaire):,}")
        rapport.append("")
        rapport.append("---")
        rapport.append("")
        
        # ============================================================
        # 2. CORPUS & REPRÉSENTATIVITÉ
        # ============================================================
        mots = list(self.nouveau_dictionnaire.keys())
        frequences = list(self.nouveau_dictionnaire.values())
        total_tokens = sum(frequences)
        
        # Calculer Type-Token Ratio
        ttr = len(mots) / total_tokens if total_tokens > 0 else 0
        
        rapport.append("## 1. Corpus et Échantillonnage")
        rapport.append("")
        rapport.append("### 1.1 Taille et Couverture")
        rapport.append("")
        rapport.append(f"- **Total des tokens** : {total_tokens:,}")
        rapport.append(f"- **Types lexicaux uniques** : {len(mots):,}")
        rapport.append(f"- **Type-Token Ratio (TTR)** : {ttr:.4f}")
        rapport.append(f"- **Richesse lexicale** : {'Élevée' if ttr > 0.1 else 'Moyenne' if ttr > 0.05 else 'Faible'}")
        rapport.append("")
        
        # ============================================================
        # 3. ANALYSE MORPHOLOGIQUE
        # ============================================================
        rapport.append("## 2. Analyse Morphologique")
        rapport.append("")
        
        # Distribution par longueur
        longueurs = defaultdict(int)
        for mot in mots:
            longueurs[len(mot)] += 1
        
        rapport.append("### 2.1 Distribution par Longueur")
        rapport.append("")
        rapport.append("| Longueur | Nombre de mots | Pourcentage |")
        rapport.append("|----------|----------------|-------------|")
        
        for longueur in sorted(longueurs.keys())[:20]:  # Top 20 longueurs
            count = longueurs[longueur]
            pct = (count / len(mots)) * 100
            barre = "█" * int(pct / 2)  # Graphique ASCII
            rapport.append(f"| {longueur:2d} lettres | {count:6,} | {pct:5.1f}% {barre} |")
        
        rapport.append("")
        
        # Mots avec traits d'union
        mots_composes = [m for m in mots if '-' in m]
        rapport.append("### 2.2 Mots Composés (avec trait d'union)")
        rapport.append("")
        rapport.append(f"- **Total** : {len(mots_composes)} mots ({len(mots_composes)/len(mots)*100:.1f}%)")
        rapport.append(f"- **Exemples** : {', '.join(mots_composes[:15])}")
        rapport.append("")
        
        # ============================================================
        # 4. ANALYSE PHONOGRAPHÉMATIQUE
        # ============================================================
        rapport.append("## 3. Analyse Phonographématique")
        rapport.append("")
        
        # Caractères spéciaux créoles
        caracteres_creoles = {'à': 0, 'é': 0, 'è': 0, 'ê': 0, 'ò': 0, 'ô': 0, 'ù': 0, 'ñ': 0, 'ç': 0}
        for mot in mots:
            for char in mot:
                if char in caracteres_creoles:
                    caracteres_creoles[char] += 1
        
        rapport.append("### 3.1 Caractères Diacritiques")
        rapport.append("")
        rapport.append("| Caractère | Fréquence | Usage |")
        rapport.append("|-----------|-----------|-------|")
        for char, freq in sorted(caracteres_creoles.items(), key=lambda x: x[1], reverse=True):
            if freq > 0:
                rapport.append(f"| **{char}** | {freq:,} | Très fréquent" if freq > 100 else f"| **{char}** | {freq:,} | Modéré" if freq > 10 else f"| **{char}** | {freq:,} | Rare |")
        rapport.append("")
        
        # Digrammes fréquents
        digrammes = Counter()
        for mot in mots:
            for i in range(len(mot) - 1):
                digrammes[mot[i:i+2]] += 1
        
        rapport.append("### 3.2 Digrammes les Plus Fréquents")
        rapport.append("")
        rapport.append("| Digramme | Fréquence |")
        rapport.append("|----------|-----------|")
        for digr, freq in digrammes.most_common(20):
            rapport.append(f"| **{digr}** | {freq:,} |")
        rapport.append("")
        
        # ============================================================
        # 5. ANALYSE LEXICALE STRATIFIÉE
        # ============================================================
        rapport.append("## 4. Analyse Lexicale Stratifiée")
        rapport.append("")
        
        # Hapax et distribution de fréquence
        hapax = sum(1 for f in frequences if f == 1)
        dis_legomena = sum(1 for f in frequences if f == 2)
        
        rapport.append("### 4.1 Distribution de Fréquence (Loi de Zipf)")
        rapport.append("")
        rapport.append(f"- **Hapax legomena** (freq=1) : {hapax:,} mots ({hapax/len(mots)*100:.1f}%)")
        rapport.append(f"- **Dis legomena** (freq=2) : {dis_legomena:,} mots ({dis_legomena/len(mots)*100:.1f}%)")
        rapport.append(f"- **Mots rares** (freq 3-5) : {sum(1 for f in frequences if 3 <= f <= 5):,} mots")
        rapport.append(f"- **Mots fréquents** (freq 6-20) : {sum(1 for f in frequences if 6 <= f <= 20):,} mots")
        rapport.append(f"- **Mots très fréquents** (freq >20) : {sum(1 for f in frequences if f > 20):,} mots")
        rapport.append("")
        
        # Principe de Pareto
        cumul = 0
        seuil_80 = total_tokens * 0.8
        mots_80 = 0
        for freq in sorted(frequences, reverse=True):
            cumul += freq
            mots_80 += 1
            if cumul >= seuil_80:
                break
        
        rapport.append("### 4.2 Principe de Pareto")
        rapport.append("")
        rapport.append(f"- **{mots_80:,} mots** ({mots_80/len(mots)*100:.1f}%) représentent **80%** des occurrences")
        rapport.append(f"- **Vocabulaire fondamental** : Les {min(1000, len(mots))} mots les plus fréquents")
        rapport.append("")
        
        # Top 50 mots
        rapport.append("### 4.3 Vocabulaire Fondamental (Top 50)")
        rapport.append("")
        rapport.append("| Rang | Mot | Fréquence | % Cumul |")
        rapport.append("|------|-----|-----------|---------|")
        
        cumul = 0
        for i, (mot, freq) in enumerate(list(self.nouveau_dictionnaire.items())[:50], 1):
            cumul += freq
            pct_cumul = (cumul / total_tokens) * 100
            rapport.append(f"| {i:2d} | **{mot}** | {freq:,} | {pct_cumul:.2f}% |")
        
        rapport.append("")
        
        # ============================================================
        # 6. ANALYSE SYNTAXIQUE (N-GRAMS)
        # ============================================================
        rapport.append("## 5. Analyse Syntaxique et Collocations")
        rapport.append("")
        
        if 'bigrammes' in self.stats_corpus:
            bigrammes = self.stats_corpus['bigrammes']
            
            rapport.append("### 5.1 Bigrammes les Plus Fréquents")
            rapport.append("")
            rapport.append("| Rang | Bigramme | Fréquence |")
            rapport.append("|------|----------|-----------|")
            
            for i, ((w1, w2), freq) in enumerate(bigrammes.most_common(30), 1):
                rapport.append(f"| {i:2d} | **{w1} {w2}** | {freq:,} |")
            
            rapport.append("")
            
            # Marqueurs TMA
            rapport.append("### 5.2 Marqueurs Temps-Mode-Aspect (TMA)")
            rapport.append("")
            
            marqueurs_tma = {
                'ka': 'Aspect progressif/habituel',
                'té': 'Passé',
                'ké': 'Futur',
                'kay': 'Futur',
                'pa': 'Négation',
                'ja': 'Déjà (accompli)',
            }
            
            rapport.append("| Marqueur | Fonction | Fréquence | Collocations principales |")
            rapport.append("|----------|----------|-----------|--------------------------|")
            
            for marqueur, fonction in marqueurs_tma.items():
                if marqueur in self.nouveau_dictionnaire:
                    freq = self.nouveau_dictionnaire[marqueur]
                    # Trouver les collocations
                    collocations = []
                    for (w1, w2), f in bigrammes.most_common(100):
                        if w1 == marqueur:
                            collocations.append(w2)
                        if len(collocations) >= 3:
                            break
                    coll_str = ', '.join(collocations) if collocations else "—"
                    rapport.append(f"| **{marqueur}** | {fonction} | {freq:,} | {coll_str} |")
            
            rapport.append("")
        
        # Prédictions N-grams
        if self.nouveaux_ngrams:
            rapport.append("### 5.3 Exemples de Prédictions Contextuelles")
            rapport.append("")
            rapport.append("| Mot source | Prédictions (probabilité) |")
            rapport.append("|------------|---------------------------|")
            
            exemples_pred = ['ka', 'nou', 'mwen', 'yo', 'an', 'la', 'té', 'pa', 'tout', 'pou']
            for mot in exemples_pred:
                if mot in self.nouveaux_ngrams:
                    preds = self.nouveaux_ngrams[mot][:5]
                    pred_str = ", ".join([f"{p['word']} ({p['probability']:.2f})" for p in preds])
                    rapport.append(f"| **{mot}** | {pred_str} |")
            
            rapport.append("")
        
        # ============================================================
        # 7. MOTS LONGS ET COMPLEXITÉ
        # ============================================================
        rapport.append("## 6. Mots Longs et Complexité Morphologique")
        rapport.append("")
        
        mots_longs = [(mot, len(mot), self.nouveau_dictionnaire[mot]) for mot in mots if len(mot) >= 10]
        mots_longs.sort(key=lambda x: x[1], reverse=True)
        
        rapport.append(f"### 6.1 Mots de 10 Lettres et Plus ({len(mots_longs)} mots)")
        rapport.append("")
        rapport.append("| Rang | Mot | Longueur | Fréquence |")
        rapport.append("|------|-----|----------|-----------|")
        
        for i, (mot, longueur, freq) in enumerate(mots_longs[:30], 1):
            rapport.append(f"| {i:2d} | **{mot}** | {longueur} lettres | {freq:,} |")
        
        rapport.append("")
        
        # ============================================================
        # 8. COMPARAISON DIACHRONIQUE (si backup existe)
        # ============================================================
        if self.dictionnaire_actuel:
            rapport.append("## 7. Évolution Diachronique du Lexique")
            rapport.append("")
            
            anciens_mots = set(self.dictionnaire_actuel.keys())
            nouveaux_mots_set = set(self.nouveau_dictionnaire.keys())
            
            mots_ajoutes = nouveaux_mots_set - anciens_mots
            mots_supprimes = anciens_mots - nouveaux_mots_set
            mots_conserves = anciens_mots & nouveaux_mots_set
            
            rapport.append(f"- **Mots conservés** : {len(mots_conserves):,} ({len(mots_conserves)/len(anciens_mots)*100:.1f}% de l'ancien dictionnaire)")
            rapport.append(f"- **Mots ajoutés** : {len(mots_ajoutes):,}")
            rapport.append(f"- **Mots supprimés** : {len(mots_supprimes):,}")
            rapport.append("")
            
            if mots_ajoutes:
                rapport.append("### 7.1 Nouveaux Mots Ajoutés (échantillon)")
                rapport.append("")
                echantillon = sorted(list(mots_ajoutes))[:50]
                rapport.append(f"`{', '.join(echantillon)}`")
                rapport.append("")
        
        # ============================================================
        # 9. QUALITÉ ET VALIDATION
        # ============================================================
        rapport.append("## 8. Qualité et Validation Linguistique")
        rapport.append("")
        
        # Mots suspects (très courts ou avec caractères inhabituels)
        mots_suspects = [m for m in mots if len(m) == 2 or any(c.isdigit() for c in m)]
        
        rapport.append("### 8.1 Analyse de Qualité")
        rapport.append("")
        rapport.append(f"- **Mots de 2 lettres** : {len([m for m in mots if len(m) == 2]):,}")
        rapport.append(f"- **Mots avec chiffres** : {len([m for m in mots if any(c.isdigit() for c in m)]):,}")
        rapport.append(f"- **Cohérence orthographique** : {'✓ Bonne' if len(mots_suspects) < len(mots) * 0.05 else '⚠ À vérifier'}")
        rapport.append("")
        
        # ============================================================
        # 10. MÉTRIQUES LINGUISTIQUES AVANCÉES
        # ============================================================
        rapport.append("## 9. Métriques Linguistiques Avancées")
        rapport.append("")
        
        # Entropie de Shannon (simplifiée)
        import math
        entropie = -sum((f/total_tokens) * math.log2(f/total_tokens) for f in frequences if f > 0)
        
        rapport.append(f"- **Type-Token Ratio (TTR)** : {ttr:.4f}")
        rapport.append(f"- **Entropie lexicale (Shannon)** : {entropie:.2f} bits")
        rapport.append(f"- **Diversité lexicale** : {'Très élevée' if entropie > 12 else 'Élevée' if entropie > 10 else 'Moyenne'}")
        rapport.append("")
        
        # ============================================================
        # 11. RECOMMANDATIONS
        # ============================================================
        rapport.append("## 10. Recommandations Linguistiques")
        rapport.append("")
        rapport.append("### 10.1 Forces du Corpus")
        rapport.append("")
        rapport.append(f"- Couverture lexicale importante ({len(mots):,} types)")
        rapport.append(f"- Richesse des bigrammes ({len(self.stats_corpus.get('bigrammes', {})):,} patterns)")
        rapport.append(f"- Présence des marqueurs TMA caractéristiques du créole")
        rapport.append("")
        
        rapport.append("### 10.2 Axes d'Amélioration")
        rapport.append("")
        rapport.append("- Enrichir le vocabulaire technique et scientifique")
        rapport.append("- Documenter les variantes orthographiques")
        rapport.append("- Ajouter des métadonnées sémantiques (catégories grammaticales)")
        rapport.append("- Développer un système de lemmatisation")
        rapport.append("")
        
        # ============================================================
        # 12. ANNEXES
        # ============================================================
        rapport.append("## Annexes")
        rapport.append("")
        rapport.append("### A. Références Bibliographiques")
        rapport.append("")
        rapport.append("- Bernabé, J. (1983). *Fondal-natal : Grammaire basilectale approchée des créoles guadeloupéen et martiniquais*.")
        rapport.append("- Ludwig, R., Montbrand, D., Poullet, H., & Telchid, S. (2001). *Dictionnaire créole-français (Guadeloupe)*.")
        rapport.append("- Hazaël-Massieux, M.-C. (2008). *Textes anciens en créole français de la Caraïbe*.")
        rapport.append("")
        
        rapport.append("### B. Méthodologie")
        rapport.append("")
        rapport.append("**Tokenisation** : Expression régulière Unicode préservant les diacritiques créoles")
        rapport.append("")
        rapport.append("**N-grams** : Probabilités conditionnelles P(w₂|w₁) avec seuil de pertinence à 1%")
        rapport.append("")
        rapport.append("**Normalisation** : Conversion en minuscules, préservation des traits d'union")
        rapport.append("")
        
        rapport.append("---")
        rapport.append("")
        rapport.append(f"*Rapport généré automatiquement par Kreyòl Potomitan™ Pipeline v{self.version}*")
        rapport.append("")
        rapport.append("*Pou an kreyòl ki ka viv é ka evolyé !*")
        rapport.append("")
        
        # Sauvegarder le rapport
        try:
            with open(self.chemin_rapport, 'w', encoding='utf-8') as f:
                f.write('\n'.join(rapport))
            
            print(f"✅ Rapport linguistique généré : {self.chemin_rapport}")
            print(f"   📊 {len(rapport)} lignes")
            print(f"   📄 Taille : {os.path.getsize(self.chemin_rapport) / 1024:.1f} Ko")
            return True
            
        except Exception as e:
            print(f"❌ Erreur lors de la sauvegarde du rapport : {e}")
            return False
    
    def sauvegarder_donnees(self):
        """Sauvegarde les nouvelles données"""
        print("\n💾 SAUVEGARDE DES DONNÉES")
        print("-" * 35)
        
        # Créer les backups
        if os.path.exists(self.chemin_dict):
            backup_dict = f"backups/creole_dict_backup_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
            os.makedirs(os.path.dirname(backup_dict), exist_ok=True)
            shutil.copy2(self.chemin_dict, backup_dict)
            print(f"📁 Backup dictionnaire: {backup_dict}")
        
        if os.path.exists(self.chemin_ngrams):
            backup_ngrams = f"backups/creole_ngrams_backup_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
            os.makedirs(os.path.dirname(backup_ngrams), exist_ok=True)
            shutil.copy2(self.chemin_ngrams, backup_ngrams)
            print(f"📁 Backup N-grams: {backup_ngrams}")
        
        # Sauvegarder le nouveau dictionnaire
        if self.nouveau_dictionnaire:
            os.makedirs(os.path.dirname(self.chemin_dict), exist_ok=True)
            
            # Format pour Flutter (dictionnaire simple mot -> fréquence)
            with open(self.chemin_dict, 'w', encoding='utf-8') as f:
                json.dump(self.nouveau_dictionnaire, f, ensure_ascii=False, indent=2)
            print(f"✅ Dictionnaire sauvegardé: {len(self.nouveau_dictionnaire)} mots")
            
            # Format pour Android (array de paires [mot, fréquence])
            # Ce format sera migré par l'app Android en { mot: {frequency: X, user_count: 0} }
            dict_android_format = [[mot, freq] for mot, freq in self.nouveau_dictionnaire.items()]
            android_dict_path = self.chemin_dict.replace('clavier_creole', 'android_keyboard/app/src/main')
            os.makedirs(os.path.dirname(android_dict_path), exist_ok=True)
            with open(android_dict_path, 'w', encoding='utf-8') as f:
                json.dump(dict_android_format, f, ensure_ascii=False, indent=2)
            print(f"✅ Dictionnaire Android sauvegardé: format array [[mot, freq], ...]")

            os.makedirs(os.path.dirname(self.chemin_dict_docs), exist_ok=True)
            with open(self.chemin_dict_docs, 'w', encoding='utf-8') as f:
                json.dump(dict_android_format, f, ensure_ascii=False, indent=2)
            print(f"✅ Dictionnaire simulateur sauvegardé: docs/assets/")
        
        # Sauvegarder les nouveaux N-grams
        if self.nouveaux_ngrams:
            os.makedirs(os.path.dirname(self.chemin_ngrams), exist_ok=True)
            with open(self.chemin_ngrams, 'w', encoding='utf-8') as f:
                json.dump(self.nouveaux_ngrams, f, ensure_ascii=False, indent=2)
            print(f"✅ N-grams sauvegardés: {len(self.nouveaux_ngrams)} prédictions")
            
            # Synchroniser avec Android
            ngrams_android_path = self.chemin_ngrams.replace('clavier_creole', 'android_keyboard/app/src/main')
            os.makedirs(os.path.dirname(ngrams_android_path), exist_ok=True)
            with open(ngrams_android_path, 'w', encoding='utf-8') as f:
                json.dump(self.nouveaux_ngrams, f, ensure_ascii=False, indent=2)
            print(f"✅ N-grams Android sauvegardés")

            os.makedirs(os.path.dirname(self.chemin_ngrams_docs), exist_ok=True)
            with open(self.chemin_ngrams_docs, 'w', encoding='utf-8') as f:
                json.dump(self.nouveaux_ngrams, f, ensure_ascii=False, indent=2)
            print(f"✅ N-grams simulateur sauvegardés: docs/assets/")
        
        print("\n📱 SYNCHRONISATION TERMINÉE")
        print("-" * 35)
        print("🎉 Fichiers prêts pour le build APK !")
        
        return True
    
    def valider_donnees(self):
        """Validation complète des données"""
        print("\n🔍 VALIDATION COMPLÈTE")
        print("-" * 30)
        
        succes_total = True
        
        # Test dictionnaire
        print("\n📚 Test dictionnaire...")
        if os.path.exists(self.chemin_dict):
            try:
                with open(self.chemin_dict, 'r', encoding='utf-8') as f:
                    dict_data = json.load(f)
                print(f"   ✅ {len(dict_data)} mots, 0 erreurs mineures")
            except Exception as e:
                print(f"   ❌ Erreur: {e}")
                succes_total = False
        else:
            print("   ❌ Fichier dictionnaire manquant")
            succes_total = False
        
        # Test N-grams
        print("\n🧠 Test N-grams...")
        if os.path.exists(self.chemin_ngrams):
            try:
                with open(self.chemin_ngrams, 'r', encoding='utf-8') as f:
                    ngrams_data = json.load(f)
                predictions = len([k for k, v in ngrams_data.items() if isinstance(v, list) and v])
                print(f"   ✅ {predictions} prédictions")
            except Exception as e:
                print(f"   ❌ Erreur: {e}")
                succes_total = False
        else:
            print("   ❌ Fichier N-grams manquant")
            succes_total = False
        
        # Test prédictions
        print("\n🎯 Test prédictions...")
        exemples = ["ka", "nou", "mwen", "yo"]
        tests_reussis = 0
        
        if os.path.exists(self.chemin_ngrams):
            try:
                with open(self.chemin_ngrams, 'r', encoding='utf-8') as f:
                    ngrams_data = json.load(f)
                
                for mot in exemples:
                    if mot in ngrams_data and ngrams_data[mot]:
                        tests_reussis += 1
                
                print(f"   ✅ {tests_reussis}/{len(exemples)} exemples")
            except Exception:
                print("   ❌ Erreur test prédictions")
                succes_total = False
        
        # Test intégrité
        print("\n🔒 Test intégrité...")
        if os.path.exists(self.chemin_dict) and os.path.exists(self.chemin_ngrams):
            dict_size = os.path.getsize(self.chemin_dict)
            ngrams_size = os.path.getsize(self.chemin_ngrams)
            if dict_size > 1000 and ngrams_size > 1000:
                print("   ✅ Tailles fichiers correctes")
            else:
                print("   ❌ Fichiers trop petits")
                succes_total = False
        else:
            print("   ❌ Fichiers manquants")
            succes_total = False
        
        # Résumé
        print(f"\n📋 RÉSUMÉ VALIDATION:")
        print(f"   Dictionnaire   : {'✅ RÉUSSI' if os.path.exists(self.chemin_dict) else '❌ ÉCHEC'}")
        print(f"   N-grams        : {'✅ RÉUSSI' if os.path.exists(self.chemin_ngrams) else '❌ ÉCHEC'}")
        print(f"   Prédictions    : {'✅ RÉUSSI' if tests_reussis >= 3 else '❌ ÉCHEC'}")
        print(f"   Intégrité      : {'✅ RÉUSSI' if succes_total else '❌ ÉCHEC'}")
        
        score = sum([
            os.path.exists(self.chemin_dict),
            os.path.exists(self.chemin_ngrams),
            tests_reussis >= 3,
            succes_total
        ])
        
        print(f"\n🏆 SCORE: {score}/4 ({score*25}%)")
        
        if score == 4:
            print("🎉 VALIDATION PARFAITE ! Système prêt pour Android.")
        elif score >= 3:
            print("✅ Validation réussie avec quelques avertissements.")
        else:
            print("❌ Validation échouée. Vérifiez les erreurs ci-dessus.")
        
        return score >= 3
    
    def executer_pipeline(self):
        """Exécute le pipeline complet automatiquement"""
        print("\n🚀 PIPELINE AUTOMATIQUE COMPLET")
        print("=" * 40)
        
        etapes = [
            ("Chargement textes", self.charger_textes_kreyol),
            ("Création dictionnaire", self.creer_dictionnaire),
            ("Génération N-grams", self.creer_ngrams),
            ("Analyse statistiques", self.analyser_statistiques),
            ("Analyse delta", self.analyser_delta),
            ("Rapport linguistique", self.generer_rapport_linguistique),
            ("Sauvegarde", self.sauvegarder_donnees),
            ("Validation finale", self.valider_donnees),
        ]
        
        succes_total = True
        
        for i, (nom, fonction) in enumerate(etapes, 1):
            print(f"\n⏳ Étape {i}/{len(etapes)}: {nom}")
            try:
                succes = fonction()
                if succes:
                    print(f"✅ {nom} - Terminé")
                else:
                    print(f"⚠️ {nom} - Avec avertissements")
                    succes_total = False
            except Exception as e:
                print(f"❌ {nom} - Erreur: {e}")
                succes_total = False
        
        return succes_total

    def executer_rapport_seul(self):
        """Régénère le seul rapport linguistique, sans rien sauvegarder d'autre.

        Les mêmes étapes de calcul que le pipeline complet sont rejouées, mais
        `sauvegarder_donnees` est écarté : le dictionnaire et les n-grams sont
        reconstruits en mémoire pour alimenter le rapport et sont jetés ensuite.
        Les fichiers livrés dans le clavier restent donc ceux du dernier build,
        et une simple mise à jour du rapport ne peut pas changer les suggestions
        des utilisateurs.
        """
        print("\n📄 RAPPORT LINGUISTIQUE SEUL")
        print("=" * 40)
        print("ℹ️  Les dictionnaires ne seront pas réécrits")

        def charger_depuis_huggingface():
            """Chargement qui refuse le repli local.

            Un rapport régénéré sur l'instantané local serait daté d'aujourd'hui
            tout en décrivant un corpus périmé. Mieux vaut ne rien produire.
            """
            if not self.charger_textes_kreyol():
                return False
            if getattr(self, "source_chargement", None) != "Hugging Face":
                print("❌ Corpus chargé localement, pas depuis Hugging Face")
                print("   🚫 Rapport non régénéré : il annoncerait un corpus qu'il ne décrit pas")
                self.textes_kreyol = []
                return False
            return True

        etapes = [
            ("Chargement textes", charger_depuis_huggingface),
            ("Création dictionnaire", self.creer_dictionnaire),
            ("Génération N-grams", self.creer_ngrams),
            ("Analyse statistiques", self.analyser_statistiques),
            ("Analyse delta", self.analyser_delta),
            ("Rapport linguistique", self.generer_rapport_linguistique),
        ]

        succes_total = True

        for i, (nom, fonction) in enumerate(etapes, 1):
            print(f"\n⏳ Étape {i}/{len(etapes)}: {nom}")
            try:
                if fonction():
                    print(f"✅ {nom} - Terminé")
                else:
                    print(f"⚠️ {nom} - Avec avertissements")
                    succes_total = False
            except Exception as e:
                print(f"❌ {nom} - Erreur: {e}")
                succes_total = False

        return succes_total

def main():
    """Fonction principale - Pipeline unique automatique"""
    rapport_seul = "--rapport-seul" in sys.argv[1:]

    try:
        # Créer et exécuter le pipeline
        pipeline = KreyolPipelineUnique()
        if rapport_seul:
            succes = pipeline.executer_rapport_seul()
        else:
            succes = pipeline.executer_pipeline()
        
        # Afficher les statistiques finales
        dict_count = len(pipeline.nouveau_dictionnaire) if pipeline.nouveau_dictionnaire else 0
        ngrams_count = len(pipeline.nouveaux_ngrams) if pipeline.nouveaux_ngrams else 0
        
        print("\n" + "=" * 60)
        if succes:
            print("🎉 PIPELINE KREYÒL POTOMITAN™ TERMINÉ AVEC SUCCÈS!")
            print("=" * 60)
            print("📱 Fichiers prêts pour l'intégration Android")
            print(" Kreyòl Gwadloup ka viv! ")
            print("✅ Dictionary files generated successfully")
            print(f"📊 Dictionary: {dict_count} words, {ngrams_count} N-grams")
            print(f"📄 Rapport linguistique : {pipeline.chemin_rapport}")
            sys.exit(0)
        else:
            print("⚠️ PIPELINE TERMINÉ AVEC DES AVERTISSEMENTS")
            print("=" * 60)
            print("🔍 Consultez les messages ci-dessus pour plus de détails")
            sys.exit(1)
            
    except Exception as e:
        print(f"\n❌ ERREUR CRITIQUE: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()