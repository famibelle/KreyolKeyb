#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Disposition Clavier Créole Hybride - Basée sur AZERTY
Conserve la disposition AZERTY de base avec optimisations créoles
"""

import json
import pandas as pd
from colorama import init, Fore, Back, Style

# Initialiser colorama
init(autoreset=True)

def generer_disposition_hybride():
    """Génère une disposition hybride AZERTY + optimisations créoles"""
    
    # Base AZERTY conservée (rangées principales)
    azerty_base = {
        'rangee1': ['a', 'z', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'],
        'rangee2': ['q', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'm'],
        'rangee3': ['w', 'x', 'c', 'v', 'b', 'n', ',', ';', ':', '!']
    }
    
    return azerty_base

def propositions_optimisations_creoles():
    """Propose différentes optimisations compatibles AZERTY"""
    
    propositions = {
        "Proposition 1: Substitutions Minimales": {
            "description": "Garde 90% d'AZERTY, remplace seulement les caractères les moins utilisés",
            "changements": {
                'w': 'é',  # W très rare en français → é très fréquent en créole
                'x': 'è',  # X rare → è créole
                'q': 'ò',  # Q peu utilisé → ò créole
                ';': 'à',  # Point-virgule → à créole
                ':': 'ô'   # Deux-points → ô créole
            },
            "avantages": ["Apprentissage minimal", "Muscle memory préservée", "Transition douce"],
            "inconvénients": ["Optimisation limitée", "Caractères créoles en positions sous-optimales"]
        },
        
        "Proposition 2: Permutations Intelligentes": {
            "description": "Échange des caractères pour optimiser sans bouleverser",
            "changements": {
                'a': 'a',   # A reste en place (fréquent en français ET créole)
                'e': 'é',   # E → É (plus fréquent en créole)
                'z': 'e',   # Z → E (Z rare, E fréquent)
                'w': 'è',   # W → È
                'x': 'ò',   # X → Ò
                'q': 'à',   # Q → À
                ';': 'ô'    # ; → Ô
            },
            "avantages": ["Optimisation significative", "É en position accessible", "Logique intuitive"],
            "inconvénients": ["Quelques réajustements nécessaires"]
        },
        
        "Proposition 3: Zone Créole Dédiée": {
            "description": "Crée une 'zone créole' sur la partie droite du clavier",
            "changements": {
                'p': 'é',   # P → É (position excellente, main droite)
                'm': 'è',   # M → È (accessible, main droite)
                '!': 'ò',   # ! → Ò
                ':': 'à',   # : → À
                ';': 'ô'    # ; → Ô
            },
            "avantages": ["Concentration des accents", "Main droite spécialisée", "Logique géographique"],
            "inconvénients": ["P et M déplacés (fréquents en français)"]
        }
    }
    
    return propositions

def afficher_proposition(nom, prop):
    """Affiche une proposition de manière claire"""
    
    print(f"\n{Fore.CYAN}{Style.BRIGHT}{'='*60}")
    print(f"{nom}")
    print(f"{'='*60}{Style.RESET_ALL}")
    
    print(f"\n{Fore.GREEN}{Style.BRIGHT}📋 DESCRIPTION:{Style.RESET_ALL}")
    print(f"   {prop['description']}")
    
    print(f"\n{Fore.YELLOW}{Style.BRIGHT}🔄 CHANGEMENTS:{Style.RESET_ALL}")
    for ancien, nouveau in prop['changements'].items():
        if ancien != nouveau:
            print(f"   {ancien} → {Fore.YELLOW}{Style.BRIGHT}{nouveau}{Style.RESET_ALL}")
    
    print(f"\n{Fore.GREEN}{Style.BRIGHT}✅ AVANTAGES:{Style.RESET_ALL}")
    for avantage in prop['avantages']:
        print(f"   • {avantage}")
    
    print(f"\n{Fore.RED}{Style.BRIGHT}⚠️  INCONVÉNIENTS:{Style.RESET_ALL}")
    for inconvenient in prop['inconvénients']:
        print(f"   • {inconvenient}")

def generer_clavier_hybride(changements):
    """Génère un clavier hybride avec les changements spécifiés"""
    
    # Base AZERTY
    clavier = {
        'rangee1': ['a', 'z', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'],
        'rangee2': ['q', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'm'],
        'rangee3': ['w', 'x', 'c', 'v', 'b', 'n', ',', ';', ':', '!']
    }
    
    # Appliquer les changements
    for rangee_nom, rangee in clavier.items():
        for i, char in enumerate(rangee):
            if char in changements:
                clavier[rangee_nom][i] = changements[char]
    
    return clavier

def afficher_clavier_comparaison(clavier_azerty, clavier_hybride, nom_proposition):
    """Affiche une comparaison visuelle des claviers"""
    
    print(f"\n{Fore.MAGENTA}{Style.BRIGHT}🎹 COMPARAISON: AZERTY vs {nom_proposition}{Style.RESET_ALL}")
    print(f"{Fore.MAGENTA}{'─'*70}{Style.RESET_ALL}")
    
    # AZERTY original
    print(f"\n{Fore.BLUE}{Style.BRIGHT}📌 AZERTY ORIGINAL:{Style.RESET_ALL}")
    for i, (rangee_nom, rangee) in enumerate(clavier_azerty.items(), 1):
        print(f"Rangée {i}: {' '.join([f'{char:^3}' for char in rangee])}")
    
    # Clavier hybride
    print(f"\n{Fore.GREEN}{Style.BRIGHT}🌴 CLAVIER CRÉOLE HYBRIDE:{Style.RESET_ALL}")
    for i, (rangee_nom, rangee) in enumerate(clavier_hybride.items(), 1):
        chars_formatted = []
        for char in rangee:
            if char in ['é', 'è', 'ò', 'à', 'ô']:
                chars_formatted.append(f"{Back.YELLOW}{Fore.BLACK}{char:^3}{Style.RESET_ALL}")
            else:
                chars_formatted.append(f'{char:^3}')
        print(f"Rangée {i}: {' '.join(chars_formatted)}")

def calculer_score_compatibilite(changements):
    """Calcule un score de compatibilité avec AZERTY"""
    
    # Caractères AZERTY par fréquence d'usage (approximatif)
    freq_francais = {
        'e': 10, 'a': 9, 's': 8, 'i': 8, 't': 7, 'n': 7, 'r': 6, 'u': 6, 'l': 5, 'o': 5,
        'd': 4, 'c': 4, 'm': 3, 'p': 3, 'f': 3, 'h': 3, 'g': 2, 'b': 2, 'v': 2, 'j': 1,
        'k': 1, 'y': 1, 'q': 1, 'w': 1, 'x': 1, 'z': 1
    }
    
    score_impact = 0
    for ancien, nouveau in changements.items():
        if ancien != nouveau and ancien in freq_francais:
            score_impact += freq_francais[ancien]
    
    # Score sur 100 (100 = aucun impact, 0 = impact maximum)
    score_compatibilite = max(0, 100 - score_impact * 2)
    
    return score_compatibilite

def main():
    """Fonction principale"""
    
    print(f"{Fore.MAGENTA}{Style.BRIGHT}")
    print("🎹 PROPOSITIONS CLAVIER CRÉOLE HYBRIDE")
    print("Optimisations compatibles avec AZERTY")
    print("=" * 50)
    print(f"{Style.RESET_ALL}")
    
    # Base AZERTY
    azerty_base = generer_disposition_hybride()
    
    # Obtenir les propositions
    propositions = propositions_optimisations_creoles()
    
    # Afficher chaque proposition
    for nom, prop in propositions.items():
        afficher_proposition(nom, prop)
        
        # Générer le clavier hybride
        clavier_hybride = generer_clavier_hybride(prop['changements'])
        
        # Afficher la comparaison
        afficher_clavier_comparaison(azerty_base, clavier_hybride, nom.split(':')[0])
        
        # Score de compatibilité
        score = calculer_score_compatibilite(prop['changements'])
        print(f"\n{Fore.CYAN}{Style.BRIGHT}📊 SCORE COMPATIBILITÉ AZERTY: {score}/100{Style.RESET_ALL}")
        
        # Nombre de changements
        nb_changements = len([c for a, c in prop['changements'].items() if a != c])
        print(f"{Fore.CYAN}🔢 NOMBRE DE CHANGEMENTS: {nb_changements}{Style.RESET_ALL}")
        
        print(f"\n{Fore.YELLOW}{'─'*70}{Style.RESET_ALL}")
    
    # Recommandation
    print(f"\n{Fore.GREEN}{Style.BRIGHT}🎯 RECOMMANDATION:{Style.RESET_ALL}")
    print(f"{Fore.GREEN}Pour un usage bilingue optimal, je recommande la {Style.BRIGHT}Proposition 2{Style.RESET_ALL}")
    print(f"{Fore.GREEN}• Bon équilibre optimisation/familiarité")
    print(f"• É facilement accessible en position E")
    print(f"• Impact minimal sur la frappe française")
    print(f"• Transition naturelle pour les bilingues{Style.RESET_ALL}")

if __name__ == "__main__":
    main()
