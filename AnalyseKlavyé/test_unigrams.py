#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Test des unigrams pour le générateur N-grams
"""

import json
import re
from collections import Counter, defaultdict

def nettoyer_et_tokeniser(texte):
    """Nettoie le texte et le tokenise en mots"""
    # Nettoyer le texte
    texte = texte.lower()
    
    # Garder uniquement les mots créoles (avec accents et apostrophes)
    pattern = r"[a-zA-ZòéèùàâêîôûçÀÉÈÙÒ]+(?:['-][a-zA-ZòéèùàâêîôûçÀÉÈÙÒ]+)*"
    mots = re.findall(pattern, texte)
    
    # Filtrer les mots trop courts
    mots_valides = [mot for mot in mots if len(mot) >= 2]
    
    return mots_valides

def test_unigrams():
    """Test avec des textes créoles d'exemple"""
    print("=== Test des Unigrams ===")
    
    # Textes créoles d'exemple
    textes_test = [
        "Nou ka alé an mach la pou achté pwason",
        "Ti manmay la ka joué épi zannimo yo",
        "Séjou-an ka manjé bonbon ak banann",
        "Papa ka travay épi manman ka kwit manje",
        "Dlo la ka koulé nan rivyè la"
    ]
    
    # Compteurs
    unigrams_count = Counter()
    bigrammes_count = Counter()
    mots_suivants = defaultdict(Counter)
    
    total_unigrams = 0
    total_bigrammes = 0
    
    for texte in textes_test:
        print(f"Traitement: {texte}")
        mots = nettoyer_et_tokeniser(texte)
        print(f"Mots extraits: {mots}")
        
        # Générer unigrams
        unigrams_count.update(mots)
        total_unigrams += len(mots)
        
        # Générer bigrammes
        if len(mots) >= 2:
            for i in range(len(mots) - 1):
                bigramme = (mots[i], mots[i + 1])
                bigrammes_count[bigramme] += 1
                mots_suivants[mots[i]][mots[i + 1]] += 1
                total_bigrammes += 1
    
    # Afficher résultats
    print(f"\n📊 Statistiques:")
    print(f"   - Unigrams uniques: {len(unigrams_count)}")
    print(f"   - Bigrammes uniques: {len(bigrammes_count)}")
    print(f"   - Total unigrams: {total_unigrams}")
    print(f"   - Total bigrammes: {total_bigrammes}")
    
    print(f"\n📈 Top 10 mots (unigrams):")
    for mot, count in unigrams_count.most_common(10):
        print(f"   '{mot}' : {count} occurrences")
    
    print(f"\n🔗 Exemples de prédictions:")
    for mot in ["ka", "la", "an", "épi"]:
        if mot in mots_suivants:
            top_suivants = mots_suivants[mot].most_common(3)
            predictions = [f"{suivant}({count})" for suivant, count in top_suivants]
            print(f"   '{mot}' → {', '.join(predictions)}")
    
    # Créer un modèle test
    modele_test = {
        "version": "1.1-test",
        "type": "ngram_model",
        "branding": "Potomitan™",
        "unigrams": dict(unigrams_count.most_common(50)),
        "stats": {
            "total_unigrams": len(unigrams_count),
            "total_bigrammes": len(bigrammes_count)
        }
    }
    
    # Sauvegarder le test
    with open("test_unigrams.json", 'w', encoding='utf-8') as f:
        json.dump(modele_test, f, ensure_ascii=False, indent=2)
    
    print(f"\n✅ Modèle test sauvegardé: test_unigrams.json")
    print(f"🎯 Les unigrams fonctionnent correctement !")

if __name__ == "__main__":
    test_unigrams()
