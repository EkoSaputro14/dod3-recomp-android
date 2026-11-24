#!/usr/bin/env bash
set -euo pipefail

# Fetches pinned soyjxck/dod3-recomp and soyjxck/ps3recomp into upstream/
# and applies the Android ARM64 + Snapdragon Vulkan 1.1-1.3 patches.

DOD3_REPO="https://github.com/soyjxck/dod3-recomp.git"
DOD3_COMMIT="ce8b9b30289ef7e0c17d34fd4a5e085a9278ad41"

PS3RECOMP_REPO="https://github.com/soyjxck/ps3recomp.git"
PS3RECOMP_COMMIT="2fa6555a304eb2f9323a3dc45eca0e57abd74b58"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UPSTREAM_DIR="$REPO_ROOT/upstream"

rm -rf "$UPSTREAM_DIR"
mkdir -p "$UPSTREAM_DIR"

echo "==> Fetching soyjxck/dod3-recomp @ ${DOD3_COMMIT:0:12}..."
git init -q "$UPSTREAM_DIR"
git -C "$UPSTREAM_DIR" remote add origin "$DOD3_REPO"
git -C "$UPSTREAM_DIR" fetch --depth 1 origin "$DOD3_COMMIT"
git -C "$UPSTREAM_DIR" checkout -q FETCH_HEAD

# Windows D3D12 shader cache (~17 MB) is unused on Android Vulkan
rm -rf "$UPSTREAM_DIR/shaders/dxbc"

echo "==> Fetching soyjxck/ps3recomp @ ${PS3RECOMP_COMMIT:0:12}..."
rm -rf "$UPSTREAM_DIR/ps3recomp"
mkdir -p "$UPSTREAM_DIR/ps3recomp"
git init -q "$UPSTREAM_DIR/ps3recomp"
git -C "$UPSTREAM_DIR/ps3recomp" remote add origin "$PS3RECOMP_REPO"
git -C "$UPSTREAM_DIR/ps3recomp" fetch --depth 1 origin "$PS3RECOMP_COMMIT"
git -C "$UPSTREAM_DIR/ps3recomp" checkout -q FETCH_HEAD

echo "==> Applying patches/01-ps3recomp-android-vk11.patch..."
git -C "$UPSTREAM_DIR/ps3recomp" apply --check "$REPO_ROOT/patches/01-ps3recomp-android-vk11.patch"
git -C "$UPSTREAM_DIR/ps3recomp" apply "$REPO_ROOT/patches/01-ps3recomp-android-vk11.patch"

echo "==> Applying patches/02-dod3-android-mvp.patch..."
git -C "$UPSTREAM_DIR" apply --check "$REPO_ROOT/patches/02-dod3-android-mvp.patch"
git -C "$UPSTREAM_DIR" apply "$REPO_ROOT/patches/02-dod3-android-mvp.patch"

echo "[OK] Upstream dod3-recomp + ps3recomp synced and patched for Android ARM64."
