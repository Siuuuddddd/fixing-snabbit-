# Android frontend rules

These rules are mandatory for every change under `android/`.

1. Use Kotlin, Jetpack Compose, and Material 3. Do not add XML screen layouts.
2. Use Koin for dependency injection. Do not add Hilt, Dagger, KSP, or kapt.
3. Use AndroidX Navigation 3. Do not add Navigation Compose 2.x.
4. Follow MVVM with unidirectional data flow: immutable UI state exposed as `StateFlow`, UI events sent to the ViewModel, and one owner for every state transition.
5. Keep feature boundaries explicit: UI, ViewModel, repository, remote/local data sources, and DTO/domain mapping must not be collapsed into one class.
6. A composable does not call Retrofit, databases, secure storage, or SDKs directly.
7. Put user-visible text in string resources. Give meaningful icons and images a resource-backed content description; decorative imagery uses `null`.
8. Use the shared design tokens and components. The primary color is `#F80E78`; use Inter; keep 4/8/12/16/24/32 dp spacing and 16/20 dp radii.
9. Design against the Google Pixel 9 Pro reference viewport (1280 × 2856, approximately 20:9) while remaining responsive to other Android sizes, font scales, and system insets.
10. Keep customer and worker experiences in product flavors. Shared behavior belongs in `main`; role-specific implementation belongs in the appropriate source set when it actually diverges.
11. Never put secrets in source, resources, Gradle files, logs, screenshots, or commits. Local SDK/payment/map values belong in ignored `android/local.properties`; production values come from the build/deployment environment.
12. Store authentication material only through `TokenStore`. Never log authorization headers or tokens. A provider/client success response is not proof of payment.
13. Match backend endpoint paths, JSON field names, enum values, and money units exactly. Do not invent API behavior to make a screen easier.
14. Model loading, empty, content, recoverable error, terminal error, and offline states explicitly for real screens.
15. Every feature change needs ViewModel/repository tests proportional to risk. Navigation and critical money/booking paths need instrumentation coverage before launch.
16. Keep previews small and deterministic. Use sample resources or clearly fake preview data, never production/user data.
17. Run `./gradlew build` from the repository root and `./gradlew build` from `android/` before merging.
18. Update `docs/android/api-map.md`, `api-gaps.md`, and `PROGRESS.md` whenever a screen or backend contract changes.
19. Never modify a backend module, backend build file, backend dependency version, Flyway migration, or backend API while doing Android work. Never invent an endpoint. If a screen needs a missing contract, record it in `docs/android/api-gaps.md`, use a clearly marked fake data source behind a repository interface only when implementing that screen, and continue.
20. `android/` is a standalone Gradle build with its own settings, wrapper, and ignored local properties. Never include it in the backend settings. After every chunk, verify the backend build/tests plus Android debug assembly and unit tests.
21. Do not trace or redraw logos, illustrations, photos, or custom icon art from the board. Use the single `BrandLogo` composable, named placeholders, and Material Icons; record missing production assets in `docs/android/assets-todo.md`.
22. Read Razorpay test keys and Google Maps keys from ignored `android/local.properties`; keep Firebase configuration in an ignored `google-services.json` placeholder when that SDK is added. Missing credentials must not prevent the non-integrated foundation from compiling.
23. Debug networking uses `http://10.0.2.2:8080/` and permits cleartext only in the debug manifest. Release configuration must use TLS.
24. Commit with clear messages and push to the current branch after each completed chunk. Update `docs/android/PROGRESS.md` at the end of every chunk.

The current foundation deliberately contains placeholders only. A placeholder route must not be mistaken for a completed feature.
