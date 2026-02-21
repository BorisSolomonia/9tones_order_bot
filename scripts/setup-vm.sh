#!/bin/bash
# Setup script for GCP VM
# Run: bash setup-vm.sh

set -e

echo "=== Order App VM Setup ==="

# Update system
sudo apt-get update && sudo apt-get upgrade -y

# Install Docker
if ! command -v docker &> /dev/null; then
    echo "Installing Docker..."
    curl -fsSL https://get.docker.com | sh
    sudo usermod -aG docker $USER
    echo "Docker installed. Log out and back in for group changes."
fi

# Install Docker Compose (v2 comes with Docker)
docker compose version

# Install gcloud CLI if not present
if ! command -v gcloud &> /dev/null; then
    echo "Installing gcloud CLI..."
    curl https://sdk.cloud.google.com | bash
    exec -l $SHELL
    gcloud init
fi

# Create app directory
APP_DIR="$HOME/apps/order-app"
mkdir -p "$APP_DIR/secrets" "$APP_DIR/backups" "$APP_DIR/docker"

# Create external network and volume
docker network create web 2>/dev/null || true
docker volume create caddy_data 2>/dev/null || true

echo ""
echo "=== Setup complete ==="
echo ""
echo "Next steps:"
echo "1. Configure gcloud: gcloud auth login"
echo "2. Configure Docker for AR: gcloud auth configure-docker us-central1-docker.pkg.dev"
echo "3. Add secrets to GCP Secret Manager:"
echo "   - orderapp-env: Full .env file"
echo "   - orderapp-sa: Google Sheets service account JSON"
echo "4. Add GitHub secrets: GCP_SA_KEY, GCP_PROJECT_ID, VM_HOST, VM_SSH_USER, VM_SSH_KEY"
echo "5. Push to main branch to trigger deployment"
