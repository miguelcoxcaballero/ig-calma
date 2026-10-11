# Account security review — 0.4.19

A user reported a real security lock after using 0.4.18 and later recovered the account. This review did not access their account, email, credentials or traffic. The cause of the lock is unknown; timing alone does not establish causation.

## Evidence and change

The extension automatically advanced every Following cursor after the native completion callback while Home remained visible. There was no overall page budget. Explore also requested friendship information for up to 100 unknown authors per response, in batches of 50, with two workers and a short generic failure cooldown. These added requests were unnecessary for displaying an already received page and could increase account activity.

0.4.19 removes the extension's network pagination and friendship requests entirely. The original Instagram controller owns requests, retries, rate limits and security challenges. Calma only filters received models and replays locally cached rows. The unused older independent timeline transport has also been removed. No device, signature, session, IP or attestation spoofing is added; login and security checks are preserved.

Following keeps received posts and native pagination for normal scrolling. It no longer downloads the entire 48-hour window automatically. Explore uses the requesting account's locally known relationship state; unknown authors stay hidden. An empty filtered Explore page has its continuation cleared so the native adapter cannot automatically drain more recommendations. Typed searches keep their original pagination and can include accounts the user does not follow.

## Limits and public sources

Meta describes account protection using multiple signals and verification challenges, but these sources provide neither the reason for this user's lock nor a guarantee that a modified client will be accepted:

- [Designing Account Security Across Our Apps](https://about.fb.com/news/2022/12/designing-account-security-across-our-apps/): suspicious-activity signals and access verification.
- [Scraping by the Numbers](https://about.fb.com/news/2021/05/scraping-by-the-numbers/): rate/data limits and controls on unauthorized automation.
- [Keeping Instagram Safe and Secure](https://about.fb.com/news/2021/07/keeping-instagram-safe-and-secure/): login alerts, two-factor authentication, recovery contacts and checking authentic Instagram emails.

The app has a different package and signing identity from official Instagram. Removing extra activity does not make it an official or undetectable client. Anyone who needs to minimize the risk from a modified client should use official Instagram. Keep 2FA enabled and review unfamiliar sessions there; no password or recovery code is needed by this project.

## Verification

`native/tests/pager.py` drives production lifecycle/replay code against a native pagination trap; automatic calls must remain zero. `native/tests/network_safety.py` compiles the real relation code against an exact stock-request factory spy and tests repeated/concurrent invocations, account isolation and later native cache updates. Feed/Discover tests exercise user-driven page delivery, snapshots, empty Explore stopping and unrestricted typed searches. APK signature, original native library/class preservation and Android emulator checks are recorded separately for the release. Logged-out tests cannot establish whether Meta will flag a real account.
