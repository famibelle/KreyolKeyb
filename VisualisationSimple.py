#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Visualisation Simple et Claire du Clavier Créole Optimisé
Génère une représentation épurée et facile à comprendre
"""

import matplotlib.pyplot as plt
import matplotlib.patches as patches
import json
import pandas as pd

def visualisation_clavier_simple():
    """Crée une visualisation simple et claire du clavier optimisé"""
    
    # Charger les données
    with open("disposition_creole_optimisee.json", 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    disposition = data['character_positions']
    stats = data['stats']
    
    # Charger les fréquences
    df_freq = pd.read_csv("frequences_caracteres_creoles.csv")
    freq_dict = dict(zip(df_freq['caractere'], df_freq['frequence']))
    
    # Configuration
    fig, ax = plt.subplots(figsize=(16, 10))
    
    # Titre principal
    fig.suptitle('Clavier Creole Optimise - Potomitan\nDisposition Scientifiquement Adaptee au Creole Guadeloupeen', 
                 fontsize=18, fontweight='bold', y=0.95)
    
    # Positions des touches (rangées du clavier)
    positions_clavier = {
        # Index gauche
        3: [(3, 2), (3, 1), (3, 0)],
        # Index droit  
        6: [(6, 2), (6, 1), (6, 0)],
        # Majeur gauche
        2: [(2, 2), (2, 1), (2, 0)],
        # Majeur droit
        7: [(7, 2), (7, 1), (7, 0)],
        # Index étendu gauche
        4: [(4, 2), (4, 1), (4, 0)],
        # Index étendu droit
        5: [(5, 2), (5, 1), (5, 0)],
        # Annulaire gauche
        1: [(1, 2), (1, 1), (1, 0)],
        # Annulaire droit
        8: [(8, 2), (8, 1), (8, 0)],
        # Auriculaire gauche
        0: [(0, 2), (0, 1), (0, 0)],
        # Auriculaire droit
        9: [(9, 2), (9, 1), (9, 0)]
    }
    
    # Couleurs selon la force des doigts
    couleurs_doigts = {
        0: '#FF6B6B',  # Auriculaire - Rouge
        1: '#FFD93D',  # Annulaire - Jaune
        2: '#6BCF7F',  # Majeur - Vert clair
        3: '#4ECDC4',  # Index - Turquoise
        4: '#4ECDC4',  # Index étendu - Turquoise
        5: '#4ECDC4',  # Index étendu - Turquoise
        6: '#4ECDC4',  # Index - Turquoise
        7: '#6BCF7F',  # Majeur - Vert clair
        8: '#FFD93D',  # Annulaire - Jaune
        9: '#FF6B6B'   # Auriculaire - Rouge
    }
    
    # Noms des doigts
    noms_doigts = {
        0: 'Auriculaire G', 1: 'Annulaire G', 2: 'Majeur G', 3: 'Index G', 4: 'Index G+',
        5: 'Index D+', 6: 'Index D', 7: 'Majeur D', 8: 'Annulaire D', 9: 'Auriculaire D'
    }
    
    # Dessiner les touches
    touches_utilisees = set()
    
    for char, doigt in disposition.items():
        if doigt in positions_clavier:
            # Trouver une position libre pour ce doigt
            for pos_x, pos_y in positions_clavier[doigt]:
                if (pos_x, pos_y) not in touches_utilisees:
                    
                    # Taille selon la fréquence
                    freq = freq_dict.get(char, 0)
                    max_freq = max(freq_dict.values())
                    taille = 0.6 + (freq / max_freq * 0.35)
                    
                    # Couleur du doigt
                    couleur = couleurs_doigts[doigt]
                    
                    # Intensité selon la fréquence (caractères créoles en surbrillance)
                    alpha = 0.9 if char in ['é', 'è', 'ò', 'à', 'ô'] else 0.7
                    
                    # Dessiner la touche
                    rect = patches.FancyBboxPatch(
                        (pos_x - taille/2, pos_y - taille/2),
                        taille, taille,
                        boxstyle="round,pad=0.05",
                        facecolor=couleur, 
                        edgecolor='black',
                        alpha=alpha, 
                        linewidth=2
                    )
                    ax.add_patch(rect)
                    
                    # Caractère (plus gros si créole)
                    taille_font = 18 if char in ['é', 'è', 'ò', 'à', 'ô'] else 16
                    poids_font = 'bold' if char in ['é', 'è', 'ò', 'à', 'ô'] else 'normal'
                    
                    ax.text(pos_x, pos_y + 0.05, char, 
                           ha='center', va='center',
                           fontsize=taille_font, fontweight=poids_font,
                           color='white')
                    
                    # Fréquence en petit
                    ax.text(pos_x, pos_y - 0.25, f'{freq:,}', 
                           ha='center', va='center',
                           fontsize=9, color='gray', fontweight='bold')
                    
                    touches_utilisees.add((pos_x, pos_y))
                    break
    
    # Configuration de l'axe
    ax.set_xlim(-0.5, 10.5)
    ax.set_ylim(-0.5, 3.5)
    ax.set_aspect('equal')
    
    # Labels des rangées
    ax.text(-0.3, 2, 'Rangee 1', ha='center', va='center', fontsize=10, fontweight='bold', rotation=90)
    ax.text(-0.3, 1, 'Rangee 2', ha='center', va='center', fontsize=10, fontweight='bold', rotation=90)
    ax.text(-0.3, 0, 'Rangee 3', ha='center', va='center', fontsize=10, fontweight='bold', rotation=90)
    
    # Labels des doigts en bas
    for doigt, nom in noms_doigts.items():
        if doigt in positions_clavier:
            pos_x = positions_clavier[doigt][0][0]  # Position X du doigt
            ax.text(pos_x, -0.3, nom, ha='center', va='center', 
                   fontsize=9, fontweight='bold', rotation=45)
    
    ax.axis('off')
    
    # Informations sur le côté
    info_text = f"""PERFORMANCE:
