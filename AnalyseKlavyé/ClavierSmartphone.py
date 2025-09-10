#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Disposition Clavier Créole pour Smartphone
Optimisé pour écrans tactiles et saisie à deux pouces
"""

import json
import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.patches as patches
from datetime import datetime
from colorama import init, Fore, Back, Style

# Initialiser colorama
init(autoreset=True)

def analyser_contraintes_smartphone():
    """Analyse les contraintes spécifiques aux smartphones"""
    
    contraintes = {
        "taille_ecran": "Limité - 3 rangées maximum recommandées",
        "precision_tactile": "Zone de touche minimale 44x44 pixels",
        "ergonomie_pouces": "Zones facilement accessibles par les pouces",
        "saisie_type": "Une main ou deux pouces",
        "prediction_texte": "Intégration avec correcteur automatique",
        "swipe_gestures": "Support des gestes de balayage",
        "accents_frequents": "Accès rapide nécessaire pour É, È, Ò"
    }
    
    print(f"{Fore.CYAN}{Style.BRIGHT}📱 CONTRAINTES SMARTPHONE:{Style.RESET_ALL}")
    for contrainte, description in contraintes.items():
        print(f"   • {contrainte.replace('_', ' ').title()}: {description}")
    
    return contraintes

def generer_layout_smartphone_3x10():
    """Génère un layout smartphone 3 rangées x 10 touches"""
    
    # Layout smartphone standard (inspiration QWERTY mobile)
    layout_smartphone = {
        'rangee1': {  # Rangée du haut (10 touches)
            0: 'q', 1: 'w', 2: 'e', 3: 'r', 4: 't', 
            5: 'y', 6: 'u', 7: 'i', 8: 'o', 9: 'p'
        },
        'rangee2': {  # Rangée du milieu (10 touches) 
            0: 'a', 1: 's', 2: 'd', 3: 'f', 4: 'g',
            5: 'h', 6: 'j', 7: 'k', 8: 'l', 9: 'é'  # É en position accessible
        },
        'rangee3': {  # Rangée du bas (10 touches)
            0: 'z', 1: 'x', 2: 'c', 3: 'v', 4: 'b',
            5: 'n', 6: 'm', 7: 'è', 8: 'ò', 9: 'à'  # Accents créoles groupés à droite
        }
    }
    
    return layout_smartphone

def generer_layout_smartphone_optimise():
    """Génère un layout optimisé pour le créole sur smartphone"""
    
    # Charger les fréquences créoles
    try:
        df_freq = pd.read_csv("frequences_caracteres_creoles.csv")
        freq_dict = dict(zip(df_freq['caractere'], df_freq['frequence']))
    except:
        # Fréquences approximatives créoles
        freq_dict = {
            'é': 14974, 'a': 8200, 'e': 7500, 'i': 7800, 'n': 7500, 'r': 6800,
            'l': 6200, 'o': 5900, 't': 5600, 's': 5400, 'u': 4900, 'd': 4200,
            'è': 7327, 'c': 3800, 'm': 3500, 'f': 3200, 'g': 2900, 'h': 2600,
            'ò': 2388, 'p': 2400, 'b': 2000, 'v': 1800, 'y': 1600, 'j': 1400,
            'k': 1200, 'z': 800, 'w': 600, 'x': 400, 'à': 33, 'ô': 11, 'q': 200
        }
    
    # Zones d'accessibilité smartphone (0=difficile, 1=parfait)
    accessibilite_zones = {
        # Rangée 1 (haut) - Moins accessible
        (0, 0): 0.6, (0, 1): 0.7, (0, 2): 0.8, (0, 3): 0.9, (0, 4): 0.95,
        (0, 5): 0.95, (0, 6): 0.9, (0, 7): 0.8, (0, 8): 0.7, (0, 9): 0.6,
        
        # Rangée 2 (milieu) - Zone optimale
        (1, 0): 0.8, (1, 1): 0.9, (1, 2): 0.95, (1, 3): 1.0, (1, 4): 1.0,
        (1, 5): 1.0, (1, 6): 1.0, (1, 7): 0.95, (1, 8): 0.9, (1, 9): 0.8,
        
        # Rangée 3 (bas) - Accessible mais moins précise
        (2, 0): 0.7, (2, 1): 0.8, (2, 2): 0.85, (2, 3): 0.9, (2, 4): 0.95,
        (2, 5): 0.95, (2, 6): 0.9, (2, 7): 0.85, (2, 8): 0.8, (2, 9): 0.7
    }
    
    # Optimisation : Placer les caractères selon fréquence × accessibilité
    chars_scores = []
    for char, freq in freq_dict.items():
        if char.isalpha() or char in ['é', 'è', 'ò', 'à', 'ô']:
            chars_scores.append((char, freq))
    
    # Trier par fréquence décroissante
    chars_scores.sort(key=lambda x: x[1], reverse=True)
    
    # Trier les positions par accessibilité décroissante
    positions_triees = sorted(accessibilite_zones.items(), key=lambda x: x[1], reverse=True)
    
    # Attribution optimisée
    layout_optimise = {'rangee1': {}, 'rangee2': {}, 'rangee3': {}}
    
    for i, (char, freq) in enumerate(chars_scores[:30]):  # 30 positions max
        if i < len(positions_triees):
            (rangee, col), accessibilite = positions_triees[i]
            rangee_nom = f'rangee{rangee + 1}'
            layout_optimise[rangee_nom][col] = char
    
    return layout_optimise, freq_dict, accessibilite_zones

def layout_smartphone_creole_hybride():
    """Layout hybride smartphone pour usage bilingue"""
    
    layout_hybride = {
        'rangee1': {  # Haut - Caractères fréquents
            0: 'a', 1: 'z', 2: 'e', 3: 'r', 4: 't',
            5: 'y', 6: 'u', 7: 'i', 8: 'o', 9: 'p'
        },
        'rangee2': {  # Milieu - Zone optimale avec É
            0: 'q', 1: 's', 2: 'd', 3: 'f', 4: 'g',
            5: 'h', 6: 'j', 7: 'k', 8: 'l', 9: 'é'  # É en zone premium
        },
        'rangee3': {  # Bas - Accents créoles + caractères moins fréquents
            0: 'w', 1: 'x', 2: 'c', 3: 'v', 4: 'b',
            5: 'n', 6: 'm', 7: 'è', 8: 'ò', 9: 'à'  # Zone créole
        },
        'rangee4_speciale': {  # Quatrième rangée (optionnelle) - Chiffres et ponctuation
            0: '1', 1: '2', 2: '3', 3: '4', 4: '5',
            5: '6', 6: '7', 7: '8', 8: '9', 9: '0'
        },
        'acces_long_press': {  # Accès par appui long
            'o': ['ô', 'ó', 'ò', 'õ'],  # O → variations
            'e': ['é', 'è', 'ê', 'ë'],  # E → variations  
            'a': ['à', 'á', 'â', 'ã'],  # A → variations
            'i': ['ì', 'í', 'î', 'ï'],  # I → variations
            'u': ['ù', 'ú', 'û', 'ü']   # U → variations
        }
    }
    
    return layout_hybride

def afficher_layout_smartphone(layout, titre="LAYOUT SMARTPHONE"):
    """Affiche un layout smartphone de manière visuelle"""
    
    print(f"\n{Fore.MAGENTA}{Style.BRIGHT}{'='*60}")
    print(f"📱 {titre}")
    print(f"{'='*60}{Style.RESET_ALL}")
    
    # Afficher chaque rangée
    for i, rangee_nom in enumerate(['rangee1', 'rangee2', 'rangee3'], 1):
        if rangee_nom in layout:
            print(f"\n{Fore.BLUE}{Style.BRIGHT}RANGÉE {i}:{Style.RESET_ALL}")
            print("┌" + "─" * 59 + "┐")
            print("│", end="")
            
            rangee = layout[rangee_nom]
            for col in range(10):
                char = rangee.get(col, ' ')
                
                if char in ['é', 'è', 'ò', 'à', 'ô']:
                    # Caractères créoles en surbrillance
                    print(f"{Back.YELLOW}{Fore.BLACK} {char} {Style.RESET_ALL}│", end="")
                elif char in ['a', 'e', 'i', 'o', 'u', 'n', 'r', 'l', 't', 's']:
                    # Voyelles et consonnes fréquentes en vert
                    print(f"{Back.GREEN}{Fore.WHITE} {char} {Style.RESET_ALL}│", end="")
                else:
                    # Autres caractères
                    print(f" {char} │", end="")
            
            print("\n└" + "─" * 59 + "┘")
    
    # Accès par appui long si disponible
    if 'acces_long_press' in layout:
        print(f"\n{Fore.CYAN}{Style.BRIGHT}👆 ACCÈS PAR APPUI LONG:{Style.RESET_ALL}")
        for base, variations in layout['acces_long_press'].items():
            variations_str = " → ".join(variations)
            print(f"   {base.upper()} : {variations_str}")

def calculer_score_smartphone(layout, freq_dict, accessibilite_zones):
    """Calcule le score d'optimisation pour smartphone"""
    
    score_total = 0
    score_max = 0
    
    for rangee_nom, rangee in layout.items():
        if rangee_nom.startswith('rangee') and rangee_nom != 'rangee4_speciale':
            rangee_num = int(rangee_nom[-1]) - 1
            
            for col, char in rangee.items():
                if char in freq_dict:
                    freq = freq_dict[char]
                    accessibilite = accessibilite_zones.get((rangee_num, col), 0.5)
                    
                    # Score = fréquence × accessibilité
                    score_char = freq * accessibilite
                    score_total += score_char
                    score_max += freq  # Score maximum si accessibilité parfaite
    
    score_pct = (score_total / score_max * 100) if score_max > 0 else 0
    
    return {
        'score_optimisation': score_pct,
        'score_total': score_total,
        'score_max': score_max,
        'efficacite': "Excellente" if score_pct > 85 else "Bonne" if score_pct > 75 else "Moyenne"
    }

