# Installing the Webora demo APK

Webora is an Android browser that adapts its chrome to websites publishing a **SiteSkin** manifest.
This page is for installing a demo build on a phone.

## Before you start

**This is a debug build.** It is:

- **debuggable**, and signed with Android's default debug key — not a release key;
- larger and slower than a release build would be (~14 MB, unminified);
- carrying the **SiteSkin Integration Inspector**, a developer panel that shows what the browser
  decided about a site's manifest.

That combination is right for a build handed to people you know and wrong for general distribution.
Do not post the file publicly.

Requirements: **Android 8.0 (API 26) or newer**. The app requests one permission, `INTERNET`, and
nothing else — no contacts, no storage, no location.

The application id is `app.webora.browser.debug`. The `.debug` suffix means it installs alongside
any future release build rather than replacing it.

## Installing

1. **Download the APK** from the Release link you were sent, using the phone's browser.
2. Open it — from the notification shade, or from Files → Downloads.
3. **Android will refuse the first time.** You will see something like *"For your security, your
   phone is not allowed to install unknown apps from this source"*. This is expected for any app not
   installed from the Play Store, and it is the step most people stop at.
   - Tap **Settings** in that dialog.
   - Turn on **Allow from this source** for the app you are installing from (usually Chrome or
     Files).
   - Press back. The install proceeds.
4. Confirm the install. Android may run a scan and warn that the app was not checked by Play
   Protect — expected for a sideloaded build.
5. Open **Webora**.

You can turn "Allow from this source" back off afterwards; the installed app is unaffected.

### Checking you got the right file

The asset name carries the version and the commit it was built from — for example
`webora-0.1.0-a1b2c3d-debug.apk`. `versionName` and `versionCode` do not change between demo builds,
so **the commit in the file name is the only thing that distinguishes two APKs.**

## Seeing SiteSkin work

1. On the home screen, open the suggested integration — **Bloom Flowers**,
   <https://denrzv.github.io>.
2. The page loads as an ordinary website first. That is deliberate: manifest discovery never blocks
   rendering.
3. The browser then asks whether to apply the site's branding, showing the site's full origin. This
   is a per-site decision and you can decline it permanently.
4. Allow it. The top bar takes the site's colours, logo, title and subtitle; a bottom navigation bar
   appears with Home, Catalog, Cart and Profile, plus a Call quick action.
5. Tap through the tabs — the active tab tracks the page you are on.

**What to notice, because it is the point of the design:** the site's registrable domain and the TLS
indicator stay visible in the browser's own typography the whole time. No manifest field can hide,
restyle or move them. The site gets colours, a title and a bounded logo slot *beside* its domain,
never instead of it.

Try any other website too. Sites without a manifest behave like an ordinary browser, which is the
other half of the contract.

## Turning it off

Settings has a global SiteSkin switch, and per-site decisions can be reset individually. Turning the
switch off returns to regular browser chrome immediately, without reloading the page you are on.

## A note on the download link

While `denrzv/webora` is a **private** repository, GitHub serves release assets only to signed-in
users with access to it. A friend opening the Release link will get a 404 rather than a download.

Three ways round it, none of which the release workflow can decide:

| Option | Trade-off |
|---|---|
| Make the repository public | The link works for anyone. The whole source becomes public. |
| Send the APK file directly | Works today with no repository change; loses the reproducible link, and you are re-sending a 14 MB file each time. |
| Publish the Release from a public repository instead | Puts an app binary in a repository that exists for something else. |

Until one is chosen, download the APK yourself from the Release and pass the file on.
