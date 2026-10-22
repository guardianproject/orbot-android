# AGENTS.md

Comprehensive guide for AI coding agents (and humans) working in the Orbot repository.

## Project Overview

Orbot is the Guardian Project's Android Tor Proxy + VPN app. It routes other apps' traffic through Tor with optional pluggable transports (obfs4, Snowflake, meek, WebTunnel, DNSTT). It is a high-risk, security-focused app: assume every change can affect user anonymity. Never log, commit, or expose secrets, credentials, or identifying data. Keep new code mindful of privacy (no personalized keyboard learning, no world-readable files, no cleartext network calls).

- **Package/applicationId:** `org.torproject.android` (debug: `.debug`, nightly: `org.torproject.android.nightly`)
- **minSdk 24** (target/compileSdk live in `app/build.gradle.kts` and change with Android releases)
- **Default branch: `master`** (hosted on both GitHub and GitLab; CI exists on both)

## Build & Verify Commands

```bash
# One-time: NATIVE SUBMODULE REQUIRED or the build fails
git submodule update --init --recursive   # hev-socks5-tunnel in app/src/main/jni/

# Build a debug APK
./gradlew assembleFullpermDebug           # README-canonical variant
./gradlew assembleDebug                   # what CI runs

# Unit tests (pure JUnit, no Robolectric)
./gradlew :app:testFullpermDebugUnitTest  # or ./runtests.sh at repo root

# Lint
./gradlew lint                            # abortOnError=false; config in lint.xml

# Dependency update check
./gradlew versionCatalogUpdate --check
```

Things that trip up agents:
- Tests are flavor-scoped (`fullperm`); `./gradlew test` alone may not do what you want.
- **Release variants run the `updateBuiltinBridges` task** (buildSrc/UpdateBridgeConfig.kt): it downloads fresh bridge JSONs from bridges.torproject.org + 16 country DNSTT configs, then **deliberately fails** and tells you to commit the updated bridge files. Never "fix" this by disabling it — release builds won't proceed with stale bridge data (staleness check: >1 day, and refuses a dirty git worktree).
- Legacy packaging is enabled (`useLegacyPackaging = true`) because the shadowsocks-rust binary (`libsslocal.so`) is executed as a subprocess — don't "optimize" packaging flags.
- Gradle uses **configuration cache** (`STABLE_CONFIGURATION_CACHE` preview fails builds on config-cache-breaking changes) and its own toolchain (pinned via `gradle/gradle-daemon-jvm.properties`, auto-provisioned; the CI machine JDK is fine).
- Toolchains (Java/Kotlin versions, NDK, AGP) are pinned in `app/build.gradle.kts` and `gradle/libs.versions.toml` — don't assume specific versions from this file; check there.

## Project Layout

```
app/                                  # single Android module
  build.gradle.kts                    # flavors, splits, native build, custom tasks
  src/main/java/org/torproject/android/
    OrbotApp.kt                       # Application: Prefs init, locale, app-lock flags
    OrbotActivity.kt                  # launcher, bottom nav host, status receiver
    localization/Languages.kt         # locale switching (vendored, not a submodule)
    Regionalization.kt                # country display names / flag emoji
    receivers/OnBootReceiver.kt
    service/                          # runs in :tor process (see Architecture)
    service/circumvention/            # bridges/transports: Bridge, SmartConnect,
                                      #   AutoConf, MoatApi (Retrofit), BuiltInBridges
    service/tor/                      # TorConfig (torrc builder), ShadowSocks, installer
    service/vpn/                      # OrbotVpnManager, TorifiedApp, TProxyService (JNI)
    service/db/                       # onion service / client auth columns
    util/                             # Prefs facade, Settings (encrypted DataStore),
                                      #   PreferenceProvider (ContentProvider), extensions
    ui/connect/                       # "Connect" tab: ConnectViewModel (StateFlow),
                                      #   bridge/pathway bottom sheets (newest code, Kotlin)
    ui/kindness/                      # Snowflake proxy mode + SnowflakeProxyService (FGS)
    ui/more/                          # "More" tab, Settings/Safety/Camo PreferenceFragments
    ui/core/                          # BaseActivity, biometric prompt
    ui/settings/                      # SecureEditTextPreference etc. (encrypted DataStore)
    ui/v3onionservice/ + clientauth/  # onion hosting UI (legacy, mostly Java)
    ui/quicksettings/                 # QS tile services
    ui/                               # AppManagerFragment (torified app list)
  src/main/jni/                       # ndkBuild + hev-socks5-tunnel SUBMODULE (don't edit)
  src/main/res/xml/preferences.xml    # full settings tree; safety_prefs.xml safety screen
  src/test/                           # JVM unit tests (9 files, kotlin.test/JUnit)
  src/androidTest/                    # fastlane Screengrab screenshot tests only
buildSrc/                             # UpdateBridgeConfig task (release bridge refresh)
gradle/libs.versions.toml             # version catalog (tor-android, IPtProxy, AGP...)
libs/                                 # geoip.jar + build scripts
docs/                                 # BUILD.md, design specs (kindness, smart-connect)
fastlane/                             # Play metadata (~70 locales), Fastfile, Screengrabfile
scripts/                              # log viewer, translation fixers, release script
```

