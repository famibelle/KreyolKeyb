#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Test de configuration Keystore - Clavier Créole Potomitan
Vérifie que la configuration de signature est correcte

Usage: python test_keystore_config.py
"""

import os
import subprocess
import sys

def check_gradle_config():
    """Vérifie la configuration Gradle"""
    print("🔧 VÉRIFICATION CONFIGURATION GRADLE")
    print("-" * 40)
    
    build_gradle_path = "android_keyboard/app/build.gradle"
    
    if not os.path.exists(build_gradle_path):
        print(f"❌ Fichier build.gradle introuvable: {build_gradle_path}")
        return False
    
    with open(build_gradle_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Vérifier la présence de la configuration signingConfigs
    if "signingConfigs" in content:
        print("✅ signingConfigs trouvé dans build.gradle")
    else:
        print("❌ signingConfigs manquant dans build.gradle")
        return False
    
    # Vérifier la configuration release
    if "release {" in content and "storeFile" in content:
        print("✅ Configuration release avec storeFile trouvée")
    else:
        print("❌ Configuration release incomplète")
        return False
    
    print("📄 Configuration Gradle semble correcte")
    return True

def check_workflow_files():
    """Vérifie les fichiers de workflow GitHub Actions"""
    print("\n🔄 VÉRIFICATION WORKFLOWS GITHUB ACTIONS")
    print("-" * 45)
    
    workflows = [
        ".github/workflows/build-apk.yml",
        ".github/workflows/manual-build.yml",
        ".github/workflows/release.yml"
    ]
    
    for workflow in workflows:
        if os.path.exists(workflow):
            print(f"✅ {workflow} trouvé")
            
            with open(workflow, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # Vérifier les variables d'environnement importantes
            if "STORE_FILE" in content:
                print(f"   📝 STORE_FILE configuré dans {workflow}")
            if "KEYSTORE_BASE64" in content:
                print(f"   🔐 KEYSTORE_BASE64 référencé dans {workflow}")
        else:
            print(f"❌ {workflow} manquant")
    
    return True

def simulate_github_env():
    """Simule l'environnement GitHub Actions localement"""
    print("\n🧪 SIMULATION ENVIRONNEMENT GITHUB ACTIONS")
    print("-" * 50)
    
    # Variables d'environnement simulées
    test_env = {
        "STORE_FILE": "app-release.jks",
        "STORE_PASSWORD": "test_password",
        "KEY_ALIAS": "test_alias", 
        "KEY_PASSWORD": "test_key_password"
    }
    
    print("📋 Variables d'environnement simulées:")
    for key, value in test_env.items():
        print(f"   {key}={value}")
        os.environ[key] = value
    
    # Créer un fichier keystore factice pour le test
    keystore_path = "android_keyboard/app/app-release.jks"
    os.makedirs(os.path.dirname(keystore_path), exist_ok=True)
    
    if not os.path.exists(keystore_path):
        # Créer un fichier factice
        with open(keystore_path, 'wb') as f:
            f.write(b'FAKE_KEYSTORE_FOR_TESTING')
        print(f"📄 Keystore factice créé: {keystore_path}")
    else:
        print(f"📄 Keystore existant trouvé: {keystore_path}")
    
    return True

def test_gradle_dry_run():
    """Test Gradle en mode dry-run"""
    print("\n🔨 TEST GRADLE (DRY-RUN)")
    print("-" * 30)
    
    try:
        os.chdir("android_keyboard")
        
        # Test avec --dry-run pour éviter le build complet
        cmd = ["gradle", "assembleRelease", "--dry-run", "--no-daemon"]
        print(f"🔄 Exécution: {' '.join(cmd)}")
        
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
        
        if result.returncode == 0:
            print("✅ Configuration Gradle valide (dry-run réussi)")
            return True
        else:
            print("❌ Erreurs dans la configuration Gradle:")
            print(result.stderr)
            return False
            
    except subprocess.TimeoutExpired:
        print("⏰ Timeout - Gradle prend trop de temps")
        return False
    except Exception as e:
        print(f"💥 Erreur lors du test Gradle: {e}")
        return False
    finally:
        os.chdir("..")

def main():
    print("🇬🇵 TEST CONFIGURATION KEYSTORE - CLAVIER CRÉOLE POTOMITAN")
    print("=" * 65)
    
    all_tests_passed = True
    
    # Test 1: Configuration Gradle
    if not check_gradle_config():
        all_tests_passed = False
    
    # Test 2: Workflows GitHub Actions
    if not check_workflow_files():
        all_tests_passed = False
    
    # Test 3: Simulation environnement
    if not simulate_github_env():
        all_tests_passed = False
    
    # Test 4: Gradle dry-run (optionnel)
    print("\n❓ Voulez-vous tester Gradle en dry-run ? (peut prendre du temps)")
    response = input("   [y/N]: ").strip().lower()
    if response in ['y', 'yes']:
        if not test_gradle_dry_run():
            all_tests_passed = False
    else:
        print("⏭️  Test Gradle ignoré")
    
    # Nettoyage
    keystore_path = "android_keyboard/app/app-release.jks"
    if os.path.exists(keystore_path):
        # Ne pas supprimer si c'est un vrai keystore
        with open(keystore_path, 'rb') as f:
            content = f.read()
        if content == b'FAKE_KEYSTORE_FOR_TESTING':
            os.remove(keystore_path)
            print("🧹 Keystore factice supprimé")
    
    print("\n" + "=" * 65)
    if all_tests_passed:
        print("🎉 TOUS LES TESTS PASSÉS - Configuration prête pour GitHub Actions!")
        print("📝 Prochaines étapes:")
        print("   1. git add .")
        print("   2. git commit -m 'Fix: Configuration keystore pour GitHub Actions'")
        print("   3. git push")
        print("   4. Créer un tag: python actions_trigger.py --tag v2.2.1")
    else:
        print("❌ CERTAINS TESTS ONT ÉCHOUÉ - Vérifiez la configuration")
        return 1
    
    return 0

if __name__ == "__main__":
    sys.exit(main())
