# Top Ports

## Qué hace

- **Inicio de sesión con Telegram** (TDLib): cada usuario entra con su propia cuenta, así
  que las descargas van directas desde Telegram, sin servidor intermedio y con archivos de
  hasta 4 GB.
- **Catálogo**: agrupa los posts del canal (foto o álbum + descripción + archivos) en fichas
  con buscador. Se actualiza solo cuando llegan posts nuevos.
- **Descargas** de 2–4 GB en segundo plano, con pausa y reanudación, comprobación de espacio
  y notificación de progreso. Al terminar, cada archivo pasa solo a `/TopPorts/<juego>`.
- **Extractor integrado** (motor 7-Zip): ZIP, 7z, RAR4/RAR5, TAR… incluidos los
  multivolumen (`.7z.001`, `.zip.001`, `.part1.rar`) y los archivos con contraseña.
- **Instalador**: APK sueltos, APK partidos (`.apk.001`), split APKs y XAPK/APKS. Los OBB se
  copian a `Android/obb/<paquete>`.
- **Explorador de archivos** al estilo CX: navegar por todo el almacenamiento, copiar,
  cortar, pegar, renombrar, borrar, crear carpetas, ver el contenido de un comprimido,
  extraer e instalar.
- **Instalados**: los juegos instalados se marcan en la tienda y tienen su propia pestaña
  para abrirlos, desinstalarlos y borrar sus datos (OBB, `Android/data`, lo extraído y las
  descargas de Telegram).

## Privacidad

- Top Ports no tiene servidores propios y no recopila ningún dato: sin estadísticas, sin
  anuncios y sin rastreadores.
- El inicio de sesión usa la librería oficial de Telegram (TDLib). El número de teléfono, el
  código y la contraseña en dos pasos viajan cifrados directamente a los servidores de
  Telegram. No pasan por nadie más: ni el autor de la app ni los administradores del canal los
  ven.
- La sesión se guarda solo en el dispositivo, en la carpeta privada de la app. La app solo lee
  el canal de Top Ports: no lee los chats ni envía mensajes.
- La sesión aparece en Telegram → Ajustes → Dispositivos y se puede cerrar desde ahí en
  cualquier momento (o desde Ajustes → Cuenta → Cerrar sesión en la app).
- Los juegos se descargan directamente de Telegram al dispositivo.
- Como con cualquier app de Telegram, conviene instalar Top Ports solo desde el canal
  oficial: una copia modificada sí podría acceder a la cuenta.

## Aviso legal

- Top Ports es una app no oficial que usa la API de Telegram. No está afiliada a Telegram,
  Google ni a ninguna de las empresas o marcas de los juegos que aparecen en el canal.
- La app no aloja ningún archivo: solo muestra y descarga lo que se publica en el canal de
  Telegram. Los nombres, carátulas y marcas pertenecen a sus respectivos dueños.
- Cada usuario es responsable de lo que descarga e instala. Descarga solo contenido que tengas
  derecho a usar; si un port necesita los archivos de un juego, usa los de tu copia original.
- Si eres titular de derechos y crees que algún contenido del canal los infringe, escribe con
  el enlace a la publicación y se retirará (contacto en Ajustes → Aviso legal).

## Cómo conseguir el APK

El workflow de GitHub Actions (`.github/workflows/android.yml`) compila todo:

1. Crea una app de Telegram en <https://my.telegram.org> → *API development tools* y apunta
   el `api_id` y el `api_hash`.
2. En GitHub: **Settings → Secrets and variables → Actions → New repository secret**:
   - `TELEGRAM_API_ID`
   - `TELEGRAM_API_HASH`
3. Opcional, como secreto o como variable (vale cualquiera de las dos pestañas):
   - `TOPPORTS_CHANNEL_USERNAME`: el @usuario del canal, si es público.
   - `TOPPORTS_CHANNEL_INVITE_LINK`: el enlace de invitación (`https://t.me/+…`), si es privado.
     Con él la app puede unir al usuario al canal automáticamente.
   - `TOPPORTS_LEGAL_CONTACT`: contacto para reclamaciones de derechos (`@usuario` de Telegram,
     email o enlace). Si no se pone, el botón de Ajustes → Aviso legal abre el canal.
4. Clave de firma (para que cada APK nuevo se instale encima del anterior sin perder datos):
   - `SIGNING_KEYSTORE_BASE64`: el archivo `.p12` de la clave en base64.
   - `SIGNING_STORE_PASSWORD`: su contraseña.

   Guarda una copia del `.p12` y de la contraseña: si se pierden, la siguiente versión
   tendrá que instalarse desinstalando la anterior. Para crear una clave nueva:
   `keytool -genkeypair -storetype PKCS12 -keystore topports.p12 -alias topports -keyalg RSA -keysize 4096 -validity 10000`
   y luego `base64 -w0 topports.p12`.
5. Lanza el workflow (pestaña **Actions → Android → Run workflow**) y descarga el artefacto
   **TopPorts-0.1.N** (dentro va `TopPorts-0.1.N.apk`).

La primera ejecución compila TDLib desde el código fuente y tarda bastante (1–3 h). Después
queda en caché y cada build tarda unos minutos.

> El canal configurado por defecto es `4399047221` (chat `-1004399047221`). Se cambia con la
> variable `TOPPORTS_CHANNEL_ID`.

## Compilar en local

```bash
# 1. TDLib (una sola vez; necesita gperf, php, perl, cmake, JDK 17)
./scripts/build-tdlib.sh          # o descarga el artefacto "tdlib" del workflow en ./tdlib

# 2. Credenciales en local.properties
echo "TELEGRAM_API_ID=123456" >> local.properties
echo "TELEGRAM_API_HASH=abcdef0123456789" >> local.properties

# 3. APK
./gradlew :app:assembleRelease
```

## Permisos que pide la app

| Permiso | Para qué |
|---|---|
| Instalar apps desconocidas | Instalar los APK de los ports |
| Acceso a todos los archivos | Explorador, extraer en `/TopPorts` y copiar OBB |
| Notificaciones | Ver el progreso de descargas y extracciones |

## Estructura

```
core/   Lógica pura en Kotlin (agrupar posts, volúmenes, plan de instalación) con tests
app/    App Android (Jetpack Compose)
  telegram/  Cliente TDLib y lectura del canal
  archive/   Extractor 7-Zip (multivolumen y contraseña)
  install/   Instalador de APK con PackageInstaller
  work/      Descargas y tareas largas + servicio en primer plano
  ui/        Pantallas pixel art: tienda, explorador y tareas
scripts/build-tdlib.sh   Compila TDLib para Android
```

## Créditos

- [TDLib](https://github.com/tdlib/td) (Boost Software License)
- [7-Zip-JBinding-4Android](https://github.com/omicronapps/7-Zip-JBinding-4Android) (LGPL, con la restricción unRAR para RAR)
- Fuentes [Press Start 2P](https://fonts.google.com/specimen/Press+Start+2P) y
  [VT323](https://fonts.google.com/specimen/VT323) (SIL OFL, ver `licenses/`)
- Jetpack Compose, AndroidX, Media3, Coil y kotlinx.coroutines (Apache 2.0)
- El robot de Android del logo se reproduce o modifica a partir de trabajo creado y compartido
  por Google y se usa según la licencia Creative Commons 3.0 Attribution
