# Contexto de mejoras v1.6 — Detección de modo desarrollador + herramientas root

> Complementa a `CONTEXT.md` (estado base v1.5). Léelo junto a él.
> Creado: 2026-09-08 (sesión en PC). Estado: **código implementado, SIN compilar todavía** — pendiente build y tests en Termux.
> Idioma de trabajo: español (es-419).

## Qué añade esta versión (1.6, versionCode 7)

1. **Detección de opciones de desarrollador y de opciones Bluetooth útiles** (pantalla principal):
   - Estado "Dev: ON/OFF/?" → lee `Settings.Secure.DEVELOPMENT_SETTINGS_ENABLED`.
   - Estado del registro **Bluetooth HCI snoop** → `Settings.Global`/`Settings.Secure` clave `bluetooth_hci_log`.
   - La app **no puede activar** el modo desarrollador por sí sola (necesita WRITE_SECURE_SETTINGS o acción manual); el diálogo guía al usuario y abre los ajustes de desarrollador (`Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS`). Con **root** sí puede activar/desactivar el snoop log (`settings put global bluetooth_hci_log 1|0`).

2. **Solicitud de acceso root desde la app + acciones solo-root**:
   - Botón "Root" en pantalla principal: detecta `su` sin disparar el prompt, solicita permiso (`su -c id` → diálogo Magisk/Superuser), y abre "Herramientas root".
   - Botón "Root tools (target)" en la pantalla de ataque: acciones `hcitool` sobre la MAC del objetivo.

## Archivos

**Nuevos:**
- `app/src/main/java/com/eikarna/bluetoothjammer/util/RootManager.kt`
- `app/src/main/java/com/eikarna/bluetoothjammer/util/DevOptions.kt`

**Modificados:**
- `MainActivity.kt` — fila de botones `btnDevOptions` / `btnRootStatus`, diálogos de dev y de root tools.
- `AttackActivity.kt` — botón `btnRootTools` + acciones root por objetivo; `FrameworkVersion = 1.6`.
- `activity_main.xml` / `attack_layout.xml` — fila nueva y botón nuevo.
- `app/src/main/res/values/strings.xml` (EN) y `values-es/strings.xml` (ES) — **60 strings nuevos por idioma** (181 = 181).
- `app/build.gradle.kts` — `versionName = "1.6"`, `versionCode = 7`.

## API de `util/RootManager.kt`

- `SuResult(exitCode, output, error)` con `.ok` y `.text` (output+error).
- `suPath(): String?` — localiza `su` (PATH + rutas típicas) **sin disparar el prompt** de root. Hilo IO.
- `requestRoot(timeoutMs = 90_000): SuResult` — ejecuta `su -c id`; **esto es lo que muestra el diálogo Magisk/Superuser**; bloquea hasta que el usuario responde (usa `Dispatchers.IO`).
- `suExec(command, timeoutMs = 30_000): SuResult` — ejecuta `su -c <comando>`; marca `granted = true` si exit 0.
- `bluetoothToolsInstalled(): List<String>` — `hcitool`, `hciconfig`, `hcidump`, `btmon` alcanzables (sin root).
- `granted: Boolean` — estado en memoria (se re-verifica al ejecutar).

## API de `util/DevOptions.kt`

- `developerOptionsEnabled(context): Boolean?` — null si no es legible.
- `hciSnoopEnabled(context): Boolean?` — prueba Global y luego Secure (varía por ROM).

## Flujos de UI

**MainActivity**
- Chip `Dev`: estado modo desarrollador; al pulsar → diálogo: líneas de estado (modo dev + snoop), guía según ON/OFF, botón "Abrir opciones de desarrollador". Si `RootManager.granted`: botón neutral Activar/Desactivar snoop HCI (con nota: reiniciar Bluetooth para que surta efecto).
- Chip `Root`: `—` (sin su) / `disp` (su presente, sin conceder) / `OK` (concedido). Al pulsar:
  - sin su → diálogo "Root not available";
  - sin conceder → toast "Requesting root…", `requestRoot()`, resultado (concedido → herramientas; denegado → diálogo con salida cruda);
  - concedido → diálogo "Root tools" con 6 acciones: radio (hcitool dev + hciconfig), enlaces propios (hcitool con), snoop ON/OFF (settings put), Bluetooth OFF (svc bluetooth disable, con confirmación), Bluetooth ON.
  - Cada acción muestra un diálogo con la **salida cruda del comando** + exit code (nada de fingir éxito; las denegaciones SELinux se ven tal cual).

**AttackActivity**
- Botón `Root tools (target)` bajo Start/Stop. Pide root si falta; luego lista de acciones sobre las MAC del/los objetivo(s): RSSI/LQ (`hcitool rssi`+`lq` por MAC), `hcitool con`, `hcitool dev`, y **`hcitool dc`** (con confirmación explícita: solo corta enlaces ACL del propio teléfono; si se está emitiendo A2DP desde el propio teléfono hacia el objetivo, se corta — efecto real documentado en CONTEXT.md).

## Límites honestos que respetar (no cambiar)

- Sin root: la app no puede activar opciones de desarrollador; solo informar + deep-link.
- Con root: SELinux `enforcing` en ROMs stock puede denegar el acceso HCI aunque haya `su` — mostrar la salida real.
- `hcitool dc`/`rssi`/`lq` solo afectan a enlaces del **propio** adaptador.
- Los binarios bluez (`hcitool`…) no vienen en todas las ROMs (avisar con `bluetoothToolsInstalled`).
- Todo texto nuevo de UI va SIEMPRE a `values/strings.xml` (EN) **y** `values-es/strings.xml` (ES); nunca literales hardcodeados.

## Pendiente (hacer en Termux)

1. Compilar `sh gradlew :app:assembleDebug` (ver prompt.md para el entorno exacto).
2. Tests `sh gradlew :app:testDebugUnitTest` — se esperan **22 verdes** (el código nuevo no añade tests: no hay `su` en el runner).
3. Copiar APK a `~/storage/downloads/` y verificar (preferencia global).
4. Probar en el teléfono: chips Dev/Root, solicitud de root (requiere teléfono rooteado con Magisk/Superuser), herramientas root con un altavoz propio emparejado/conectado.
5. Opcional/pendiente de decisión: actualizar `README.md` con sección v1.6 y hacer commit.
