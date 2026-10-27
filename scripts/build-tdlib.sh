#!/usr/bin/env bash
# Compila TDLib para Android (interfaz Java) y deja el resultado en ./tdlib:
#   tdlib/java/org/drinkless/tdlib/{Client,TdApi}.java
#   tdlib/libs/<abi>/libtdjni.so
#
# Requisitos (Linux): git, JDK 17, php, perl, gperf, make, cmake, unzip, curl.
# Tarda bastante (30–90 min por arquitectura); el workflow de CI lo cachea.
set -euo pipefail

TDLIB_COMMIT=${TDLIB_COMMIT:-4c6377aa7386c39217878a9a56d581922ad37ca4} # TDLib 1.8.69
TDLIB_ABIS=${TDLIB_ABIS:-"arm64-v8a armeabi-v7a"}
NDK_VERSION=${NDK_VERSION:-23.2.8568313}

ROOT=$(cd "$(dirname "$0")/.." && pwd)
WORK=${TDLIB_WORK_DIR:-$ROOT/build/tdlib-src}

mkdir -p "$WORK"
cd "$WORK"
if [ ! -d td/.git ]; then
  git init -q td
  git -C td remote add origin https://github.com/tdlib/td.git
fi
git -C td fetch -q --depth 1 origin "$TDLIB_COMMIT"
git -C td checkout -q FETCH_HEAD

cd td/example/android
SDK_DIR=${ANDROID_SDK_FOR_TDLIB:-$PWD/SDK}
if [ ! -d "$SDK_DIR/ndk/$NDK_VERSION" ]; then
  rm -rf "$SDK_DIR"
  ./fetch-sdk.sh "$SDK_DIR" "$NDK_VERSION"
fi
if [ ! -d third-party/openssl ]; then
  ./build-openssl.sh "$SDK_DIR" "$NDK_VERSION" third-party/openssl
fi
./build-tdlib.sh "$SDK_DIR" "$NDK_VERSION" third-party/openssl c++_static Java 21 "$TDLIB_ABIS"

rm -rf "$ROOT/tdlib"
unzip -q tdlib/tdlib.zip -d "$ROOT"
rm -rf "$ROOT/tdlib/javadoc"
echo "TDLib listo en $ROOT/tdlib"
