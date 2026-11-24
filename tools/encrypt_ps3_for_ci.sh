#!/usr/bin/env bash
set -euo pipefail

# Encrypts EBOOT_101.ELF (v1.01 decrypted) and libsre.prx (PS3 FW 4.91)
# for GitHub Actions CI standalone APK builds.
#
# Usage:
#   PS3_CI_KEY="your-strong-passphrase" bash tools/encrypt_ps3_for_ci.sh /path/to/EBOOT_101.ELF /path/to/libsre.prx

EBOOT_IN="${1:-}"
LIBSRE_IN="${2:-}"

if [[ -z "$EBOOT_IN" || -z "$LIBSRE_IN" ]]; then
  echo "Usage: PS3_CI_KEY=<secret> bash tools/encrypt_ps3_for_ci.sh <EBOOT_101.ELF> <libsre.prx>"
  exit 1
fi

if [[ ! -f "$EBOOT_IN" ]]; then
  echo "[ERROR] File EBOOT_101.ELF tidak ditemukan: $EBOOT_IN"
  exit 1
fi

if [[ ! -f "$LIBSRE_IN" ]]; then
  echo "[ERROR] File libsre.prx tidak ditemukan: $LIBSRE_IN"
  exit 1
fi

if [[ -z "${PS3_CI_KEY:-}" ]]; then
  echo "[ERROR] Set environment variable PS3_CI_KEY terlebih dahulu."
  exit 1
fi

EBOOT_SIZE=$(wc -c < "$EBOOT_IN" | tr -d ' ')
if [[ "$EBOOT_SIZE" != "26872424" ]]; then
  echo "[WARN] Ukuran EBOOT_101.ELF adalah $EBOOT_SIZE bytes (ekspektasi 1.01: 26872424 bytes)."
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mkdir -p "$REPO_ROOT/ci"

key_file=$(mktemp)
trap 'rm -f "$key_file"' EXIT
chmod 600 "$key_file"
printf '%s' "$PS3_CI_KEY" > "$key_file"

openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt \
  -in "$EBOOT_IN" \
  -out "$REPO_ROOT/ci/EBOOT_101.ELF.enc" \
  -pass "file:$key_file"

openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt \
  -in "$LIBSRE_IN" \
  -out "$REPO_ROOT/ci/libsre.prx.enc" \
  -pass "file:$key_file"

echo "[OK] Terenkripsi di ci/EBOOT_101.ELF.enc dan ci/libsre.prx.enc"
echo "Commit kedua file .enc ke repo PRIVATE, lalu simpan PS3_CI_KEY di GitHub Actions Secrets."
