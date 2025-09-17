# 🎯 Guide de Test Manuel - Clavier Kreyòl Karukera
## Version Architecture Refactorisée v3.0.0

---

## 📱 ÉTAPES D'ACTIVATION

### 1. Activation du clavier (OBLIGATOIRE)
1. **Ouvrir les paramètres** : Les paramètres se sont ouverts automatiquement
2. **Chercher "Kreyòl Karukera"** dans la liste des claviers
3. **Activer le toggle** à côté du nom du clavier
4. **Accepter les autorisations** si demandées
5. **Définir comme clavier par défaut** (optionnel)

### 2. Test de sélection du clavier
1. Ouvrir une app de saisie (Messages, Notes, Chrome...)
2. Appuyer dans un champ de texte
3. **Méthode 1** : Appuyer sur l'icône clavier dans la barre de navigation
4. **Méthode 2** : Maintenir la touche espace et sélectionner "Kreyòl Karukera"

---

## ⌨️ TESTS FONCTIONNELS DÉTAILLÉS

### Test A : Saisie de base ✏️
- [ ] **Lettres minuscules** : `a b c d e f g h i j k l m n o p q r s t u v w x y z`
- [ ] **Chiffres** : Appuyer sur `123` puis `1 2 3 4 5 6 7 8 9 0`
- [ ] **Retour aux lettres** : Appuyer sur `ABC`
- [ ] **Touches spéciales** : 
  - Espace (barre longue en bas)
  - Retour arrière (←)
  - Entrée (↵)

**✅ Résultat attendu** : Tous les caractères s'affichent correctement

### Test B : Gestion des majuscules 🔤
- [ ] **Majuscule simple** : Appuyer sur `⇧` une fois, puis `A` → doit donner `A`
- [ ] **Retour automatique** : Après la majuscule, `b` doit donner `b` (minuscule)
- [ ] **Caps Lock** : Double-appui sur `⇧`, toutes les lettres en majuscules
- [ ] **Désactivation Caps** : Re-appuyer sur `⇧` pour désactiver

**✅ Résultat attendu** : Gestion correcte des modes majuscule/minuscule

### Test C : Accents et caractères spéciaux ✨
- [ ] **Accent grave** : Appui long sur `a` → popup avec `à` → sélectionner `à`
- [ ] **Accent aigu** : Appui long sur `e` → popup avec `é è ê ë` → sélectionner `é`
- [ ] **Autres accents** : 
  - `o` → `ò ó ô õ`
  - `u` → `ù ú û`
  - `c` → `ç`
  - `n` → `ñ`

**✅ Résultat attendu** : Popups d'accents apparaissent et fonctionnent

### Test D : Suggestions de mots 💡
Taper les mots suivants et vérifier les suggestions :

- [ ] **"bo"** → doit suggérer : `bonjou`, `bonswa`, `bon`
- [ ] **"ka"** → doit suggérer : `kalbas`, `kay`, `ka`  
- [ ] **"an"** → doit suggérer : `annou`, `anni`, `an`
- [ ] **"mwen"** → doit suggérer des mots commençant par `mwen`
- [ ] **Sélection** : Appuyer sur une suggestion pour l'insérer

**✅ Résultat attendu** : Suggestions créoles pertinentes affichées

### Test E : Mode numérique 🔢
- [ ] **Passage en mode 123** : Appuyer sur `123`
- [ ] **Chiffres** : Taper `1234567890`
- [ ] **Symboles** : Tester `+ - * / = ( ) . , ? !`
- [ ] **Retour lettres** : Appuyer sur `ABC`

**✅ Résultat attendu** : Mode numérique complet et fonctionnel

---

## 🎨 TESTS VISUELS

### Interface Guadeloupéenne 🏝️
- [ ] **Couleurs** : Vérifier que le clavier utilise les couleurs de la Guadeloupe
  - Bleu océan pour le fond
  - Jaune soleil pour les accents
  - Vert tropical pour les touches spéciales
- [ ] **Animations** : Les touches doivent avoir un effet visuel au toucher
- [ ] **Lisibilité** : Tous les textes sont bien visibles

### Responsive Design 📱
- [ ] **Portrait** : Clavier bien proportionné en mode portrait
- [ ] **Paysage** : Clavier adapté en mode paysage (tourner l'écran)
- [ ] **Tailles d'écran** : Test sur différentes résolutions

---

## 🚀 TESTS DE PERFORMANCE

### Réactivité ⚡
- [ ] **Saisie rapide** : Taper rapidement → tous les caractères enregistrés
- [ ] **Changement de mode** : Transitions fluides entre modes
- [ ] **Suggestions** : Apparition rapide des suggestions
- [ ] **Accents** : Popups réactifs aux appuis longs

### Stabilité 🛡️
- [ ] **Utilisation prolongée** : Taper pendant 2-3 minutes sans interruption
- [ ] **Changement d'apps** : Passer entre plusieurs apps avec saisie
- [ ] **Rotation écran** : Tourner l'écran plusieurs fois
- [ ] **Mémoire** : Le clavier ne ralentit pas l'appareil

---

## 📊 CHECKLIST FINAL

### Tests automatiques (déjà effectués) ✅
- [x] Installation APK réussie
- [x] Service IME déclaré correctement  
- [x] Clavier disponible dans la liste système
- [x] Manifeste Android valide

### Tests manuels à effectuer 📝
- [ ] Activation dans les paramètres
- [ ] Saisie de base (A)
- [ ] Gestion majuscules (B)  
- [ ] Accents créoles (C)
- [ ] Suggestions N-grams (D)
- [ ] Mode numérique (E)
- [ ] Interface Guadeloupéenne
- [ ] Performance et stabilité

---

## 🎉 VALIDATION FINALE

**Le clavier est validé si :**
1. ✅ Tous les tests automatiques passent (4/4)
2. ✅ Au moins 90% des tests manuels réussissent (8/9 minimum)
3. ✅ Aucun crash ou comportement anormal
4. ✅ Performance fluide et réactive

**En cas de problème :**
- Vérifier les logs : `adb logcat | grep -i kreyol`
- Redémarrer l'app ou l'émulateur
- Réinstaller l'APK si nécessaire

---

## 📞 SUPPORT TECHNIQUE

**Logs de débogage :**
```bash
# Surveiller les logs en temps réel
adb logcat | findstr /i "kreyol ime input"

# Informations mémoire
adb shell dumpsys meminfo com.potomitan.kreyolkeyboard

# Redémarrage service clavier
adb shell ime reset
```

**Contact développeur :** Architecture refactorisée terminée le 11 septembre 2025
