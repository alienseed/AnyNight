# Changelog

## 1.9 — 2026-11-09

First public release.

- Per-app dark **and** light direction, both at once.
- Event-driven foreground detection (task-stack listener + accessibility + process observer); zero polling while idle.
- Automatic restore on exit, plus crash/reinstall reconciliation, a 45 s watchdog and a one-tap reset.
- Bilingual UI (Chinese / English) that follows the system language, with a manual switch on the home screen.
- “Follow system” guidance in the app picker — the most common reason a picked app does not change (Xiaohongshu and friends).
- Read-only compatibility page with an exportable report.
- Shizuku setup guide with official download plus two mainland-China mirrors.
- “Hide from Recents” gated behind a mandatory lock tutorial, so the card cannot become unreachable.
- Separate notification and “other permissions” entry points.

### Notes

- `minSdk 30` (Android 11). Verified on HyperOS 4.0.7.0 / Android 17.
- Requires Shizuku. There is no root-free way around that on stock Android.
