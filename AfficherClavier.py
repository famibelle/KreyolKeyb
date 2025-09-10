#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Affichage CLI de la Disposition Complète du Clavier Créole Optimisé
Interface en ligne de commande pour visualiser le clavier
"""

import json
import pandas as pd
from colorama import init, Fore, Back, Style
import sys
import os

# Initialiser colorama pour Windows
init(autoreset=True)

def charger_donnees():
    """Charge les données du clavier optimisé"""
    try:
        with open("disposition_creole_optimisee.json", 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        df_freq = pd.read_csv("frequences_caracteres_creoles.csv")
        freq_dict = dict(zip(df_freq['caractere'], df_freq['frequence']))
        
        return data, freq_dict
    except FileNotFoundError as e:
        print(f"❌ Fichier manquant: {e}")
        print("🔄 Exécutez d'abord DispositionKlavyé.py pour générer les données")
        sys.exit(1)

def generer_disposition_complete(freq_dict):
    """Génère une disposition complète avec tous les caractères"""
    
    # Tous les caractères à placer (alphabet + caractères créoles + ponctuation)
    tous_caracteres = list('abcdefghijklmnopqrstuvwxyz') + ['é', 'è', 'ò', 'à', 'ô'] + [' ', '.', ',', ';', '!', '?']
    
    # Trier par fréquence décroissante
    chars_freq = [(char, freq_dict.get(char, 0)) for char in tous_caracteres if char in freq_dict]
    chars_freq.sort(key=lambda x: x[1], reverse=True)
    
    # Force des doigts (0 = auriculaire gauche, 9 = auriculaire droit)
    force_doigts = {0: 0.5, 1: 0.7, 2: 0.9, 3: 1.0, 4: 1.0, 5: 1.0, 6: 1.0, 7: 0.9, 8: 0.7, 9: 0.5}
    
    # Positions disponibles par doigt (3 rangées par doigt)
    positions_doigts = {}
    for doigt in range(10):
        positions_doigts[doigt] = [(doigt, f'rangee{i}') for i in [1, 2, 3]]
    
    # Attribution optimisée
    disposition = {}
    positions_utilisees = set()
    
    for char, freq in chars_freq:
        if len(disposition) >= 30:  # Limite à 30 caractères (3 rangées × 10 doigts)
            break
            
        # Calculer le score pour chaque position disponible
        meilleur_score = float('inf')
        meilleure_position = None
        
        for doigt in range(10):
            for pos_doigt, rangee in positions_doigts[doigt]:
                if (pos_doigt, rangee) not in positions_utilisees:
                    # Score = effort (inverse de la force) × fréquence
                    effort = (1 / force_doigts[doigt]) * freq
                    
                    # Bonus pour rangée du milieu (plus accessible)
                    if rangee == 'rangee2':
                        effort *= 0.8
                    elif rangee == 'rangee3':
                        effort *= 1.1
                    
                    if effort < meilleur_score:
                        meilleur_score = effort
                        meilleure_position = (doigt, pos_doigt, rangee)
        
        if meilleure_position:
            doigt, pos_doigt, rangee = meilleure_position
            disposition[char] = doigt
            positions_utilisees.add((pos_doigt, rangee))
    
    return disposition

def organiser_clavier(disposition, freq_dict):
    """Organise les caractères par position de clavier"""
    
    # Générer disposition complète si nécessaire
    if len(disposition) < 20:  # Si pas assez de caractères, générer complet
        disposition = generer_disposition_complete(freq_dict)
    
    # Structure du clavier QWERTY physique
    clavier = {
        'rangee1': ['', '', '', '', '', '', '', '', '', ''],  # Rangée du haut
        'rangee2': ['', '', '', '', '', '', '', '', '', ''],  # Rangée du milieu  
        'rangee3': ['', '', '', '', '', '', '', '', '', '']   # Rangée du bas
    }
    
    # Mapping doigt -> positions sur le clavier avec priorité aux rangées
    positions_doigts = {
        0: [(0, 'rangee1'), (0, 'rangee2'), (0, 'rangee3')],  # Auriculaire gauche
        1: [(1, 'rangee1'), (1, 'rangee2'), (1, 'rangee3')],  # Annulaire gauche
        2: [(2, 'rangee1'), (2, 'rangee2'), (2, 'rangee3')],  # Majeur gauche
        3: [(3, 'rangee1'), (3, 'rangee2'), (3, 'rangee3')],  # Index gauche
        4: [(4, 'rangee1'), (4, 'rangee2'), (4, 'rangee3')],  # Index gauche étendu
        5: [(5, 'rangee1'), (5, 'rangee2'), (5, 'rangee3')],  # Index droit étendu
        6: [(6, 'rangee1'), (6, 'rangee2'), (6, 'rangee3')],  # Index droit
        7: [(7, 'rangee1'), (7, 'rangee2'), (7, 'rangee3')],  # Majeur droit
        8: [(8, 'rangee1'), (8, 'rangee2'), (8, 'rangee3')],  # Annulaire droit
        9: [(9, 'rangee1'), (9, 'rangee2'), (9, 'rangee3')]   # Auriculaire droit
    }
    
    # Grouper les caractères par doigt
    chars_par_doigt = {}
    for char, doigt in disposition.items():
        if doigt not in chars_par_doigt:
            chars_par_doigt[doigt] = []
        chars_par_doigt[doigt].append(char)
    
    # Trier les caractères de chaque doigt par fréquence (plus fréquent = rangée du milieu)
    for doigt in chars_par_doigt:
        chars_par_doigt[doigt].sort(key=lambda x: freq_dict.get(x, 0), reverse=True)
    
    # Placer les caractères
    for doigt, chars in chars_par_doigt.items():
        if doigt in positions_doigts:
            for i, char in enumerate(chars):
                if i < len(positions_doigts[doigt]):
                    col, rangee = positions_doigts[doigt][i]
                    clavier[rangee][col] = char
    
    return clavier, disposition

def get_couleur_doigt(doigt):
    """Retourne la couleur pour chaque doigt"""
    couleurs = {
        0: Fore.RED,      # Auriculaire gauche
        1: Fore.YELLOW,   # Annulaire gauche  
        2: Fore.GREEN,    # Majeur gauche
        3: Fore.CYAN,     # Index gauche
        4: Fore.CYAN,     # Index gauche étendu
        5: Fore.CYAN,     # Index droit étendu
        6: Fore.CYAN,     # Index droit
        7: Fore.GREEN,    # Majeur droit
        8: Fore.YELLOW,   # Annulaire droit
        9: Fore.RED       # Auriculaire droit
    }
    return couleurs.get(doigt, Fore.WHITE)

def afficher_clavier_ascii(clavier, disposition, freq_dict):
    """Affiche le clavier en ASCII art coloré"""
    
    print(f"\n{Fore.MAGENTA}{Style.BRIGHT}{'='*80}")
    print(f"{Fore.MAGENTA}{Style.BRIGHT}🎹 CLAVIER CRÉOLE OPTIMISÉ - DISPOSITION POTOMITAN")
    print(f"{Fore.MAGENTA}{Style.BRIGHT}{'='*80}{Style.RESET_ALL}")
    
    # En-tête avec les doigts
    noms_doigts = ['Aur.G', 'Ann.G', 'Maj.G', 'Ind.G', 'Ind.G+', 'Ind.D+', 'Ind.D', 'Maj.D', 'Ann.D', 'Aur.D']
    
    print(f"\n{Fore.BLUE}{Style.BRIGHT}DOIGTS:", end="")
    for i, nom in enumerate(noms_doigts):
        couleur = get_couleur_doigt(i)
        print(f"  {couleur}{nom:^6}", end="")
    print(f"{Style.RESET_ALL}")
    
    # Séparateur
    print(f"{Fore.BLUE}{'─' * 8}" + "┬" + "─" * 6 * 10 + "┐")
    
    # Afficher chaque rangée
    rangees_noms = ['RANGÉE 1', 'RANGÉE 2', 'RANGÉE 3']
    
    for i, (rangee_nom, rangee_key) in enumerate(zip(rangees_noms, ['rangee1', 'rangee2', 'rangee3'])):
        print(f"{Fore.BLUE}{rangee_nom:>7} {Style.BRIGHT}│", end="")
        
        for j, char in enumerate(clavier[rangee_key]):
            if char:
                # Trouver le doigt pour la couleur
                doigt = disposition.get(char, -1)
                couleur = get_couleur_doigt(doigt)
                
                # Caractères créoles en surbrillance
                if char in ['é', 'è', 'ò', 'à', 'ô']:
                    print(f"{Back.YELLOW}{Fore.BLACK}{Style.BRIGHT} {char:^4} {Style.RESET_ALL}", end="")
                else:
                    print(f"{couleur}{Style.BRIGHT} {char:^4} {Style.RESET_ALL}", end="")
            else:
                print(f"{Fore.LIGHTBLACK_EX} .... ", end="")
        
        print(f"{Fore.BLUE} │{Style.RESET_ALL}")
    
    # Bas du clavier
    print(f"{Fore.BLUE}{'─' * 8}" + "┴" + "─" * 6 * 10 + "┘{Style.RESET_ALL}")

def afficher_statistiques(stats):
    """Affiche les statistiques de performance"""
    
    print(f"\n{Fore.GREEN}{Style.BRIGHT}📊 STATISTIQUES DE PERFORMANCE{Style.RESET_ALL}")
    print(f"{Fore.GREEN}{'─' * 40}{Style.RESET_ALL}")
    
    print(f"{Fore.WHITE}💪 Amélioration globale    : {Fore.GREEN}{Style.BRIGHT}{stats['amelioration_pct']:>6.1f}%{Style.RESET_ALL}")
    print(f"{Fore.WHITE}⚡ Effort réduit          : {Fore.GREEN}{Style.BRIGHT}{(stats['azerty_total'] - stats['optimise_total']):>8,.0f} unités{Style.RESET_ALL}")
    print(f"{Fore.WHITE}🔤 Effort caractères      : {Fore.CYAN}{stats['optimise_chars']:>8,.0f} unités{Style.RESET_ALL}")
    print(f"{Fore.WHITE}🔗 Effort bigrammes       : {Fore.CYAN}{stats['optimise_bigrammes']:>8,.0f} unités{Style.RESET_ALL}")
    print(f"{Fore.WHITE}📈 Score total optimisé   : {Fore.GREEN}{Style.BRIGHT}{stats['optimise_total']:>8,.0f} unités{Style.RESET_ALL}")

def afficher_caracteres_creoles(freq_dict):
    """Affiche la liste des caractères créoles"""
    
    print(f"\n{Fore.YELLOW}{Style.BRIGHT}🌴 CARACTÈRES CRÉOLES INTÉGRÉS{Style.RESET_ALL}")
    print(f"{Fore.YELLOW}{'─' * 35}{Style.RESET_ALL}")
    
    # Filtrer les caractères créoles
    creoles = ['é', 'è', 'ò', 'à', 'ô']
    
    for char in creoles:
        if char in freq_dict:
            freq = freq_dict[char]
            print(f"{Back.YELLOW}{Fore.BLACK} {char} {Style.RESET_ALL} → {Fore.YELLOW}{freq:>6,} occurrences{Style.RESET_ALL}")

def afficher_legende():
    """Affiche la légende des couleurs"""
    
    print(f"\n{Fore.MAGENTA}{Style.BRIGHT}🎨 LÉGENDE DES COULEURS{Style.RESET_ALL}")
    print(f"{Fore.MAGENTA}{'─' * 25}{Style.RESET_ALL}")
    
    legendes = [
        (Fore.CYAN, "Index", "Force maximale, accès rapide"),
        (Fore.GREEN, "Majeur", "Force élevée, très accessible"),
        (Fore.YELLOW, "Annulaire", "Force moyenne, utilisation modérée"),
        (Fore.RED, "Auriculaire", "Force faible, caractères rares"),
        (Back.YELLOW + Fore.BLACK, "Surbrillance", "Caractères spécifiques au créole")
    ]
    
    for couleur, doigt, description in legendes:
        print(f"{couleur}{Style.BRIGHT} ● {doigt:<12}{Style.RESET_ALL} : {description}")

def menu_interactif(data, freq_dict):
    """Menu interactif pour explorer le clavier"""
    
    while True:
        print(f"\n{Fore.BLUE}{Style.BRIGHT}🔍 MENU INTERACTIF{Style.RESET_ALL}")
        print(f"{Fore.BLUE}{'─' * 20}{Style.RESET_ALL}")
        print("1. 🎹 Afficher le clavier complet")
        print("2. 📊 Voir les statistiques")
        print("3. 🌴 Caractères créoles")
        print("4. 🎨 Légende des couleurs")
        print("5. 🔍 Rechercher un caractère")
        print("6. 🚪 Quitter")
        
        choix = input(f"\n{Fore.CYAN}Votre choix (1-6): {Style.RESET_ALL}").strip()
        
        if choix == "1":
            clavier, disposition_complete = organiser_clavier(data['character_positions'], freq_dict)
            afficher_clavier_ascii(clavier, disposition_complete, freq_dict)
        elif choix == "2":
            afficher_statistiques(data['stats'])
        elif choix == "3":
            afficher_caracteres_creoles(freq_dict)
        elif choix == "4":
            afficher_legende()
        elif choix == "5":
            clavier, disposition_complete = organiser_clavier(data['character_positions'], freq_dict)
            rechercher_caractere(disposition_complete, freq_dict)
        elif choix == "6":
            print(f"\n{Fore.GREEN}👋 Au revoir ! Bon usage de votre clavier créole !{Style.RESET_ALL}")
            break
        else:
            print(f"{Fore.RED}❌ Choix invalide. Essayez encore.{Style.RESET_ALL}")

def rechercher_caractere(disposition, freq_dict):
    """Recherche et affiche les infos d'un caractère"""
    
    char = input(f"\n{Fore.CYAN}Entrez un caractère à rechercher: {Style.RESET_ALL}").strip()
    
    if not char:
        print(f"{Fore.RED}❌ Aucun caractère saisi.{Style.RESET_ALL}")
        return
    
    # Générer disposition complète si nécessaire
    if len(disposition) < 20:
        disposition = generer_disposition_complete(freq_dict)
    
    if char in disposition:
        doigt = disposition[char]
        couleur = get_couleur_doigt(doigt)
        freq = freq_dict.get(char, 0)
        
        noms_doigts_complets = {
            0: 'Auriculaire gauche', 1: 'Annulaire gauche', 2: 'Majeur gauche', 3: 'Index gauche', 4: 'Index gauche étendu',
            5: 'Index droit étendu', 6: 'Index droit', 7: 'Majeur droit', 8: 'Annulaire droit', 9: 'Auriculaire droit'
        }
        
        print(f"\n{Fore.GREEN}🔍 RÉSULTAT DE RECHERCHE{Style.RESET_ALL}")
        print(f"{Fore.GREEN}{'─' * 25}{Style.RESET_ALL}")
        print(f"Caractère      : {couleur}{Style.BRIGHT}{char}{Style.RESET_ALL}")
        print(f"Doigt assigné  : {couleur}{noms_doigts_complets[doigt]}{Style.RESET_ALL}")
        print(f"Fréquence      : {freq:,} occurrences")
        print(f"Type           : {'Créole' if char in ['é', 'è', 'ò', 'à', 'ô'] else 'Standard'}")
    else:
        print(f"{Fore.RED}❌ Caractère '{char}' non trouvé dans la disposition.{Style.RESET_ALL}")

