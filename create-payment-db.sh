#!/bin/bash
# Script para criar o banco payment se não existir
# Execute: ./create-payment-db.sh

docker compose exec postgres psql -U postgres -tc "SELECT 1 FROM pg_database WHERE datname = 'payment'" | grep -q 1 || \
docker compose exec postgres psql -U postgres -c "CREATE DATABASE payment"

echo "✅ Banco 'payment' criado ou já existe!"

