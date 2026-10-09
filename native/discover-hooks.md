# Native Discover and Search hooks

These hooks were mapped from Instagram 439.0.0.37.89 / version code 384510827. The implementation is Calma's own following-only rule applied to stock native response models and the main Search provider. No third-party feature patch is included.

## Verified stock boundaries

| Boundary | Stock method | Filtered content |
| --- | --- | --- |
| Explore | `X.094e.unsafeParseFromJson` | `X.02P3.A06`, sectional items |
| REST typeahead | `X.0XFB.unsafeParseFromJson` | `X.0J38.A02`, rendered search rows |
| Submitted keyword media grid | `X.0XEv.unsafeParseFromJson` | `X.0cnK.A05`, sections |
| Submitted keyword top results | `X.0XFD.unsafeParseFromJson` | `X.0YCE.A01` raw entities and inherited `A0A` rendered rows |
| Submitted keyword accounts | `X.0XFE.unsafeParseFromJson` | `X.0YCJ.A01` raw users and inherited `A0A` rendered rows |
| Submitted keyword tags | `X.0XEu.unsafeParseFromJson` | `X.0YC8.A00` raw entries and inherited `A0A` rendered rows |
| Submitted keyword places | `X.0XEw.unsafeParseFromJson` | `X.0YC9.A00` raw entries and inherited `A0A` rendered rows |
| Cached/recent/GraphQL main Search | `X.0I4B.GDb`, `X.0I4B.GDc` | `X.0I2T.A00` rows and parallel `A01` metadata |

The submitted search request `X.0YC7` uses `fbsearch/top_serp_stream/`; its `X.0mar` callback selects `X.0XFD`. The shared SERP base parser `X.0bHl` parses `media_grid` using `X.0XEv`. Filtering only typeahead would therefore leave submitted media searches unfiltered.

The main provider is identified by its own `MainSearchResultsProvider` diagnostic and `UserSession` field. Shared interfaces used by DM recipient selectors and other account pickers are not patched. Parser hooks obtain their session from native `03gD.A01`; provider hooks bind the provider's session immediately before filtering its result.

## Following and rendering

Author identity comes from `Media.A04`, `LiveTreeMediaDict.A33()` (author), and `A8F()` (collaborators). The shared Calma native relationship helper checks Instagram's native friendship state and its account-specific verified batch cache. Unknown identities use the existing native batch lookup; it never blocks the UI thread and has a bounded wait on parser worker threads. Unverified accounts remain absent.

Explore and submitted media search sections use the same stock discovery layout. The traversal covers the mapped single tiles, media lists, nested sections, and fallback section with identity and depth bounds. Accepted media are deduplicated, copied into clean native tiles, and rendered in a native `MEDIA_GRID`. Copying strips unverified alternate-media/clip siblings. The Reels setting also applies to these grids.

Search row identity covers native `User`, `X.0C9d`, `X.0I7G`, and `X.0YCr` wrappers. Raw and rendered SERP account lists are both filtered. Tag/place entries without a followed account are removed under the following-only rule. Preview and upsell modules without a verifiable owner are removed; the native inform module remains. The inherited preview field `X.0WDs.A01` is accessed through its declaring class because child responses reuse `A01` for their raw list.

Only content collections change. Server cursors, paging tokens, rank tokens, and more-available flags remain intact even for an empty filtered page. Discover has no 48-hour cutoff; the chronological 48-hour window belongs to the home feed.

## Control flow and validation

Each return hook replaces the original return instruction with the helper call and appends a new return. This preserves branch labels on the hook. Merely inserting instructions before an existing return can leave stock branches jumping past the filter.

`python native/tests/discover.py` exercises following/collaborator rules, unknown relationships, account separation, duplicate/nested tiles, clean native tile construction, empty-page pagination, raw/rendered SERP agreement, inherited field shadowing, and parallel search metadata. Its native model fixtures are independent of an authenticated Instagram session.

`python native/tests/discover_dex.py FINAL.apk --morphe MORPHE.jar` verifies all nine methods in the packaged DEX: every return enters its hook, branch/switch/catch targets cannot bypass it, parser and result registers match, and provider account binding precedes filtering. The patch also asserts the mapped stock signatures and parser strings before modifying the APK.

These are host-side contract and artifact checks. They do not substitute for testing an authenticated account on a physical Android device.