Amelioration: {stats['amelioration_pct']:.1f}%
Effort reduit: {(stats['azerty_total'] - stats['optimise_total']):,.0f} unites

CARACTERISTIQUES:
• Caracteres analyses: {len(disposition)}
• Optimise pour le creole
• Accents integres
• Ergonomie biomecanique

COULEURS:
• Turquoise: Index (force max)
• Vert: Majeur (force haute)
• Jaune: Annulaire (force moyenne)
• Rouge: Auriculaire (force faible)

TAILLE = FREQUENCE
SURBRILLANCE = CREOLE"""
    
    ax.text(11, 1.5, info_text, fontsize=11, va='center', ha='left',
           bbox=dict(boxstyle='round,pad=0.5', facecolor='lightblue', alpha=0.8))
    
    plt.tight_layout()
    plt.savefig('clavier_creole_simple.png', dpi=300, bbox_inches='tight', 
                facecolor='white', edgecolor='none')
    plt.show()
    
    print("✅ Visualisation simple sauvegardee: clavier_creole_simple.png")

def creer_schema_comparatif():
    """Crée un schéma comparatif simple"""
    
    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(14, 10))
    
    # Données de performance
    with open("disposition_creole_optimisee.json", 'r', encoding='utf-8') as f:
        stats = json.load(f)['stats']
    
    # AZERTY vs Optimisé - Graphique en barres
    categories = ['Effort Caracteres', 'Effort Bigrammes', 'Effort Total']
    azerty_vals = [stats['azerty_chars']/1000, stats['azerty_bigrammes']/1000, stats['azerty_total']/1000]
    optimise_vals = [stats['optimise_chars']/1000, stats['optimise_bigrammes']/1000, stats['optimise_total']/1000]
    
    x = range(len(categories))
    width = 0.35
    
    bars1 = ax1.bar([i - width/2 for i in x], azerty_vals, width, 
                   label='AZERTY', color='#FF6B6B', alpha=0.8)
    bars2 = ax1.bar([i + width/2 for i in x], optimise_vals, width,
                   label='Creole Optimise', color='#4ECDC4', alpha=0.8)
    
    # Pourcentages d'amélioration
    for i, (azerty, optimise) in enumerate(zip(azerty_vals, optimise_vals)):
        improvement = ((azerty - optimise) / azerty) * 100
        ax1.text(i, max(azerty, optimise) * 1.1, f'-{improvement:.1f}%', 
                ha='center', va='bottom', fontsize=12, fontweight='bold', color='green')
    
    ax1.set_ylabel('Effort (milliers d\'unites)')
    ax1.set_title('Comparaison Performance: AZERTY vs Clavier Creole Optimise', fontweight='bold')
    ax1.set_xticks(x)
    ax1.set_xticklabels(categories)
    ax1.legend()
    ax1.grid(True, alpha=0.3)
    
    # Top caractères créoles
    df_freq = pd.read_csv("frequences_caracteres_creoles.csv")
    creoles = df_freq[df_freq['type'] == 'creole'].head(5)
    
    if not creoles.empty:
        ax2.barh(creoles['caractere'], creoles['frequence'], color='gold', alpha=0.8)
        ax2.set_xlabel('Frequence d\'utilisation')
        ax2.set_title('Caracteres Creoles les Plus Frequents', fontweight='bold')
        ax2.grid(True, alpha=0.3)
        
        # Ajouter les valeurs
        for i, (char, freq) in enumerate(zip(creoles['caractere'], creoles['frequence'])):
            ax2.text(freq + 200, i, f'{freq:,}', va='center', fontweight='bold')
    
    plt.tight_layout()
    plt.savefig('schema_comparatif_performance.png', dpi=300, bbox_inches='tight')
    plt.show()
    
    print("✅ Schema comparatif sauvegarde: schema_comparatif_performance.png")

def main():
    """Fonction principale"""
    print("🎨 Creation des visualisations simples et claires...")
    print("=" * 60)
    
    try:
        print("\n1️⃣ Visualisation principale du clavier...")
        visualisation_clavier_simple()
        
        print("\n2️⃣ Schema comparatif de performance...")
        creer_schema_comparatif()
        
        print("\n🎉 VISUALISATIONS SIMPLES CREEES AVEC SUCCES !")
        print("📁 Fichiers generes:")
        print("   • clavier_creole_simple.png")
        print("   • schema_comparatif_performance.png")
        
    except Exception as e:
        print(f"❌ Erreur: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    main()
