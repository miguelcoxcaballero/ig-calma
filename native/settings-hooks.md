# Native Settings integration

These mappings were identified directly in the verified stock Instagram APK
439.0.0.37.89 / versionCode 384510827. No existing third-party feature patch was
used to derive or implement this integration.

| Stock symbol | Verified role | Calma integration |
| --- | --- | --- |
| `X.0E6Z` | `SettingsScreenFragment`; returns a ComposeView | `onViewCreated` records the existing Activity weakly for navigation. |
| `X.0E71` | Settings `UiState` constructor | Passes screen ID and content through `CalmaSettings.content`. Only `MAIN_SETTINGS_SCREEN` is changed. |
| `X.0E72` | `Sections`, immutable list plus loading state | Replaced with a copy preserving the loading state and all original sections. |
| `X.0R5u` | Section ID, title, children, footers | The first section is copied with a new first row. No section is removed or reordered. |
| `X.0E6t` | `NavigationRowUiState` | Stock renderer draws `Tu feed`, handles its touch target and uses Instagram's theme. |
| `X.0vAE` | Public marker interface used as navigation row identity | Unique `CALMA_FEED_SETTINGS` identity, avoiding reuse of an Instagram enum ID. |
| `X.0R4P` | Literal abstract destination | Holds the private `calma:settings:feed` destination. |
| `SettingsScreenViewModel.A03` | Resolves navigation destination, then emits the navigation event | Consumes only Calma's destination and returns stock Kotlin Unit before any stock destination cast. |
| `X.00nM.A00(Iterable)` | Immutable native list factory | Used for the copied children and sections. |

Redex removed the empty constructor of `X.0R5u`. The patch replaces the DEX body
of `CalmaSettings.newSection()` with the verified stock allocation sequence
(`new-instance`, then `Object.<init>`). This avoids hidden allocation APIs and
does not assume a reflective constructor exists.

The patch validates constructor signatures, field types, marker interface,
immutable-list factory and the original fragment identity before applying.
`native/tests/settings.py` exercises insertion, original-data preservation,
loading states, main-screen scope, recomposition, duplicate prevention and
navigation handling using the mapped model contract. These are JVM tests, not
an on-device rendering or login test.

`CalmaSettingsActivity` is reached from the real settings row. Its controls are
the native port of this repository's `app/assets/settings.js`, using the same
preference keys, custom radio/switch drawing, touch feedback and accessibility
states. It follows the device's night mode and uses no overlay, floating button,
default Android settings picker or web feed. The update action launches the
existing `es.calma.instagram.UpdateActivity`; no updater is reimplemented here.
