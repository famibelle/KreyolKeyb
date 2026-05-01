#!/bin/bash
# generate_csr.sh
# Génère une clé privée + CSR pour Apple sans Mac
# Usage : bash generate_csr.sh

set -e

NAME="Medhi Famibelle"
EMAIL="medhi@potomitan.io"
COUNTRY="FR"

echo "🔑 Génération de la clé privée RSA 2048..."
openssl genrsa -out ios_distribution.key 2048

echo "📄 Génération du CSR..."
openssl req -new \
  -key ios_distribution.key \
  -out CertificateSigningRequest.certSigningRequest \
  -subj "/CN=$NAME/C=$COUNTRY/emailAddress=$EMAIL"

echo ""
echo "✅ Fichiers créés :"
echo "   - ios_distribution.key          (GARDE-LE SECRET — ne jamais commiter)"
echo "   - CertificateSigningRequest.certSigningRequest"
echo ""
echo "📋 ÉTAPES SUIVANTES :"
echo ""
echo "1. Va sur : https://developer.apple.com/account/resources/certificates/add"
echo "   → Choisis : iOS Distribution (App Store and Ad Hoc)"
echo "   → Upload : CertificateSigningRequest.certSigningRequest"
echo "   → Télécharge le fichier ios_distribution.cer"
echo ""
echo "2. Convertis le certificat en .p12 :"
echo "   openssl x509 -in ios_distribution.cer -inform DER -out ios_distribution.pem -outform PEM"
echo "   openssl pkcs12 -export -inkey ios_distribution.key -in ios_distribution.pem -out ios_distribution.p12"
echo "   (choisis un mot de passe → c'est ton CERTIFICATE_PASSWORD)"
echo ""
echo "3. Encode en base64 pour GitHub Secrets :"
echo "   base64 -w 0 ios_distribution.p12 > certificate_base64.txt"
echo "   cat certificate_base64.txt   ← copie tout → secret CERTIFICATE_P12_BASE64"
echo ""
echo "4. Crée les Provisioning Profiles sur :"
echo "   https://developer.apple.com/account/resources/profiles/add"
echo "   → App Store → com.potomitan.kreyolkeyb        → nom : 'KreyolKeyb AppStore'"
echo "   → App Store → com.potomitan.kreyolkeyb.keyboard → nom : 'KreyolKeyb Extension AppStore'"
echo "   Encode chacun : base64 -w 0 fichier.mobileprovision"
echo ""
echo "5. GitHub Secrets à créer (Settings → Secrets → Actions) :"
echo "   CERTIFICATE_P12_BASE64      ← contenu de certificate_base64.txt"
echo "   CERTIFICATE_PASSWORD        ← mot de passe choisi à l'étape 2"
echo "   PP_APP_BASE64               ← provisioning profile app encodé"
echo "   PP_EXTENSION_BASE64         ← provisioning profile extension encodé"
echo "   DEVELOPMENT_TEAM_ID         ← ton Team ID Apple (10 caractères, ex: AB12CD34EF)"
echo "   APPLE_ID                    ← ton email Apple Developer"
echo "   APP_SPECIFIC_PASSWORD       ← mot de passe app-specific (appleid.apple.com)"
