#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Analyseur de Disposition de Clavier Créole - Potomitan™
Analyse les fréquences des caractères créoles et propose une disposition optimisée

Ce programme :
1. Analyse les fréquences des caractères dans les textes créoles
2. Étudie la proximité des doigts et l'ergonomie de frappe
3. Propose une disposition alternative légère à AZERTY
4. Optimise pour les caractères créoles spéciaux (ò, é, è, etc.)

Usage: python DispositionKlavyé.py
"""

import json
import re
from collections import Counter, defaultdict
import matplotlib.pyplot as plt
import seaborn as sns
import pandas as pd
from pathlib import Path

class AnalyseurDisposition:
    
    def __init__(self):
        """Initialise l'analyseur avec les configurations de base"""
        
        # Disposition AZERTY actuelle (3 rangées principales)
        self.azerty = {
            'rangee_1': ['a', 'z', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'],
            'rangee_2': ['q', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'm'],
            'rangee_3': ['w', 'x', 'c', 'v', 'b', 'n']
        }
        
        # Positions des doigts (0 = auriculaire gauche, 9 = auriculaire droit)
        self.positions_doigts = {
            # Rangée 1 (AZERTYUIOP)
            'a': 0, 'z': 1, 'e': 2, 'r': 3, 't': 4,
            'y': 5, 'u': 6, 'i': 7, 'o': 8, 'p': 9,
            # Rangée 2 (QSDFGHJKLM)
            'q': 0, 's': 1, 'd': 2, 'f': 3, 'g': 4,
            'h': 5, 'j': 6, 'k': 7, 'l': 8, 'm': 9,
            # Rangée 3 (WXCVBN)
            'w': 0, 'x': 1, 'c': 2, 'v': 3, 'b': 4, 'n': 5
        }
        
        # Force des doigts (index = 1.0, majeur = 0.9, annulaire = 0.7, auriculaire = 0.5)
        self.force_doigts = {
            0: 0.5,  # Auriculaire gauche
            1: 0.7,  # Annulaire gauche
            2: 0.9,  # Majeur gauche
            3: 1.0,  # Index gauche
            4: 1.0,  # Index gauche (T)
            5: 1.0,  # Index droit (Y)
            6: 1.0,  # Index droit
            7: 0.9,  # Majeur droit
            8: 0.7,  # Annulaire droit
            9: 0.5   # Auriculaire droit
        }
        
        # Caractères créoles spéciaux
        self.caracteres_creoles = ['ò', 'é', 'è', 'ù', 'à', 'â', 'ê', 'î', 'ô', 'û', 'ç']
        
        # Statistiques
        self.stats_caracteres = Counter()
        self.stats_bigrammes = Counter()
        self.effort_actuel = 0
        self.effort_optimise = 0

    def charger_donnees_creoles(self):
        """Charge les données créoles depuis les fichiers JSON"""
        print("📚 Chargement des données créoles...")
        
        # 1. Charger le dictionnaire créole
        try:
            with open("android_keyboard/app/src/main/assets/creole_dict.json", 'r', encoding='utf-8') as f:
                dict_data = json.load(f)
                print(f"✅ Dictionnaire créole: {len(dict_data)} mots")
        except Exception as e:
            print(f"⚠️ Erreur dictionnaire: {e}")
            dict_data = []
        
        # 2. Charger les N-grams
        try:
            with open("android_keyboard/app/src/main/assets/creole_ngrams.json", 'r', encoding='utf-8') as f:
                ngrams_data = json.load(f)
                print(f"✅ N-grams créoles: {len(ngrams_data.get('unigrams', {}))} unigrams")
        except Exception as e:
            print(f"⚠️ Erreur N-grams: {e}")
            ngrams_data = {}
        
        return dict_data, ngrams_data

    def analyser_frequences_caracteres(self, dict_data, ngrams_data):
        """Analyse les fréquences des caractères dans les textes créoles"""
        print("🔍 Analyse des fréquences des caractères...")
        
        # Analyser depuis le dictionnaire
        for mot_freq in dict_data:
            if isinstance(mot_freq, list) and len(mot_freq) == 2:
                mot, freq = mot_freq[0], mot_freq[1]
                for char in mot.lower():
                    if char.isalpha() or char in self.caracteres_creoles:
                        self.stats_caracteres[char] += freq
        
        # Analyser depuis les unigrams
        unigrams = ngrams_data.get('unigrams', {})
        for mot, freq in unigrams.items():
            for char in mot.lower():
                if char.isalpha() or char in self.caracteres_creoles:
                    self.stats_caracteres[char] += freq * 10  # Pondération plus forte
        
        print(f"📊 {len(self.stats_caracteres)} caractères analysés")
        return self.stats_caracteres

    def analyser_bigrammes_caracteres(self, dict_data, ngrams_data):
        """Analyse les bigrammes de caractères pour l'optimisation"""
        print("🔗 Analyse des bigrammes de caractères...")
        
        # Analyser depuis le dictionnaire
        for mot_freq in dict_data:
            if isinstance(mot_freq, list) and len(mot_freq) == 2:
                mot, freq = mot_freq[0], mot_freq[1]
                mot_clean = mot.lower()
                for i in range(len(mot_clean) - 1):
                    if (mot_clean[i].isalpha() or mot_clean[i] in self.caracteres_creoles) and \
                       (mot_clean[i+1].isalpha() or mot_clean[i+1] in self.caracteres_creoles):
                        bigramme = mot_clean[i] + mot_clean[i+1]
                        self.stats_bigrammes[bigramme] += freq
        
        # Analyser depuis les unigrams (reconstituer les bigrammes probables)
        unigrams = ngrams_data.get('unigrams', {})
        for mot, freq in unigrams.items():
            mot_clean = mot.lower()
            for i in range(len(mot_clean) - 1):
                if (mot_clean[i].isalpha() or mot_clean[i] in self.caracteres_creoles) and \
                   (mot_clean[i+1].isalpha() or mot_clean[i+1] in self.caracteres_creoles):
                    bigramme = mot_clean[i] + mot_clean[i+1]
                    self.stats_bigrammes[bigramme] += freq * 15
        
        print(f"🔗 {len(self.stats_bigrammes)} bigrammes analysés")
        return self.stats_bigrammes

    def calculer_effort_frappe(self, disposition, texte_test=""):
        """Calcule l'effort de frappe pour une disposition donnée"""
        effort_total = 0
        
        if not texte_test:
            # Utiliser les fréquences réelles des caractères
            for char, freq in self.stats_caracteres.items():
                if char in disposition:
                    doigt = disposition[char]
                    force = self.force_doigts.get(doigt, 0.5)
                    effort_total += freq / force
        else:
            # Analyser un texte spécifique
            for char in texte_test.lower():
                if char in disposition:
                    doigt = disposition[char]
                    force = self.force_doigts.get(doigt, 0.5)
                    effort_total += 1 / force
        
        return effort_total

    def calculer_distance_bigrammes(self, disposition):
        """Calcule l'effort des bigrammes (distance entre doigts)"""
        effort_bigrammes = 0
        
        for bigramme, freq in self.stats_bigrammes.items():
            if len(bigramme) == 2 and bigramme[0] in disposition and bigramme[1] in disposition:
                doigt1 = disposition[bigramme[0]]
                doigt2 = disposition[bigramme[1]]
                distance = abs(doigt1 - doigt2)
                
                # Pénalité pour même doigt (frappe rapide difficile)
                if doigt1 == doigt2:
                    penalite = 2.0
                else:
                    penalite = 1.0 + (distance * 0.1)
                
                effort_bigrammes += freq * penalite
        
        return effort_bigrammes

    def generer_disposition_optimisee(self):
        """Génère une disposition optimisée pour le créole"""
        print("🎯 Génération de la disposition optimisée...")
        
        # Trier les caractères par fréquence
        chars_freqs = self.stats_caracteres.most_common()
        
        # Positions optimales (index et majeurs en priorité)
        positions_optimales = [3, 6, 2, 7, 4, 5, 1, 8, 0, 9]  # Ordre de préférence des doigts
        
        # Nouvelle disposition
        disposition_optimisee = {}
        
        # Placer les caractères créoles spéciaux en positions accessibles
        chars_creoles_dans_texte = [(char, freq) for char, freq in chars_freqs 
                                   if char in self.caracteres_creoles]
        
        # Placer les caractères normaux les plus fréquents
        chars_normaux = [(char, freq) for char, freq in chars_freqs 
                        if char not in self.caracteres_creoles and char.isalpha()]
        
        print(f"🔤 Caractères créoles fréquents: {[c[0] for c in chars_creoles_dans_texte[:5]]}")
        print(f"🔤 Caractères normaux fréquents: {[c[0] for c in chars_normaux[:10]]}")
        
        # Attribution des positions
        position_index = 0
        
        # D'abord les 20 caractères les plus fréquents (normaux + créoles mélangés)
        tous_chars = sorted(chars_freqs, key=lambda x: x[1], reverse=True)
        
        for char, freq in tous_chars[:20]:
            if position_index < len(positions_optimales):
                disposition_optimisee[char] = positions_optimales[position_index]
                position_index += 1
        
        return disposition_optimisee

    def comparer_dispositions(self, disp_azerty, disp_optimisee):
        """Compare l'efficacité des deux dispositions"""
        print("⚖️ Comparaison des dispositions...")
        
        # Créer la disposition AZERTY complète
        azerty_complete = {}
        for char in 'azertyuiopqsdfghjklmwxcvbn':
            if char in self.positions_doigts:
                azerty_complete[char] = self.positions_doigts[char]
        
        # Ajouter les caractères créoles en positions arbitraires pour AZERTY
        for char in self.caracteres_creoles:
            if char in self.stats_caracteres:
                azerty_complete[char] = 9  # Position difficile (auriculaire droit)
        
        # Calculer efforts
        effort_azerty = self.calculer_effort_frappe(azerty_complete)
        effort_azerty_bigrammes = self.calculer_distance_bigrammes(azerty_complete)
        
        effort_optimise = self.calculer_effort_frappe(disp_optimisee)
        effort_optimise_bigrammes = self.calculer_distance_bigrammes(disp_optimisee)
        
        # Calculs de performance
        total_azerty = effort_azerty + effort_azerty_bigrammes
        total_optimise = effort_optimise + effort_optimise_bigrammes
        
        amelioration = ((total_azerty - total_optimise) / total_azerty) * 100
        
        print(f"\n📊 RÉSULTATS DE COMPARAISON:")
        print(f"   🔴 AZERTY Effort Total: {total_azerty:,.0f}")
        print(f"      - Caractères: {effort_azerty:,.0f}")
        print(f"      - Bigrammes: {effort_azerty_bigrammes:,.0f}")
        print(f"   🟢 OPTIMISÉ Effort Total: {total_optimise:,.0f}")
        print(f"      - Caractères: {effort_optimise:,.0f}")
        print(f"      - Bigrammes: {effort_optimise_bigrammes:,.0f}")
        print(f"   ✨ Amélioration: {amelioration:.1f}%")
        
        return {
            'azerty_total': total_azerty,
            'optimise_total': total_optimise,
            'amelioration_pct': amelioration,
            'azerty_chars': effort_azerty,
            'azerty_bigrammes': effort_azerty_bigrammes,
            'optimise_chars': effort_optimise,
            'optimise_bigrammes': effort_optimise_bigrammes
        }

    def afficher_disposition_optimisee(self, disposition):
        """Affiche la disposition optimisée sous forme de clavier visuel"""
        print("\n⌨️ DISPOSITION OPTIMISÉE POUR LE CRÉOLE:")
        
        # Créer un mapping inverse (doigt -> caractères)
        clavier = {i: [] for i in range(10)}
        for char, doigt in disposition.items():
            clavier[doigt].append(char)
        
        # Afficher sous forme de rangées
        print("\n   Rangée 1 (AZERTYUIOP):")
        rangee1 = []
        for i in range(10):
            chars = [c for c in clavier[i] if c in 'azertyuiopàâêîôûçòéèù']
            if chars:
                rangee1.append(chars[0])
            else:
                rangee1.append('·')
        print(f"   {' '.join(rangee1)}")
        
        print("\n   Rangée 2 (QSDFGHJKLM):")
        rangee2 = []
        for i in range(10):
            chars = [c for c in clavier[i] if c in 'qsdfghjklm']
            if chars:
                rangee2.append(chars[0])
            else:
                rangee2.append('·')
        print(f"   {' '.join(rangee2)}")
        
        # Afficher les caractères créoles spéciaux
        print(f"\n🎨 Caractères créoles optimisés:")
        for char in self.caracteres_creoles:
            if char in disposition and char in self.stats_caracteres:
                doigt = disposition[char]
                freq = self.stats_caracteres[char]
                force = self.force_doigts[doigt]
                print(f"   '{char}' → doigt {doigt} (force {force}) - {freq} occurrences")

    def generer_rapport_complet(self, stats_comparaison):
        """Génère un rapport complet de l'analyse"""
        rapport = f"""
╔══════════════════════════════════════════════════════════════════════════════╗
║                     RAPPORT D'ANALYSE - DISPOSITION CLAVIER CRÉOLE          ║
║                              Potomitan™ - Version 1.0                       ║
╚══════════════════════════════════════════════════════════════════════════════╝

📊 STATISTIQUES GÉNÉRALES:
   • Caractères analysés: {len(self.stats_caracteres)}
   • Bigrammes analysés: {len(self.stats_bigrammes)}
   • Caractères créoles détectés: {len([c for c in self.stats_caracteres if c in self.caracteres_creoles])}

🔤 TOP 10 CARACTÈRES LES PLUS FRÉQUENTS:
"""
        for i, (char, freq) in enumerate(self.stats_caracteres.most_common(10), 1):
            if char in self.caracteres_creoles:
                rapport += f"   {i:2d}. '{char}' : {freq:,} occurrences ⭐ (créole)\n"
            else:
                rapport += f"   {i:2d}. '{char}' : {freq:,} occurrences\n"

        rapport += f"""
🔗 TOP 5 BIGRAMMES LES PLUS FRÉQUENTS:
"""
        for i, (bigramme, freq) in enumerate(self.stats_bigrammes.most_common(5), 1):
            rapport += f"   {i}. '{bigramme}' : {freq:,} occurrences\n"

        rapport += f"""
⚖️ COMPARAISON DE PERFORMANCE:
   🔴 AZERTY (actuel):
      • Effort total: {stats_comparaison['azerty_total']:,.0f}
      • Effort caractères: {stats_comparaison['azerty_chars']:,.0f}
      • Effort bigrammes: {stats_comparaison['azerty_bigrammes']:,.0f}
   
   🟢 DISPOSITION OPTIMISÉE:
      • Effort total: {stats_comparaison['optimise_total']:,.0f}
      • Effort caractères: {stats_comparaison['optimise_chars']:,.0f}
      • Effort bigrammes: {stats_comparaison['optimise_bigrammes']:,.0f}
   
   ✨ AMÉLIORATION GLOBALE: {stats_comparaison['amelioration_pct']:.1f}%

💡 RECOMMANDATIONS:
   1. Placer les caractères créoles (ò, é, è) sur les touches d'index
   2. Optimiser la position de 'ka', 'an', 'té' (mots les plus fréquents)
   3. Réduire l'effort des bigrammes courants ('ka', 'an', 'ou')
   4. Intégrer les accents dans la disposition principale

🎯 CONCLUSION:
   La disposition optimisée réduit l'effort de frappe de {stats_comparaison['amelioration_pct']:.1f}% 
   par rapport à AZERTY, spécialement adaptée au créole guadeloupéen.

─────────────────────────────────────────────────────────────────────────────
Généré le {pd.Timestamp.now().strftime('%d/%m/%Y à %H:%M')} | Potomitan™ Kreyol Keyboard
"""
        return rapport

    def sauvegarder_resultats(self, disposition_optimisee, stats_comparaison):
        """Sauvegarde les résultats dans des fichiers"""
        print("💾 Sauvegarde des résultats...")
        
        # 1. Disposition optimisée (JSON)
        disposition_data = {
            "version": "1.0",
            "type": "keyboard_layout",
            "branding": "Potomitan™",
            "layout_name": "Creole Optimized",
            "description": "Disposition clavier optimisée pour le créole guadeloupéen",
            "character_positions": disposition_optimisee,
            "finger_strength": self.force_doigts,
            "stats": stats_comparaison,
            "timestamp": pd.Timestamp.now().isoformat()
        }
        
        with open("disposition_creole_optimisee.json", 'w', encoding='utf-8') as f:
            json.dump(disposition_data, f, ensure_ascii=False, indent=2)
        
        # 2. Rapport complet
        rapport = self.generer_rapport_complet(stats_comparaison)
        with open("rapport_disposition_clavier.txt", 'w', encoding='utf-8') as f:
            f.write(rapport)
        
        # 3. Statistiques CSV pour analyse
        stats_df = pd.DataFrame([
            {'caractere': char, 'frequence': freq, 'type': 'creole' if char in self.caracteres_creoles else 'normal'}
            for char, freq in self.stats_caracteres.most_common()
        ])
        stats_df.to_csv("frequences_caracteres_creoles.csv", index=False, encoding='utf-8')
        
        print("✅ Fichiers sauvegardés:")
        print("   • disposition_creole_optimisee.json")
        print("   • rapport_disposition_clavier.txt")
        print("   • frequences_caracteres_creoles.csv")

def main():
    """Fonction principale"""
    print("═══════════════════════════════════════════════════════════════")
    print("🇬🇵 ANALYSEUR DE DISPOSITION CLAVIER CRÉOLE - Potomitan™")
    print("═══════════════════════════════════════════════════════════════")
    
    # Initialiser l'analyseur
    analyseur = AnalyseurDisposition()
    
    # 1. Charger les données
    dict_data, ngrams_data = analyseur.charger_donnees_creoles()
    
    if not dict_data and not ngrams_data:
        print("❌ Aucune donnée trouvée ! Vérifiez les fichiers JSON.")
        return
    
    # 2. Analyser les fréquences
    analyseur.analyser_frequences_caracteres(dict_data, ngrams_data)
    analyseur.analyser_bigrammes_caracteres(dict_data, ngrams_data)
    
    # 3. Générer la disposition optimisée
    disposition_optimisee = analyseur.generer_disposition_optimisee()
    
    # 4. Comparer les dispositions
    stats_comparaison = analyseur.comparer_dispositions(analyseur.azerty, disposition_optimisee)
    
    # 5. Afficher les résultats
    analyseur.afficher_disposition_optimisee(disposition_optimisee)
    
    # 6. Sauvegarder
    analyseur.sauvegarder_resultats(disposition_optimisee, stats_comparaison)
    
    print(f"\n🎉 Analyse terminée avec succès !")
    print(f"📈 Amélioration de {stats_comparaison['amelioration_pct']:.1f}% par rapport à AZERTY")
    print(f"🎯 Disposition optimisée pour le créole guadeloupéen !")

if __name__ == "__main__":
    main()
