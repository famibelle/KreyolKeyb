#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Analyseur du Dictionnaire Créole - Potomitan™
Analyse et affiche les statistiques du dictionnaire final

Usage: python AnalyserDictionnaire.py
"""

import json
import os
from collections import Counter

def analyser_dictionnaire():
    """Analyse le dictionnaire créole et affiche les statistiques"""
    
    chemin_dict = "android_keyboard/app/src/main/assets/creole_dict.json"
    
    if not os.path.exists(chemin_dict):
        print(f"❌ Dictionnaire non trouvé: {chemin_dict}")
        return
    
    print("🇬🇵 ANALYSE DU DICTIONNAIRE CRÉOLE POTOMITAN")
    print("=" * 50)
    
    # Charger le dictionnaire
    try:
        with open(chemin_dict, 'r', encoding='utf-8') as f:
            data = json.load(f)
    except Exception as e:
        print(f"❌ Erreur de chargement: {e}")
        return
    
    if not isinstance(data, list):
        print("❌ Format de dictionnaire invalide")
        return
    
    # Extraire les mots et fréquences
    mots_freqs = []
    for item in data:
        if len(item) == 2:
            mot, freq = item
            mots_freqs.append((mot, freq))
    
    print(f"📚 Total mots dans le dictionnaire: {len(mots_freqs)}")
    
    if not mots_freqs:
        print("❌ Aucun mot trouvé dans le dictionnaire")
        return
    
    # Statistiques générales
    freqs = [freq for _, freq in mots_freqs]
    
    print(f"\n📊 STATISTIQUES DES FRÉQUENCES")
    print(f"   - Fréquence minimale: {min(freqs)}")
    print(f"   - Fréquence maximale: {max(freqs)}")
    print(f"   - Fréquence moyenne: {sum(freqs)/len(freqs):.1f}")
    print(f"   - Fréquence médiane: {sorted(freqs)[len(freqs)//2]}")
    
    # Distribution par niveaux
    freq_1 = sum(1 for f in freqs if f == 1)
    freq_2_5 = sum(1 for f in freqs if 2 <= f <= 5)
    freq_6_10 = sum(1 for f in freqs if 6 <= f <= 10)
    freq_11_50 = sum(1 for f in freqs if 11 <= f <= 50)
    freq_plus_50 = sum(1 for f in freqs if f > 50)
    
    print(f"\n📈 DISTRIBUTION PAR NIVEAUX")
    print(f"   - Très rares (freq = 1): {freq_1} mots ({freq_1/len(freqs)*100:.1f}%)")
    print(f"   - Rares (freq 2-5): {freq_2_5} mots ({freq_2_5/len(freqs)*100:.1f}%)")
    print(f"   - Peu fréquents (freq 6-10): {freq_6_10} mots ({freq_6_10/len(freqs)*100:.1f}%)")
    print(f"   - Fréquents (freq 11-50): {freq_11_50} mots ({freq_11_50/len(freqs)*100:.1f}%)")
    print(f"   - Très fréquents (freq > 50): {freq_plus_50} mots ({freq_plus_50/len(freqs)*100:.1f}%)")
    
    # Top mots les plus fréquents
    print(f"\n🏆 TOP 20 MOTS LES PLUS FRÉQUENTS")
    mots_freqs_tries = sorted(mots_freqs, key=lambda x: x[1], reverse=True)
    for i, (mot, freq) in enumerate(mots_freqs_tries[:20], 1):
        print(f"   {i:2d}. {mot:15s} (freq: {freq})")
    
    # Analyse par longueur de mots
    longueurs = [len(mot) for mot, _ in mots_freqs]
    
    print(f"\n📏 ANALYSE PAR LONGUEUR DE MOTS")
    print(f"   - Mot le plus court: {min(longueurs)} caractères")
    print(f"   - Mot le plus long: {max(longueurs)} caractères")
    print(f"   - Longueur moyenne: {sum(longueurs)/len(longueurs):.1f} caractères")
    
    # Distribution par longueur
    compteur_longueurs = Counter(longueurs)
    print(f"\n   Distribution par longueur:")
    for longueur in sorted(compteur_longueurs.keys()):
        count = compteur_longueurs[longueur]
        print(f"   - {longueur} caractères: {count} mots ({count/len(longueurs)*100:.1f}%)")
    
    # Mots les plus longs
    mots_longs = sorted(mots_freqs, key=lambda x: len(x[0]), reverse=True)[:10]
    print(f"\n📐 TOP 10 MOTS LES PLUS LONGS")
    for i, (mot, freq) in enumerate(mots_longs, 1):
        print(f"   {i:2d}. {mot:20s} ({len(mot)} car., freq: {freq})")
    
    # Échantillon de mots rares intéressants
    mots_rares = [(mot, freq) for mot, freq in mots_freqs if freq == 1]
    if mots_rares:
        print(f"\n🔍 ÉCHANTILLON DE MOTS RARES INTÉRESSANTS")
        # Prendre quelques mots rares mais pas trop courts
        mots_rares_interessants = [
            (mot, freq) for mot, freq in mots_rares 
            if len(mot) >= 4 and not mot.endswith('-la') and not mot.endswith('-an')
        ][:15]
        
        for mot, freq in mots_rares_interessants:
            print(f"   - {mot} (freq: {freq})")
    
    print(f"\n✅ Analyse terminée - Dictionnaire prêt pour Android !")

def main():
    analyser_dictionnaire()

if __name__ == "__main__":
    main()
