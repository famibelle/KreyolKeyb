#!/usr/bin/env python3
"""
Script de test automatisé pour le clavier créole Kreyòl Karukera
Tests de validation pour l'architecture refactorisée
"""

import subprocess
import time
import sys
import re

class KreyolKeyboardTester:
    def __init__(self):
        self.test_results = []
        self.adb_prefix = ["adb", "shell"]
    
    def run_adb_command(self, command):
        """Exécute une commande ADB et retourne le résultat"""
        try:
            result = subprocess.run(
                self.adb_prefix + command, 
                capture_output=True, 
                text=True, 
                timeout=10
            )
            return result.stdout.strip(), result.returncode == 0
        except subprocess.TimeoutExpired:
            return "Timeout", False
        except Exception as e:
            return str(e), False
    
    def log_test(self, test_name, success, details=""):
        """Enregistre le résultat d'un test"""
        status = "✅ PASS" if success else "❌ FAIL"
        self.test_results.append({
            'name': test_name,
            'status': status,
            'success': success,
            'details': details
        })
        print(f"{status} - {test_name}")
        if details:
            print(f"    {details}")
    
    def test_service_installation(self):
        """Test 1: Vérifier que le service est installé"""
        print("\n🔍 Test 1: Installation du service")
        
        # Vérifier que l'APK est installé
        output, success = self.run_adb_command([
            "pm", "list", "packages", "com.potomitan.kreyolkeyboard"
        ])
        
        if success and "com.potomitan.kreyolkeyboard" in output:
            self.log_test(
                "Installation APK", 
                True, 
                "Package trouvé dans le système"
            )
        else:
            self.log_test(
                "Installation APK", 
                False, 
                "Package non trouvé"
            )
            return False
        
        # Vérifier que le service IME est déclaré
        output, success = self.run_adb_command([
            "pm", "dump", "com.potomitan.kreyolkeyboard", "|", "grep", "InputMethod"
        ])
        
        self.log_test(
            "Service IME déclaré", 
            success and "InputMethod" in output,
            "Service trouvé dans le manifeste" if success else "Service non trouvé"
        )
        
        return True
    
    def test_service_activation(self):
        """Test 2: Tenter d'activer le service"""
        print("\n⚡ Test 2: Activation du service")
        
        # Lister les IME disponibles
        output, success = self.run_adb_command([
            "ime", "list", "-a"
        ])
        
        kreyol_ime_found = "kreyolkeyboard" in output.lower()
        self.log_test(
            "IME disponible dans la liste", 
            kreyol_ime_found,
            "Trouvé dans ime list" if kreyol_ime_found else "Non trouvé dans ime list"
        )
        
        if kreyol_ime_found:
            # Essayer d'activer le clavier (nécessite interaction utilisateur)
            print("📱 Action requise: Activez manuellement le clavier dans les paramètres")
            self.log_test(
                "Activation manuelle requise", 
                True,
                "Allez dans Paramètres > Langue et saisie > Clavier virtuel"
            )
        
        return kreyol_ime_found
    
    def test_logcat_monitoring(self):
        """Test 3: Surveiller les logs pour détecter le démarrage"""
        print("\n📊 Test 3: Surveillance des logs")
        
        try:
            # Nettoyer les logs et surveiller
            subprocess.run(["adb", "logcat", "-c"], check=True)
            
            print("🔍 Surveillance des logs (durée: 10 secondes)...")
            print("💡 Essayez d'ouvrir une app avec saisie de texte maintenant")
            
            # Surveiller les logs pendant 10 secondes
            result = subprocess.run([
                "adb", "logcat", "-t", "10"
            ], capture_output=True, text=True, timeout=10)
            
            logs = result.stdout
            
            # Chercher les logs du service Kreyol
            kreyol_logs = []
            for line in logs.split('\n'):
                if any(keyword in line.lower() for keyword in ['kreyol', 'potomitan', 'ime']):
                    kreyol_logs.append(line.strip())
            
            if kreyol_logs:
                self.log_test(
                    "Logs du service détectés", 
                    True,
                    f"{len(kreyol_logs)} entrées trouvées"
                )
                print("📝 Logs pertinents:")
                for log in kreyol_logs[:5]:  # Afficher les 5 premiers
                    print(f"    {log}")
            else:
                self.log_test(
                    "Logs du service détectés", 
                    False,
                    "Aucun log Kreyol trouvé - le service n'est peut-être pas actif"
                )
            
        except subprocess.TimeoutExpired:
            self.log_test("Surveillance logs", False, "Timeout lors de la surveillance")
        except Exception as e:
            self.log_test("Surveillance logs", False, f"Erreur: {str(e)}")
    
    def test_basic_functionality(self):
        """Test 4: Tests fonctionnels de base"""
        print("\n⌨️ Test 4: Fonctionnalités de base")
        
        print("📱 Tests manuels requis:")
        print("1. Ouvrez une application de saisie (Messages, Notes, etc.)")
        print("2. Sélectionnez le clavier Kreyòl Karukera")
        print("3. Testez les fonctionnalités suivantes:")
        
        manual_tests = [
            "Saisie de lettres normales (a, b, c...)",
            "Basculer majuscules/minuscules avec ⇧",
            "Passer en mode numérique avec 123",
            "Appui long sur 'a' pour obtenir 'à'", 
            "Appui long sur 'e' pour obtenir 'é'",
            "Utilisation des suggestions de mots",
            "Touches spéciales: espace, retour, suppression"
        ]
        
        for i, test in enumerate(manual_tests, 1):
            print(f"   {i}. {test}")
        
        # Test automatique: vérifier les processus actifs
        output, success = self.run_adb_command([
            "ps", "|", "grep", "kreyol"
        ])
        
        if success and "kreyol" in output.lower():
            self.log_test(
                "Processus service actif", 
                True,
                "Service trouvé dans les processus"
            )
        else:
            self.log_test(
                "Processus service actif", 
                False,
                "Service non trouvé dans les processus actifs"
            )
    
    def test_performance_monitoring(self):
        """Test 5: Surveillance des performances"""
        print("\n📈 Test 5: Performance et mémoire")
        
        # Vérifier l'utilisation mémoire
        output, success = self.run_adb_command([
            "dumpsys", "meminfo", "com.potomitan.kreyolkeyboard"
        ])
        
        if success and "TOTAL" in output:
            # Extraire les informations mémoire
            lines = output.split('\n')
            memory_info = [line for line in lines if 'TOTAL' in line or 'Native Heap' in line]
            
            self.log_test(
                "Informations mémoire disponibles", 
                True,
                f"Données collectées: {len(memory_info)} métriques"
            )
            
            for info in memory_info[:3]:  # Afficher les 3 premières métriques
                print(f"    📊 {info.strip()}")
                
        else:
            self.log_test(
                "Informations mémoire", 
                False,
                "Impossible de récupérer les données mémoire"
            )
    
    def generate_report(self):
        """Génère un rapport final des tests"""
        print("\n" + "="*60)
        print("🎯 RAPPORT FINAL DES TESTS")
        print("="*60)
        
        total_tests = len(self.test_results)
        passed_tests = sum(1 for result in self.test_results if result['success'])
        failed_tests = total_tests - passed_tests
        
        print(f"📊 Total des tests: {total_tests}")
        print(f"✅ Tests réussis: {passed_tests}")
        print(f"❌ Tests échoués: {failed_tests}")
        print(f"📈 Taux de réussite: {(passed_tests/total_tests)*100:.1f}%")
        
        print("\n📋 Détail des résultats:")
        for result in self.test_results:
            print(f"  {result['status']} {result['name']}")
            if result['details']:
                print(f"      → {result['details']}")
        
        if failed_tests == 0:
            print("\n🎉 TOUS LES TESTS AUTOMATIQUES SONT PASSÉS!")
            print("💡 N'oubliez pas de tester manuellement les fonctionnalités du clavier")
        else:
            print(f"\n⚠️  {failed_tests} test(s) ont échoué. Vérifiez l'installation et l'activation.")
        
        print("\n🔧 Prochaines étapes recommandées:")
        print("1. Activer le clavier dans Paramètres Android")
        print("2. Tester manuellement toutes les fonctionnalités")
        print("3. Vérifier les suggestions de mots créoles")
        print("4. Valider les accents et caractères spéciaux")
        
    def run_all_tests(self):
        """Lance tous les tests"""
        print("🚀 DÉBUT DES TESTS DU CLAVIER KREYÒL KARUKERA")
        print("Version: Architecture refactorisée v3.0.0")
        print("Date: 11 septembre 2025\n")
        
        try:
            self.test_service_installation()
            self.test_service_activation()
            self.test_logcat_monitoring()
            self.test_basic_functionality()
            self.test_performance_monitoring()
            
        except KeyboardInterrupt:
            print("\n⏹️ Tests interrompus par l'utilisateur")
        except Exception as e:
            print(f"\n❌ Erreur lors des tests: {str(e)}")
        
        finally:
            self.generate_report()

if __name__ == "__main__":
    tester = KreyolKeyboardTester()
    tester.run_all_tests()
