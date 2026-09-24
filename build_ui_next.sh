#!/bin/sh
# Build ui-next and copy assets into Quarkus server resource directory.
set -e

cd ui-next
pwd
pnpm install
pnpm build
echo "Done building ui-next, copying dist to server"
cd ..
pwd
mkdir -p server/src/main/resources/META-INF/resources
rm -rf server/src/main/resources/META-INF/resources/*
cp -r ui-next/dist/. server/src/main/resources/META-INF/resources/
