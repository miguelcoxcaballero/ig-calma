# Native package compatibility

The patch targets the verified Instagram 439.0.0.37.89 APK (version code 384510827). The original classes remain in their original Java packages; only the Android installation identity changes to `es.calma.instagram`. The Calma signing key provides updates to the existing Calma installation. It cannot update the official Instagram installation signed by Meta.

`CalmaBase.kt` and `CloneNames.kt` apply these independently verified changes:

| Identifier | Treatment |
| --- | --- |
| Manifest package and resource table package | `es.calma.instagram` |
| Android version code | 384510829; original Instagram protocol version name retained |
| Relative or unqualified component class names | Resolve against the original package before renaming the installation |
| Class names, activity aliases and metadata keys | Preserve original spelling |
| Provider authorities | Assign Calma authorities; change exact DEX authority strings and the authority part of `content://` URIs |
| Manifest custom permissions, actions, categories, process and task identities using the original package | Rename consistently; update corresponding exact DEX strings |
| Exact DEX installation package strings | Rename for native package checks and self-directed intents |
| Fragment Bundle keys, DM push payload keys and nested class strings | Preserve; no broad substring replacement |

The generic Morphe XML parser is not namespace-aware. Consequently, these edits use qualified `android:*` attribute names. Namespace-based DOM access missed the original attributes and could leave the old version code and provider authorities in the rebuilt APK.

Original stock bytecode evidence: `X/03Ro.A00/A01` and `X/07kq.A01` construct `ComponentName` values for the original MainTabActivity and InternalLauncher aliases; `X/0GGi` holds the launcher alias list. Renaming those strings without changing the manifest aliases breaks component lookup. `Ig4aAppComponentFactory.instantiateClassLoader` checks the application package against an exact original-package string, which must change. Provider implementations use exact authority strings in `UriMatcher.addURI`; these require authority replacement while keeping their Java class names. The original `com.instagram.android.igns.*` notification payload keys remain intact.

A second scan checked every instruction using any of the 20 unique stock provider authorities. The six bare authority strings that also resemble provider class names are used for URI access: five are passed directly to `UriMatcher.addURI`, and `FirstPartyUserValuesLiteProviderV2` is passed to `X/05U6.A02`, which prepends `content://`. No such literal is used by `Class.forName` or `ComponentName`. `DeferredCurrentUserProvider` has no dotted string literal in the stock DEX pools; `ThreadsContentProvider` appears only in a content URI. These findings apply to the pinned APK version.

Run `python3 native/tests/package_clone.py` for manifest round-trip, component, URI-boundary and payload-preservation regressions. The release verifier additionally checks the rebuilt manifest, component classes, native libraries and signing certificate. The rebuilt debug resource table was inspected with Android `aapt2`; it reports the Calma package. None of the 14 stock native libraries contains a literal original package string.

The generic FULL writer does not preserve Instagram's DEX partitions. Version 0.4.1 restores every original class to its original `classes*.dex` file after patching, adds Calma and its canary to `classes21.dex`, and regenerates `metadata.txt` SHA-1 values and the additional `dex_manifest.txt` entry. The original 20 canaries and loader flags are preserved. Original baseline profiles are removed because their checksums and method indices no longer describe the rebuilt DEX. The release verifier rejects changed partitions, stale hashes, missing canaries and reference overflows. The old 0.4.0 artifact fails this check.

This corrects a concrete packaging defect, but does not by itself establish the cause of every startup crash: `DexLibLoader.loadAll` follows different paths before and after Android 30. The original component factory returns Android's multidex-capable class loader; added providers remain visible in `classes21.dex` before `Application.onCreate` on the supported Android versions.

These are static/build checks. Login, background DM notifications, cross-app signature-protected integrations, remote integrity checks and device-specific startup require testing on an Android device with an account. A different signing key cannot retain Meta's signing identity; no signature/integrity bypass is included or claimed.