## Architecture (must-know before changing service code)

### Multi-process model
- **Default (UI) process:** `OrbotActivity`, fragment UI, `SnowflakeProxyService` (kindness FGS), QS tiles, `OrbotApp`.
- **`:tor` process:** `OrbotService` (the VpnService, `foregroundServiceType=systemExempted`, `stopWithTask=false`) and `org.torproject.jni.TorService` (from the tor-android AAR). The two services are bound via a ServiceConnection; `OrbotService` talks to Tor via a `TorControlConnection` (jtorctl).

### IPC patterns (UI ⇄ service)
1. **Commands UI → service:** `Context.sendIntentToService(action)` (in `util/OrbotExtensionFunctions.kt`); routed by `OrbotService.IncomingIntentRouter`. A full list of actions lives in `service/OrbotConstants.kt`.
2. **Events service → UI:** local broadcasts with `LOCAL_ACTION_STATUS` / `LOCAL_ACTION_LOG` / `LOCAL_ACTION_PORT`. Receivers register with `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)` and `setPackage()`.
3. **Cross-process prefs:** `PreferenceProvider` (ContentProvider, authority `${applicationId}.provider.preferences`, not exported). Most code reads/writes through the **`Prefs` facade object** (`util/Prefs.kt`) — do NOT touch SharedPreferences or DataStore directly from feature code.
4. Content providers (`OnionServiceContentProvider`, `ClientAuthContentProvider`) for onion service DB (package-internal only).

If you add a screen that reacts to Tor state, follow the Connect screen pattern: `ConnectFragment` + `ConnectViewModel` (StateFlow) over the broadcast receiver rather than wiring receivers into every fragment.

### VPN layer
`OrbotVpnManager` (Java) builds the TUN interface and writes `tproxy.conf` for `TProxyService` (JNI wrapper over the vendored hev-socks5-tunnel; constants: virtual gateway 198.18.0.1, fake DNS 198.18.0.2, MTU 8500). Per-app routing comes from `TorifiedApp` (`Prefs.torifiedApps`).

### Circumvention/bridges
- `Transport` enum ↔ IPtProxy controller (library: `com.netzarchitekten:IPtProxy`, version in `gradle/libs.versions.toml`) starts/stops transports and emits torrc `ClientTransportPlugin`/`Bridge` lines.
- `SmartConnect` auto-configures/retreats through transports; `AutoConf` + `MoatApi` (Retrofit 3 + kotlinx-serialization converter, pinned ISRG Root X1 cert) query bridges.torproject.org Moat endpoints.
- Built-in bridges live in `app/src/main/assets/builtin-bridges.json` (regenerated by the release task).

### Persistence layers (three, in order of preference)
1. `Prefs` facade → `PreferenceProvider` ContentProvider → default SharedPreferences (or migrated keys) — works across processes.
2. `Settings.kt` — **Tink-encrypted DataStore**; sensitive proxy credentials (host/port/user/pass/ss) with one-way migration off SharedPreferences. Writable via `ui/settings/Secure*Preference` widgets.
3. onion service JSON DB via its own ContentProvider.

### Settings UI specifics
- Settings are `PreferenceFragmentCompat` XML screens (`res/xml/preferences.xml`, `safety_prefs.xml`); `AbstractPreferenceFragment` implements an **in-place subscreen navigation** trick (`onNavigateToScreen` re-inflates the same fragment and re-runs `initPrefs()` — all wiring runs on every subscreen load and relies on null-tolerant `findPreference`). A planned Compose port (`compose`/`more-compose` branches) restructures this into an explicit `SettingsScreen` enum, per-screen wiring, and pure state helpers (e.g. `proxyFieldState(type)`), keeping Views in the meantime.
- `Prefs` is the sole data API for settings UI; sensitive fields go through `SecureEditTextPreference`/`SecureListPreference` (encrypted DataStore).

## Code Conventions

- **Kotlin-first:** ~82% Kotlin; new code must be Kotlin. Remaining Java pockets (`OrbotService.java`, `OrbotVpnManager.java`, onion service UI) are migrated opportunistically — small, focused PRs only.
- **Coroutines:** light usage; IO work uses `CoroutineScope(Dispatchers.IO)` or `runBlocking` inside `Settings.kt`. Don't introduce an architectural framework (Hilt, Ktor, Room) without an issue-first discussion.
- **Serialization:** models use `@Serializable` (kotlinx-serialization), Retrofit converter is kotlinx-based (`MoatApi.kt` is the only HTTP client class).
- **Null-safety:** idiomatic safe-calls; avoid `!!` (only a handful in the codebase).
- **Version guarding:** minSdk 24 means frequent `Build.VERSION.SDK_INT` checks — most-hit APIs: O(26) notification channels, TIRAMISU(33) per-app locales, CINNAMON_BUN(36) local-network permission, plus foreground-service type rules 31–35. Match the guard style of neighboring code.
- **No code comments unless asked for** (repo style is mostly comment-free).
- **UI:** View system + viewBinding + androidx Preference; no Compose yet. Material components (material3 artifact). Follow `BaseActivity` for locale/secure-window handling.
- **Strings:** put all user-visible strings in `res/values/strings.xml`; never hardcode. `...` → `…` (see `scripts/fix-ellipsis.sh`). Translations come from Weblate — never hand-edit translated `values-<locale>/` files.

