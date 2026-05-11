# AI To Do Entry

AI To Do Entry is a lightweight Android companion for Microsoft To Do. It focuses on one workflow: describe a task in natural language, let an OpenAI-compatible model convert it into structured task data, review it, and create it in Microsoft To Do through Microsoft Graph.

This project is not a full Microsoft To Do clone. It keeps task management intentionally small and uses Microsoft To Do as the source of truth.

## Features

- Natural-language task creation with an OpenAI-compatible LLM endpoint.
- Microsoft personal account sign-in through MSAL Android.
- Microsoft Graph To Do integration:
  - fetch task lists
  - create tasks
  - view tasks
  - complete or reopen tasks
  - delete tasks
  - edit title, notes, importance, due date, and reminder
- Editable AI preview before creating tasks.
- Optional setting to skip AI creation confirmation.
- 2x1 Android home-screen widget for quick AI task entry.
- Local AI connectivity test in the settings screen.
- Encrypted local storage for the LLM API key.
- GitHub Actions CI for tests, lint, and release APK build.

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- Coroutines
- MSAL Android
- Microsoft Graph REST API
- OkHttp
- Moshi
- DataStore
- Jetpack Security Crypto

## Requirements

- Android Studio
- JDK 17
- Android SDK with compile SDK 35
- Android device or emulator running Android 8.0 or later
- Microsoft personal account
- Azure App Registration for MSAL Android
- OpenAI-compatible LLM endpoint and API key

## Microsoft Setup

The app uses delegated Microsoft Graph permissions for a personal Microsoft account.

Required scopes:

- `User.Read`
- `Tasks.ReadWrite`

The included package name is:

```text
com.xiang.ai.todoentry
```

The MSAL config is stored in:

```text
app/src/main/res/raw/msal_config.json
```

For your own deployment, create an Azure App Registration and update:

- `client_id`
- `redirect_uri`
- Android redirect URI in the Azure portal

The committed `client_id` is a public-client identifier, not a secret. Do not commit client secrets, signing keystores, or API keys.

## LLM Setup

Open the app and go to `My` / `AI Settings`.

Configure:

- Base URL, for example `https://api.deepseek.com`
- Model, for example `deepseek-v4-flash`
- API Key

The API key is stored only on the device through encrypted shared preferences. It is not committed, logged, or sent to Microsoft Graph.

## Build

Run unit tests:

```bash
./gradlew testDebugUnitTest
```

Run Android lint:

```bash
./gradlew lintDebug
```

Build debug APK:

```bash
./gradlew assembleDebug
```

Build release APK:

```bash
./gradlew assembleRelease
```

Without signing environment variables, the default release APK is unsigned:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

For a signed release build, provide these environment variables:

```text
AI_TODO_KEYSTORE_FILE
AI_TODO_KEYSTORE_PASSWORD
AI_TODO_KEY_ALIAS
AI_TODO_KEY_PASSWORD
```

Do not commit the keystore or passwords.

## CI

GitHub Actions workflow:

```text
.github/workflows/android.yml
```

It runs:

- `testDebugUnitTest`
- `lintDebug`
- `assembleRelease`

The release APK is uploaded as a workflow artifact.

To let CI build a signed release APK, add these repository secrets:

```text
AI_TODO_KEYSTORE_BASE64
AI_TODO_KEYSTORE_PASSWORD
AI_TODO_KEY_ALIAS
AI_TODO_KEY_PASSWORD
```

`AI_TODO_KEYSTORE_BASE64` should be the base64-encoded contents of the `.jks` keystore file.

## Privacy

- Natural-language task input is sent to the configured LLM service.
- Created tasks are sent to Microsoft Graph.
- The LLM API key is stored locally in encrypted storage.
- The app does not intentionally store task input history.
- Do not commit API keys, keystores, passwords, or Microsoft client secrets.

## Repository Notes

Ignored local files include:

- `.idea/`
- `.gradle/`
- `local.properties`
- build outputs
- local device/window debug captures

## License

No license has been selected yet.
