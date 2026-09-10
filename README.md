# BluetoothJammer — Improved Edition (v1.6)

**English** · [Español](README-es.md)

> **Research status — cycle closed (2026).** Modern Android releases implement Bluetooth security end to end: user-consented pairing/bonding, encryption, hidden-API restrictions, SELinux policies and a continuously patched stack. After testing this project on stock and rooted devices, the conclusion is honest and definitive: **there is no practical margin to use Bluetooth as an attack vector on current Android**, and RF jamming is impossible with phone hardware (it needs SDR). This closes the jamming investigation. The repository is kept as an **educational tool for protocol-level probes and measurements on your own devices**.

Educational **Bluetooth security research** tool for Android (Kotlin / Material 3). **Bilingual: Spanish/English** — the app detects the system language automatically (Spanish → Spanish UI; any other language → English UI).

Improved version of the original [eikarna/BluetoothJammer](https://github.com/eikarna/BluetoothJammer) project, with real nearby-device discovery, speaker classification, multiple selectable attack techniques and a coroutine-based engine.

---

## ⚠️ LEGAL DISCLAIMER

> **THIS APPLICATION IS FOR EDUCATIONAL AND RESEARCH PURPOSES ONLY.**
>
> - Use it **ONLY with devices you own** and inside your own environment.
> - **Interfering with, attacking or degrading devices that do not belong to you is illegal** in most jurisdictions (FCC in the US, European regulations, local law, etc.) and may constitute a crime.
> - The app includes a **mandatory notice on first launch** and reminders whenever an attack is started.
> - The developer, contributors and maintainers **are not responsible for misuse** of this tool. Correct or incorrect use is the sole responsibility of the end user.
> - This tool is **not** an RF jammer: a phone cannot emit arbitrary RF. It implements denial-of-service techniques at the Bluetooth protocol level (L2CAP/RFCOMM, GATT, SDP, BLE advertising) within the limits of the Android SDK.
> - This version is **EXCLUSIVELY for experimental, educational and private use**. It is not intended for use outside a controlled research environment.

---

## 📜 License

This fork is published under the **MIT license** (see [LICENSE](LICENSE)), with one important legal caveat:

- The original repository ([eikarna/BluetoothJammer](https://github.com/eikarna/BluetoothJammer)) is **unlicensed**, so its author retains all rights over the original code.
- The MIT license covers **only the modifications and new code** contributed by this fork (manuti), as detailed in the LICENSE file itself.
- The original author has not granted explicit permission for this publication; this fork intends to contribute to the educational study of Bluetooth security with clear attribution to the original work.

**Legal responsibilities:** this software is provided "AS IS", without warranties of any kind. The author of this fork **is not responsible** for any damage, loss or legal consequence derived from misuse. It is the end user's sole responsibility to know and respect local law (FCC, European regulations, etc.) and to use the application only with their own devices and for educational/private purposes.

---

## Version history

Milestones of this fork (APK releases: [v1.4, v1.5, v1.6](https://github.com/manuti/BluetoothJammer/releases)):

- **Base version** (v1.0 → v1.4) — real discovery + speaker classifier, 8 attack techniques, multi-target/AttackManager, TX/Sleep duty cycle, MIT license.
- **i18n version** (v1.5) — fully bilingual ES/EN UI driven by the system locale (no hardcoded strings).
- **Dev Options version** (v1.6) — developer-options detector + HCI snoop state on the main screen.
- **Root version** (v1.6) — root request (Magisk-style `su`), bluez CLI tools (`hcitool`/`hciconfig`) with raw output, own-link actions. The bluez-utils add-on is published separately (see below).

### v1.6 highlights

- **Developer-mode detection and HCI snoop state** (main screen): `Dev` chip reads `Settings.Secure.DEVELOPMENT_SETTINGS_ENABLED` and the `bluetooth_hci_log` key (`Settings.Global`/`Secure`). The app **cannot enable** developer mode by itself — it informs, guides and opens the developer settings (`Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS`). With root it can toggle the snoop log (`settings put global bluetooth_hci_log 1|0`).
- **Root request from the app** (`Root` chip): detects `su` **without triggering the prompt** of the root manager (`suPath()` only locates the binary), and on tap runs `su -c id` (`requestRoot()`), which shows the Magisk/Superuser dialog. Visible state: `—` (no su) / `disp` (su present, not granted) / `OK` (granted).
- **Root tools** (main screen, with root granted): `hcitool dev` + `hciconfig`, `hcitool con` (own links), HCI snoop ON/OFF and the `svc bluetooth disable/enable` kill switch (with confirmation). Every action shows the **raw command output** + exit code — SELinux denials are shown as they are, never faked as success.
- **Per-target root tools** (attack screen): `hcitool` actions against the target MAC(s): `rssi`, `lq`, `con`, `dev` and **`hcitool dc`** (with explicit confirmation: it only disconnects ACL links of THIS phone — if A2DP is streamed from this phone to the target, playback really stops).
- **bluez binary detection**: `bluetoothToolsInstalled()` reports whether `hcitool`/`hciconfig`/`hcidump`/`btmon` are available on the ROM (not present on all).
- New `util/RootManager.kt` (suPath/requestRoot/suExec/SuResult) and `util/DevOptions.kt` (Settings reads). `FrameworkVersion = 1.6`, versionCode 7.

**Respected limits** (see "Expected effects"): without root the app cannot enable developer options (it only informs + deep-links); with root, `enforcing` SELinux on stock ROMs may deny HCI access even with `su` (the UI shows the real output); `hcitool dc`/`rssi`/`lq` only affect links of the **own** adapter.

### v1.5 highlights

- **Bilingual Spanish/English app**: every UI text (layouts, dialogs, toasts, spinners, attack log and classifier labels) moved to localized resources. Android detects the system language automatically: **Spanish → Spanish UI**, any other language → **English UI**. No manual selector or restart needed.
- Attack names, payload patterns and classifier labels moved from code literals to language resources (engine logic unchanged).
- `SpeakerClassifier` and `BluetoothDeviceInfo` refactor: visible labels (device type, speaker-classification reason) are now resource IDs; unknown device names are resolved in the UI ("Unknown"/"Desconocido").

### v1.4 highlights

- **RFCOMM Channel Flood**: sweeps RFCOMM channels 1-30 using the hidden API `createInsecureRfcommSocket(int)` via reflection (classic SPP technique; may be blocked by *hidden API enforcement* on Android 9+ — logged).
- **Bombardment mode**: fast connect → send burst → close cycles (instead of holding the socket) to exhaust resources through cycle saturation.
- **Configurable payload size** (bytes): 0 = automatic (maxTransmitPacketSize or 600), e.g. 990 B.
- **Wider combo**: now combines L2CAP + **RFCOMM channel** + GATT + Pairing + SDP (5 layers).
- Ideas adopted from [hackeringtrue/bluetooth2jam](https://github.com/hackeringtrue/bluetooth2jam) (channel sweep, bombardment, 990 B payload).

### v1.3 highlights

- **Burst/pause cycle (TX/Sleep) with jitter**: runs attacks in bursts (e.g. 10 s on / 5 s off) with pseudo-random timers — concept ported from the **PortaPack Mayhem Jammer TX** (SDR firmware).
- **Payload pattern selector** (analogous to PortaPack signal types): random noise, fixed pattern (A-Z), sawtooth (0-255) or wavy (chirp).
- **Session summary**: on stop, the log prints elapsed time, CONN/DATA/RETRY events and the number of targets.

### v1.2 highlights

- **Centralized AttackManager**: per-target attack registry and **global stop** (pattern ported from the [PIXELQUADRO07/BluetoothJammer](https://github.com/PIXELQUADRO07/BluetoothJammer) fork). Stops everything even if the Activity died.
- **Multi-target**: long-press a device to add it to the selection and tap **Attack (N)** to launch the same attack type against several targets at once.
- **Category-structured logs**: `[THREAD] [CONN] [DATA] [RETRY] [PAIR] [GATT] [SDP] [ADV] [SPOOF] [COMBO]` — lets you measure connection/send/retry rates in multi-target sessions.
- **Manager tests**: `AttackManagerTest` (4 cases) covers multi-target registration and global stop.

### v1.1 highlights

- **7 selectable attack types** (previously 5): added **Profile Spoofing** and **layered Combo**.
- **Layered attack (Combo)**: simultaneous L2CAP + GATT + Pairing + SDP on the same target with a single Start/Stop.
- **Profile Spoofing**: cycles UUIDs of well-known profiles (A2DP, HID, HFP, OPP, SPP, PBAP) presenting as each one; works as a service probe and saturates channels.
- **Rate control + jitter**: new **Delay (ms)** field to throttle each attack, with pseudo-random delays (anti-periodic pattern).
- **Pairing spam**: floods the target with pairing requests (BR/EDR; `createBond(TRANSPORT_LE)` is a hidden API, so classic only).
- **Device fingerprinting**: manufacturer by **OUI** (first 3 MAC bytes) and **supported services** (SDP for classic, ScanRecord for BLE), shown in the list and the device detail.
- **Per-worker logging**: each worker/thread reports its attempts, connections and counters.

### v1.0 highlights

- **Real nearby-device detection** (previously only bonded devices were listed).
- **Speaker classifier** combining 3 signals (Bluetooth class, BLE appearance, name).
- **5 selectable attack techniques** after choosing a target.
- **Working Stop button**: attacks really stop (previously required force-closing the app).
- **Mandatory educational notice** on app launch.
- **Renewed UI**: Scan button, "Speakers only" filter, status line, badges 🔊, RSSI and device type.
- **Fixes**: 0-byte buffer in the L2CAP flood (it wrote nothing over RFCOMM), correct `android.bluetooth.le` imports, permissions and API 24-34 compatibility.

---

## BlueZ CLI tools add-on (bluez-utils)

The root tools need the classic bluez command-line utilities, which are not shipped by Android. A prebuilt add-on is published in the companion fork [manuti/bluez](https://github.com/manuti/bluez) — release [**android-5.50-compiled**](https://github.com/manuti/bluez/releases/tag/android-5.50-compiled):

- `bluez-utils-magisk-module.zip` — ready-to-flash **Magisk module** that places `hcitool` and `hciconfig` in `/system/bin` and `/system/xbin`.
- Standalone `hcitool` / `hciconfig` binaries and `SHA256SUMS`.

Compiled from the **official bluez 5.50 source** with Termux clang 21 (target `armv7a-unknown-linux-android24`, bionic). Install: download the zip → **Magisk → Modules → Install from storage** → reboot → verify with `command -v hcitool hciconfig`.

**Honest caveat**: these tools only see a controller when the kernel exposes an HCI device (`/sys/class/bluetooth/hci0`). On many Androids (e.g. the NVIDIA SHIELD Tablet tested here, which manages the radio in userspace through the Android Bluetooth stack) there is no `hci0`: the binaries run without errors but `hcitool dev` lists zero devices. That is the hardware/stack reality, not an install failure — and it is one more data point behind the "research closed" statement above.

---

## Features

### 1. Device detection (`ScanNearbyDevices`)

Combines **three sources** into one list deduplicated by MAC address:

- **Bonded** (`bondedDevices`) — always visible as an anchor.
- **Classic discovery** (`startDiscovery` + `ACTION_FOUND`) — captures name, device class and RSSI.
- **BLE scan** (`BluetoothLeScanner`, low-latency mode) — captures name, RSSI, *appearance* and services from the `ScanRecord`.

Each device is additionally enriched with:

- **Manufacturer (OUI)**: the first 3 MAC bytes are matched against a local table (Apple, Samsung, MediaTek, Broadcom, Qualcomm, Intel, Google, Huawei, Xiaomi…). **Partial** table — may fail with random addresses.
- **Supported services**: SDP (`fetchUuidsWithSdp`, one probe per device) for classic and `ScanRecord.serviceUuids` for BLE, translated to readable names (A2DP, HID, HFP, GATT…).

Results are sorted with speakers first, then by proximity (RSSI), and delivered through events.

### 2. Speaker classifier (`SpeakerClassifier`)

Decides whether a device is probably a speaker by combining signals from most to least reliable:

| Signal | Source | Confidence |
|---|---|---|
| Bluetooth class | `BluetoothClass` major Audio/Video + minor (Speaker, Hi-Fi, Display+speaker) | High |
| Bluetooth class | Portable audio / Car audio | Medium |
| BLE appearance | GAP field `0x0017` (Generic Speaker) | High |
| Name | Keyword heuristic (JBL, Sonos, soundbar, echo, …) | Medium |

Headphones are **explicitly excluded**. The UI shows a 🔊 badge with the classification reason ("BT class", "BLE", "Name: …").

### 3. Attack techniques (`AttackType` / `BluetoothAttack` interface)

Chosen with a selector (Spinner) once a target is selected. **Threads** = intensity (workers/concurrency); **Delay (ms)** = pause between bursts (0 = maximum speed, with random jitter).

| Type | Layer | Description | Intensity |
|---|---|---|---|
| **L2CAP Flood (classic)** | RFCOMM/L2CAP | Opens RFCOMM sockets with random UUIDs and saturates the connected socket. | 1–64 workers |
| **RFCOMM Channel Flood** | RFCOMM | Sweeps RFCOMM channels 1-30 via reflection (hidden API) and saturates the sockets. | 1–30 workers |
| **GATT Flood (BLE)** | GATT | Fills the peripheral's GATT connection table (most only accept a few). | 4–64 parallel connections |
| **Pairing Flood** | Bonding | Floods the target with pairing requests (BR/EDR). | 1–3 workers (the stack serializes) |
| **SDP Query Storm** | SDP | Saturates the SDP server with repeated service queries. | 1–16 concurrent queries |
| **Advertising Flood (BLE)** | Advertising | Pollutes the BLE advertising channel with random UUIDs (requires BLE advertising support). | n/a |
| **Profile Spoofing** | RFCOMM | Cycles UUIDs of well-known profiles (A2DP, HID, HFP, OPP, SPP, PBAP) trying to connect as each one. | 1–9 workers |
| **Combo** | All | Simultaneous coordinated L2CAP + GATT + Pairing + SDP under a single Start/Stop. | per layer |

All attacks:

- Run on **coroutines** in `Dispatchers.IO` with their own execution flag.
- Report per-**worker** progress to the log (toggleable with the Log switch).
- **Stop cleanly** with the Stop button: they cancel their scope and close sockets/connections.

### 4. Security & UX

- **Mandatory educational dialog** (not cancelable) on app launch.
- **Automatic bilingual UI**: follows the system language (Spanish / English by default).
- **Reminder toast** ("use it only with devices you own") when each attack starts.
- Timestamped log (`Logger`) capped at 100 lines.

---

## Expected effects: no root vs. root

> **Honest summary**: without root this app is a **probe and protocol-load generator**, not a jammer. It produces real measurable Bluetooth traffic (connections, rejections, SDP queries) but **does not cut another speaker's A2DP audio** or "drop" other devices' connections. With root on your own phone some real radio-control capabilities unlock (below), but **RF jamming is still impossible** without external hardware (SDR).

### A. No root (current app) — what it really achieves

| Attack | Real observable effect (no root) |
|---|---|
| **L2CAP Flood** | Opens real RFCOMM connections to the target and saturates them with data. Visible in the log as `[CONN]/[RETRY]`. The target answers or rejects — that **is** a service probe. |
| **RFCOMM Channel Flood** | Same, sweeping channels 1-30; on Android 9+ the hidden API is usually blocked and the log reports it and stops. |
| **GATT Flood (BLE)** | Saturates the GATT connection table of the **attacker's own phone** (the limit lives in the attacker's stack); may degrade the attacker's own BLE capability, not the target's. |
| **Pairing Flood** | Generates real pairing requests; the target's stack decides. On modern Android it usually ends in a confirmation dialog on the target (if accepted, it creates a bond). |
| **SDP Query Storm** | Real SDP queries to the target's SDP server. Measures service response/denial — a legitimate service probe. |
| **Advertising Flood (BLE)** | Pollutes the local BLE advertising channel with random UUIDs; observable with a nearby BLE scanner. Does not affect existing links. |
| **Profile Spoofing** | Tries to connect presenting profile UUIDs; modern stacks validate the protocol, so the effect is attempt saturation + service probing. |
| **Combo** | Sum of the above in parallel. |

**What it does NOT achieve without root**:

- **Cutting/disconnecting a speaker's A2DP audio**. The audio stream runs on a parallel L2CAP channel inside the ACL link; opening RFCOMM connections does not touch it, and the stack does not expose the link-disconnect HCI command (`LMP_detach`).
- **Degrading the connection between other devices** (e.g. a neighbor's speaker and their phone): frequency hopping + encryption prevent even monitoring it; disconnecting it requires injecting LMP frames over the air (SDR hardware).
- **RF jamming**: the phone modem does not emit arbitrary RF.
- **Self-DoS** (the only possible audible effect): if your own phone plays audio and attacks the same speaker it is connected to, congestion of your own radio can make your own playback stutter. An effect on yourself, not on someone else's device.

### B. With root — what unlocks (partially implemented in v1.6)

v1.6 already implements a first set of these capabilities (`su` detection, `requestRoot()` and per-target tools — see v1.6 highlights); the table describes what each one really allows and what remains as an explored path.

| Capability | Tool | What it really allows |
|---|---|---|
| **Disconnecting your own phone's links** | `hcitool dc <MAC>` | Sends `HCI_Disconnect` (host-level `LMP_detach` equivalent) over an ACL link of the **own** adapter. If the speaker is connected to the rooted phone, **A2DP audio really stops** — the effect the no-root version cannot achieve. Only affects links of the own host, never other devices' links. |
| **Fine adapter control** | `hciconfig hci0 …` | Change device class, pscan/inquiry, name, radio reset. |
| **Real protocol probes** | `hcidump` / `btmon` | HCI packet capture of the own radio: see exactly what the target answers over the air (pages, connect/reject, link errors). |
| **Own-link quality measurement** | `hcitool rssi`, `hcitool lq` | RSSI and link quality of your own phone's links. |
| **Radio disable/enable** | `svc bluetooth disable/enable` | Turn your own phone's Bluetooth off/on (handy as a "kill switch" against self-DoS). |

**How it is integrated** (v1.6): `util/RootManager.kt` — `suPath()` locates `su` (PATH + typical paths) without triggering the prompt; `requestRoot(timeoutMs)` runs `su -c id` (this shows the Magisk/Superuser dialog); `suExec(command)` runs `su -c <command>`; `bluetoothToolsInstalled()` lists the reachable bluez binaries. Execution via `ProcessBuilder` from `Dispatchers.IO`. The `hcitool`/`hciconfig` binaries come from the bluez-utils add-on (see the [BlueZ CLI tools section](#bluez-cli-tools-add-on-bluez-utils)); they do not ship with Android ROMs.

**Practical root barriers on Android**:

- **SELinux**: on stock `enforcing` ROMs (almost all), `su` is not enough: hcitool's HCI access (`/dev/hci0` or the control socket) is denied by policy. With Magisk it can be relaxed (e.g. `magiskpolicy --live 'allow bluetooth hci_file * *'`), or it works directly on custom/permissive ROMs. It is fragile and varies by device/kernel/Bluetooth firmware.
- **Root is still not a jammer**: the controller remains a normal Bluetooth host; it can only speak protocol, not emit arbitrary noise.

### C. Still impossible even with root (without external hardware)

- **Real RF jamming** ("painting" the 2.4 GHz band): requires SDR — HackRF + PortaPack Mayhem ("Jammer TX") or ESP32 with dedicated firmware. The phone's Bluetooth controller cannot emit RF outside the protocol.
- **Deauthenticating/disconnecting links between OTHER devices**: `hcitool dc` only acts on the own host's links; disconnecting someone else's link requires injecting LMP frames over the air (RF mitM) or a stack exploit — both out of scope for an educational app.
- **MAC spoofing**: the address is fixed by the controller (it may be random, not selectable).
- **CVE exploits (BlueFrag, etc.)**: require raw malformed HCI/L2CAP frames (the firmware does not emit them) and unpatched legacy-stack targets; RCEs stay outside the educational framework.

### D. SDK limitations without root (reference)

- **BR/EDR deauthentication**: `LMP_detach` is not exposed; `createL2capSocket` is a hidden API blocked by SELinux/hidden-API enforcement on Android 9+.
- **L2CAP to arbitrary PSMs / malformed UUIDs**: `createInsecureRfcommSocketToServiceRecord(UUID)` validates the UUID; only RFCOMM (PSM 0x03) is public and malformed frames cannot be sent.
- **Stack-level packet signature variation**: headers are controlled by the stack; only payload/UUID and timing (jitter) can vary.

---

## Requirements

- **Android**: minSdk 24 (Android 7.0), targetSdk 34.
- **Permissions** (declared in the manifest): `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`.
- **Build**: JDK 17, Android SDK platform 34 + build-tools 34.0.0, Gradle 8.7 (wrapper included), AGP 8.6.0, Kotlin 1.9.0.

### Build

```bash
./gradlew :app:assembleDebug
# Output APK:
#   app/build/outputs/apk/debug/app-debug.apk
```

### Unit tests

```bash
./gradlew :app:testDebugUnitTest
# 22 tests (classifier + metadata + manager + payload + example)
```

### Note for ARM64 (Termux / aarch64)

AGP 8.6 ships `aapt2` compiled only for **x86-64**, which does not run on ARM64. To build on an ARM64 device:

1. Install the native Termux package: `pkg install aapt2`
2. Add to `~/.gradle/gradle.properties`:

```properties
android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
```

3. The `gradlew` shebang (`#!/usr/bin/env sh`) does not resolve in Termux; run it with `sh gradlew`.

> Full step-by-step setup (Termux from F-Droid, toolchain packages, JDK, Android SDK, `gh`) is in **[docs/TERMUX-SETUP.md](docs/TERMUX-SETUP.md)**.

---

## Installation

1. Download the APK from the **latest release** ([BluetoothJammer Improved releases](https://github.com/manuti/BluetoothJammer/releases)) or build it yourself (`app/build/outputs/apk/debug/app-debug.apk`).
2. Copy the APK to your device (e.g. `~/storage/downloads/` in Termux).
3. Open it with a file manager and allow "install unknown apps".
4. On first launch: accept the educational notice and grant the Bluetooth/location permissions when requested.

---

## Project structure

```
app/src/main/java/com/eikarna/bluetoothjammer/
├── MainActivity.kt            # Detection, device list, educational notice
├── AttackActivity.kt          # Target selection, attack type, Threads/Delay and Start/Stop
├── api/
│   ├── BluetoothAttack.kt     # Common interface + AttackType enum (selector + factory)
│   ├── AttackManager.kt      # Per-target registry + global stop (multi-target)
│   ├── ScanNearbyDevices.kt   # Scan engine (bonded + classic + BLE) + SDP probe
│   ├── SpeakerClassifier.kt   # Speaker classifier (BT class/appearance/name)
│   ├── DeviceMetadata.kt      # OUI→manufacturer and UUID→profile (local tables)
│   ├── AttackTiming.kt        # Rate control with jitter (jitterDelay)
│   ├── AttackDevices.kt       # L2capFloodAttack (RFCOMM/L2CAP flood)
│   ├── FloodSupport.kt        # Shared flood loop (payload/size/rate)
│   ├── RfcommChannelFloodAttack.kt # RFCOMM channel sweep 1-30 (reflection)
│   ├── GattFloodAttack.kt     # GATT connection flood (BLE)
│   ├── PairingFloodAttack.kt  # Classic pairing flood + BLE
│   ├── SdpFloodAttack.kt      # SDP query storm
│   ├── AdvertiseFloodAttack.kt# BLE advertising flood
│   ├── ProfileSpoofAttack.kt  # Profile spoofing (A2DP, HID, HFP…)
│   └── ComboAttack.kt         # Layered attack (L2CAP+GATT+Pairing+SDP)
├── util/Logger.kt             # Timestamped log
└── ui/theme/                  # Material 3 theme
```

---

## Known limitations

- **Speaker identification** depends on what each manufacturer publishes (class/appearance); devices that publish no metadata are only detected by name heuristic.
- **Manufacturer (OUI)** uses a partial table; with random MAC addresses (common in BLE) the result may be null or wrong.
- **Advertising Flood** requires BLE advertising support (`isMultipleAdvertisementSupported`) and fails when the radio is busy.
- **Profile Spoofing** is not real impersonation: modern stacks validate each profile's protocol; the effect is channel saturation and service probing.
- **Not an RF jammer** and cannot emit raw frames (see "Expected effects").
- Detection range and effectiveness depend on the phone hardware (antenna, range).

---

## Credits

- Original repository: [eikarna/BluetoothJammer](https://github.com/eikarna/BluetoothJammer)
- Fork with adopted ideas: [PIXELQUADRO07/BluetoothJammer](https://github.com/PIXELQUADRO07/BluetoothJammer) (AttackManager, structured logs)
- RF jamming concepts: [PortaPack Mayhem — Jammer TX](https://github.com/portapack-mayhem/mayhem-firmware/wiki/Jammer) (TX/Sleep duty cycle + jitter, signal types)
- Adopted no-root ideas: [hackeringtrue/bluetooth2jam](https://github.com/hackeringtrue/bluetooth2jam) (RFCOMM channel sweep, connect/disconnect bombardment, 990 B payload)
- Companion bluez-utils build: [manuti/bluez](https://github.com/manuti/bluez) — [android-5.50-compiled](https://github.com/manuti/bluez/releases/tag/android-5.50-compiled)
- Inspiration and development assistance: ChatGPT-4o (original repo) and assisted development tools (this edition).

---

*This project is published for educational purposes. Respect other people's privacy and property.*