## Tests

- **JVM unit tests** (`app/src/test`, plain JUnit + `kotlin.test`): bridge line parsing (`BridgeTest`), transport/event listener behavior, bridge input cleanup, snowflake service guards, prefs provider. Add tests for new parsing/pure logic; pure functions preferred so they stay JVM-testable.
- **androidTest** is exclusively fastlane `Screengrab` screenshot tests across locales (see `Screengrabfile`); don't add regular instrumentation tests there without checking fastlane locale tooling first.
- Run: `./runtests.sh` or `./gradlew :app:testFullpermDebugUnitTest`.

## CI/CD

- **GitHub Actions** (`.github/workflows/android.yml`, on push/PR to master, ubuntu): `assembleDebug` → `:app:testFullpermDebugUnitTest` → uploads debug APK.
- **GitLab CI** (`.gitlab-ci.yml`, F-Droid buildserver): `./gradlew assembleDebug lint`; nightly deploy lane for master with epoch versionCode.
- **Releases:** `scripts/make-release-build.sh` (verifies signed Git tags with Guardian GPG keys, TZ=UTC, produces sha256/gpg sigs). Version code scheme: fixed base code + per-ABI increment (see `orbotBaseVersionCode` / the increments map in `app/build.gradle.kts`); `versionName` from `git describe --tags`. Fastfile handles Play uploads.

## Lint

`lint.xml` at repo root. Fatal: `Aligned16KB` (16KB page alignment for native libs is mandatory). Promoted to errors: locale/RTL/Typos, security checks (`HardwareIds`, `TrustAllX509TrustManager`, `SetWorldReadable`, `PackageManagerGetSignatures`, `ProtectedPermissions`, `UnsafeProtectedBroadcastReceiver`), date/plural checks, modern-compat usage. Ignored: `MissingTranslation` (crowdsourced). Don't loosen these; fix the warning.

## Boundaries

**Never**
- Log, commit, or expose secrets, credentials, or user-identifying data (this is an anonymity-critical app).
- Push directly to `master` or create PRs/commits unless explicitly asked.
- Loosen `lint.xml` security checks or disable the `updateBuiltinBridges` staleness failure.
- Touch pack­aging flags, jniLibs ABI excludes, or `orbotBaseVersionCode`.
- Hand-edit translations in `values-<locale>/` (Weblate owns those).
- Edit the `hev-socks5-tunnel` submodule contents.

**Always**
- Run `./gradlew :app:testFullpermDebugUnitTest` (and `lint` when touching UI/resources) before declaring a task done.
- Route all prefs access through the `Prefs` facade — never direct SharedPreferences/DataStore access from feature code.
- Put user-visible strings in `res/values/strings.xml`; keep new code Kotlin and private-data-unaware.

## Git Workflow

- Default branch **`master`**; topic branches are short lowercase slugs (`agp`, `locale`, `compose`) or `fix/issue-NNNN`.
- Commit style: free-form imperative one-liners, no conventional-commit prefixes (e.g. "update tor-android to 0.4.9.14"). Weblate merge commits are routine — ignore them when bisecting.
- Never bump `orbotBaseVersionCode` or touch `fastlane/metadata` changelogs yourself; that's release management (and `updateBuiltinBridges` owns bridge assets).

## Common Gotchas (summary)

1. Missing submodule → NDK build failure: run `git submodule update --init --recursive` first.
2. Release-build failure demanding bridge commits is **intentional** (UpdateBridgeConfig).
3. Tests/commands are flavor-scoped: use `fullperm` variants.
4. Settings logic partially runs on every subscreen navigation (see Architecture); be careful where you insert `initPrefs`-style wiring.
5. Cross-process behavior: a pref written in the UI process must be visible to `:tor` — go through `Prefs`, never process-local caches.
6. Logs from `tor`/services don't show in Android Studio (separate process): use `./scripts/view_logs_tmux.sh` or `adb logcat --pid=$(adb shell pidof -s "org.torproject.android.debug")`.
7. Packaging flags and jniLibs ABI excludes are load-bearing (sslocal execution, 16KB alignment) — leave them alone.
8. Dependency updates: check `versionCatalogUpdate` and note tor-android/IPtProxy local-AAR overrides are commented out in `app/build.gradle.kts` intentionally.

## Where to Look for More

- `docs/info/BUILD.md` — build prerequisites.
- `docs/design/design-spec-kindness-mode.md`, `design-spec-smart-connect.md` — feature specs.
- `README.md` — canonical build/test/log instructions.
- Last resort: this file — if you discover something wrong or missing, update it.
