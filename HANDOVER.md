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
| Android **native** sheet | Built and installed; **the last step is unverified** (see §5) |
| Installed app | `1.1.0-thevelvet.18` — installed on the phone, launches clean |
| Plugin | `1.11.3.0`, deployed and serving |
| Moonfin | **Dropped by the user** ("i dont care about it"); changes sit uncommitted in `~/code/Moonfin-Core`, do not touch |

The one thing outstanding is **confirming that tapping a reply on the phone opens the sheet on
that comment**. Everything up to it is implemented, unit-tested and installed.

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

## 5. PENDING — the one thing left to verify (start here)

**Does tapping a reply on the phone open the native sheet on that comment?**

Expected after `1.1.0-thevelvet.18`: tapping a reply plays *that* episode (natively, no web page
involved) and the native sheet opens on `/Comments/Notifications/Threads/{commentId}`.

How it is wired: `CommentsPanel.onNotificationOpen` → `pendingThread.set(itemId, commentId)` →
pop player → `addFragment<PlayerFragment>(PlayOptions.forSingleItem(id))` →
`PlayerMenus.onQueueItemChanged` → `pendingCommentThread.take(itemId)` → if it matches,
`fragment.openCommentsThread(itemId, commentId)` → `CommentsPanel.showThread` (loads the thread).

If the sheet does **not** open, check in this order:
1. Does `PlayerMenus.onQueueItemChanged` actually run for a player started from arguments? It is
   the only consumer of the pending thread. If it doesn't fire, open the thread from
   `PlayerFragment` once the queue is initialised instead.
2. Does `take()` get the same id? (It normalises, but log both sides.)
3. `showThread` cancels `loadJob` that `show(itemId)` just started — confirm the thread call
   actually lands (a slow comments fetch could overwrite the thread; verify rendering order).

Add temporary `android.util.Log.i("CMTTRACE", …)` lines at those three points, rebuild, and read
with `adb logcat -s CMTTRACE`. (Do this rather than guessing — blind UI driving is what wasted
most of the previous session.)

Also unverified, lower priority:
- "me › them" label rendering in the native list (code in `CommentsAdapter`, string
  `comments_reply_to`), never seen on a device.
- The header bell inside the app's WebView: confirmed present (46×46, badge hidden at 0 unread)
  via the DevTools socket, but not confirmed **by eye**.

---

## 6. Build, sign, install

```bash
cd ~/code/jellyfin-android
KS=$(grep '^keystore.file=' ~/.config/jellyfin-android/keystore.properties | cut -d= -f2-)
KP=$(grep '^keystore.password=' ~/.config/jellyfin-android/keystore.properties | cut -d= -f2-)
KA=$(grep '^signing.key.alias=' ~/.config/jellyfin-android/keystore.properties | cut -d= -f2-)
KW=$(grep '^signing.key.password=' ~/.config/jellyfin-android/keystore.properties | cut -d= -f2-)
export KEYSTORE_FILE="$KS" KEYSTORE_PASSWORD="$KP" SIGNING_KEY_ALIAS="$KA" SIGNING_KEY_PASSWORD="$KW"

./gradlew :app:assembleProprietaryRelease --no-daemon -q -Pjellyfin.version=1.1.0-thevelvet.19
adb -s <device> install -r app/build/outputs/apk/proprietary/release/jellyfin-android-v1.1.0-thevelvet.19-proprietary-release.apk
```

- Never `.` source `keystore.properties` (parse with grep/cut).
- Signing cert must stay `CN=jellyfin-fork`, SHA-256 `f921d1cc…71b0`, or the app won't upgrade in
  place. Check with `apksigner verify --print-certs`.
- A **pre-existing lint error** is printed on every build and does **not** block it:
  `comments_button is not a sibling in the same ConstraintLayout`
  (`exo_player_control_view.xml`). Worth fixing eventually, it is noise left from the OSD comment
  button work.

**Tests** (the release variant has no unit-test task — use debug):

```bash
./gradlew :app:testProprietaryDebugUnitTest
# results: app/build/test-results/testProprietaryDebugUnitTest/*.xml
```

Tests live in `app/src/test/java/…` (the config had JUnit5 + Kotest + MockK wired but **no test
source set until this session**): `PendingCommentThreadTest` (9), `PlayOptionsSingleItemTest` (2).

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
| Installed app | `1.1.0-thevelvet.18` (versionCode 1010018) |
| Plugin | `1.11.3.0` |
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
