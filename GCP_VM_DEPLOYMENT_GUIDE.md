# GCP VM Deployment Guide (2026 Refresh)

This guide matches the current deployment architecture in this repository and is written for:
- rookie deployers
- LLM agents automating deployments
- teams deploying similar apps to other GCP VMs

It is intentionally practical and failure-driven..

## 1. Current Architecture (What Actually Runs)

- CI/CD: GitHub Actions (`.github/workflows/deploy.yml`)
- Image registry: GCP Artifact Registry repo `orderapp` in `us-central1`
- Runtime host: single GCP VM (Debian/Ubuntu)
- Runtime orchestrator: Docker Compose (`docker/compose.production.yml`)
- Edge proxy: Caddy (`docker/Caddyfile.production`)
- Backend image: `us-central1-docker.pkg.dev/<PROJECT_ID>/orderapp/orderapp-backend:<TAG>`
- Frontend image: `us-central1-docker.pkg.dev/<PROJECT_ID>/orderapp/orderapp-frontend:<TAG>`
- Secrets source: GCP Secret Manager
  - `orderapp-env` -> written to `docker/.env`
  - `orderapp-sa` -> written to `docker/secrets/service-account.json`

## 2. Deployment Flow

1. `vm-handshake` job: SSH connectivity check only.
2. `test` job: backend tests.
3. `build-backend` + `build-frontend` jobs:
   - auth to GCP with `GCP_SA_KEY`
   - ensure Artifact Registry repo exists
   - build and push Docker images
4. `deploy` job (SSH to VM):
   - install Docker/compose/gcloud if missing
   - authenticate gcloud on VM using `GCP_SA_KEY` passed as env
   - fetch secrets from Secret Manager
   - pull images and run compose
   - health-check `http://localhost/health`

## 3. Required GitHub Secrets

Set these repository secrets:

- `GCP_PROJECT_ID`
- `GCP_SA_KEY` (service account JSON used by Actions)
- `VM_HOST` (public IP)
- `VM_SSH_USER`
- `VM_SSH_KEY` (private key for `VM_SSH_USER`)

## 4. Required GCP Setup (One-Time)

Use your target project:

```bash
gcloud config set project <PROJECT_ID>
```

Enable APIs:

```bash
gcloud services enable \
  compute.googleapis.com \
  artifactregistry.googleapis.com \
  secretmanager.googleapis.com \
  iam.googleapis.com \
  cloudresourcemanager.googleapis.com \
  sheets.googleapis.com \
  drive.googleapis.com
```

Create Artifact Registry repo (if not existing):

```bash
gcloud artifacts repositories create orderapp \
  --repository-format=docker \
  --location=us-central1 \
  --description="Order App images"
```

Open firewall and tag VM:

```bash
gcloud compute instances add-tags <VM_NAME> \
  --zone=<ZONE> \
  --tags=http-server,https-server

gcloud compute firewall-rules create allow-http-orderapp \
  --network=default \
  --direction=INGRESS \
  --action=ALLOW \
  --rules=tcp:80 \
  --source-ranges=0.0.0.0/0 \
  --target-tags=http-server

gcloud compute firewall-rules create allow-https-orderapp \
  --network=default \
  --direction=INGRESS \
  --action=ALLOW \
  --rules=tcp:443 \
  --source-ranges=0.0.0.0/0 \
  --target-tags=https-server
```

## 5. Secret Manager Contracts

The deploy job expects these secrets:

- Secret `orderapp-env`: full env file content for production
- Secret `orderapp-sa`: JSON credentials file consumed by backend at `/secrets/service-account.json`

Create/update:

```bash
gcloud secrets create orderapp-env --data-file=docker/.env --project=<PROJECT_ID> || true
gcloud secrets versions add orderapp-env --data-file=docker/.env --project=<PROJECT_ID>

gcloud secrets create orderapp-sa --data-file=service-account.json --project=<PROJECT_ID> || true
gcloud secrets versions add orderapp-sa --data-file=service-account.json --project=<PROJECT_ID>
```

## 6. VM Bootstrap (Manual One-Time Optional)

The workflow can auto-install dependencies, but manual setup is still recommended:

```bash
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-plugin docker-compose curl ca-certificates gnupg
sudo systemctl enable docker
sudo systemctl start docker
sudo usermod -aG docker $USER
```

