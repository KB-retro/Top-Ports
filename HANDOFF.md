# HANDOFF — Top Ports Android

Notas para quien siga con el proyecto (persona o agente). Lo que hace la app, la privacidad, el
aviso legal y cómo conseguir el APK están en el [README](README.md); aquí va lo necesario para
trabajar en el código.

## Qué es

Tienda Android para el canal de Telegram de ports de juegos. Cada usuario entra con su propia
cuenta de Telegram (TDLib). La app lee el canal, agrupa los posts en fichas de juego y descarga,
extrae e instala los archivos. Está pensada para consolas Android con mando (Odin 2 Portal,
RG477M) y también para móviles.

Toda la interfaz, el código comentado y los commits van **en español**.

## Estructura

```
core/   Kotlin puro, sin Android. Lógica testeable con tests JUnit.
app/    App Android: Jetpack Compose, TDLib, 7-Zip-JBinding, Media3.
scripts/build-tdlib.sh   Compila TDLib para Android (lo usa CI, con caché).
scripts/screenshots.sh   Capturas automáticas en el emulador (modo demo).
art/, licenses/          Logo y licencias de terceros (se muestran en Ajustes → Legal).
```

### `core/` (`com.topports.core`)

| Archivo | Qué hace |
|---|---|
| `CatalogBuilder.kt` | Convierte mensajes del canal en `Game`: álbumes, archivos, etiquetas de sistema (`#N64`…), post de la propia app (`isAppPost`, no sale como juego), fecha de "actualizado" (solo cambios de archivos). |
| `FileNames.kt` | Partes y tipos de archivo: `SplitStyle` (`NUMBERED` .001, `RAR_PART` .part1.rar, `ZIP_SPAN` .z01), `kindOf` (APK, ARCHIVE, APK_BUNDLE, OBB, OTHER), nombres de carpeta seguros. |
| `InstallPlanner.kt` | Tras extraer: qué es APK, split APK, OBB o `Android/data`. |
| `UpdateFinder.kt` / `InstalledFinder.kt` | Avisos de actualización de juegos instalados. Regex de versión `(?iu)(?<![\p{L}\d])v?(\d+(?:[._]\d+)+)`, que ignora `arm64`, `x86_64`, etc. |
| `SelfUpdate.kt` | Detecta un APK de TopPorts publicado en el canal para actualizar la propia app. |
| `Announcements.kt` | Posts "ACTUALIZACIÓN DE PORTS" → popup de novedades. |
| `ChannelLink.kt` | Enlaces del canal (`t.me/...`). |

### `app/` (`com.topports.app`)

