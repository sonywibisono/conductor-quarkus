#!/bin/sh
# Build ui and copy assets into Quarkus server resource directory.
set -e

cd ui
pwd
pnpm install
NODE_OPTIONS="${NODE_OPTIONS:---max-old-space-size=4096}" pnpm build
echo "Done building ui, copying dist to server"
cd ..
pwd
mkdir -p server/src/main/resources/META-INF/resources
rm -rf server/src/main/resources/META-INF/resources/*
cp -r ui/dist/. server/src/main/resources/META-INF/resources/
