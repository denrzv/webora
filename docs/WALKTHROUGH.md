# Webora browser-first walkthrough

This walkthrough demonstrates Webora as a browser first and SiteSkin as an optional, consented
enhancement. It uses only controls in the installed app. The SiteSkin Inspector is useful for
diagnosis, but it is not part of this journey and is never needed to make a site integrate.

## Three kinds of navigation

The controls in one screenshot can belong to three different authorities:

1. **Android system navigation** belongs to the operating system. Depending on the phone, Android
   shows a gesture handle or Back/Home/Recents buttons. Webora respects that area and Android's Back
   contract; it does not draw a replacement for those controls.
2. **Webora browser navigation** belongs to the browser. Its address and security identity,
   Back, Forward, Reload, Home, Tabs, and More controls operate the current browser session. Home and
   ordinary pages share this shell.
3. **SiteSkin site navigation** belongs to the current website only within the validated SiteSkin
   contract. After consent, Bloom Flowers can request its bounded bottom navigation and quick action.
   It cannot hide or restyle the browser-owned origin/TLS identity or remove the browser Back escape.

In particular, Webora's Back button is an in-app browser-history control. It is not an imitation of
Android's system Back control, even when both actions can leave the same page.

## Reference journey

Start with an installed Webora debug APK after onboarding. Existing tabs or browsing records do not
invalidate the walkthrough, but using a new tab makes each result easy to identify.

### 1. Browse an ordinary site

1. From **Home**, tap the address field, enter `example.com`, and use the keyboard's Go action.
2. Wait for the page to load. Confirm the regular Webora shell is visible, including the
   browser-authored `Secure · example.com` identity and browser navigation.
3. Open **More** and select **Add favourite**. This creates a real local Favourite from the
   browser-observed page; it is not demo seed data.

The page at `example.com` controls its page content only. Its title, text, colours, and layout are not
trusted browser identity and are not evidence that regular mode is active.

### 2. Keep it in one tab and open Bloom in another

1. Select **Tabs** in Webora's browser shell.
2. Choose **New tab**. The tab switcher now contains at least the ordinary page and a new Home tab.
3. On Home, confirm `example.com` appears under **Recent sites** or **Favourites**. Open and close the
   tab switcher once if you also want to verify that the ordinary tab retained its own destination.
4. From the Home suggestion, open **Bloom Flowers** (`https://denrzv.github.io`).

Recent sites, history, and favourites are stored locally on the device. They are not telemetry, are
not supplied by a SiteSkin manifest, and are removed by Webora's clear-browsing-data action.

### 3. Consent to the optional SiteSkin enhancement

1. Bloom first loads as an ordinary web page while manifest discovery runs; discovery never blocks
   page rendering.
2. When Webora offers SiteSkin, verify the consent dialog names the complete
   `https://denrzv.github.io` origin, then choose **Allow**.
3. Confirm the integrated surface shows the curved Bloom header and fixed browser dock. Open the
   central Bloom control and verify its separately attributed hub contains Home, Catalog, Cart,
   Profile, and the Call quick action.
4. Open **Happy Days Bouquet** from Popular Picks. Use the fixed browser Back and Forward controls
   to traverse the real page history and confirm the integrated identity/dock remain stable.
5. Confirm browser-owned escape and identity remain visible: Webora's Back/Forward/Tabs/More
   controls and the secure `denrzv.github.io` identity are not replaced by Bloom branding.

If the live manifest is missing, unavailable, invalid, or not allowed, Webora safely remains a
regular browser. Do not use the debug Inspector to force the reference result; fix or retry the live
integration instead.

### 4. Return deterministically to ordinary browsing

1. Select **Tabs** from Bloom's fixed browser dock.
2. Select the retained `example.com` tab.
3. Confirm Webora restores that tab's ordinary security identity and regular navigation shell.
   Bloom's expressive header, dock, hub navigation, and quick action must be absent.
4. Optionally return Home and open the Favourite created in step 1 to finish through the local-data
   path as well.

This switch is stronger evidence than merely loading a different page in the same visual surface:
the ordinary and Bloom tabs retain independent browser history and mode, and the selected tab alone
owns the visible chrome.

## What the hosted screenshots prove

The current canonical Pixel 6 journey contains four guarded frames:

| Frame | What it demonstrates |
|---|---|
| `01-home.png` | Browser-owned Home before entering the reference integration. |
| `02-siteskin-consent.png` | Explicit consent for the complete Bloom origin and a bounded preview. |
| `03-siteskin-integrated.png` | Bloom's expressive header/dock alongside fixed browser identity and escape. |
| `04-regular-browsing.png` | Regular `example.com` identity/navigation after leaving Bloom, with all SiteSkin chrome absent. |

Together, frames 03 and 04 prove the integrated-to-regular layer handoff. Between those captures,
the hosted journey visits Happy Days, exercises Back/Forward, and checks the native hub. It reaches
frame 04 through visible browser Back/address controls rather than the two-tab route above. It does
**not** capture the tab switcher, Recent sites, or Favourites. Those are interactive checks in this
walkthrough, backed by the shipped M8 behavior rather than silently claimed as pixels in the contact
sheet.

See [Android screenshots](SCREENSHOTS.md) for artifact names, acceptance gates, diagnostics, and the
exact limits of visual evidence.
