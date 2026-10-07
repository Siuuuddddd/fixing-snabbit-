# Android foundation guide

The native Android app lives in the standalone `android/` Gradle build. It provides customer and worker product flavors, shared Compose design tokens/components, Koin dependency injection, secure Retrofit/OkHttp setup, and a typed Navigation 3 shell. All 23 design-board destinations are implemented; screens whose backend read contracts are missing show explicit gap states rather than invented data.

## Toolchain

- Android Gradle Plugin 9.1.1, Gradle 9.3.1, compile/target SDK 37
- Kotlin/Compose compiler plugin 2.3.20
- Compose Material 3 via BOM 2026.08.00
- AndroidX Navigation 3 1.2.0
- Koin 4.2.2 (`koin-android`, Compose, Compose ViewModel, and Navigation 3 artifacts)
- Razorpay Android Standard Checkout 1.6.41; provider callbacks are signals only, and backend reads remain payment/membership authority
- Firebase Android BoM 34.19.0 with Messaging installation-ID registration; Google Services 4.5.0 is applied only when local Firebase configuration is present

Navigation 3 and Koin are stable. The required `lifecycle-viewmodel-navigation3` back-stack ViewModel decorator is currently `2.12.0-alpha04`; it is isolated to the navigation shell and was selected explicitly because the stable lifecycle line does not yet supply this Navigation 3 integration artifact.

## Local setup

Create ignored `android/local.properties`:

```properties
sdk.dir=C\:\\path\\to\\Android\\Sdk
RAZORPAY_KEY_ID=
MAPS_API_KEY=
PROTOTYPE_WALLET_ID=
```

Real secrets never belong in Git. `PROTOTYPE_WALLET_ID` is not a secret; it is an explicit local-only bridge for the documented missing wallet-discovery API and should be blank outside a seeded prototype environment. For push delivery, place an environment-specific, ignored `android/app/google-services.json` containing clients for both flavor application IDs. Without that file the Google Services plugin stays disabled, FCM reports an explicit unavailable state, and all non-push builds and notification history continue to work.

### Real local FCM delivery

The default backend remains safe for development and logs push attempts. To send real Firebase pushes, keep the downloaded service-account JSON outside the repository, then set these ignored `.env` values:

```properties
PUSH_PROVIDER=fcm
FCM_PROJECT_ID=your-firebase-project-id
FCM_SERVICE_ACCOUNT_HOST_FILE=C:/absolute/path/outside/the/repository/firebase-service-account.json
FCM_SERVICE_ACCOUNT_FILE=/run/secrets/firebase-service-account.json
```

Start the stack with the additional Compose overlay so only Notification Service receives the key as a read-only mount:

```powershell
docker compose -f compose.yaml -f compose.apps.yaml -f compose.fcm.yaml up -d
```

Use one ignored `android/app/google-services.json` whose Firebase project contains both flavor package names. After customer or worker login, Android registers the current Firebase installation with the matching role endpoint. Token refresh re-registers it; logout removes it. Android 13+ also requires notification permission. Pushes use the `bookings`, `payments`, and `jobs` channels and carry route data for notification taps. Never copy the service-account JSON into `android/`, a Docker image, or Git.

Debug uses `http://10.0.2.2:8080/` and permits cleartext only through the debug manifest. Release uses an invalid placeholder until the deployment build supplies its TLS endpoint.

## Build, test, and install

```powershell
cd android
.\gradlew.bat clean build
.\gradlew.bat installCustomerDebug
.\gradlew.bat installWorkerDebug
```

Customer and worker have separate application IDs, so both debug apps can be installed on the Pixel 9 Pro emulator. On macOS/Linux use `./gradlew`.

Verify the unchanged backend separately from the repository root:

```powershell
.\gradlew.bat build --no-daemon
```

## Read next

- `RULES.md`: mandatory engineering constraints.
- `api-map.md`: actual backend contracts by design-board screen.
- `ui-spec.md`: tokens, components, navigation, and screen-state rules.
- `api-gaps.md`: contracts the backend does not currently expose.
- `assets-todo.md`: approved artwork still required.
- `PROGRESS.md`: completed foundation and intentionally unimplemented screens.
