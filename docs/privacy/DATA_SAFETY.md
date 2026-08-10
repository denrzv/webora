# Privacy and Google Play Data safety mapping

Status: CURRENT
Date: 2026-08-10

This mapping describes the code in this repository. It is release input, not a substitute for
rechecking the final bundle and every distribution service before Play submission.

## Product posture

Webora contains no analytics, advertising, attribution, crash-reporting, telemetry, or remote
preference-sync SDK. Telemetry defaults to zero because there is no telemetry implementation or
opt-in surface. Browser settings and SiteSkin consent decisions remain on the device.

Web pages loaded by the user are third-party content and can receive ordinary web requests, cookies,
and storage through Android WebView. Webora also fetches a site's SiteSkin manifest and same-origin
PNG/WebP brand asset. Those functional transfers go only to the user-selected site/origin; Webora
does not forward browsing activity to a Webora-operated analytics service.

## Implemented local data

| Data | Purpose | Persistence | User control |
|---|---|---|---|
| WebView cookies and Web Storage | Website functionality | Android System WebView stores | Clear browsing data |
| WebView cache, form state, and live history | Browser functionality | WebView-managed | Clear browsing data |
| SiteSkin Allow/Never decision | Browser-owned consent | Local preferences, full canonical origin key | Reset per origin or clear browsing data |
| Global SiteSkin enabled switch | App preference | Separate local preferences | Settings; preserved by clear browsing data |
| Onboarding completion | App preference | Separate local preferences | Preserved by clear browsing data |
| Validated manifest cache | Performance | Memory only | Clear browsing data; also lost on process death |

The app declares network access but no location, contacts, camera, microphone, phone, advertising,
or storage permission. Downloads use Android `DownloadManager`; uploads use the system document
picker and return only the user-selected `content:` URI to the requesting page.

## Play Data safety draft

For the current app code, Webora itself does not collect or share Play Data safety user-data
categories with a developer-controlled backend. Website data processed as an on-device browser and
functional network traffic to a user-selected website must be described consistently with Google's
browser/on-device processing guidance when the final form is completed.

Before release (`PLAY-003`), verify:

1. the release dependency graph still has no telemetry or advertising SDK;
2. the hosted privacy policy states the same WebView and website-transfer behavior;
3. any future crash reporting, sync, update service, or Webora-operated endpoint is reflected here
   and requires an explicit privacy decision before integration;
4. Play Console answers match the final signed bundle, not this development snapshot.
