# TODO – Klavyé Kreyòl Karukera (Android)

Statut actuel: dépôt recentré sur `android_keyboard/` uniquement. Cette liste structure les prochaines étapes par domaine.

## 1. Architecture & Code
- [ ] Extraire la logique de prédiction (N-grams + préfixe) dans une classe dédiée (ex: `SuggestionEngine`)
- [ ] Isoler la gestion accents / popup dans une classe utilitaire
- [ ] Implémenter ou supprimer `KreyolSpellCheckerService.kt` (actuellement placeholder)
- [ ] Introduire un flag global DEBUG pour activer/désactiver logs verbeux
- [ ] Centraliser constantes couleurs / tailles dans `values/` (vérifier doublons Kotlin)
- [ ] Ajouter un wrapper pour les accès `UserDictionary` (gestion erreurs silencieuses)

## 2. Qualité & Maintenabilité
- [ ] Ajouter KDoc sur méthodes principales `KreyolInputMethodService`
- [ ] Réduire le volume de logs (garder niveaux: ERROR, WARN, INFO principaux)
- [ ] Activer Lint & Detekt (optionnel) dans Gradle
- [ ] Script de vérification pré-commit (format + analyse)

## 3. Données Linguistiques
- [ ] Documenter format `creole_dict.json` et `creole_ngrams.json`
- [ ] Ajouter script unique `generate_language_data.sh` / `.ps1` (dictionnaire + ngrams)
- [ ] Ajouter validation (taille max, tri, champs obligatoires)
- [ ] Option: compresser N-grams (gzip) et chargement lazy
- [ ] Enrichir le corpus avec œuvres / extraits d’auteurs majeurs (Telchid, Rupaire, Fontes, Rippon, Rutil, Vérin, Boisdur, Katel, etc.) en respectant droits d’auteur (utiliser uniquement textes libres ou autorisés)

## 3bis. Anti-soulignement (orthographe / dictionnaire système)
- [ ] Implémenter `KreyolSpellCheckerService` (hérite de `SpellCheckerService`) pour déclarer une langue interne (ex: locale pseudo `gcf` ou `fr-GP-kreyol`)
- [ ] Enregistrer le service dans le `AndroidManifest` ( `<service android:name=".KreyolSpellCheckerService" android:permission="android.permission.BIND_TEXT_SERVICE">` + `<subtype android:label="Kreyòl" android:locale="fr" android:extraValue="locale_variant=gcf" />` )
- [ ] Charger un `HashSet<String>` des mots fréquents (≤ 10k) pour lookup O(1)
- [ ] Retourner dans `onGetSuggestions()` que chaque mot connu est correct (empty suggestions) afin d’empêcher soulignement rouge
- [ ] Ajouter fallback: forcer `TYPE_TEXT_FLAG_NO_SUGGESTIONS` uniquement si service indisponible
- [ ] Tester dans: Gmail, Messages, WhatsApp, Keep (certains ignorent les spell checkers custom → documenter)
- [ ] Mesurer impact performances (temps lookup moyen < 0.1 ms)
- [ ] Documenter limites (Android 13+ restrictions dictionnaire utilisateur / permissions)
- [ ] Ajouter option utilisateur dans SettingsActivity: Activer/Désactiver validation orthographique créole

## 4. Performance
- [ ] Profilage allocations lors de saisie rapide (Android Studio profiler)
- [ ] Mettre en cache résultats de filtrage préfixe (LRU < 128 entrées)
- [ ] Débouncer `updateSuggestions()` (p.ex. 16–24 ms) si input rapide
- [ ] Vérifier coût JSON parsing au démarrage (pré-générer structure minimale)

## 5. UX / UI / Accessibilité
- [ ] Vérifier contrastes (WCAG) des touches (jaune sur blanc, etc.)
- [ ] Ajouter contentDescription sur logo SettingsActivity
- [ ] Ajuster zones tactiles min 48dp (valider minWidth/minHeight)
- [ ] Mode thème sombre clair auto (valeurs alternatives)
- [ ] Option vibration configurable utilisateur
- [ ] Prévisualisation touche pressée (popup classique Android)

## 6. Internationalisation
- [ ] Déplacer tout texte dur dans `strings.xml`
- [ ] Préparer locale fallback (fr, en)

## 7. Sécurité & Vie Privée
- [ ] Ajouter section politique confidentialité (README séparé)
- [ ] Vérifier absence de capture de champs sensibles (password) → respecter flags inputType
- [ ] Désactiver logs pour versions release

## 8. Tests
- [ ] Ajouter tests instrumentés (ex: suggestions basiques via Robolectric / instrumentation)
- [ ] Test unitaire sur moteur de suggestion (données mock)
- [ ] Test de régression: appui long + sélection accent
- [ ] Test performance: temps moyen suggestion (< X ms)

## 9. CI/CD
- [ ] Adapter workflow GitHub Actions à structure allégée (`android_keyboard/gradlew ...`)
- [ ] Ajout cache Gradle + signature release conditionnelle
- [ ] Génération artifact: release + mapping.txt (si minify activé futur)
- [ ] Vérification Lint + tests automatiques

## 10. Distribution
- [ ] Script de bump version (Gradle + tag git + release notes)
- [ ] Préparer métadonnées F-Droid (metadata/)
- [ ] Générer icônes adaptatives (si non finalisé)
- [ ] Vérifier conformité Play Store (si publication future)

## 11. Observabilité
- [ ] Ajouter compteur interne (optionnel) usage accents (local, non exporté)
- [ ] Prévoir mécanisme d’opt-in télémetrie (si un jour nécessaire) – par défaut OFF

## 12. Documentation
- [ ] Ajouter schéma flux entrée → suggestion
- [ ] Ajouter ROADMAP.md (vision moyen terme)
- [ ] Ajouter section CONTRIBUTING.md
- [ ] Exemple de log normal vs debug dans README

## 13. Nettoyage technique futur
- [ ] Supprimer code/commentaires legacy marqués “DÉSACTIVÉ pour debug”
- [ ] Vérifier recours Handler/Looper → remplacer par coroutines (si besoin refonte)
- [ ] Examiner possibilité passage Jetpack Compose (long terme)

## 14. Futur Fonctionnel (Backlog idées)
- [ ] Personnalisation layout (rangées, emojis basiques)
- [ ] Mode apprentissage adaptatif (fréquences locales)
- [ ] Support suggestions multi-mots (bigrammes complets)
- [ ] Thèmes alternatifs (mode haute visibilité)
- [ ] Intégration dictionnaire enrichi utilisateur (export/import)

---
Mis à jour: 2025-09-07

Priorisation suggérée (top 5 immédiats):
1. Implémenter classe SuggestionEngine + tests unitaires
2. Réduire logs & ajouter flag DEBUG build
3. Script génération données linguistiques unifié
4. Workflow CI minimal (build + artifact)
5. Accessibilité (contrastes + contentDescription)
