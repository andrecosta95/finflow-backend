#!/bin/bash
# Gera par de chaves RSA para assinar JWTs em desenvolvimento local.
# NUNCA commite essas chaves. O .gitignore já exclui src/main/resources/keys/

set -e

KEY_DIR="src/main/resources/keys"
mkdir -p "$KEY_DIR"

echo "Gerando chave privada RSA 2048..."
openssl genrsa -out "$KEY_DIR/dev-private.pem" 2048

echo "Extraindo chave pública..."
openssl rsa -in "$KEY_DIR/dev-private.pem" -pubout -out "$KEY_DIR/dev-public.pem"

# Converte para PKCS8 (formato que o JwtService espera)
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt \
    -in "$KEY_DIR/dev-private.pem" \
    -out "$KEY_DIR/dev-private-pkcs8.pem"

mv "$KEY_DIR/dev-private-pkcs8.pem" "$KEY_DIR/dev-private.pem"

echo ""
echo "✅ Chaves geradas em $KEY_DIR/"
echo "   dev-private.pem  — chave privada (NUNCA commitar)"
echo "   dev-public.pem   — chave pública"
echo ""
echo "Atualize application-local.yml se necessário:"
echo "  app.jwt.private-key: classpath:keys/dev-private.pem"
echo "  app.jwt.public-key:  classpath:keys/dev-public.pem"