def generer_suggestions_amelioration():
    """Génère des suggestions d'amélioration pour smartphone"""
    
    suggestions = {
        "layout_physique": [
            "Utiliser 3 rangées principales + 1 rangée chiffres",
            "Taille des touches: minimum 44x44 pixels",
            "Espacement entre touches: 2-4 pixels",
            "Zone tactile élargie pour compensation imprécision"
        ],
        
        "optimisations_creoles": [
            "É en position premium (rangée 2, droite)",
            "Accents créoles groupés (È, Ò, À) pour mémorisation",
            "Accès rapide via swipe ou appui long",
            "Prédiction intelligente créole → français"
        ],
        
        "ux_smartphone": [
            "Vibration tactile pour confirmation",
            "Prévisualisation caractère avant validation",
            "Suggestions contextuelles créole/français",
            "Mode une main pour accessibilité"
        ],
        
        "fonctionnalites_avancees": [
            "Clavier adaptatif (apprentissage utilisateur)",
            "Thèmes visuels créole",
            "Raccourcis mots fréquents",
            "Correction auto créole ↔ français"
        ]
    }
    
    return suggestions

def sauvegarder_config_smartphone(layout, scores, suggestions):
    """Sauvegarde la configuration smartphone"""
    
    config_smartphone = {
        "version": "3.0-SMARTPHONE",
        "type": "mobile_keyboard_layout",
        "branding": "POTOMITAN MOBILE™",
        "layout_name": "Clavier Créole Mobile",
        "description": "Disposition tactile optimisée pour smartphone - Créole Guadeloupéen",
        "target_platform": "Android/iOS",
        
        "layout_configuration": layout,
        "performance_scores": scores,
        "ameliorations_suggestions": suggestions,
        
        "specifications_techniques": {
            "rangees": 3,
            "touches_par_rangee": 10,
            "total_touches": 30,
            "taille_minimale_touche": "44x44px",
            "espacement_touches": "2-4px",
            "zones_optimales": "Rangée 2 (milieu)"
        },
        
        "caracteres_creoles": {
            "é": {"position": "rangee2_col9", "acces": "direct", "frequence": "très_haute"},
            "è": {"position": "rangee3_col7", "acces": "direct", "frequence": "haute"},
            "ò": {"position": "rangee3_col8", "acces": "direct", "frequence": "moyenne"},
            "à": {"position": "rangee3_col9", "acces": "direct", "frequence": "faible"},
            "ô": {"position": "appui_long_o", "acces": "appui_long", "frequence": "très_faible"}
        },
        
        "integration_mobile": {
            "prediction_texte": True,
            "correction_automatique": True,
            "haptic_feedback": True,
            "swipe_gestures": True,
            "voice_input": True,
            "offline_mode": True
        },
        
        "timestamp": datetime.now().isoformat(),
        "created_by": "Potomitan Mobile Keyboard Optimizer"
    }
    
    fichier = "clavier_creole_smartphone.json"
    with open(fichier, 'w', encoding='utf-8') as f:
        json.dump(config_smartphone, f, indent=2, ensure_ascii=False)
    
    return fichier

