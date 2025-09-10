#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Test du Clavier Créole Potomitan - Interface CLI Simplifiée
Test complet des suggestions de mots en temps réel

Usage: python ClavierTest.py
"""

import json
import os
import sys
import time

class ClavierTest:
    def __init__(self):
        self.dictionnaire = {}
        self.ngrams = {}
        self.charger_donnees()
        
    def charger_donnees(self):
        """Charge le dictionnaire et les n-grams"""
        print("🔄 Chargement des données...")
        
        # Dictionnaire unique
        chemin_dict = "android_keyboard/app/src/main/assets/creole_dict.json"
        
        try:
            with open(chemin_dict, 'r', encoding='utf-8') as f:
                data = json.load(f)
                
            if isinstance(data, list):
                for item in data:
                    if len(item) == 2:
                        mot, freq = item
                        self.dictionnaire[mot.lower()] = freq
            
            print(f"✅ Dictionnaire: {len(self.dictionnaire)} mots")
            
        except Exception as e:
            print(f"❌ Erreur dictionnaire: {e}")
            return False
        
        # N-grams (optionnel)
        try:
            chemin_ngrams = "android_keyboard/app/src/main/assets/creole_ngrams.json"
            with open(chemin_ngrams, 'r', encoding='utf-8') as f:
                self.ngrams = json.load(f)
            print(f"✅ N-grams: {len(self.ngrams)} combinaisons")
        except Exception as e:
            print(f"⚠️ N-grams non disponibles")
        
        return True
    
    def suggerer(self, prefixe, contexte=None, nb=5):
        """Suggère des mots basés sur le préfixe et contexte"""
        if not prefixe:
            return []
        
        prefixe = prefixe.lower()
        suggestions = []
        
        # 1. Recherche directe dans le dictionnaire
        for mot, freq in self.dictionnaire.items():
            if mot.startswith(prefixe) and mot != prefixe:
                suggestions.append((mot, freq, "📚"))
        
        # 2. Recherche contextuelle avec n-grams
        if contexte and self.ngrams:
            contexte = contexte.lower()
            for ngram, freq in self.ngrams.items():
                if f"{contexte} {prefixe}" in ngram.lower():
                    mots = ngram.split()
                    if len(mots) >= 2:
                        mot_suggere = mots[1]
                        if mot_suggere.startswith(prefixe):
                            suggestions.append((mot_suggere, freq * 2, "🔗"))
        
        # 3. Trier par fréquence et éliminer doublons
        suggestions.sort(key=lambda x: x[1], reverse=True)
        
        vus = set()
        resultats = []
        for mot, freq, icone in suggestions:
            if mot not in vus:
                vus.add(mot)
                resultats.append((mot, freq, icone))
                if len(resultats) >= nb:
                    break
        
        return resultats
    
    def test_interactif(self):
        """Test interactif principal"""
        print("\n🎯 === TEST INTERACTIF ===")
        print("Tapez des mots pour voir les suggestions")
        print("Format: 'mot' ou 'contexte mot' pour suggestions contextuelles")
        print("Commandes spéciales:")
        print("  • 'demo' - Voir des exemples")
        print("  • 'stats' - Statistiques du système")
        print("  • 'top' - Top 20 des mots les plus fréquents")
        print("  • 'quit' - Quitter")
        print("-" * 50)
        
        while True:
            try:
                entree = input("\n🔤 Tapez > ").strip()
                
                if entree.lower() in ['quit', 'q', 'exit']:
                    break
                elif entree.lower() == 'demo':
                    self.demo_exemples()
                    continue
                elif entree.lower() == 'stats':
                    self.afficher_stats()
                    continue
                elif entree.lower() == 'top':
                    self.top_mots()
                    continue
                elif not entree:
                    continue
                
                # Parser l'entrée
                mots = entree.split()
                if len(mots) == 1:
                    prefixe = mots[0]
                    contexte = None
                else:
                    contexte = mots[-2] if len(mots) >= 2 else None
                    prefixe = mots[-1]
                
                # Générer suggestions
                debut = time.time()
                suggestions = self.suggerer(prefixe, contexte)
                duree = (time.time() - debut) * 1000
                
                if suggestions:
                    print(f"\n📋 Suggestions pour '{prefixe}' ({duree:.1f}ms):")
                    if contexte:
                        print(f"   (contexte: '{contexte}')")
                    
                    for i, (mot, freq, icone) in enumerate(suggestions, 1):
                        print(f"   {i}. {icone} {mot} (freq: {freq})")
                else:
                    print(f"💭 Aucune suggestion pour '{prefixe}'")
                
            except (KeyboardInterrupt, EOFError):
                break
    
    def demo_exemples(self):
        """Exemples prédéfinis pour démonstration"""
        exemples = [
            ("ka", None, "Verbes avec 'ka'"),
            ("mw", None, "Mots commençant par 'mw'"),
            ("nou", None, "Mots avec 'nou'"),
            ("lap", None, "Mots avec 'lap'"),
            ("bel", None, "Adjectifs 'bel'"),
            ("man", "mwen", "Après 'mwen'"),
            ("ka", "ou", "Après 'ou'"),
            ("al", "nou", "Après 'nou'")
        ]
        
        print("\n🎭 === EXEMPLES PRÉDÉFINIS ===")
        
        for prefixe, contexte, description in exemples:
            print(f"\n📝 {description}:")
            contexte_str = f"[{contexte}] " if contexte else ""
            print(f"   Recherche: {contexte_str}{prefixe}")
            
            suggestions = self.suggerer(prefixe, contexte, 3)
            
            if suggestions:
                for mot, freq, icone in suggestions:
                    print(f"   → {icone} {mot} (freq: {freq})")
            else:
                print("   → Aucune suggestion")
    
    def afficher_stats(self):
        """Affiche les statistiques du système"""
        print("\n📊 === STATISTIQUES ===")
        print(f"📚 Total mots: {len(self.dictionnaire)}")
        print(f"🔗 N-grams: {len(self.ngrams)}")
        
        if self.dictionnaire:
            freqs = list(self.dictionnaire.values())
            print(f"📈 Fréquence min: {min(freqs)}")
            print(f"📈 Fréquence max: {max(freqs)}")
            print(f"📈 Fréquence moyenne: {sum(freqs)/len(freqs):.1f}")
        
        # Test de performance
        print("\n⚡ Test de performance...")
        prefixes_test = ["ka", "mw", "nou", "an", "bel"]
        
        debut = time.time()
        total_suggestions = 0
        
        for prefixe in prefixes_test:
            suggestions = self.suggerer(prefixe)
            total_suggestions += len(suggestions)
        
        duree = (time.time() - debut) * 1000
        
        print(f"✅ {len(prefixes_test)} recherches: {duree:.1f}ms")
        print(f"🎯 Moyenne: {duree/len(prefixes_test):.1f}ms par recherche")
        print(f"📊 {total_suggestions} suggestions générées")
    
    def top_mots(self):
        """Affiche le top des mots les plus fréquents"""
        print("\n🏆 === TOP 20 MOTS LES PLUS FRÉQUENTS ===")
        
        mots_tries = sorted(self.dictionnaire.items(), key=lambda x: x[1], reverse=True)[:20]
        
        for i, (mot, freq) in enumerate(mots_tries, 1):
            print(f"   {i:2d}. {mot:15s} (freq: {freq})")
    
    def test_automatique(self):
        """Tests automatiques pour validation"""
        print("\n🧪 === TESTS AUTOMATIQUES ===")
        
        tests = [
            ("ka", None, "Test basique"),
            ("mw", None, "Préfixe court"),
            ("xyz", None, "Mot inexistant"),
            ("man", "mwen", "Test contextuel"),
        ]
        
        tests_ok = 0
        
        for prefixe, contexte, description in tests:
            suggestions = self.suggerer(prefixe, contexte)
            
            if prefixe == "xyz":
                # On s'attend à aucune suggestion
                if not suggestions:
                    print(f"   ✅ {description}: OK (aucune suggestion)")
                    tests_ok += 1
                else:
                    print(f"   ❌ {description}: ÉCHEC (suggestions trouvées)")
            else:
                # On s'attend à des suggestions
                if suggestions:
                    print(f"   ✅ {description}: OK ({len(suggestions)} suggestions)")
                    tests_ok += 1
                else:
                    print(f"   ❌ {description}: ÉCHEC (aucune suggestion)")
        
        print(f"\n🎯 Résultat: {tests_ok}/{len(tests)} tests réussis")
        return tests_ok == len(tests)
    
    def demarrer(self):
        """Point d'entrée principal"""
        if not self.dictionnaire:
            print("❌ Impossible de démarrer sans dictionnaire")
            return
        
        print("🇬🇵 CLAVIER CRÉOLE POTOMITAN - TEST")
        print("=" * 40)
        
        while True:
            print("\n🎯 MENU:")
            print("1. Test interactif")
            print("2. Exemples prédéfinis") 
            print("3. Statistiques système")
            print("4. Tests automatiques")
            print("5. Quitter")
            
            try:
                choix = input("\nVotre choix (1-5): ").strip()
                
                if choix == "1":
                    self.test_interactif()
                elif choix == "2":
                    self.demo_exemples()
                elif choix == "3":
                    self.afficher_stats()
                elif choix == "4":
                    if self.test_automatique():
                        print("🎉 Tous les tests sont passés !")
                    else:
                        print("⚠️ Certains tests ont échoué")
                elif choix == "5":
                    break
                else:
                    print("❌ Choix invalide (1-5)")
            
            except (KeyboardInterrupt, EOFError):
                break
        
        print("\n👋 Merci d'avoir testé le clavier créole Potomitan !")

def main():
    """Fonction principale"""
    test = ClavierTest()
    test.demarrer()

if __name__ == "__main__":
    main()
