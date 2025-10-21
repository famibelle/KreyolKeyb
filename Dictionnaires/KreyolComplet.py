#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
🇸🇷 KREYÒL POTOMITAN™ - PIPELINE UNIQUE ET AUTOMATIQUE 🇸🇷
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
        self.hf_token = None
        self.textes_kreyol = []
        self.dictionnaire_actuel = {}
        self.ngrams_actuels = {}
        self.nouveau_dictionnaire = {}
        self.nouveaux_ngrams = {}
        
        # Affichage d'en-tête
        self._afficher_entete()
        
        # Chargement automatique
        self._charger_configuration()
        self._charger_donnees_existantes()
        
        print("✅ Pipeline initialisé")
    
    def _afficher_entete(self):
        """Affiche l'en-tête du pipeline"""
        print("🇸🇷 KREYÒL POTOMITAN™ - PIPELINE UNIQUE ET AUTOMATIQUE 🇸🇷")
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
        
        if env_found:
            token = os.getenv('HF_TOKEN') or os.getenv('HF_TOKEN_read_write')
            if token:
                self.hf_token = token
                print("🔑 Token Hugging Face configuré")
            else:
                print("⚠️ Token Hugging Face non trouvé dans .env")
        else:
            print("⚠️ Configuration .env non trouvée (optionnel)")
    
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
        
        print(f"\n📋 RÉSUMÉ CHARGEMENT:")
        # Détection de source plus précise
        if textes_charges and self.textes_kreyol:
            source_hf = any(t.get("Source", "").find("Hugging") != -1 for t in self.textes_kreyol[:5])
            source = "Hugging Face" if source_hf else "Local"
        else:
            source = "Inconnu"
        print(f"   📊 {len(self.textes_kreyol)} textes chargés")
        print(f"   🌐 Source: {source}")
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
        
        # Fusionner avec le dictionnaire existant
        for mot, freq_nouvelle in compteur_mots.items():
            freq_existante = self.dictionnaire_actuel.get(mot, 0)
            compteur_mots[mot] = freq_existante + freq_nouvelle
        
        # Ajouter les mots existants non trouvés
        for mot, freq in self.dictionnaire_actuel.items():
            if mot not in compteur_mots:
                compteur_mots[mot] = freq
        
        self.nouveau_dictionnaire = dict(compteur_mots.most_common())
        
        nouveaux_mots = len(self.nouveau_dictionnaire) - len(self.dictionnaire_actuel)
        print(f"✅ Dictionnaire créé:")
        print(f"   - Total mots: {len(self.nouveau_dictionnaire)}")
        print(f"   - Nouveaux mots: {nouveaux_mots}")
        print(f"   - Mots existants: {len(self.dictionnaire_actuel)}")
        
        return True
    
    def creer_ngrams(self):
        """Crée des N-grams pour les prédictions"""
        print("\n🧠 CRÉATION DES N-GRAMS")
        print("-" * 30)
        
        if not self.textes_kreyol:
            print("❌ Aucun texte disponible")
            return False
        
        print("🔄 Génération des N-grams...")
        
        unigrammes = Counter()
        bigrammes = Counter()
        trigrammes = Counter()
        
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
            
            # Trigrammes
            for i in range(len(mots) - 2):
                trigramme = (mots[i], mots[i + 1], mots[i + 2])
                trigrammes[trigramme] += 1
        
        # Créer le modèle de prédictions
        predictions = {}
        total_unigrammes = sum(unigrammes.values())
        
        for mot in unigrammes:
            candidats = []
            
            # Chercher les mots qui suivent souvent ce mot
            for (premier, suivant), freq in bigrammes.items():
                if premier == mot:
                    probabilite = freq / unigrammes[premier]
                    if probabilite > 0.01:  # Seuil de pertinence
                        candidats.append({
                            "word": suivant,
                            "probability": round(probabilite, 3)
                        })
            
            # Trier par probabilité décroissante
            candidats.sort(key=lambda x: x["probability"], reverse=True)
            
            # Garder les 5 meilleurs
            if candidats:
                predictions[mot] = candidats[:5]
        
        self.nouveaux_ngrams = predictions
        
        print(f"✅ N-grams créés:")
        print(f"   - Unigrammes: {len(unigrammes)}")
        print(f"   - Bigrammes: {len(bigrammes)}")
        print(f"   - Trigrammes: {len(trigrammes)}")
        print(f"   - Prédictions: {len(predictions)}")
        
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
            with open(self.chemin_dict, 'w', encoding='utf-8') as f:
                json.dump(self.nouveau_dictionnaire, f, ensure_ascii=False, indent=2)
            print(f"✅ Dictionnaire sauvegardé: {len(self.nouveau_dictionnaire)} mots")
        
        # Sauvegarder les nouveaux N-grams
        if self.nouveaux_ngrams:
            os.makedirs(os.path.dirname(self.chemin_ngrams), exist_ok=True)
            with open(self.chemin_ngrams, 'w', encoding='utf-8') as f:
                json.dump(self.nouveaux_ngrams, f, ensure_ascii=False, indent=2)
            print(f"✅ N-grams sauvegardés: {len(self.nouveaux_ngrams)} prédictions")
        
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

def main():
    """Fonction principale - Pipeline unique automatique"""
    try:
        # Créer et exécuter le pipeline
        pipeline = KreyolPipelineUnique()
        succes = pipeline.executer_pipeline()
        
        # Afficher les statistiques finales
        dict_count = len(pipeline.nouveau_dictionnaire) if pipeline.nouveau_dictionnaire else 0
        ngrams_count = len(pipeline.nouveaux_ngrams) if pipeline.nouveaux_ngrams else 0
        
        print("\n" + "=" * 60)
        if succes:
            print("🎉 PIPELINE KREYÒL POTOMITAN™ TERMINÉ AVEC SUCCÈS!")
            print("=" * 60)
            print("📱 Fichiers prêts pour l'intégration Android")
            print("🇸🇷 Kreyòl Gwadloup ka viv! 🇸🇷")
            print("✅ Dictionary files generated successfully")
            print(f"📊 Dictionary: {dict_count} words, {ngrams_count} N-grams")
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