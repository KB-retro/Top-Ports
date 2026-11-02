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

# Como shot, pero pulsando la cruceta antes: Android arranca en modo táctil y no enseña lo
# seleccionado hasta que se usa el mando (la derecha pasa de la 1.ª a la 2.ª carátula).
shot_key() {
  local name=$1
  shift
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/.ui.MainActivity" "$@" >/dev/null
  sleep 5
  adb shell input keyevent KEYCODE_DPAD_RIGHT
  sleep 1
  adb exec-out screencap -p > "$OUT/$name.png"
  echo "  $name.png"
}

# nombre  ancho  alto  densidad  mando(true = consola)
# La lista se lee por el descriptor 3: "adb shell" se come la entrada estándar y, si no, solo se
# haría el primer formato.
while read -r name w h d pad <&3; do
  case "$name" in ""|"#"*) continue ;; esac
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
# El emulador no pasa de 1920 px de lado: 21:9 y 21:10 se simulan a 1920 con la misma
# proporción y unos dp parecidos a un móvil real (1080x2400 a 420 ppp = 411x914 dp).
21x9-horizontal     1920 823 320 false
21x9-vertical       823 1920 320 false
21x10-horizontal    1920 914 320 false
21x10-vertical      914 1920 320 false
FORMATS

# Cada estilo de interfaz (con un tema de colores distinto), en consola horizontal y móvil vertical.
while read -r style theme <&3; do
  case "$style" in ""|"#"*) continue ;; esac
  for fmt in "16x9-horizontal 1920 1080 400 true" "16x9-vertical 1080 1920 400 false"; do
    read -r name w h d pad <<<"$fmt"
    echo "== estilo $style ($name)"
    adb shell wm size "${w}x${h}"
    adb shell wm density "$d"
    sleep 2
    shot "estilo-$style-$name-1-tienda" --es demo_tab STORE --es demo_style "$style" --es demo_theme "$theme" --ez demo_gamepad "$pad"
    shot "estilo-$style-$name-2-ficha" --es demo_tab STORE --ez demo_game true --es demo_style "$style" --es demo_theme "$theme" --ez demo_gamepad "$pad"
    shot "estilo-$style-$name-3-ajustes" --es demo_tab SETTINGS --es demo_section THEME --es demo_style "$style" --es demo_theme "$theme" --ez demo_gamepad "$pad"
  done
done 3<<'STYLES'
PIXEL      PICO8
TERMINAL   GRUVBOX
WAVES      SYNTHWAVE
FLAT       DREAMCAST
RPG        SNES
NEON       SYNTHWAVE
BEVEL      MASTER_SYSTEM
KEYCAP     GAMEBOY
STYLES

# Lo seleccionado con el mando (la primera carátula) en las paletas de un solo tono, donde
# más cuesta verlo, con un tema de marco grueso y otro fino.
adb shell wm size 1920x1080
adb shell wm density 400
sleep 2
for theme in GAMEBOY GB_DMG VIRTUAL_BOY NES SYNTHWAVE; do
  for style in PIXEL WAVES; do
    shot_key "seleccion-$theme-$style" --es demo_tab STORE --es demo_style "$style" --es demo_theme "$theme" --ez demo_gamepad true
  done
done

adb shell wm size reset
adb shell wm density reset
ls -1 "$OUT" | wc -l