Reconnect SSH after group change.

## 7. Compose and Path Rules (Critical)

Because deploy uses:

```bash
docker compose -f docker/compose.production.yml ...
```

all relative paths in compose are resolved from `docker/` directory.

So deployment writes:

- env file to `docker/.env`
- service account file to `docker/secrets/service-account.json`

Do not write these files in repo root unless compose file is changed.

## 8. Caddy Routing (Current)

`docker/Caddyfile.production`:

- `/api/*` -> `backend:8080`
- `/actuator/*` -> `backend:8080`
- `/health` -> static 200 from Caddy
- everything else -> `frontend:3000`

Access app by:

- `http://<VM_PUBLIC_IP>/login`

## 9. Known Failure Modes and Fixes

### A. SSH handshake failed

Symptom:
- `ssh: unable to authenticate`

Fix:
- verify `VM_SSH_USER` and `VM_SSH_KEY`
- test locally:

```bash
ssh -i <key> <user>@<ip>
```

### B. Artifact Registry repository not found

Symptom:
- `Repository "orderapp" not found`

Fix:
- create repo manually, or keep workflow auto-create step.

### C. VM can SSH but deploy fails with missing docker

Fix:
- workflow auto-installs docker/compose.
- for Debian variants, fallback to `docker-compose` is handled.

### D. Secret Manager PERMISSION_DENIED / insufficient scopes

Symptom:
- `ACCESS_TOKEN_SCOPE_INSUFFICIENT`

Fix:
- do not rely on VM metadata scopes.
- workflow authenticates `gcloud` using `GCP_SA_KEY` on VM.

### E. Backend startup fails with Google Sheets 403 SERVICE_DISABLED

Symptom:
- `Google Sheets API has not been used in project ...`

Fix:
- enable `sheets.googleapis.com` for project tied to used credentials
- share spreadsheet with service account email used by backend.

### F. External IP timeout despite containers up

Symptom:
- `curl http://<IP>` timeout

Fix:
- set VM tags `http-server`/`https-server`
- add firewall ingress for ports 80/443.

### G. Compose cannot find env file

Symptom:
- `Couldn't find env file: .../docker/.env`

Fix:
- ensure deploy writes `docker/.env`, not root `.env`.

### H. RS.GE sync error: too many concurrent streams

Symptom:
- `Chunk fetch failed: ... too many concurrent streams`

Fix already in code:
- force HTTP/1.1
- configurable chunk parallelism (`RSGE_CHUNK_PARALLELISM`)
- sequential fallback on stream-limit errors.

## 10. Deployment Verification Checklist

On VM:

```bash
cd ~/apps/order-app
docker compose -f docker/compose.production.yml ps
curl -sf http://localhost/health
```

From local:

```bash
curl -I http://<VM_PUBLIC_IP>/
curl -I http://<VM_PUBLIC_IP>/health
```

Log triage:

```bash
docker compose -f docker/compose.production.yml logs --tail=200 caddy
docker compose -f docker/compose.production.yml logs --tail=200 backend
docker compose -f docker/compose.production.yml logs --tail=200 frontend
```

## 11. How to Adapt for Other Apps and Other VMs

Change these only:

- image names and compose services
- Caddy routes
- secret names (`<app>-env`, `<app>-sa`, etc.)
- health endpoint used by deploy job
- VM host/user secrets

Keep these patterns unchanged:

- handshake job before builds
- Artifact Registry existence check
- VM-side dependency self-heal
- Secret Manager fetch during deploy
- health-gated rollout

## 12. Security Notes

- never commit `.env`, service-account JSON, or SSH private keys
- prefer dedicated deploy keys per repo
- rotate `GCP_SA_KEY` periodically
- keep Caddy as only internet-exposed container

## 13. Quick Command Card

```bash
# Trigger manual deployment
# (GitHub UI -> Actions -> Deploy Order App -> Run workflow)

# Validate ingress
curl -I http://<VM_PUBLIC_IP>/

# Check running services
ssh <user>@<ip> "cd ~/apps/order-app && docker compose -f docker/compose.production.yml ps"

# View last 100 lines of backend logs
ssh <user>@<ip> "cd ~/apps/order-app && docker compose -f docker/compose.production.yml logs --tail=100 backend"
```
