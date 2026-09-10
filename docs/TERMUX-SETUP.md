# Local Android build setup with Termux

**English** · [Español (anexo)](#anexo-en-español)

This guide covers everything needed to **build and publish this repository's APK from the phone itself**, with no computer: Termux installation, toolchain packages, the Android SDK, the ARM64 `aapt2` override and the `gh` workflow.

Every command below was executed on a real ARM64 device running Termux; the versions and the build result are recorded in [Verified environment](#11-verified-environment). Nothing here is theoretical.

- Termux on F-Droid: <https://f-droid.org/packages/com.termux/>
- F-Droid client: <https://f-droid.org/>
- Android command line tools: <https://developer.android.com/studio> ("Command line tools only")
- This project's releases: <https://github.com/manuti/BluetoothJammer/releases>

---

## 1. Install Termux from F-Droid

Termux is a terminal emulator and Linux environment for Android; it requires **Android 7.0 or newer**.

1. Install the **F-Droid** client from <https://f-droid.org/>, or open the package page directly: <https://f-droid.org/packages/com.termux/>.
2. Install **Termux** (stable, `0.118.x` at the time of writing) and let the first run finish its bootstrap.
3. Open Termux and confirm you have a shell prompt.

**Do not install Termux from Google Play.** That build is deprecated and no longer updated. The supported sources are F-Droid and the project's own releases (<https://github.com/termux/termux-app/releases>).

**Never mix sources.** The F-Droid build and the GitHub build are signed with different keys, so one cannot update over the other: Android rejects the update with a signature mismatch. Pick one source and stay on it.

---

## 2. First steps inside Termux

```bash
pkg update && pkg upgrade
termux-setup-storage
```

- `termux-setup-storage` requests the storage permission and creates `~/storage` with symlinks into shared storage (`~/storage/downloads` → `/storage/emulated/0/Download`). It is what lets you deliver the built APK to Android's Downloads folder.
- If package downloads fail or crawl, run `termux-change-repo` and pick another mirror.

---

## 3. Install the toolchain packages

```bash
pkg install git openjdk-17 gradle aapt2 gh openssl unzip wget curl
```

- `aapt2` is Termux's **native aarch64** build — required on ARM64 (section 6).
- `unzip` is needed for the SDK command-line tools archive; `openssl` for key material.
- The `gradle` package is optional: this project uses the **wrapper** (8.7), which downloads its own distribution on first run.

---

## 4. Java: set `JAVA_HOME`

On a bare Termux, running the wrapper fails even though `java` is on `PATH`:

```text
$ sh gradlew :app:assembleDebug
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
```

**Why:** the `gradlew` launcher validates the Java command with `which java`, and `which` is **not installed** by default in Termux. The check fails before it ever tries `java` itself.

Two valid fixes:

```bash
# Option A (used here): point JAVA_HOME at the JDK
export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk
java -version    # openjdk 17.x

# Option B: install the missing utility and leave JAVA_HOME unset
pkg install which
```

To persist option A across sessions:

```bash
echo 'export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk' >> ~/.bashrc
```

Notes:

- `/data/data/com.termux/files/usr` is `$PREFIX`, so `$PREFIX/lib/jvm/java-17-openjdk` is the same path.
- This project pins **JDK 17**. Termux also ships JDK 21 (`$PREFIX/lib/jvm/java-21-openjdk`, the one the plain `java` command points to), and the build succeeds with it too, but JDK 17 is what the README specifies.

---

## 5. Android SDK (command-line tools)

```bash
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip
unzip -q cmdline-tools.zip
mv cmdline-tools latest
```

`11076708` is the build this device used. Grab the current link from <https://developer.android.com/studio> → "Command line tools only"; the resulting directory layout must be `~/android-sdk/cmdline-tools/latest/bin/`.

### Running `sdkmanager` in Termux

The shipped launcher starts with `#!/usr/bin/env sh`, and Termux has no `/usr/bin/env`, so invoking it directly fails with a confusing error even though the file exists:

```text
$ ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --version
No such file or directory
```

**Run it through the shell** (`JAVA_HOME` must be set — it is a Java program):

```bash
sh ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --version          # 12.0 here
sh ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --list_installed
```

### Install the components this project needs

```bash
yes | sh ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses
sh ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --install "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

Verified installed set on this device:

- `build-tools;34.0.0` and `build-tools;36.0.0`
- `platform-tools` 37.0.1
- `platforms;android-34` (rev 3) and `platforms;android-36`

`sdkmanager` prints a warning — *"This version only understands SDK XML versions up to 3 but an SDK XML file of version 4 was encountered"* — it is harmless and does not prevent installing packages.

---

## 6. ARM64 fix: `aapt2` override

AGP 8.6 resolves an `aapt2` binary compiled **only for x86-64**, which cannot run on an ARM64 phone; the resource-merging step fails. Use Termux's native build instead:

```bash
pkg install aapt2
```

and add to `~/.gradle/gradle.properties`:

```properties
android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
```

Confirm the path exists with `ls -l $PREFIX/bin/aapt2` (`$PREFIX` = `/data/data/com.termux/files/usr`).

---

## 7. Get the code and point Gradle at the SDK

```bash
cd ~
git clone https://github.com/manuti/BluetoothJammer.git
cd BluetoothJammer
printf 'sdk.dir=%s/android-sdk\n' "$HOME" > local.properties
```

`local.properties` is **gitignored** (it never enters the repository) and is how Gradle locates the SDK installed in section 5.

---

## 8. Build and test

Like `sdkmanager`, the `gradlew` script uses a `#!/usr/bin/env sh` shebang that does not resolve in Termux — **run it as `sh gradlew`**, never as `./gradlew`.

```bash
export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk
cd ~/BluetoothJammer

sh gradlew :app:assembleDebug        # builds the debug APK
sh gradlew :app:testDebugUnitTest    # runs the 22 unit tests
```

Outputs:

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Test report: `app/build/reports/tests/testDebugUnitTest/index.html`

First build downloads the Gradle 8.7 distribution and all dependencies, so it can take many minutes; later builds are much faster.

### Hand the APK to Android

```bash
cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/<name>-v<version>.apk
ls -la ~/storage/downloads/            # verify the copy before calling it done
```

Name the file after the version you are publishing (for example `btjam-v1.6.apk`) — release assets in this project are uploaded already renamed. Then open it from the file manager and allow "install unknown apps".

### If the build is killed by low memory

`gradle.properties` sets `org.gradle.jvmargs=-Xmx2048m`. On a device with little free RAM, lower that value and/or build without the Gradle daemon (`--no-daemon`) so the JVM does not compete with the system.

---

## 9. `gh` — GitHub from the phone

```bash
pkg install gh
gh auth login      # GitHub.com → HTTPS → authenticate with the browser/device code
gh auth status
gh auth setup-git  # registers gh as git's credential helper for https://github.com
```

After `gh auth setup-git`, `git clone`/`git push` over HTTPS reuse the `gh` token: no SSH keys or password prompts.

Typical commands for this repository:

```bash
gh repo view manuti/BluetoothJammer --web     # open the fork
gh release create v1.6 app-v1.6.apk --title "v1.6" --notes "Release notes"
gh issue list                                 # triage
gh pr create --fill                           # propose changes
```

Tip: `gh release create` keeps the file name you pass — the `file#newname` form does not rename the asset — so rename the APK **before** uploading if the asset must carry the version.

---

## 10. Troubleshooting

- **`./gradlew: Permission denied` or `bad interpreter: /usr/bin/env: No such file or directory`** — Termux has no `/usr/bin/env`. Run `sh gradlew ...` instead.
- **`ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.`** — `gradlew` validates Java with `which`, which a bare Termux lacks. Set `JAVA_HOME` (section 4) or `pkg install which`.
- **`sdkmanager: No such file or directory` while the file clearly exists** — same shebang problem. Run `sh ~/android-sdk/cmdline-tools/latest/bin/sdkmanager ...`.
- **`aapt2` fails during `mergeDebugResources` / cannot execute** — the x86-64 `aapt2` downloaded by AGP is running on ARM64. Apply the override in section 6.
- **`sdkmanager` warns about "SDK XML versions up to 3 ... version 4"** — harmless; package installation still works.
- **Slow or failing `pkg` downloads** — `termux-change-repo` to switch mirror.
- **Build killed (OOM)** — reduce `org.gradle.jvmargs` in `gradle.properties`, or add `--no-daemon`.

---

## 11. Verified environment

Recorded on the machine where this guide was written (2026-09-10), Android aarch64, Termux from F-Droid:

- **Termux packages**: `git` 2.55.0, `gh` 2.100.0, `openjdk-17` 17.0.20, `openjdk-21` 21.0.12, `aapt2` 16.0.0.4-2, `gradle` 9.7.1 (system, unused — the wrapper rules), `openssl` 3.6.3, `wget` 1.25.0-1, `curl` 8.22.0, `unzip` 6.0-10.
- **Android SDK** in `~/android-sdk`: `cmdline-tools` 12.0 (archive build 11076708), `build-tools` 34.0.0 / 36.0.0, `platform-tools` 37.0.1, `platforms` android-34 (rev 3) / android-36.
- **Project toolchain**: Gradle wrapper 8.7, AGP 8.6.0, Kotlin 1.9.0, `compileSdk` 34, `minSdk` 24, `targetSdk` 34.
- **Verification run**: `sh gradlew clean :app:assembleDebug :app:testDebugUnitTest` with `JAVA_HOME=$PREFIX/lib/jvm/java-17-openjdk` → **BUILD SUCCESSFUL in 3m 30s**, 22 tests / 0 failures, APK `com.eikarna.bluetoothjammer` 1.6 (versionCode 7).

---
---

# Anexo en español

[English](#local-android-build-setup-with-termux) · **Español**

Esta guía cubre todo lo necesario para **compilar y publicar el APK de este repositorio desde el propio teléfono**, sin ordenador: instalación de Termux, paquetes de la cadena de compilación, el SDK de Android, el reemplazo de `aapt2` para ARM64 y el flujo de trabajo con `gh`.

Todos los comandos de abajo se han ejecutado en un dispositivo ARM64 real con Termux; las versiones y el resultado de la compilación están registrados en [Entorno verificado](#11-entorno-verificado). Nada de esto es teórico.

- Termux en F-Droid: <https://f-droid.org/packages/com.termux/>
- Cliente de F-Droid: <https://f-droid.org/>
- Herramientas de línea de comandos de Android: <https://developer.android.com/studio> ("Command line tools only")
- Releases de este proyecto: <https://github.com/manuti/BluetoothJammer/releases>

---

## 1. Instalar Termux desde F-Droid

Termux es un emulador de terminal y entorno Linux para Android; requiere **Android 7.0 o superior**.

1. Instala el cliente de **F-Droid** desde <https://f-droid.org/>, o abre directamente la página del paquete: <https://f-droid.org/packages/com.termux/>.
2. Instala **Termux** (estable, `0.118.x` en el momento de escribir esto) y deja que la primera ejecución complete su bootstrap.
3. Abre Termux y comprueba que tienes un intérprete de comandos.

**No instales Termux desde Google Play.** Esa versión está descatalogada y ya no se actualiza. Las fuentes soportadas son F-Droid y los releases del propio proyecto (<https://github.com/termux/termux-app/releases>).

**No mezcles fuentes.** La versión de F-Droid y la de GitHub están firmadas con claves distintas, así que una no puede actualizarse sobre la otra: Android rechaza la actualización con un error de firma. Elige una fuente y quédate con ella.

---

## 2. Primeros pasos dentro de Termux

```bash
pkg update && pkg upgrade
termux-setup-storage
```

- `termux-setup-storage` solicita el permiso de almacenamiento y crea `~/storage` con enlaces simbólicos al almacenamiento compartido (`~/storage/downloads` → `/storage/emulated/0/Download`). Es lo que permite entregar el APK compilado a la carpeta Descargas de Android.
- Si las descargas de paquetes fallan o van muy lentas, ejecuta `termux-change-repo` y elige otro espejo.

---

## 3. Instalar los paquetes de la cadena de compilación

```bash
pkg install git openjdk-17 gradle aapt2 gh openssl unzip wget curl
```

- `aapt2` es la versión **nativa aarch64** de Termux, obligatoria en ARM64 (sección 6).
- `unzip` hace falta para el archivo de las herramientas de línea de comandos del SDK; `openssl` para material de claves.
- El paquete `gradle` es opcional: este proyecto usa el **wrapper** (8.7), que descarga su propia distribución en la primera ejecución.

---

## 4. Java: configurar `JAVA_HOME`

En un Termux recién instalado, el wrapper falla aunque `java` esté en el `PATH`:

```text
$ sh gradlew :app:assembleDebug
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
```

**Por qué:** el lanzador `gradlew` valida el comando de Java con `which java`, y `which` **no está instalado** por defecto en Termux. La comprobación falla antes de intentar usar `java`.

Dos soluciones válidas:

```bash
# Opción A (la usada aquí): apuntar JAVA_HOME al JDK
export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk
java -version    # openjdk 17.x

# Opción B: instalar la utilidad que falta y dejar JAVA_HOME sin definir
pkg install which
```

Para que la opción A persista entre sesiones:

```bash
echo 'export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk' >> ~/.bashrc
```

Notas:

- `/data/data/com.termux/files/usr` es `$PREFIX`, así que `$PREFIX/lib/jvm/java-17-openjdk` es la misma ruta.
- Este proyecto fija **JDK 17**. Termux también distribuye JDK 21 (`$PREFIX/lib/jvm/java-21-openjdk`, al que apunta el comando `java` sin más) y la compilación también funciona con él, pero el README especifica JDK 17.

---

## 5. SDK de Android (herramientas de línea de comandos)

```bash
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip
unzip -q cmdline-tools.zip
mv cmdline-tools latest
```

`11076708` es la compilación que usó este dispositivo. Consigue el enlace actual en <https://developer.android.com/studio> → "Command line tools only"; la estructura de directorios resultante debe ser `~/android-sdk/cmdline-tools/latest/bin/`.

### Ejecutar `sdkmanager` en Termux

El lanzador que viene en el paquete empieza con `#!/usr/bin/env sh`, y Termux no tiene `/usr/bin/env`, así que invocarlo directamente falla con un error confuso aunque el archivo exista:

```text
$ ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --version
No such file or directory
```

**Ejecútalo a través del shell** (`JAVA_HOME` debe estar definido: es un programa Java):

```bash
sh ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --version          # 12.0 aquí
sh ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --list_installed
```

### Instalar los componentes que necesita este proyecto

```bash
yes | sh ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses
sh ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --install "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

Conjunto instalado y verificado en este dispositivo:

- `build-tools;34.0.0` y `build-tools;36.0.0`
- `platform-tools` 37.0.1
- `platforms;android-34` (rev 3) y `platforms;android-36`

`sdkmanager` muestra un aviso — *"This version only understands SDK XML versions up to 3 but an SDK XML file of version 4 was encountered"* — es inocuo y no impide instalar paquetes.

---

## 6. Arreglo para ARM64: reemplazo de `aapt2`

AGP 8.6 resuelve un binario `aapt2` compilado **solo para x86-64**, que no puede ejecutarse en un teléfono ARM64; el paso de fusión de recursos falla. Usa en su lugar la versión nativa de Termux:

```bash
pkg install aapt2
```

y añade en `~/.gradle/gradle.properties`:

```properties
android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
```

Comprueba que la ruta existe con `ls -l $PREFIX/bin/aapt2` (`$PREFIX` = `/data/data/com.termux/files/usr`).

---

## 7. Obtener el código y apuntar Gradle al SDK

```bash
cd ~
git clone https://github.com/manuti/BluetoothJammer.git
cd BluetoothJammer
printf 'sdk.dir=%s/android-sdk\n' "$HOME" > local.properties
```

`local.properties` está **en .gitignore** (nunca entra en el repositorio) y es como Gradle localiza el SDK instalado en la sección 5.

---

## 8. Compilar y probar

Igual que `sdkmanager`, el script `gradlew` usa un shebang `#!/usr/bin/env sh` que no se resuelve en Termux: **ejecútalo como `sh gradlew`**, nunca como `./gradlew`.

```bash
export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk
cd ~/BluetoothJammer

sh gradlew :app:assembleDebug        # compila el APK de depuración
sh gradlew :app:testDebugUnitTest    # ejecuta los 22 tests unitarios
```

Salidas:

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Informe de tests: `app/build/reports/tests/testDebugUnitTest/index.html`

La primera compilación descarga la distribución de Gradle 8.7 y todas las dependencias, así que puede tardar bastantes minutos; las siguientes son mucho más rápidas.

### Entregar el APK a Android

```bash
cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/<nombre>-v<version>.apk
ls -la ~/storage/downloads/            # verifica la copia antes de darla por cerrada
```

Nombra el archivo según la versión que estés publicando (por ejemplo `btjam-v1.6.apk`), porque los assets de release de este proyecto se suben ya renombrados. Después ábrelo desde el gestor de archivos y permite "instalar aplicaciones desconocidas".

### Si el proceso muere por falta de memoria

`gradle.properties` fija `org.gradle.jvmargs=-Xmx2048m`. En un dispositivo con poca memoria libre, baja ese valor y/o compila sin el demonio de Gradle (`--no-daemon`) para que la JVM no compita con el sistema.

---

## 9. `gh` — GitHub desde el teléfono

```bash
pkg install gh
gh auth login      # GitHub.com → HTTPS → autentícate con el código de navegador/dispositivo
gh auth status
gh auth setup-git  # registra gh como helper de credenciales de git para https://github.com
```

Tras `gh auth setup-git`, `git clone`/`git push` por HTTPS reutilizan el token de `gh`: sin claves SSH ni peticiones de contraseña.

Comandos típicos en este repositorio:

```bash
gh repo view manuti/BluetoothJammer --web     # abrir el fork
gh release create v1.6 app-v1.6.apk --title "v1.6" --notes "Notas de la versión"
gh issue list                                 # triaje
gh pr create --fill                           # proponer cambios
```

Consejo: `gh release create` conserva el nombre del archivo que le pasas — la forma `archivo#nuevonombre` no renombra el asset — así que renombra el APK **antes** de subirlo si el asset debe llevar la versión.

---

## 10. Resolución de problemas

- **`./gradlew: Permission denied` o `bad interpreter: /usr/bin/env: No such file or directory`** — Termux no tiene `/usr/bin/env`. Ejecuta `sh gradlew ...`.
- **`ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.`** — `gradlew` valida Java con `which`, que un Termux recién instalado no tiene. Define `JAVA_HOME` (sección 4) o instala `which`.
- **`sdkmanager: No such file or directory` aunque el archivo exista** — el mismo problema de shebang. Ejecuta `sh ~/android-sdk/cmdline-tools/latest/bin/sdkmanager ...`.
- **`aapt2` falla durante `mergeDebugResources` / no puede ejecutarse** — el `aapt2` x86-64 que descarga AGP está corriendo en ARM64. Aplica el reemplazo de la sección 6.
- **`sdkmanager` avisa de "SDK XML versions up to 3 ... version 4"** — inocuo; la instalación de paquetes sigue funcionando.
- **Descargas de `pkg` lentas o fallidas** — `termux-change-repo` para cambiar de espejo.
- **Compilación matada (OOM)** — reduce `org.gradle.jvmargs` en `gradle.properties`, o añade `--no-daemon`.

---

## 11. Entorno verificado

Registrado en la máquina donde se escribió esta guía (2026-09-10), Android aarch64, Termux desde F-Droid:

- **Paquetes de Termux**: `git` 2.55.0, `gh` 2.100.0, `openjdk-17` 17.0.20, `openjdk-21` 21.0.12, `aapt2` 16.0.0.4-2, `gradle` 9.7.1 (del sistema, sin usar: manda el wrapper), `openssl` 3.6.3, `wget` 1.25.0-1, `curl` 8.22.0, `unzip` 6.0-10.
- **SDK de Android** en `~/android-sdk`: `cmdline-tools` 12.0 (archivo de compilación 11076708), `build-tools` 34.0.0 / 36.0.0, `platform-tools` 37.0.1, `platforms` android-34 (rev 3) / android-36.
- **Cadena del proyecto**: Gradle wrapper 8.7, AGP 8.6.0, Kotlin 1.9.0, `compileSdk` 34, `minSdk` 24, `targetSdk` 34.
- **Ejecución de verificación**: `sh gradlew clean :app:assembleDebug :app:testDebugUnitTest` con `JAVA_HOME=$PREFIX/lib/jvm/java-17-openjdk` → **BUILD SUCCESSFUL en 3m 30s**, 22 tests / 0 fallos, APK `com.eikarna.bluetoothjammer` 1.6 (versionCode 7).
