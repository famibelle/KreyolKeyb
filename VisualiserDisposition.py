#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Visualisateur pour l'analyse de disposition de clavier créole
Génère des graphiques pour illustrer les résultats de l'analyse
"""

import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import json
from pathlib import Path

def visualiser_frequences():
    """Crée des visualisations des fréquences de caractères"""
    
    # Configuration du style
    plt.style.use('seaborn-v0_8')
    sns.set_palette("husl")
    
    # Charger les données
    df = pd.read_csv("frequences_caracteres_creoles.csv")
    
    # Figure avec plusieurs sous-graphiques
    fig, ((ax1, ax2), (ax3, ax4)) = plt.subplots(2, 2, figsize=(16, 12))
    fig.suptitle('🇬🇵 Analyse de Disposition Clavier Créole - Potomitan™', 
                 fontsize=16, fontweight='bold')
    
    # 1. Top 15 caractères les plus fréquents
    top15 = df.head(15)
    colors = ['red' if t == 'creole' else 'steelblue' for t in top15['type']]
    
    bars1 = ax1.bar(range(len(top15)), top15['frequence'], color=colors, alpha=0.8)
    ax1.set_title('Top 15 Caractères les Plus Fréquents', fontweight='bold')
    ax1.set_xlabel('Caractères')
    ax1.set_ylabel('Fréquence')
    ax1.set_xticks(range(len(top15)))
    ax1.set_xticklabels(top15['caractere'], fontsize=12, fontweight='bold')
    
    # Ajouter les valeurs sur les barres
    for i, (bar, freq) in enumerate(zip(bars1, top15['frequence'])):
        ax1.text(bar.get_x() + bar.get_width()/2, bar.get_height() + 500,
                f'{freq:,}', ha='center', va='bottom', fontsize=9)
    
    # Légende pour les couleurs
    ax1.text(0.02, 0.98, '🔴 Caractères créoles\n🔵 Caractères normaux', 
             transform=ax1.transAxes, va='top', ha='left',
             bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.8))
    
    # 2. Répartition créole vs normal
    type_counts = df['type'].value_counts()
    colors_pie = ['lightcoral', 'lightblue']
    
    wedges, texts, autotexts = ax2.pie(type_counts.values, labels=type_counts.index, 
                                      autopct='%1.1f%%', colors=colors_pie,
                                      explode=(0.1, 0))
    ax2.set_title('Répartition Caractères Créoles vs Normaux', fontweight='bold')
    
    # 3. Fréquence cumulative
    df_sorted = df.sort_values('frequence', ascending=False).reset_index(drop=True)
    df_sorted['freq_cumulative'] = df_sorted['frequence'].cumsum()
    df_sorted['pct_cumulative'] = (df_sorted['freq_cumulative'] / df_sorted['frequence'].sum()) * 100
    
    ax3.plot(range(len(df_sorted)), df_sorted['pct_cumulative'], 'o-', linewidth=2, markersize=4)
    ax3.set_title('Distribution Cumulative des Fréquences', fontweight='bold')
    ax3.set_xlabel('Rang du caractère')
    ax3.set_ylabel('Pourcentage cumulé (%)')
    ax3.grid(True, alpha=0.3)
    ax3.axhline(y=80, color='red', linestyle='--', alpha=0.7, label='80% du corpus')
    ax3.legend()
    
    # 4. Comparaison effort AZERTY vs Optimisé
    with open("disposition_creole_optimisee.json", 'r', encoding='utf-8') as f:
        stats = json.load(f)['stats']
    
    categories = ['Caractères', 'Bigrammes', 'Total']
    azerty_values = [stats['azerty_chars'], stats['azerty_bigrammes'], stats['azerty_total']]
    optimise_values = [stats['optimise_chars'], stats['optimise_bigrammes'], stats['optimise_total']]
    
    x = range(len(categories))
    width = 0.35
    
    bars_azerty = ax4.bar([i - width/2 for i in x], azerty_values, width, 
                         label='AZERTY', color='lightcoral', alpha=0.8)
    bars_optimise = ax4.bar([i + width/2 for i in x], optimise_values, width,
                           label='Optimisé', color='lightgreen', alpha=0.8)
    
    ax4.set_title('Comparaison Effort de Frappe', fontweight='bold')
    ax4.set_ylabel('Effort (unités)')
    ax4.set_xticks(x)
    ax4.set_xticklabels(categories)
    ax4.legend()
    
    # Ajouter les pourcentages d'amélioration
    for i, (azerty, optimise) in enumerate(zip(azerty_values, optimise_values)):
        improvement = ((azerty - optimise) / azerty) * 100
        ax4.text(i, max(azerty, optimise) * 1.05, f'-{improvement:.1f}%', 
                ha='center', va='bottom', fontweight='bold', color='green')
    
    plt.tight_layout()
    plt.savefig('analyse_disposition_clavier_creole.png', dpi=300, bbox_inches='tight')
    plt.show()
    
    print("✅ Graphique sauvegardé: analyse_disposition_clavier_creole.png")

def visualiser_clavier_optimise():
    """Crée une visualisation du clavier optimisé"""
    
    # Charger la disposition
    with open("disposition_creole_optimisee.json", 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    disposition = data['character_positions']
    force_doigts = data['finger_strength']
    
    # Créer la visualisation du clavier
    fig, ax = plt.subplots(figsize=(14, 8))
    
    # Positions des touches (simuler un clavier QWERTY)
    positions_clavier = {
        0: (0, 2), 1: (1, 2), 2: (2, 2), 3: (3, 2), 4: (4, 2),  # Rangée du haut (gauche)
        5: (6, 2), 6: (7, 2), 7: (8, 2), 8: (9, 2), 9: (10, 2)  # Rangée du haut (droite)
    }
    
    # Couleurs selon la force des doigts
    couleurs_force = {0.5: 'lightcoral', 0.7: 'orange', 0.9: 'lightgreen', 1.0: 'lightblue'}
    
    # Dessiner les touches
    for doigt, (x, y) in positions_clavier.items():
        force = force_doigts[str(doigt)]
        couleur = couleurs_force.get(force, 'gray')
        
        # Trouver le caractère assigné à ce doigt
        char_assigne = None
        for char, doigt_pos in disposition.items():
            if doigt_pos == doigt:
                char_assigne = char
                break
        
        # Dessiner la touche
        rect = plt.Rectangle((x-0.4, y-0.4), 0.8, 0.8, 
                           facecolor=couleur, edgecolor='black', linewidth=2)
        ax.add_patch(rect)
        
        # Ajouter le caractère
        if char_assigne:
            ax.text(x, y, char_assigne, ha='center', va='center', 
                   fontsize=16, fontweight='bold')
        
        # Ajouter le numéro du doigt
        ax.text(x, y-0.6, f'D{doigt}', ha='center', va='center', 
               fontsize=8, alpha=0.7)
    
    # Configuration du graphique
    ax.set_xlim(-1, 11)
    ax.set_ylim(1, 3)
    ax.set_aspect('equal')
    ax.set_title('🇬🇵 Disposition Clavier Optimisée pour le Créole\nPotomitan™ Kreyol Keyboard', 
                fontsize=16, fontweight='bold', pad=20)
    
    # Supprimer les axes
    ax.set_xticks([])
    ax.set_yticks([])
    
    # Légende des couleurs
    legend_elements = [
        plt.Rectangle((0, 0), 1, 1, facecolor='lightblue', label='Index (Force 1.0)'),
        plt.Rectangle((0, 0), 1, 1, facecolor='lightgreen', label='Majeur (Force 0.9)'),
        plt.Rectangle((0, 0), 1, 1, facecolor='orange', label='Annulaire (Force 0.7)'),
        plt.Rectangle((0, 0), 1, 1, facecolor='lightcoral', label='Auriculaire (Force 0.5)')
    ]
    ax.legend(handles=legend_elements, loc='upper center', bbox_to_anchor=(0.5, -0.05), ncol=4)
    
    # Ajouter des informations
    info_text = f"""
📊 Amélioration: {data['stats']['amelioration_pct']:.1f}% par rapport à AZERTY
🎯 Caractères optimisés: {len(disposition)}
⚡ Réduction effort: {(data['stats']['azerty_total'] - data['stats']['optimise_total']):,.0f} unités
"""
    ax.text(5.5, 1.2, info_text, ha='center', va='center', 
           bbox=dict(boxstyle='round', facecolor='lightyellow', alpha=0.8),
           fontsize=10)
    
    plt.tight_layout()
    plt.savefig('clavier_creole_optimise.png', dpi=300, bbox_inches='tight')
    plt.show()
    
    print("✅ Visualisation clavier sauvegardée: clavier_creole_optimise.png")

def main():
    """Fonction principale"""
    print("🎨 Génération des visualisations...")
    
    try:
        visualiser_frequences()
        visualiser_clavier_optimise()
        print("\n✅ Toutes les visualisations ont été générées avec succès !")
        print("📁 Fichiers créés:")
        print("   • analyse_disposition_clavier_creole.png")
        print("   • clavier_creole_optimise.png")
    except Exception as e:
        print(f"❌ Erreur lors de la génération: {e}")

if __name__ == "__main__":
    main()
