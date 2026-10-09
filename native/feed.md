# Native feed integration

The code in `src/es/calma/instagram/nativeapp/` and `patches/FeedHooks.kt` is original Calma code. The mappings below were derived from the downloaded stock Instagram 439.0.0.37.89 APK, not from a third-party feature patch. The patch refuses a different version or an unexpected verified model shape.

## Following and the 48-hour window

`NativeFeed.parameters` selects Instagram's own `following` main-feed source. In the stock DEX, `X.06yP` maps both the `FOLLOWING` and `RECENTS` selections to `following` and `feed_timeline_following`. Instagram still owns authenticated requests, pagination, media caching and RecyclerView rendering.

The default Following mode performs no additional friendship requests and never waits for a lookup. The response's `pagination_source`, or a matched request ID when that field is absent, identifies this native Following source. Explicit native relationship properties are respected when present.

Posts use their native `taken_at` value in seconds, retain the inclusive last 48 hours, and are sorted from newest to oldest within each response. Missing dates are excluded rather than invented. Dates more than five minutes ahead of the device clock are excluded. There is no 20-post or 100-post limit. Coauthor relationships are considered and duplicate IDs within a page are removed. Instagram retains its ordinary identity-based handling of repeated media across pages.

Native controls keep their original positions relative to retained post slots; sorting does not move a leading story/control row to the end. Known recommendation and cross-app units are removed, while neutral controls, follow requests and take-a-break controls remain. The response disables later client-side insertions in filtered modes.

Pagination stops only when every nonempty media representation of a native Following page is entirely older than the window and the observed stream remains chronological. An empty filtered page, unknown timestamp, ranked source, disagreement between response representations, or an out-of-order page is insufficient to declare the end. Account and head/tail request metadata isolate chronology tracking. A new native head request starts a fresh chronology.

Global chronology relies on Instagram's native Following stream. Sorting one response cannot move a late post above posts already rendered from earlier responses. If an out-of-order page is observed, Calma keeps pagination available rather than dropping that late post or claiming that it has reached the time boundary. Server completeness and ordering still need verification with a real account.

## Stock mappings

| Stock member | Meaning |
| --- | --- |
| `X.02qb.A01` / request `X.02pp.A0L` | Main-feed parameter map |
| request `A0H`, `A0G` | Request ID and requested cursor |
| `X.02px.unsafeParseFromJson` | Main-feed response parser |
| parser `X.03gD.A01` | Actual requesting `UserSession` |
| response `X.07do.A0S`, `A0U` | Feed-item and direct-media representations |
| response `A0O`, `A0P` | Pagination source and response request ID |
| response `A0a`, `A0W`, `A0N` | More available, auto load more, next cursor |
| response `A0E`, `A08` | Disable client insertions, suggested users |
| feed item `X.05qw.A0A()` | Native media accessor after item initialization |
| `Media.A04` | Native `LiveTreeMediaDict` |
| media dictionary `A6X()`, `A7W()`, `getId()` | Taken-at seconds, product type, stable media ID |
| media dictionary `A33()`, `A8F()` | Author and coauthors |
| `User.A00.C8H()` | Nullable native FriendshipStatus |
| friendship `C66()`, `C5v()` | Following and followed-by |
| user dictionary `C5s()`, `A1J()` | Alternative native following status and followed-by |

## Automatic mutual checks

`NativeRelations` reads the actual account's `UserCache`. Missing properties remain unknown. `NativeRelationLookup` uses the stock `X.0BnR.A04` request for `friendships/show_many/`, requesting `include_followed_by`. It reuses Instagram's authentication and native parser without extracting credentials.

The stock batch parser can leave a user's FriendshipStatus null or have no cached User at all. Calma therefore captures the verified account/user row from `X.0ICa` itself: `X.0BnY.A0H` is following and `A02` is followed-by. This small memory cache is account-scoped and expires after 15 minutes. It never turns an absent followed-by value into a cached negative.

DM callers use `resolveMutual` asynchronously, recheck the result, and resume the original action only after confirmation. Duplicate requests are coalesced. A request has a 15-second deadline and cancellation; failed, incomplete or timed-out attempts pause subsequent account queries for five minutes. The native network request remains outside the UI thread.

The optional mutual-feed mode may wait up to 2.5 seconds off the UI thread for missing metadata. If that request is still unresolved, or a cached feed is parsed on the UI thread, unverified posts remain excluded until the next native feed refresh. Automatic replay of such posts into an already delivered response is not established; guessing a refresh/adapter call could reorder or duplicate content. This is a known limitation of the optional mutual mode, not a false negative stored as relationship data. The default Following feed has no dependency on this lookup.

## Verification

- `python native/tests/feed.py`: 31 regression assertions cover more than 100 recent posts, the exact time boundary, dates, page sorting, duplicates, multiple list representations, request-source fallback, controls, coauthors, account isolation and incomplete friendship facts.
- `python native/tests/verify_feed_hooks.py APK --morphe TOOL.jar`: disassembles the built APK and checks actual branch destinations for response filtering, Following selection and batch relationship capture.
- Hook insertion must preserve branch labels. Inserting code *before* a branch-targeted return left the old return as the destination in an initial build. The final patch replaces the original instruction with the hook and adds the original operation afterwards; the bytecode check detects regressions of this failure.

These checks establish policy behavior and patch control flow. They do not replace a signed APK test on a device with a real logged-in Instagram account.
