#!/usr/bin/env bash
set -euo pipefail

IMAGE=${1:-new-conductor-ui}
TAG=${2:-latest}
DOCKERFILE=${3:-Dockerfile}
CONTEXT=${4:-.}

function usage() {
  echo "Usage: scripts/build-docker.sh [image] [tag] [Dockerfile] [context]"
  echo "Example: scripts/build-docker.sh myrepo/new-conductor-ui v1.0.0 Dockerfile ."
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker not found or not running." >&2
  exit 1
fi

FULL_TAG="$IMAGE:$TAG"
echo "Building Docker image $FULL_TAG using $DOCKERFILE and context $CONTEXT"
docker build -f "$DOCKERFILE" -t "$FULL_TAG" "$CONTEXT"

echo "Done: $FULL_TAG"
