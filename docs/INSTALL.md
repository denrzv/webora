# Installing the Webora demo APK

Webora is an Android browser that adapts its chrome to websites publishing a **SiteSkin** manifest.
This page is for installing a demo build on a phone.

## Before you start

**This is a debug build.** It is:

- **debuggable**, and signed with a shared debug key — not a release key;
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
`webora-0.1.0-a1b2c3d-debug.apk`. `versionName` stays `0.1.0` across demo builds, so **the commit in
the file name is what distinguishes two APKs on disk.**

`versionCode` *does* change: it is the number of commits behind the build. That matters because
Android refuses to install an APK whose `versionCode` is not higher than the installed one — so a
newer demo build installs straight over an older one, with no uninstall step. Two consequences worth
knowing:

- Re-running the release workflow on an **unchanged commit** produces the same `versionCode`, by
  design: the same source should give the same artifact. To force a bump without a new commit, run
  the build with `-PweboraVersionCode=<n>`.
- Installing an **older** build over a newer one is still refused by Android. Uninstall first if you
  need to go backwards.

### One-time uninstall for early builds

Upgrading in place also requires both APKs to be signed with the same key, and demo builds are
signed with a **shared debug key committed to the repository** (`app/debug.keystore`) so that a
build from CI and a build from a developer's machine match.

Builds cut before that was pinned were signed with a per-machine key that GitHub Actions generated
fresh on every run. If you installed one of those — anything from release `v0.1.0-7e6b620` or
earlier — Android will refuse the next build with *"App not installed"* or
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`. **Uninstall Webora once**, then install the new build; every
build after that upgrades in place.

## Walk through Webora as a browser

For the complete product tour, follow the
[browser-first reference walkthrough](WALKTHROUGH.md). It starts with ordinary HTTPS browsing, uses
two real tabs and a locally created Recent/Favourite entry, consents to Bloom Flowers, and then
switches back to regular chrome. No developer-only control or hidden gesture is required.

## Seeing SiteSkin work

1. On the home screen, open the suggested integration — **Bloom Flowers**,
   <https://denrzv.github.io>.
2. The page loads as an ordinary website first. That is deliberate: manifest discovery never blocks
   rendering.
3. The browser then asks whether to apply the site's branding, showing the site's full origin. This
   is a per-site decision and you can decline it permanently.
4. Allow it. The top bar takes the site's colours, logo, title and subtitle; a bottom navigation bar
   appears with Home, Flowers, Cart and Account, plus a Call quick action.
5. Tap through the tabs — the active tab tracks the page you are on.

**What to notice, because it is the point of the design:** the site's registrable domain and the TLS
indicator stay visible in the browser's own typography the whole time. No manifest field can hide,
restyle or move them. The site gets colours, a title and a bounded logo slot *beside* its domain,
never instead of it.

Try any other website too. Sites without a manifest behave like an ordinary browser, which is the
other half of the contract. Android's gesture or three-button navigation remains OS-owned; Webora's
labelled Back, Forward, Reload, Home, Tabs and More controls are browser navigation, while Bloom's
bottom bar is bounded site navigation. SiteSkin can replace only that site-navigation slot, never
the browser's security identity or escape control.

## Turning it off

Settings has a global SiteSkin switch, and per-site decisions can be reset individually. Turning the
switch off returns to regular browser chrome immediately, without reloading the page you are on.

## A note on the download link

`denrzv/webora` is a **private** repository, and GitHub serves release assets only to signed-in
users with access to it. A friend opening the Release link gets a 404 rather than a download.

**This is settled and the repository stays private.** Distribution is therefore one of:

- download the APK from the Release and send the file on, or
- grant the person access to the repository, after which the Release link works for them.

Keeping it private is why the release workflow does not advertise a public link, and why the
instructions above start from "the Release link you were sent" rather than a URL anyone can open.
