# Changelog

## 1.9.1 — 2026-11-10

Sharing fix. Two bugs, one visible symptom: sending a photo from the gallery to WeChat
interrupted the launch and made the screen flip dark → light → dark.

- **Transient windows are no longer treated as "left the app."** The share / open-with
  resolver, permission dialogs and system UI used to count as leaving the target app, so
  AnyNight restored the theme ~0.7 s after the share sheet appeared — right in the middle of
  handing the intent to the receiving app.
- **Accessibility service restarts no longer restore the theme.** When the system recreates
  the service (which it does around theme changes and cross-app launches), `onDestroy`
  restored the theme and the reconnected service came back with no state, leaving the phone
  stuck in the wrong mode. The state is now kept, and a grace timer restores only once you
  have actually left the target app.
- Settle window before switching after a chooser/dialog, plus a 350 ms cooldown between
  switches, so a burst of window events cannot cause a ping-pong.

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
