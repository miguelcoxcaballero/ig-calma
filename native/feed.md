# Native Friends and Following timelines

Calma's original hooks are mapped against the stock Instagram 439.0.0.37.89 APK. No third-party feature patches are used.

## Request binding and progressive delivery (0.4.8)

Friends and Following use the native following source and the stock FOLLOWING protocol. `03u7.A01` fixes the final HTTP parameter map after experiment/supplier parameters. `05qX.A0C` binds the original client request (including copied envelopes) to its delivered page and updates the `04ss` list, without relying on the server echoing request_id. A cold head returns known eligible posts and native controls immediately. The original controller loads later pages: `05qX.A0G` schedules `05qX.A0J` with the server cursor and PAGINATION reason after the normal completion callback. `GRO` retains Instagram's own in-flight guards. The extension no longer calls a parallel timeline transport or waits for a complete 48-hour download inside the parser.

The account/mode accumulator retains unresolved authors and reconsiders them after the native batch friendship response. Known positive mutual models can render immediately; fresh account-scoped batch facts override stale model data. Metadata requests retain their 15-second deadline and three-second backoff. One unavailable author or malformed optional row cannot discard the rest of a page. Original organic cursors remain available when a page is empty after filtering.

Head controls/stories are retained once, posts deduplicated across pages, and a completed snapshot sorted newest first. Completion requires server EOF or a verified continuously chronological 48-hour boundary, plus resolved friendship data. Incomplete relationship data produces a retry row rather than `That's it`. Resolved late rows are delivered immediately through the original LOCAL controller (`05qX.A0E`), including partial successes before EOF; they do not require another timeline HTTP request. Requests for the same account/author are shared across overlapping pages, with up to two automatic retries after failure. Cached/disk replay preserves native head refresh behavior.

Preload runs only while Home is visible and the selection generation is current. Each accepted cursor is recorded once; an in-flight rejection remains retryable, and a fresh head resets the attempt guard, leaving native scrolling/retries available; preload stops after 90 seconds without treating that timeout as EOF. Switching modes retains independent completed caches. Images/videos continue to use Instagram's native asset cache; the complete metadata snapshot is not a guarantee that every video byte is offline.

Tests cover cold parsing on the main thread without a pre-seeded cache, blocked friendship results, late resolution, duplicates, stories, malformed rows and stock algorithm/favorites pagination. APK instrumentation additionally uses actual Media, LiveTreeMediaDict, User, LiveTreeUserDict, native wrappers, response and parser-context classes initialized with synthetic cached data. It also uses original request, HTTP parameter and delivery classes to check cold delivery with absent/different response IDs, stale native FollowStatusNotFollowing values, native cursors, duplicate removal and complete replay. It does not make authenticated HTTP requests or prove performance on the user's device.

## Local storage and end row

Since 0.4.4, snapshots are held per account and selected relationship mode in memory. A private `files/calma-timeline/<account>-<mode>.snapshot` also stores filtered media metadata with Instagram's original `MediaExtKt.A1h` / `04ve.A00` codec. Atomic replacement and per-record SHA-256 checks prevent a partially written file from replacing a valid cache. No tokens or credentials are stored. Persistent snapshots are reused only within 15 minutes, with matching Reels settings; head controls are taken from the current native response. The disk cache has a 64 MiB metadata budget; exceeding it skips persistence, not posts in the in-memory timeline.

A completed snapshot appends Instagram's own `06qT` end-of-feed model in a `05qw` wrapper. Only the Calma completion ID is drawn with the large `That's it` text and outlined smiley. `06gK.bindView` retains its original path for every other row. The completion is an actual measured feed row, not an overlay. It adapts to the device's light/dark mode and supplies an accessibility label. Recycled rows restore their original native binding path.

## Verified mappings

| Stock member | Purpose |
| --- | --- |
| `02qb.A01` parameters 0–5 | Context, builder, native state, UserSession, request, feed dependencies |
| `02pp.A0H`, `A0G`, `A09`, `A0L` | Request ID, cursor, reason, parameter map |
| `03u7.A01` / `AOA` | Final HTTP map serialization and original parameter setter |
| `05qX.A0C` / `04ss.A02` | Actual client-request delivery and its adapter-facing list |
| `05qX.A0E` | Original LOCAL delivery without network refresh |
| `05qX.A0G` / `A0J` | Original completion callback and guarded pagination |
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


## Reels in relationship feeds (0.4.9)

Friends and Following retain organic Reels under the same relationship, age and chronological rules as photos. Pending Reel authors participate in the same coalesced relationship lookup and immediate local delivery. The disk cache format changes so that earlier snapshots which omitted Reels are not reused.

The Reels setting blocks navigation between clips, not Reel posts. An explicitly opened feed, grid or DM clip remains playable on its own. Paid For you keeps normal Reel pagination. The native pager checks its own viewer configuration so a DM cannot inherit the global For you exception. See `reels-map.json` for the mapped hooks and validation limits.
