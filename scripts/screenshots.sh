#!/usr/bin/env bash
# Capturas de pantalla de Top Ports en distintos formatos (16:9, 16:10, 4:3, 1:1, 8:7, 21:9…),
# en horizontal y en vertical. Se ejecuta en el emulador de GitHub Actions con el APK de demo
# (TOPPORTS_DEMO=true): la tienda se llena con un catálogo de ejemplo, sin cuenta de Telegram.
set -u
PKG=com.topports.app
OUT=capturas
mkdir -p "$OUT"

APK=$(ls app/build/outputs/apk/release/*.apk | head -1)
adb install -r "$APK"
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
# Sin giro automático: la orientación la decide el tamaño de pantalla que se fija con wm size.
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0

shot() {
  local name=$1
  shift
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/.ui.MainActivity" "$@" >/dev/null
  sleep 5
  adb exec-out screencap -p > "$OUT/$name.png"
  echo "  $name.png"
}

# nombre  ancho  alto  densidad  mando(true = consola)
# La lista se lee por el descriptor 3: "adb shell" se come la entrada estándar y, si no, solo se
# haría el primer formato.
while read -r name w h d pad <&3; do
  [ -z "$name" ] && continue
  echo "== $name (${w}x${h} @ ${d}dpi)"
  adb shell wm size "${w}x${h}"
  adb shell wm density "$d"
  sleep 2
  shot "$name-1-tienda"  --es demo_tab STORE --ez demo_gamepad "$pad"
  shot "$name-2-ficha"   --es demo_tab STORE --ez demo_game true --ez demo_gamepad "$pad"
  shot "$name-3-ajustes" --es demo_tab SETTINGS --es demo_section THEME --ez demo_gamepad "$pad"
done 3<<'FORMATS'
16x9-horizontal     1920 1080 400 true
16x9-vertical       1080 1920 400 false
16x10-horizontal    1920 1200 320 true
16x10-vertical      1200 1920 320 false
4x3-horizontal      1280 960  320 true
4x3-vertical        960  1280 320 false
1x1                 720  720  240 true
8x7                 1024 896  240 true
21x9-horizontal     2400 1080 420 false
21x9-vertical       1080 2400 420 false
21x10-horizontal    2520 1200 420 false
21x10-vertical      1200 2520 420 false
FORMATS

adb shell wm size reset
adb shell wm density reset
ls -1 "$OUT" | wc -l
