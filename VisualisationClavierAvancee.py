#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Visualisateur Avancé du Clavier Créole Optimisé - Potomitan™
Crée une représentation visuelle détaillée et attrayante du nouveau clavier
"""

import matplotlib.pyplot as plt
import matplotlib.patches as patches
import numpy as np
import json
import pandas as pd
from matplotlib.colors import LinearSegmentedColormap
import seaborn as sns

def creer_clavier_visuel_avance():
    """Crée une visualisation avancée du clavier créole optimisé"""
    
    # Charger les données de disposition
    with open("disposition_creole_optimisee.json", 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    disposition = data['character_positions']
    force_doigts = data['finger_strength']
    stats = data['stats']
    
    # Charger les fréquences pour le sizing
    df_freq = pd.read_csv("frequences_caracteres_creoles.csv")
    freq_dict = dict(zip(df_freq['caractere'], df_freq['frequence']))
    
    # Configuration de la figure
    fig = plt.figure(figsize=(18, 12))
    gs = fig.add_gridspec(3, 3, height_ratios=[0.8, 2, 0.8], width_ratios=[1, 2, 1])
    
    # Titre principal
    fig.suptitle('🇬🇵 Clavier Créole Optimisé - Potomitan™\nDisposition Scientifiquement Adaptée au Créole Guadeloupéen', 
                 fontsize=20, fontweight='bold', y=0.95)
    
    # ========== CLAVIER PRINCIPAL ==========
    ax_clavier = fig.add_subplot(gs[1, 1])
    
    # Définir les positions des touches (3 rangées + espace)
    positions_touches = {
        # Rangée 1 (nombres et caractères spéciaux)
        'row0': [(i, 3) for i in range(13)],
        # Rangée 2 (AZERTYUIOP)
        'row1': [(i + 0.5, 2) for i in range(12)],
        # Rangée 3 (QSDFGHJKLM)  
        'row2': [(i + 0.75, 1) for i in range(11)],
        # Rangée 4 (WXCVBN)
        'row3': [(i + 1.25, 0) for i in range(10)]
    }
    
    # Couleurs selon la force des doigts et fréquence
    def get_color_intensity(char, doigt):
        """Détermine l'intensité de couleur selon la fréquence et la force"""
        freq = freq_dict.get(char, 0)
        force = force_doigts[str(doigt)]
        
        # Normaliser la fréquence (0-1)
        max_freq = max(freq_dict.values()) if freq_dict else 1
        freq_norm = freq / max_freq if max_freq > 0 else 0
        
        # Couleur de base selon la force du doigt
        if force == 1.0:  # Index
            base_color = '#4CAF50'  # Vert
        elif force == 0.9:  # Majeur
            base_color = '#2196F3'  # Bleu
        elif force == 0.7:  # Annulaire
            base_color = '#FF9800'  # Orange
        else:  # Auriculaire
            base_color = '#F44336'  # Rouge
        
        # Intensité selon la fréquence
        alpha = 0.3 + (freq_norm * 0.7)  # Alpha entre 0.3 et 1.0
        
        return base_color, alpha
    
    # Mapping des doigts aux positions sur le clavier AZERTY
    doigt_to_positions = {
        0: [(0, 2), (0, 1), (0, 0)],  # Auriculaire gauche
        1: [(1, 2), (1, 1), (1, 0)],  # Annulaire gauche
        2: [(2, 2), (2, 1), (2, 0)],  # Majeur gauche
        3: [(3, 2), (3, 1), (3, 0)],  # Index gauche
        4: [(4, 2), (4, 1), (4, 0)],  # Index gauche étendu
        5: [(5, 2), (5, 1), (5, 0)],  # Index droit étendu
        6: [(6, 2), (6, 1), (6, 0)],  # Index droit
        7: [(7, 2), (7, 1), (7, 0)],  # Majeur droit
        8: [(8, 2), (8, 1), (8, 0)],  # Annulaire droit
        9: [(9, 2), (9, 1), (9, 0)]   # Auriculaire droit
    }
    
    # Dessiner les touches
    touches_dessinées = set()
    for char, doigt in disposition.items():
        if doigt in doigt_to_positions:
            # Prendre la première position disponible pour ce doigt
            for pos_x, pos_y in doigt_to_positions[doigt]:
                if (pos_x, pos_y) not in touches_dessinées:
                    color, alpha = get_color_intensity(char, doigt)
                    
                    # Taille de la touche selon la fréquence
                    freq = freq_dict.get(char, 0)
                    max_freq = max(freq_dict.values()) if freq_dict else 1
                    size_factor = 0.7 + (freq / max_freq * 0.3) if max_freq > 0 else 0.8
                    
                    # Dessiner la touche
                    rect = patches.FancyBboxPatch(
                        (pos_x - 0.4 * size_factor, pos_y - 0.4 * size_factor),
                        0.8 * size_factor, 0.8 * size_factor,
                        boxstyle="round,pad=0.05",
                        facecolor=color, edgecolor='black',
                        alpha=alpha, linewidth=2
                    )
                    ax_clavier.add_patch(rect)
                    
                    # Texte du caractère
                    fontsize = 16 if char in ['é', 'è', 'ò', 'à', 'ô'] else 14
                    fontweight = 'bold' if char in ['é', 'è', 'ò', 'à', 'ô'] else 'normal'
                    
                    ax_clavier.text(pos_x, pos_y + 0.1, char, 
                                  ha='center', va='center',
                                  fontsize=fontsize, fontweight=fontweight,
                                  color='white' if alpha > 0.6 else 'black')
                    
                    # Fréquence en petit
                    if freq > 0:
                        ax_clavier.text(pos_x, pos_y - 0.25, f'{freq:,}',
                                      ha='center', va='center',
                                      fontsize=8, alpha=0.8,
                                      color='white' if alpha > 0.6 else 'gray')
                    
                    # Numéro du doigt
                    ax_clavier.text(pos_x + 0.3, pos_y + 0.3, f'D{doigt}',
                                  ha='center', va='center',
                                  fontsize=8, alpha=0.6,
                                  color='white' if alpha > 0.6 else 'darkgray')
                    
                    touches_dessinées.add((pos_x, pos_y))
                    break
    
    # Configuration de l'axe du clavier
    ax_clavier.set_xlim(-0.5, 10.5)
    ax_clavier.set_ylim(-0.5, 3.5)
    ax_clavier.set_aspect('equal')
    ax_clavier.set_title('Disposition Optimisée (Taille = Fréquence, Couleur = Force des Doigts)', 
                        fontsize=14, fontweight='bold', pad=20)
    ax_clavier.axis('off')
    
    # ========== LÉGENDE DES COULEURS ==========
    ax_legende = fig.add_subplot(gs[1, 0])
    
    # Créer la légende des forces
    forces = [(1.0, '#4CAF50', 'Index'), (0.9, '#2196F3', 'Majeur'), 
              (0.7, '#FF9800', 'Annulaire'), (0.5, '#F44336', 'Auriculaire')]
    
    for i, (force, color, nom) in enumerate(forces):
        rect = patches.Rectangle((0, i), 1, 0.8, facecolor=color, alpha=0.8, edgecolor='black')
        ax_legende.add_patch(rect)
        ax_legende.text(1.2, i + 0.4, f'{nom}\n(Force: {force})', 
                       va='center', fontsize=10, fontweight='bold')
    
    ax_legende.set_xlim(0, 3)
    ax_legende.set_ylim(-0.5, 4.5)
    ax_legende.set_title('Force des Doigts', fontsize=12, fontweight='bold')
    ax_legende.axis('off')
    
    # ========== STATISTIQUES ==========
    ax_stats = fig.add_subplot(gs[1, 2])
    
    # Préparer les données de comparaison
    categories = ['Effort\nCaractères', 'Effort\nBigrammes', 'Effort\nTotal']
    azerty_vals = [stats['azerty_chars'], stats['azerty_bigrammes'], stats['azerty_total']]
    optimise_vals = [stats['optimise_chars'], stats['optimise_bigrammes'], stats['optimise_total']]
    
    # Normaliser pour le graphique
    azerty_norm = [v / 1000 for v in azerty_vals]
    optimise_norm = [v / 1000 for v in optimise_vals]
    
    x = np.arange(len(categories))
    width = 0.35
    
    bars1 = ax_stats.bar(x - width/2, azerty_norm, width, label='AZERTY', 
                        color='#ffcdd2', edgecolor='#d32f2f', linewidth=2)
    bars2 = ax_stats.bar(x + width/2, optimise_norm, width, label='Optimisé', 
                        color='#c8e6c9', edgecolor='#388e3c', linewidth=2)
    
    # Ajouter les pourcentages d'amélioration
    for i, (azerty, optimise) in enumerate(zip(azerty_vals, optimise_vals)):
        improvement = ((azerty - optimise) / azerty) * 100
        ax_stats.text(i, max(azerty_norm[i], optimise_norm[i]) * 1.1, 
                     f'-{improvement:.1f}%', ha='center', va='bottom',
                     fontsize=11, fontweight='bold', color='green')
    
    ax_stats.set_ylabel('Effort (milliers d\'unités)')
    ax_stats.set_title('Comparaison Performance')
    ax_stats.set_xticks(x)
    ax_stats.set_xticklabels(categories)
    ax_stats.legend()
    ax_stats.grid(True, alpha=0.3)
    
    # ========== TEXTE INFORMATIF HAUT ==========
    ax_info_haut = fig.add_subplot(gs[0, :])
    
    info_text = f"""
🎯 AMÉLIORATION GLOBALE: {stats['amelioration_pct']:.1f}% | 📊 CARACTÈRES ANALYSÉS: {len(disposition)} | 🔤 CRÉOLES OPTIMISÉS: é, è, ò, à, ô
⚡ RÉDUCTION D'EFFORT: {(stats['azerty_total'] - stats['optimise_total']):,.0f} unités | 🏆 SPÉCIALEMENT CONÇU POUR LE CRÉOLE GUADELOUPÉEN
    """
    
    ax_info_haut.text(0.5, 0.5, info_text, ha='center', va='center',
                     fontsize=12, fontweight='bold',
                     bbox=dict(boxstyle='round,pad=0.5', facecolor='lightblue', alpha=0.8))
    ax_info_haut.axis('off')
    
    # ========== CARACTÈRES CRÉOLES SPÉCIAUX ==========
    ax_creoles = fig.add_subplot(gs[2, :])
    
    # Identifier les caractères créoles dans la disposition
    caracteres_creoles = ['é', 'è', 'ò', 'à', 'ô', 'ç', 'û', 'ê', 'î', 'â', 'ù']
    creoles_optimises = [(char, disposition[char], freq_dict.get(char, 0)) 
                        for char in caracteres_creoles if char in disposition]
    
    if creoles_optimises:
        creoles_text = "🎨 CARACTÈRES CRÉOLES OPTIMISÉS: "
        for i, (char, doigt, freq) in enumerate(creoles_optimises):
            force = force_doigts[str(doigt)]
            creoles_text += f"'{char}' (D{doigt}, Force:{force}, {freq:,} occ.)"
            if i < len(creoles_optimises) - 1:
                creoles_text += " • "
    else:
        creoles_text = "🎨 CARACTÈRES CRÉOLES: Intégrés dans la disposition principale"
    
    ax_creoles.text(0.5, 0.5, creoles_text, ha='center', va='center',
                   fontsize=11, fontweight='bold',
                   bbox=dict(boxstyle='round,pad=0.3', facecolor='lightyellow', alpha=0.8))
    ax_creoles.axis('off')
    
    # Sauvegarder
    plt.tight_layout()
    plt.savefig('clavier_creole_optimise_avance.png', dpi=300, bbox_inches='tight', 
                facecolor='white', edgecolor='none')
    plt.show()
    
    print("✅ Visualisation avancée sauvegardée: clavier_creole_optimise_avance.png")