| Archivo | Qué hace |
|---|---|
| `TopPortsApp.kt` | Grafo de dependencias (`graph`): telegram, channel, tasks, installs, updates, storage. |
| `telegram/TelegramClient.kt` | Envoltorio de TDLib: auth, archivos (`files` flow: solo los que pide la app, no carátulas), descargas, `setOnline`, reacciones. En `DEMO` no arranca TDLib. |
| `telegram/ChannelRepository.kt` | Lee el canal (historial + pasada de `SearchChatMessages` en servidor para no perder juegos), `syncRecent()` al volver a la app, corazones (❤ = reacción del canal), `publish()` sincronizado. |
| `telegram/DemoCatalog.kt` | Catálogo falso para el modo demo y las capturas. |
| `work/TaskCenter.kt` | Descargas y tareas: mover a `/TopPorts/<juego>` al terminar, **extracción automática**, instalar, guardar sin extraer, cancelar, copiar OBB, autoactualización. |
| `work/TaskCenter.kt` (cola) | Cola de descargas: `maxParallel` (1, 2, 3 o sin límite) y `wifiOnly`; `schedule()` arranca por orden de petición (`PendingSave.addedAt`) y respeta las pausadas a mano (`paused`). Expone `queued` y `waitingWifi` para la ficha y TAREAS. |
| `ui/ChipMusic.kt` | Banda sonora chiptune generada al momento (sin archivos): canciones al azar con acordes, motivo de melodía, bajo, arpegio y batería. Solo con la app en pantalla (`setForeground`), se calla con los vídeos (`hold`). Volumen en Ajustes → Sonido → Música (alta por defecto). |
| `ui/RetroSound.kt` | Sonidos de menú sintetizados (sin archivos): mover, aceptar y volver. Tipo de sonido (`RetroSound.type`: Suave —por defecto—, 8 bits, Arcade, Beeper o Campanita, para menú y música) y activarlos en Ajustes → Sonido. |
| `work/WorkService.kt` | Servicio en primer plano (dataSync) mientras hay trabajo; `onTimeout` para el límite diario de Android 15. |
| `archive/ArchiveExtractor.kt` | 7-Zip-JBinding. `VolumeSet` resuelve las partes por nombre lógico (TDLib puede renombrar). Comprobación zip-slip. |
| `install/ApkInstaller.kt`, `InstallResultReceiver.kt` | PackageInstaller (APK, partido, split, XAPK). Si la app está en segundo plano, notificación "Listo para instalar". |
| `updates/Updates.kt` (`InstallRegistry`) | SharedPreferences `installs`: instalados, extraídos por juego, guardados (`saved_`), extraídos solos (`unpacked_`), descargas pendientes de mover (`pending_`), avisos ignorados, carpeta de cada juego (`gamedir_`, "Juego (2)" si se repite el título) y APK partidos esperando a que Android confirme su paquete (`session_`). |
| `settings/StorageTools.kt` | Uso de espacio, borrar caché (`OptimizeStorage` de TDLib), huérfanos. |
| `storage/Storage.kt` | Rutas: `/TopPorts` si hay permiso de todos los archivos; si no, carpeta privada. |
| `ui/AppRoot.kt` | Menú lateral (horizontal y pantallas casi cuadradas) o barra inferior (vertical). |
| `ui/AppViewModel.kt` (tienda) | Favoritos (`favorites`, prefs `ui`; botón ★ junto a Buscar o filtro `FAVORITES_FILTER`; en Favoritos, SELECT o mantener pulsada la carátula lo quita, con confirmación), órdenes (`SortMode`: nuevos, actualizados, A-Z, sistema, más corazones, más ligeros, más pesados) y botón "Al azar" en la tienda. |
| `ui/AppViewModel.kt` | Estado de la UI, pestañas con historial, vuelta a la ficha al salir de TAREAS (`switchTab`). |
| `ui/Gamepad.kt`, `ui/MainActivity.kt` | Mando: A/B (intercambiables), L1/R1 (filas en la tienda, secciones en Ajustes), L2/R2, X, Y, SELECT y stick derecho (descripción de la ficha y Ajustes). `lockSquareScreensToLandscape()` para pantallas con proporción < 1.26. |
| `ui/store/*` | Tienda (`CatalogScreen`), ficha (`GameScreen`) y popup de novedades. |
| `ui/settings/*` | Ajustes por secciones: Versión, Permisos, Descargas, Sonido, Temas, Mando, Cuenta, Privacidad, Legal. |
| `ui/theme/PixelTheme.kt` | **Paletas de colores** (`AppTheme`: NES —por defecto—, PICO-8, Game Boy, NES, SNES, Mega Drive, PSX…) y **temas** (`UiStyle`: Ondas —por defecto—, Plano, RPG 16 bits, Neón, Relieve, Cartucho, Híbrida y Espiral), que se combinan. En la interfaz se llaman "Paleta de colores" y "Temas". Un tema (`StyleSpec`) fija letras, tamaños, mayúsculas, tipo de recuadro, barra de progreso y fondo. Todos los temas comparten el alto de línea (botones, pestañas y logo miden igual) y los tamaños de letra salen de igualar el **ancho** de un texto de muestra con la letra del sistema, para que quepan las mismas opciones por línea. `UiAnim` son las animaciones de cambio de pantalla. |
| `ui/components/PxTransition.kt` | Animación al cambiar de pestaña, abrir una ficha y abrir la app: Fundido, Deslizar, Cortinilla, Persiana, Píxeles o ninguna. |
| `ui/components/PixelComponents.kt` | `pixelFrame` dibuja cada recuadro según `Px.style`; `pxBackdrop` es el fondo (barrido, ondas, degradado). Usar `pxCaps()` en vez de `uppercase()` para títulos. |

## Compilar

**La única compilación fiable es GitHub Actions** (`.github/workflows/android.yml`). No hay SDK de
Android en local.

- Jobs:
  - `tdlib`: compila TDLib en el commit `4c6377aa`, cacheado.
  - Build del APK firmado: la versión sale de `run_number` (N): `0.1.N` hasta la 99 y desde la 100
    `0.<1 + N/100>.<N % 100>` (100 → 0.2.0, 200 → 0.3.0). `versionCode` es N. Está en
    `app/build.gradle.kts` y en el paso "Versión" de `android.yml`: cambiar los dos a la vez.
  - `screenshots`: solo con `workflow_dispatch`.
- **Secretos del repositorio:**
  - `TELEGRAM_API_ID`, `TELEGRAM_API_HASH`.
  - `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD` (y los de la clave).
  - `TOPPORTS_CHANNEL_ID`, `TOPPORTS_CHANNEL_USERNAME`, `TOPPORTS_CHANNEL_INVITE_LINK`, `TOPPORTS_LEGAL_CONTACT`. Estos cuatro pueden ir como secreto o como variable.
- **Nunca** subir al repo el enlace de invitación ni el keystore de firma. Si cambia la firma,
  los usuarios no podrán actualizar encima.
- Los artefactos (APK) se descargan desde la página del run en GitHub.
- **Tests de `core/`:** sí se pueden pasar en local. Basta un proyecto Gradle de JVM que incluya
  `core/src` y ejecutar `gradle test`. Conviene pasarlos antes de cada push que toque `core/`.

## Modo demo y capturas

- Con la variable de entorno `TOPPORTS_DEMO=true` en la compilación se activa
  `BuildConfig.DEMO`: no usa TDLib, el usuario entra directamente y carga `DemoCatalog`.