def main():
    """Fonction principale"""
    
    print(f"{Fore.MAGENTA}{Style.BRIGHT}")
    print("📱 CLAVIER CRÉOLE POUR SMARTPHONE")
    print("Optimisation tactile et ergonomie mobile")
    print("=" * 45)
    print(f"{Style.RESET_ALL}")
    
    # 1. Analyser les contraintes smartphone
    print(f"\n{Fore.CYAN}1️⃣ Analyse des contraintes smartphone...{Style.RESET_ALL}")
    contraintes = analyser_contraintes_smartphone()
    
    # 2. Générer layouts candidats
    print(f"\n{Fore.CYAN}2️⃣ Génération des layouts candidats...{Style.RESET_ALL}")
    
    # Layout standard
    layout_standard = generer_layout_smartphone_3x10()
    
    # Layout optimisé
    layout_optimise, freq_dict, accessibilite = generer_layout_smartphone_optimise()
    
    # Layout hybride (recommandé)
    layout_hybride = layout_smartphone_creole_hybride()
    
    # 3. Afficher les layouts
    afficher_layout_smartphone(layout_standard, "LAYOUT STANDARD SMARTPHONE")
    afficher_layout_smartphone(layout_optimise, "LAYOUT OPTIMISÉ CRÉOLE")
    afficher_layout_smartphone(layout_hybride, "LAYOUT HYBRIDE RECOMMANDÉ")
    
    # 4. Calculer les scores
    print(f"\n{Fore.CYAN}3️⃣ Évaluation des performances...{Style.RESET_ALL}")
    score_hybride = calculer_score_smartphone(layout_hybride, freq_dict, accessibilite)
    
    print(f"\n{Fore.GREEN}{Style.BRIGHT}📊 SCORES LAYOUT HYBRIDE:{Style.RESET_ALL}")
    print(f"   🎯 Score d'optimisation: {score_hybride['score_optimisation']:.1f}%")
    print(f"   ⚡ Efficacité: {score_hybride['efficacite']}")
    print(f"   📱 Adapté smartphone: ✅")
    
    # 5. Suggestions d'amélioration
    print(f"\n{Fore.CYAN}4️⃣ Génération des suggestions...{Style.RESET_ALL}")
    suggestions = generer_suggestions_amelioration()
    
    print(f"\n{Fore.YELLOW}{Style.BRIGHT}💡 SUGGESTIONS SMARTPHONE:{Style.RESET_ALL}")
    for categorie, items in suggestions.items():
        print(f"\n{Fore.YELLOW}{categorie.replace('_', ' ').title()}:{Style.RESET_ALL}")
        for item in items:
            print(f"   • {item}")
    
    # 6. Sauvegarder
    print(f"\n{Fore.CYAN}5️⃣ Sauvegarde configuration mobile...{Style.RESET_ALL}")
    fichier_sauve = sauvegarder_config_smartphone(layout_hybride, score_hybride, suggestions)
    
    # 7. Recommandations finales
    print(f"\n{Fore.GREEN}{Style.BRIGHT}🎉 RECOMMANDATIONS FINALES SMARTPHONE:{Style.RESET_ALL}")
    print(f"📁 Configuration: {Fore.CYAN}{fichier_sauve}{Style.RESET_ALL}")
    print(f"🎹 Layout recommandé: {Fore.YELLOW}HYBRIDE avec appui long{Style.RESET_ALL}")
    print(f"🌴 Accents créoles: {Fore.GREEN}É en zone premium + È/Ò/À groupés{Style.RESET_ALL}")
    print(f"📱 Optimisation: {Fore.GREEN}{score_hybride['score_optimisation']:.1f}% pour smartphone{Style.RESET_ALL}")
    print(f"🔧 Implémentation: {Fore.CYAN}Android/iOS avec prédiction intelligente{Style.RESET_ALL}")

if __name__ == "__main__":
    main()
