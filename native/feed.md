# Native Friends and Following timelines

Calma's original hooks are mapped against the stock Instagram 439.0.0.37.89 APK. No third-party feature patches are used.

## Complete snapshot (0.4.3)

Friends and Following select Instagram's `following` source; For you and Favourites retain their original algorithm/favorites responses and remove only ads. Its request ID correlates the response with the actual UserSession and request context. A head response starts a snapshot on its native worker. Continuations use Instagram's own `02dy.A01` authenticated request factory, cloned `02pp` parameters, a fresh request ID and the native pagination reason. They do not extract credentials or call a separate web API.

Calma collects the entire 48-hour interval, starts batched friendship checks alongside pagination in Friends, then sorts globally and deduplicates media IDs. The time window is fixed at the start of synchronization. Native controls and stories from the head are retained once; later pages cannot inject another story/control row. Both feed-item and direct-media representations are handled. Ads, suggestions, old posts and hidden Reels are excluded.

A response is complete only when the server reports no more pages or an entirely old organic page proves the boundary in a continuously chronological stream. Empty pages, advertisements and out-of-order pages do not prove the end. Repeated/missing continuation cursors fail instead of looping. Each continuation has a 20-second deadline and cancellation; a synchronization has a 90-second deadline. There is no fixed post-count limit. Very large or slow timelines can hit that deadline and need a retry.

Only a complete generation replaces the saved snapshot. Once complete, the native RecyclerView receives the saved snapshot with no continuation cursor, so scrolling that snapshot never requests another feed page. Warm head responses reuse it. Pull-to-refresh, the new-post pill, new-follow and explicit content-refresh reasons build a fresh generation. Settings changes invalidate the old generation. Switching modes cancels stale in-flight responses but preserves the independent completed timeline caches. The main thread never waits on the account synchronization lock.

Since 0.4.5, the cold head returns its first filtered page without waiting for the complete synchronization. A separate preload worker retains an immutable copy of the raw head and builds the complete, globally sorted snapshot. Native pagination stays available until the preload completes; the next parsed continuation then receives the cached remaining posts without duplicating already delivered IDs or stories. A failed optional preload leaves normal native pagination available. During cold loading, pages follow native delivery order and are sorted within each page; the completed cached snapshot is globally sorted. Known positive native mutual relationships do not trigger a redundant batch request; incomplete or negative unverified relationships still require verification. Cached head delivery replaces its old controls with current stories. The first synchronization needs a connection. Full metadata loading is not a promise that every photo/video byte is already downloaded: those assets still use Instagram's own media cache. No full-resolution bitmaps are kept by Calma.

## Friendship correctness and load failures

Every recent eligible author/coauthor with a numeric ID is checked against fresh, account-scoped `friendships/show_many/` results, including users whose native model incorrectly contains an old `false`. The stock `0BnR.A04` request uses cache=false, include_followed_by=true and native-cache notification=true. Its parser hook captures `following` (`0BnY.A0H`) and nullable `followed_by` (`A02`) even without a cached User.

Fresh verified facts take precedence over stale model fields. Missing fields remain unknown. Requests larger than 100 users are split into complete batches, not truncated. Active duplicate batches share a future. Failed batches have a three-second retry backoff; they do not disable all checks for an account for five minutes. Requests have a 15-second deadline. A snapshot waits for its friendship results before filtering; a slow result no longer permanently discards its posts after 2.5 seconds.

Network failures, unresolved relationships and pagination cycles do not become a successful empty page with more loading enabled. The previous complete timeline is retained when available, pagination is stopped, and an inline native end-row says `No se pudo cargar` / `Desliza hacia abajo para reintentar`. Exceptions cannot escape the extension into UI-thread cache parsing. A failed/incomplete generation never displays `That's it`.

## Local storage and end row

Since 0.4.4, snapshots are held per account and selected relationship mode in memory. A private `files/calma-timeline/<account>-<mode>.snapshot` also stores filtered media metadata with Instagram's original `MediaExtKt.A1h` / `04ve.A00` codec. Atomic replacement and per-record SHA-256 checks prevent a partially written file from replacing a valid cache. No tokens or credentials are stored. Persistent snapshots are reused only within 15 minutes, with matching Reels settings; head controls are taken from the current native response. The disk cache has a 64 MiB metadata budget; exceeding it skips persistence, not posts in the in-memory timeline.

