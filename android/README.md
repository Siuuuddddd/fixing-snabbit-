# Snabbit Android app

This standalone project contains the customer and worker apps. It includes the shared design system, secure networking/DI foundation, Navigation 3 shell, and all 23 design-board destinations. Missing backend read contracts are surfaced as explicit gap states rather than filled with invented data.

## Prerequisites

- Android Studio with Android SDK 37
- JDK 17 or newer
- A running backend gateway on the host at port 8080 for emulator networking

## Local configuration

Create `android/local.properties` (ignored by Git):

```properties
sdk.dir=C\:\\path\\to\\Android\\Sdk
RAZORPAY_KEY_ID=
MAPS_API_KEY=
```

Never commit real values. Debug builds use `http://10.0.2.2:8080/`; release builds retain an intentionally invalid URL until CI/deployment injects a production configuration.

## Build and test

From `android/` on Windows:

```powershell
.\gradlew.bat clean build
.\gradlew.bat testCustomerDebugUnitTest testWorkerDebugUnitTest
```

On macOS/Linux use `./gradlew`.

Install a flavor:

```powershell
.\gradlew.bat installCustomerDebug
.\gradlew.bat installWorkerDebug
```

Use the `customerDebug` or `workerDebug` Android Studio run configuration. Customer and worker flavors have distinct application IDs, so both can be installed together.

## Project boundaries

- `ui/theme` and `ui/components`: shared design system.
- `navigation`: typed Navigation 3 keys and role-specific shells.
- `data/network`: Retrofit/OkHttp and authentication boundaries.
- `data/auth`: OTP remote/repository layers and secure access-token storage.
- `data/catalog`: service catalogue remote and repository layers.
- `data/address`: saved-address remote, repository, and consented device-location layers.
- `data/launch`: persisted onboarding and launch routing.
- `domain`: feature-facing repository contracts and models.
- `di`: Koin modules. Feature modules are added here as real screens are built.
- `docs/android`: API contracts, gaps, visual specification, assets, and progress.

Read `docs/android/RULES.md` before adding a screen.
