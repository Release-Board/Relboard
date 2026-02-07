#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Build Jib image for local Docker (dev)
cd "$ROOT_DIR"
./gradlew jibDockerBuild -Djib.from.platforms=linux/arm64

# Restart only the crawler service and clean up orphan containers
cd "$SCRIPT_DIR"
docker compose up -d relboard-crawler