A completed snapshot appends Instagram's own `06qT` end-of-feed model in a `05qw` wrapper. Only the Calma completion ID is drawn with the large `That's it` text and outlined smiley. `06gK.bindView` retains its original path for every other row. The completion is an actual measured feed row, not an overlay. It adapts to the device's light/dark mode and supplies an accessibility label. Recycled rows restore their original native binding path.

## Verified mappings

| Stock member | Purpose |
| --- | --- |
| `02qb.A01` parameters 0–5 | Context, builder, native state, UserSession, request, feed dependencies |
| `02pp.A0H`, `A0G`, `A09`, `A0L` | Request ID, cursor, reason, parameter map |
| `02dy.A01` | Native continuation request factory |
| `02px.unsafeParseFromJson` | Native response boundary |
| `07do.A0S`, `A0U` | Feed wrappers and direct-media lists |
| `07do.A0a`, `A0W`, `A0N` | More available, automatic load-more, next cursor |
| `07do.A0O`, `A0P` | Source and request ID |
| `Media.A04.A6X()`, `A7W()`, `getId()` | Date, product kind, stable ID |
| `Media.A04.A33()`, `A8F()` | Author and coauthors |
| `0ICa.A00` / `0BnY` | Account-scoped verified batch friendships |
| `0AMQ` / `06qT` / `04a3.A0G` | Original end-of-feed model and type |
| `06gK.bindView` | Original inline end-row binder |

`native/tests/feed.py` covers filtering and snapshot behavior, including late friendship completion, cross-account isolation, global order, duplicates, empty intermediate pages, repeated cursors, failed refresh preservation and main-thread nonblocking. `verify_feed_hooks.py` checks actual signed-DEX branch destinations for the response, request, friendship and end-row hooks. Patcher guards reject changed transport constructors, codec signatures and model shapes.

These checks do not replace testing a logged-in Instagram account on a physical device. First-sync latency, media-cache behavior and native list rendering with real account data remain unmeasured here.

## Four native options and hourly credits (0.4.4)

The original `06yP` enums BLENDED_FOR_YOU, FAVORITES, RECENTS and FOLLOWING back the four native `06yQ` option models. RECENTS is labelled Friends. The menu uses Instagram's original `0E4c` popup and `0VTM` rows, including the original disabled/selection rendering and balance subtitle. Both old and new Home headers use this picker. Selecting a mode invokes the mapped native clear, feed-type refresh and title update sequence; no WebView or overlay is involved.

`HourlyCredits` earns minutes at each new clock-hour boundary, starts at zero, persists remaining milliseconds and individual expiration timestamps, and never re-earns hours after a backwards clock change. Default allowance is one minute per hour; settings accept 0–60. Each bucket expires exactly 24 hours after earning, and spending consumes oldest available buckets first. Earnings while closed are reconstructed with at most 24 buckets. A rate change first settles earnings under the old rate. No initial retroactive balance is awarded.

`NativeFeedBudget` settles consumption using elapsed real time while the native Home/Clips surface for For you is visible in a focused, resumed activity. Hidden/offscreen Home or Clips pages and paused activities do not spend credits. Native Reels-tab launches start the same credit chain as viewer launches; direct-message launches reset that chain and retain one-clip playback. The selector shows remaining minutes/seconds and disables For you at zero; exhaustion closes its visible Reels viewer and switches the native feed back to Friends. The ordinary For you response retains its order, age range, suggested modules, pagination and Reels; only ads are removed. The dedicated `0Es9.Bd0` ClipsFeedOfAds source returns an empty list.

Pure tests cover expiry, offline earnings, fractional usage, persistence, clock boundaries/rollback, changed rates, oversized absence, zero allowance and corrupted storage. Signed-APK instrumentation verifies the four real option/IGDS row models and the remaining-time/disabled flags. These checks do not validate native navigation with a logged-in account.
