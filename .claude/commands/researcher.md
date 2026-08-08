# /researcher

Map relevant code, files and risks into `docs/research/<TICKET>.md` before planning.

For SiteSkin work, always include: which origins are involved, what the manifest can influence, and
what must stay browser-controlled.

Runs between `/idea` and `/plan`. `/plan` reads this file — the trust boundary and file list are
decided there, so the map has to exist first.

Set `Status: RESEARCH_READY` when done. The artifact gate blocks `Edit|Write` until it is set.
