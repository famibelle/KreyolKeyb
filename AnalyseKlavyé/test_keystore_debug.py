#!/usr/bin/env python3
"""
Script pour déclencher manuellement le workflow de build avec debug
"""

import requests
import json
import time
from datetime import datetime

# Configuration GitHub
GITHUB_TOKEN = "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"
REPO_OWNER = "famibelle"
REPO_NAME = "KreyolKeyb"

def trigger_manual_build():
    """Déclenche le workflow manual-build avec debug"""
    url = f"https://api.github.com/repos/{REPO_OWNER}/{REPO_NAME}/actions/workflows/manual-build.yml/dispatches"
    
    headers = {
        "Authorization": f"token {GITHUB_TOKEN}",
        "Accept": "application/vnd.github.v3+json",
        "Content-Type": "application/json"
    }
    
    data = {
        "ref": "main",
        "inputs": {
            "build_type": "release",
            "debug_mode": "true"
        }
    }
    
    print(f"🚀 Déclenchement du workflow manual-build...")
    print(f"📊 Build type: release (avec signature)")
    print(f"🐛 Debug mode: activé")
    print(f"⏰ Timestamp: {datetime.now().strftime('%H:%M:%S')}")
    
    try:
        response = requests.post(url, headers=headers, json=data)
        
        if response.status_code == 204:
            print("✅ Workflow déclenché avec succès!")
            print("🔍 Le workflow va exécuter avec un debug complet du keystore")
            print("📋 Vérifiez les logs pour:")
            print("   - Variables d'environnement STORE_*")
            print("   - Chemins de fichiers absolus")
            print("   - Contenu des répertoires")
            print("   - Ordre d'exécution: clean → keystore → build")
            return True
        else:
            print(f"❌ Erreur lors du déclenchement: {response.status_code}")
            print(f"📄 Réponse: {response.text}")
            return False
            
    except Exception as e:
        print(f"❌ Exception: {e}")
        return False

def get_latest_run_status():
    """Récupère le statut du dernier run"""
    url = f"https://api.github.com/repos/{REPO_OWNER}/{REPO_NAME}/actions/runs"
    
    headers = {
        "Authorization": f"token {GITHUB_TOKEN}",
        "Accept": "application/vnd.github.v3+json"
    }
    
    try:
        response = requests.get(url, headers=headers)
        if response.status_code == 200:
            runs = response.json().get("workflow_runs", [])
            if runs:
                latest = runs[0]
                print(f"\n📊 DERNIER RUN:")
                print(f"   Status: {latest['status']} | Conclusion: {latest.get('conclusion', 'N/A')}")
                print(f"   Workflow: {latest['name']}")
                print(f"   Branch: {latest['head_branch']}")
                print(f"   Commit: {latest['head_sha'][:8]}")
                print(f"   URL: {latest['html_url']}")
                return latest
        return None
    except Exception as e:
        print(f"❌ Erreur récupération statut: {e}")
        return None

if __name__ == "__main__":
    print("🛠️  TEST KEYSTORE DEBUG")
    print("=" * 50)
    
    # Déclencher le workflow
    if trigger_manual_build():
        print("\n⏳ Attente 10 secondes avant de vérifier le statut...")
        time.sleep(10)
        get_latest_run_status()
        print(f"\n🔗 Surveillez l'exécution sur GitHub Actions")
        print(f"   https://github.com/{REPO_OWNER}/{REPO_NAME}/actions")
    else:
        print("❌ Impossible de déclencher le workflow")