def main():
    """Fonction principale"""
    
    # Vérifier si on est en mode interactif ou direct
    if len(sys.argv) > 1 and sys.argv[1] == "--direct":
        mode_direct = True
    else:
        mode_direct = False
    
    try:
        # Charger les données
        data, freq_dict = charger_donnees()
        
        if mode_direct:
            # Affichage direct complet
            clavier, disposition_complete = organiser_clavier(data['character_positions'], freq_dict)
            afficher_clavier_ascii(clavier, disposition_complete, freq_dict)
            afficher_statistiques(data['stats'])
            afficher_caracteres_creoles(freq_dict)
            afficher_legende()
        else:
            # Mode interactif
            print(f"{Fore.MAGENTA}{Style.BRIGHT}")
            print("🎹 BIENVENUE DANS L'AFFICHEUR DE CLAVIER CRÉOLE OPTIMISÉ")
            print("=" * 60)
            print(f"{Style.RESET_ALL}")
            menu_interactif(data, freq_dict)
            
    except KeyboardInterrupt:
        print(f"\n\n{Fore.YELLOW}👋 Arrêt demandé par l'utilisateur.{Style.RESET_ALL}")
    except Exception as e:
        print(f"\n{Fore.RED}❌ Erreur: {e}{Style.RESET_ALL}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    main()
