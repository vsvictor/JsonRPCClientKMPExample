This is a Kotlin Multiplatform project targeting Android, iOS.

The app is an interactive showcase for the local `JsonRPCClientKMP` module. It demonstrates the shared builder, every FFI transport option, calls and notifications, transport ping, runtime timeout/keepalive controls, broker and transport-specific options, and PEM/DER certificates supplied as text, filesystem paths, app assets, or shared Compose resources.

Before connecting, enter an endpoint and any server-specific options. The sample does not bundle a server CA; supply the certificate that matches your endpoint. Shared Compose certificates can be placed in `shared/src/commonMain/composeResources/files` and selected by filename in the UI. Platform asset loading remains available through Android's `androidApp/src/main/assets` or the iOS app target's main bundle resources. `JsonRpcClientKmp.installAndroidContext` is installed by the Android activity before the Compose screen is shown.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Architecture

The shared app follows Clean Architecture with MVVM. Dependencies point inward only: presentation → domain ← data.

* `domain` — pure Kotlin models (`ConnectionConfig`, `RuntimeSettings`, `LivenessEvent`), the `JsonRpcRepository`
  interface and use cases that enforce business rules (port range, non-blank method, positive timeouts…).
  It does not depend on Compose or on the `JsonRPCClientKMP` library.
* `data` — `JsonRpcRepositoryImpl` owns the native `JsonRpcClient`, maps domain models to `JsonRpcClientBuilder`
  and reads shared Compose certificate resources.
* `presentation` — `MainViewModel` exposes a single `StateFlow<MainUiState>` and receives `MainAction`s;
  `MainFormMapper` parses text input; `MainScreen` is a stateless composable.
* `di` — `AppContainer` is the composition root. The repository is process-wide, so the connection survives
  ViewModel recreation; the ViewModel polls liveness events only while connected.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

| Suite | Command | Contents |
|---|---|---|
| Shared, JVM host | `./gradlew :shared:testAndroidHostTest` | Domain, use cases, mappers, data layer, ViewModel |
| Shared, iOS simulator | `./gradlew :shared:iosSimulatorArm64Test` | The same, plus Compose UI tests (`MainScreenUiTest`) and repository integration |
| Shared, Android device | `./gradlew :shared:connectedAndroidDeviceTest` | The same, plus repository integration on a device |
| Client, JVM host | `./gradlew :JsonRPCClientKMP:testAndroidHostTest` | Client API, builder validation, options JSON |
| Client, iOS simulator | `./gradlew :JsonRPCClientKMP:iosSimulatorArm64Test` | The same, plus the server integration matrix (17 transports) |
| Client, Android device | `./gradlew :JsonRPCClientKMP:connectedAndroidDeviceTest` | The same, plus the server integration matrix (17 transports) |
| Rust client/FFI | `cargo test -p json-rpc-2-client -p json-rpc-2-ffi` (in `JsonRPCClientKMP/build/upstream/JSON-RPC-2`) | Liveness, restore/reconnect, FFI |

Integration tests require a running `json-rpc-2` server (`JSON_RPC_2 server ... --bind 0.0.0.0`) with the `echo`
method and the standard ports (TCP 8000, TLS 8445, …). They are skipped automatically when the server is unreachable,
and always skipped on the JVM host, because the host JVM cannot load the native library.

- iOS simulator: uses `127.0.0.1`. Override with `SIMCTL_CHILD_JSONRPC_TEST_HOST=<ip>`.
- Android device: uses `10.1.0.196`. Override with
  `-Pandroid.testInstrumentationRunnerArguments.jsonrpcHost=<ip>`.
- The device must be on the same subnet as the server. Secure transports use the bundled certificate with
  `serverName("localhost")`.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…