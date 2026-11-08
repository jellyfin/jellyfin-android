# HANDOVER — comment notifications on the Android app

Written at the end of a long session working on "replies to your comments" in the Velvet
Jellyfin stack. Read this before touching anything: most of the confusion in that session came
from not knowing **which of three comment UIs** a symptom belonged to.

---

## 1. TL;DR — where things stand

| Thing | State |
|---|---|
| Server-side reply notifications | Done long ago (`/Comments/Notifications*`), covered by tests |
| Web client (desktop browser) | Done and verified: header bell + replies overlay, click-through to episode + play + comment |
| Android **native** sheet | Built `1.1.0-thevelvet.35` (served by the plugin; phone install pending); the mark-read race, the notification-switch crash, the followed-thread handoff, the emoji-keyboard dismissal and comment editing, @mentions and keyboard GIFs are fixed (animated GIFs now decode through Coil's GIF decoder) (see §5) |
| Installed app | `1.1.0-thevelvet.32` on the phone; `1.1.0-thevelvet.35` is served by the plugin. Launcher label "Velvetok"; the artwork is used for the launcher, splash, connect-screen logo and notification/car icons |
| Plugin | `1.2.0.5`, released and deployed |
| Moonfin | **Dropped by the user** ("i dont care about it"); changes sit uncommitted in `~/code/Moonfin-Core`, do not touch |

The one thing outstanding is **confirming that tapping a reply on the phone opens the sheet on
that comment**. Everything up to it is implemented, unit-tested and installed. The app could not
be smoke-tested over the WebView in the install session because the phone's WebView was showing
`ERR_NAME_NOT_RESOLVED` for `thevelvet.space` (the phone could ping the host and the domain);
retry with the phone unlocked and the server reachable.

---

## 2. The three comment UIs — know which one you are looking at

1. **Web plugin** (`~/code/jellyfin-comments`) — JS/CSS injected into jellyfin-web by a server
   middleware. Renders the comment sheet in the **player**, the **header bell**, and the replies
   overlay. Runs in a browser **and inside the Android app's WebView**.
2. **Android native sheet** (`~/code/jellyfin-android`, `comments/` package) — a Kotlin bottom
   sheet inside the native player. **This is the one the user sees on the phone**; it is not the
   web one.
3. **Moonfin** (Flutter) — out of scope, dropped.

Consequence that caused two bugs already: the web plugin opening its own panel on the phone
opens it **behind the native player**, so nothing appears. Anything user-visible on the phone must
be done natively, or by driving the app's own fragments.

---

## 3. What is done (commits are in the app repo unless noted)

**Android app**
- `de126b24` — replies list inside the native sheet: bell in the sheet header with unread badge,
  swaps between comments and replies, tapping marks read.
- `d2bbf54e` — tapping a reply opens that episode and plays it.
- `7a95800b` — replies can be replied to (server already flattens to one level, like TikTok).
- `c0db1fc6` — "me › them" label when a reply answers another reply (server now stores
  `replyToCommentId` / `replyToUserId`; DTO exposes `replyToUsername`).
- `163e5f37` — **WebView inspection enabled in release builds** (see §7 — this is how you debug).
- `c39b27e0` — fixed ids never matching: plugin sends `32hex`, `UUID.toString()` gives `dashed`
  → the sheet never opened. Ids are now compared normalised.
- `e5dc0287` — followed comment carried across the player handoff (`PendingCommentThread`).
- `2e26913e` — following a reply plays the item **directly** (`PlayOptions.forSingleItem`).

**Web plugin** (`~/code/jellyfin-comments`)
- `be40ab6` — comment button mounts on Jellyfin 12 at all (floating fallback; React player has no
  control bar), and the item id is resolved from the route (client reports no `NowPlayingItem`).
- `54aa205` — replies moved from a home-page card to a **header bell** (user asked for this).
- `a5115a6`, `e4ffb75` — the bell was injected into the hidden `.skinHeader`; it now goes into the
  visible **MUI toolbar**, borrowing a sibling's classes so it is pixel-identical (46.3×46.3).
- `f4263f7`, `6c816e1`, `989e11e` — click a reply → open episode → play; retries the Play click
  until playback starts; **clicks only once when a native player is present** (otherwise it
  relaunched the native player every 1.5s = the play/replay loop the user reported).
- `de8e613` — replying inside a followed thread posts against **that comment's** video, not
  whatever is playing (this was the "comment being replied to does not exist" error).
- `d6c5a49` — edit box got its own layout (Save was the composer's full-size pill).
- `cac2d5e` — "me › them" label in the web list too.

**Earlier, still relevant**
- `1d86c3e1` — sheet hides in PiP/freeform/small windows.
- `189eec9b`, `c75a9cc9`, `f4b2c38d` — keyboard/composer, keyboard dismissal, circular avatars.

---

## 4. Why the last few bugs happened (do not re-introduce them)

- **`ReferenceError: ApiClient is not defined`** — the injected script is `defer` and can run
  before jellyfin-web creates `window.ApiClient`. It threw inside `init()` and **everything after
  it silently never ran** (no bell, no polling, `STATE.enabled` undefined) with no retry. Fixed with
  `whenApiReady()` + a retrying `fetchCapabilities`. Any new top-level work in that file must sit
  after the gate.
- **Wrong episode playing** — playback used to be started by asking the web client to open the
  item page and press Play. The details page **keeps the previous item's buttons in the DOM** while
  loading, so the press landed on the old page. Don't detect "right page" via `[data-id]`: the
  season's episode cards carry a `data-id` for every episode (176 of them). The app now builds
  `PlayOptions` itself; the web gate is scoped to `.mainDetailButtons [data-id]`.
- **Ids** — the plugin serialises a Guid as **32 hex chars, no dashes**; Kotlin's `UUID.toString()`
  is **36, dashed**. Always normalise before comparing (`PendingCommentThread.normalize`).
- **Fragment ordering** — pop the current player **before** starting the next one, otherwise the
  pop can remove the player that was just added (episode flashes and drops back).

---

## 5. Notification follow flow — verified on the emulator

Tapping a reply now plays that episode and opens the native sheet on its thread. Verified on the
`pana` emulator by moving between three notifications across E02 and E31: each tap switched
episode, opened the right parent/reply thread, and produced no new crash.

How it is wired: `CommentsPanel.onNotificationOpen` marks the row read locally, fires the POST on
an app-lived scope, then either opens the thread in the current player (same item) or starts a
new `PlayerFragment` with `EXTRA_COMMENT_ID` / `EXTRA_COMMENT_ITEM_ID` in its arguments.
`PlayerFragment` consumes those args once its media source is ready.

The previous crash — `Fragment PlayerFragment not associated with a fragment manager` — came from
touching `parentFragmentManager` after a suspension, when the old player had already been popped.
The manager is now captured before any suspension and the navigation is synchronous.

@mentions are parsed on the server and create `mention` notifications; the native composer suggests usernames while typing after `@`, keeps the field visible with up to three suggestions, and renders mentions bold in velvet red. GifEditText advertises image/gif to the keyboard, uploads the selected GIF to `/Comments/Media`, and the comment renders the GIF; GIF-only comments are allowed. Comment editing uses the same comment DTO permission flags as the web (`canEdit` / `canDelete`, falling back to `isOwn` for older servers). The native Edit button opens an AlertDialog, PUTs `/Comments/{id}`, and updates the row in place.

The web header bell uses the same path on Android: `Web/comments.js` detects
`NativePlayer.openCommentsThread(itemId, commentId)` and emits `ActivityEvent.OpenCommentsThread`,
which starts the native player with the comment arguments. Without it, the web thread panel opened
behind the native player and looked like nothing happened.

Also unverified, lower priority:
- "me › them" label rendering in the native list (code in `CommentsAdapter`, string
  `comments_reply_to`), never seen on a device.
- The header bell inside the app's WebView: confirmed present (46×46, badge hidden at 0 unread)
  via the DevTools socket, but not confirmed **by eye**. It is now always mounted, including when
  notifications are off.

Native unit tests now cover the API contract (`CommentModelsTest`), relative time labels
(`CommentsAdapterFormatRelativeTimeTest`), pending-thread concurrency
(`PendingCommentThreadConcurrencyTest`), the existing pending-thread cases and `PlayOptions`.
Run them with `./gradlew :app:testProprietaryDebugUnitTest`; release builds have no unit-test task.

---

## 6. Build, sign, install

```bash
cd ~/code/jellyfin-android
KS=$(grep '^keystore.file=' ~/.config/jellyfin-android/keystore.properties | cut -d= -f2-)
KP=$(grep '^keystore.password=' ~/.config/jellyfin-android/keystore.properties | cut -d= -f2-)
KA=$(grep '^signing.key.alias=' ~/.config/jellyfin-android/keystore.properties | cut -d= -f2-)
KW=$(grep '^signing.key.password=' ~/.config/jellyfin-android/keystore.properties | cut -d= -f2-)
export KEYSTORE_FILE="$KS" KEYSTORE_PASSWORD="$KP" SIGNING_KEY_ALIAS="$KA" SIGNING_KEY_PASSWORD="$KW"

./gradlew :app:assembleProprietaryRelease --no-daemon -q -Pjellyfin.version=1.1.0-thevelvet.35
adb -s <device> install -r app/build/outputs/apk/proprietary/release/jellyfin-android-v1.1.0-thevelvet.35-proprietary-release.apk
```

- Never `.` source `keystore.properties` (parse with grep/cut).
- Signing cert must stay `CN=jellyfin-fork`, SHA-256 `f921d1cc…71b0`, or the app won't upgrade in
  place. Check with `apksigner verify --print-certs`.
- A **pre-existing lint error** is printed on every build and does **not** block it:
  `comments_button is not a sibling in the same ConstraintLayout`
  (`exo_player_control_view.xml`). Worth fixing eventually, it is noise left from the OSD comment
  button work.

**Forgejo release** (required for every Android build):

```bash
cd ~/code/jellyfin-android
fj -H "$FORGEJO_SITE" release create 'Velvetok 1.1.0-thevelvet.35' \
  -r maikelthedev/velvetok -T v1.1.0-thevelvet.35 \
  -b 'Velvetok Android 1.1.0-thevelvet.35.' \
  -a 'app/build/outputs/apk/proprietary/release/jellyfin-android-v1.1.0-thevelvet.35-proprietary-release.apk:Velvetok-Android-v1.1.0-thevelvet.35.apk'
```

The blog's App page is `~/code/velvet_blog/pages/03.app/default.md`; update its
download URL to the new tag when the release is published.

**Tests** (the release variant has no unit-test task — use debug):

```bash
./gradlew :app:testProprietaryDebugUnitTest :app:testLibreDebugUnitTest
# results: app/build/test-results/testProprietaryDebugUnitTest/*.xml
```

JVM tests live in `app/src/test/java/…`: `PendingCommentThreadTest` (9),
`PlayOptionsSingleItemTest` (2), `CommentModelsTest` (4),
`CommentsAdapterFormatRelativeTimeTest` (4), `PendingCommentThreadConcurrencyTest` (1),
`CommentsWindowPolicyTest` (6), `CommentIdsTest` (3).

The instrumented bell-layout test runs locally on the `pana` AVD:

```bash
export ANDROID_SDK_ROOT=/home/maikel/android-sdk
export LD_LIBRARY_PATH=/nix/store/d6ygipmny4pw7h61b255yafbnvqjbmnx-libx11-1.8.14/lib:$LD_LIBRARY_PATH
$ANDROID_SDK_ROOT/emulator/emulator -avd pana -no-window -no-audio -gpu swiftshader_indirect -no-snapshot &
export ANDROID_SERIAL=emulator-5554
./gradlew :app:connectedProprietaryDebugAndroidTest
```

Instrumented tests live in `app/src/androidTest/java/…`: `CommentsBellLayoutTest` asserts the
native bell is visible at zero unread, and `CommentsWindowPolicyInstrumentedTest` checks the real
emulator display metrics: a full window with an emoji IME stays visible while a genuinely small
window still hides.

---

## 7. Debugging the app's WebView (this is the important tooling)

Release builds now allow WebView debugging (`WebView.setWebContentsDebuggingEnabled(true)` in
`JellyfinApplication`). Anything rendering inside the app can be inspected from the host:

```bash
adb devices                                     # find the device
adb shell cat /proc/net/unix | grep webview_devtools_remote
adb forward tcp:9223 localabstract:webview_devtools_remote_<pid>
curl -s http://127.0.0.1:9223/json | head        # lists the page target

python3 scripts/wv_eval.py 'location.hash'       # evaluate JS in the phone's page
python3 scripts/wv_watch.py 16                   # reload and print thrown exceptions
```

`scripts/wv_eval.py` is a stdlib-only WebSocket client (no `wscat`/`node ws` on this box);
`scripts/wv_watch.py` is what found the `ApiClient` ReferenceError. Both are committed.

---

## 8. Getting the phone on ADB (this cost real time)

- The phone is a **Nothing A059P**, package `space.thevelvet.jellyfin`.
- **Wireless debugging picks a new random port every time it is toggled.** The port is not stable.
- `172.22.1.100` is its **ZeroTier** address and is often unreachable. The reliable path is the
  **LAN**: the machine is `192.168.3.2`, the phone was `192.168.3.4` on port `40757`.
- `adb mdns services` does **not** work here (`unknown host service 'mdns:services'`). Discover the
  port by scanning:

```bash
python3 - <<'PY'
import socket, concurrent.futures
def probe(ip):
    s = socket.socket(); s.settimeout(0.4)
    try: s.connect((ip, PORT)); return ip
    except Exception: return None
    finally: s.close()
PORT = 40757   # the port the user reports
with concurrent.futures.ThreadPoolExecutor(max_workers=128) as pool:
    print([r for r in pool.map(probe, [f"192.168.3.{i}" for i in range(1, 255)]) if r])
PY
```

- If `adb connect` says **offline**, the phone is listening but not authorising — accept the
  "Allow wireless debugging?" prompt, or toggle wireless debugging and take the new port.

---

## 9. Other gotchas worth knowing

- **Hash navigation does not reload the page.** Navigating `#/home` → `#/details` keeps the loaded
  document, so a tab (or the app's WebView) can run an old bundle indefinitely. Force a reload
  (`location.reload()` / force-stop the app) after deploying plugin changes. The injected bundle is
  keyed by version (`?v=<plugin version>`), so **bump the plugin version** or the browser keeps the
  old file for a year.
- Plugin deploy: `cd ~/code/jellyfin-comments && bash scripts/dev-install.sh <version>` then
  `sudo systemctl restart jellyfin`. Old plugin directories are not always removed.
- Jellyfin 12's web client is React/MUI. `.skinHeader` still exists but is **zero-size**; injecting
  there is invisible. The visible toolbar is `.MuiToolbar-root`, its icon buttons live in a
  `.MuiBox-root`, and MUI styles come from an emotion class (`css-xxxx`), so **copy a sibling's
  className** rather than writing MUI classes by hand.
- The JavaScript Injector wraps each user script in `try/catch` and logs via `console.error`, so a
  runtime error is invisible to `window.onerror` and to `node --check`. Verify injected-script
  changes by executing the **served** bundle and reading `console.error`.
- nixconf upserts `most-commented.js` and `userscripts-link.js` from the nix store on **every
  Jellyfin start**; editing those through the plugin API is reverted by the next restart. Deploy
  with `sudo nixos-rebuild -I nixos-config=$HOME/code/nixconf/machines/i710700/configuration.nix --sudo switch`.
- `~/code/translations/Web/admin-menu.js` is also injected (not served by its plugin) and was
  moved off the Ratings plugin's `#ratingsButtonGroup` onto `.mainDetailButtons` — keep the repo
  copy and the injector copy in step.

---

## 10. Versions / where things are

| Item | Value |
|---|---|
| Installed app | `1.1.0-thevelvet.32` (versionCode 1010032), label Velvetok |
| Plugin | `1.2.0.5` |
| Server | `https://thevelvet.space` (127.0.0.1:8096), Jellyfin 12.1, user `maikel` |
| Admin token used for testing | in `~/.config/fish/private_vars/` and the session notes |
| Plugin repo | `~/code/jellyfin-comments` |
| App repo | `~/code/jellyfin-android` |
| Moonfin | `~/code/Moonfin-Core` — **uncommitted, dropped by the user** |

Confirmation used throughout for the server API (works on 12.1 `[Authorize]` routes):

```bash
T=<token>
curl -s -H "Authorization: MediaBrowser Token=\"$T\"" \
  "http://127.0.0.1:8096/Comments/Notifications?limit=5"
```