def creer_heatmap_frequences():
    """Crée une heatmap des fréquences sur le clavier"""
    
    # Charger les données
    with open("disposition_creole_optimisee.json", 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    disposition = data['character_positions']
    df_freq = pd.read_csv("frequences_caracteres_creoles.csv")
    freq_dict = dict(zip(df_freq['caractere'], df_freq['frequence']))
    
    # Créer une matrice 10x4 pour le clavier
    clavier_matrix = np.zeros((4, 10))
    clavier_labels = [[''] * 10 for _ in range(4)]
    
    # Mapping simplifié doigt -> position sur la grille
    doigt_to_grid = {
        0: (1, 0), 1: (1, 1), 2: (1, 2), 3: (1, 3), 4: (1, 4),
        5: (1, 5), 6: (1, 6), 7: (1, 7), 8: (1, 8), 9: (1, 9)
    }
    
    # Remplir la matrice
    for char, doigt in disposition.items():
        if doigt in doigt_to_grid:
            row, col = doigt_to_grid[doigt]
            freq = freq_dict.get(char, 0)
            clavier_matrix[row, col] = freq
            clavier_labels[row][col] = char
    
    # Créer la heatmap
    fig, ax = plt.subplots(figsize=(12, 6))
    
    # Normaliser les valeurs pour la heatmap
    clavier_matrix_norm = clavier_matrix / np.max(clavier_matrix) if np.max(clavier_matrix) > 0 else clavier_matrix
    
    # Créer la heatmap avec une colormap personnalisée
    cmap = LinearSegmentedColormap.from_list("custom", ["white", "yellow", "orange", "red"])
    
    hm = ax.imshow(clavier_matrix_norm, cmap=cmap, aspect='auto')
    
    # Ajouter les labels des caractères
    for i in range(4):
        for j in range(10):
            if clavier_labels[i][j]:
                char = clavier_labels[i][j]
                freq = clavier_matrix[i, j]
                
                # Couleur du texte selon l'intensité
                text_color = 'black' if clavier_matrix_norm[i, j] < 0.5 else 'white'
                
                ax.text(j, i, f'{char}\n{freq:,.0f}', ha='center', va='center',
                       fontsize=12, fontweight='bold', color=text_color)
    
    # Configuration
    ax.set_title('🇬🇵 Heatmap des Fréquences - Clavier Créole Optimisé\nPotomitan™', 
                fontsize=16, fontweight='bold', pad=20)
    
    # Labels des axes
    doigt_labels = ['Auric.G', 'Ann.G', 'Maj.G', 'Ind.G', 'Ind.G+', 
                   'Ind.D+', 'Ind.D', 'Maj.D', 'Ann.D', 'Auric.D']
    ax.set_xticks(range(10))
    ax.set_xticklabels(doigt_labels, rotation=45, ha='right')
    
    ax.set_yticks(range(4))
    ax.set_yticklabels(['Ligne 3', 'Ligne 2', 'Ligne 1', 'Ligne 0'])
    
    # Colorbar
    cbar = plt.colorbar(hm, ax=ax, shrink=0.8)
    cbar.set_label('Fréquence Relative', rotation=270, labelpad=20)
    
    plt.tight_layout()
    plt.savefig('heatmap_frequences_clavier.png', dpi=300, bbox_inches='tight')
    plt.show()
    
    print("✅ Heatmap sauvegardée: heatmap_frequences_clavier.png")

def creer_comparaison_visuelle():
    """Crée une comparaison visuelle AZERTY vs Optimisé"""
    
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(20, 8))
    
    # AZERTY traditionnel
    azerty_layout = [
        ['a', 'z', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'],
        ['q', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'm'],
        ['w', 'x', 'c', 'v', 'b', 'n', '', '', '', '']
    ]
    
    # Dessiner AZERTY
    for row_idx, row in enumerate(azerty_layout):
        for col_idx, char in enumerate(row):
            if char:
                rect = patches.Rectangle((col_idx, 2-row_idx), 0.8, 0.8, 
                                       facecolor='lightgray', edgecolor='black')
                ax1.add_patch(rect)
                ax1.text(col_idx + 0.4, 2-row_idx + 0.4, char, 
                        ha='center', va='center', fontsize=12, fontweight='bold')
    
    ax1.set_xlim(0, 10)
    ax1.set_ylim(0, 3)
    ax1.set_title('🔴 AZERTY Traditionnel\n(Non optimisé pour le créole)', 
                 fontsize=14, fontweight='bold')
    ax1.set_aspect('equal')
    ax1.axis('off')
    
    # Clavier optimisé (simplifié)
    with open("disposition_creole_optimisee.json", 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    disposition = data['character_positions']
    force_doigts = data['finger_strength']
    
    # Créer la disposition optimisée
    optimise_layout = [[''] * 10 for _ in range(3)]
    
    # Mapping simplifié pour l'affichage
    doigt_to_display = {
        0: (0, 0), 1: (1, 0), 2: (2, 0), 3: (3, 0), 4: (4, 0),
        5: (5, 0), 6: (6, 0), 7: (7, 0), 8: (8, 0), 9: (9, 0)
    }
    
    for char, doigt in disposition.items():
        if doigt in doigt_to_display:
            col, row = doigt_to_display[doigt]
            if row < 3 and col < 10:
                optimise_layout[row][col] = char
    
    # Dessiner le clavier optimisé
    for row_idx, row in enumerate(optimise_layout):
        for col_idx, char in enumerate(row):
            if char:
                # Couleur selon le type de caractère
                if char in ['é', 'è', 'ò', 'à', 'ô']:
                    color = '#ffeb3b'  # Jaune pour les créoles
                elif char in ['a', 'n', 'o', 'i']:
                    color = '#4caf50'  # Vert pour les plus fréquents
                else:
                    color = '#2196f3'  # Bleu pour les autres
                
                rect = patches.Rectangle((col_idx, 2-row_idx), 0.8, 0.8, 
                                       facecolor=color, edgecolor='black', alpha=0.8)
                ax2.add_patch(rect)
                ax2.text(col_idx + 0.4, 2-row_idx + 0.4, char, 
                        ha='center', va='center', fontsize=12, fontweight='bold',
                        color='white' if char not in ['é', 'è', 'ò', 'à', 'ô'] else 'black')
    
    ax2.set_xlim(0, 10)
    ax2.set_ylim(0, 3)
    ax2.set_title('🟢 Disposition Optimisée Créole\n(52% plus efficace)', 
                 fontsize=14, fontweight='bold')
    ax2.set_aspect('equal')
    ax2.axis('off')
    
    # Légende
    legend_elements = [
        patches.Patch(color='#ffeb3b', label='Caractères créoles'),
        patches.Patch(color='#4caf50', label='Plus fréquents'),
        patches.Patch(color='#2196f3', label='Autres optimisés')
    ]
    ax2.legend(handles=legend_elements, loc='upper center', bbox_to_anchor=(0.5, -0.05), ncol=3)
    
    plt.suptitle('🇬🇵 Comparaison AZERTY vs Clavier Créole Optimisé - Potomitan™', 
                fontsize=18, fontweight='bold', y=0.95)
    
    plt.tight_layout()
    plt.savefig('comparaison_azerty_vs_optimise.png', dpi=300, bbox_inches='tight')
    plt.show()
    
    print("✅ Comparaison sauvegardée: comparaison_azerty_vs_optimise.png")

def main():
    """Fonction principale pour toutes les visualisations"""
    print("🎨 Création des visualisations avancées du clavier créole optimisé...")
    print("=" * 70)
    
    try:
        print("\n1️⃣ Création de la visualisation principale...")
        creer_clavier_visuel_avance()
        
        print("\n2️⃣ Création de la heatmap des fréquences...")
        creer_heatmap_frequences()
        
        print("\n3️⃣ Création de la comparaison AZERTY vs Optimisé...")
        creer_comparaison_visuelle()
        
        print("\n🎉 TOUTES LES VISUALISATIONS CRÉÉES AVEC SUCCÈS !")
        print("📁 Fichiers générés:")
        print("   • clavier_creole_optimise_avance.png")
        print("   • heatmap_frequences_clavier.png") 
        print("   • comparaison_azerty_vs_optimise.png")
        
    except Exception as e:
        print(f"❌ Erreur lors de la génération: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    main()