- `MainActivity.openDemoScreen()` acepta estos extras de intent:
  - `demo_tab` (`STORE`, `SETTINGS`…)
  - `demo_section` (`THEME`…)
  - `demo_game` (booleano)
  - `demo_gamepad` (booleano)
  - `demo_style` (`WAVES`, `FLAT`, `RPG`, `NEON`, `BEVEL`, `KEYCAP`, `HYBRID`, `SPIRAL`), `demo_theme` (`SNES`…) y `demo_anim` (por defecto `NONE` en las capturas)
- Lanzar el workflow a mano (Run workflow) genera las capturas:
  - 3 pantallas × 12 formatos: 16:9, 16:10, 4:3, 1:1, 8:7, 21:9 y 21:10, en horizontal y vertical.
  - Se publican en la rama **`capturas`**, que se sobrescribe en cada ejecución y no lanza compilaciones.
- El emulador no pasa de 1920 px de lado. Por eso 21:9 y 21:10 se simulan a 1920×823 y 1920×914.
- La barra de iconos que sale abajo en algunas capturas (4:3 y 16:10 en horizontal) es del
  emulador, no de la app.

## Flujo de descarga (lo más delicado)

1. `TaskCenter.download()`:
   - Comprueba el espacio libre, sumando el tamaño del comprimido si hay que extraerlo y el de la parte más grande si hay que copiarla.
   - Registra un `PendingSave` y pide la descarga a TDLib.
2. Al completarse todas las partes, `movePendingDownloads()` sigue uno de dos caminos:
   - **Extracción automática** (activada por defecto; se desactiva con `autoExtract`, en Ajustes → Descargas). Se aplica a .7z, .rar o archivos en partes. `extractAndInstall` extrae en `/TopPorts/<juego>`, mueve los OBB, lanza la instalación de los APK, marca `unpacked_` y borra el comprimido de la caché de Telegram.
   - **Resto** (incluido un `.zip` suelto, porque hay lanzadores de ports que lo cargan tal cual): `saveParts` lo mueve a `/TopPorts/<juego>`. Mueve con `renameTo` si está en el mismo almacenamiento y, si no, copia.
3. **Cancelar** (`cancelDownload` / `deleteDownload`): `CancelDownloadFile` + `DeleteFile` y quitar el pending.

**Arreglo clave de 7-Zip.** Las partes `.7z.001` se leen como un único flujo (`MultiFileInStream`).
A 7-Zip hay que darle el nombre **sin** `.001`: con `.001` elige el formato "Split" y solo junta
las partes en un `.7z`. Además, si una extracción por partes devuelve un único comprimido, se
extrae también y se borra.

## Convenciones

- Rama de trabajo: `claude/gallant-wozniak-duisak`. Antes de subir:
  `git pull --no-rebase origin claude/gallant-wozniak-duisak`.
- No abrir PRs salvo que se pida.
- Comentarios y textos de la UI en español, en el mismo tono que el resto del código.
- Si un cambio de UI se lee dentro de `Row`/`Column`, copiar antes `maxWidth`/`maxHeight` de
  `BoxWithConstraints` a variables locales: se compila mal si no.
- Con mando, el foco tiene que ser estable: un único botón principal por paquete que cambia de
  texto (Descargar → Pausar → Instalar…) en lugar de botones que aparecen y desaparecen.
- Regex con mayúsculas acentuadas: usar `(?iu)`, no `(?i)`.

## Pendiente de comprobar en dispositivos reales

Probado solo en CI y en el emulador. Falta que el usuario confirme:

1. Extracción automática de `.7z.001` + `.7z.002` sin que quede un `.7z` intermedio.
2. Juego de 8 partes de 4 GB: extracción completa y resumen al final.
3. Pixel: que muestre el mismo número de juegos que las consolas (~181, antes 142).
4. Posts nuevos del canal visibles al volver a la app, sin reiniciar.
5. Cancelar descargas, y volver a la ficha al salir de TAREAS.
6. Logo y ayuda de botones en el menú lateral de la Odin 2 Portal.

## Ideas descartadas por ahora (el usuario dijo que no, de momento)

- Notificación de juegos nuevos con la app cerrada.
- Etiqueta y filtro por idioma, a partir de la plantilla del post.
- Proteger la sesión de Telegram con contraseña: la base de datos de TDLib no va cifrada, y solo
  importa en móviles con root.

## Plantilla de post del canal

La app depende de que los posts sigan un formato parecido a este:

- Foto o álbum con las capturas.
- Título.
- Etiqueta de sistema (`#N64`, `#PC`…).
- Descripción y, después, estas líneas:
  - 📦 Versión
  - 🌐 Idioma
  - 📱 Requisitos
  - ⚙️ Instalación
- Archivos adjuntos con nombres coherentes entre versiones, para que funcionen los avisos de
  actualización.

Los posts "ACTUALIZACIÓN DE PORTS" generan el popup de novedades. Un APK `TopPorts*.apk` en el
canal activa la autoactualización de la app.
