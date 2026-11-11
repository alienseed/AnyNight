# Changelog

## 1.9.4 — 2026-11-11

一天里的改动合成一个版本发布（1.9.3 ~ 1.9.6 只在本机验证过，没有单独发布）。

**检查更新**

- 兼容性信息页新增「检查更新」。不走 `api.github.com`：请求 `releases/latest`，从 302 后的
  `/tag/vX.Y.Z` 读版本号，读不到再正则正文。直连 → gh-proxy → ghproxy.net → ghfast 四条线路依次尝试，
  下载同样按这四条回退，国内网络可用。
- 有新版可一键下载并调起系统安装器（自带最小 FileProvider，不引入 androidx）。
- 新增 `INTERNET` 与 `REQUEST_INSTALL_PACKAGES` 权限。**这是唯一的联网入口**：不点检查更新就不联网。

**首次使用**

- 第一次打开自动进「使用教程」，并弹窗说明「必须开启全部权限才能正常工作」。
- 教程新增第 2 步「开启无障碍服务」——之前只在首页状态行提示，教程里没有对应步骤，属于漏项。
- 教程里每张卡片的操作按钮统一移到右上角，与标题同一行。

**文案与布局**

- 首页底部按钮恢复居中，顺序改为 `[ENGLISH] [请我喝瓶大水]`。
- 「联系作者」从首页移进「兼容性信息」页底部——出问题的人本来就会先打开这一页。
- 联系作者页：邮箱左侧明文可长按选中，右侧新增「复制」按钮；底部说明改为一句「感谢你的反馈，但作者无法承诺是否回复或多久回复。」
- 捐献页：顶部改为「微信扫码，长按图片可保存到相册」，底部说明改为「此为捐献性质……非常感谢您的捐献！」，两段字号加大。
- 导出日志（原「导出报告」）统一名称：两个入口本来就是同一个实现，改名即可。文件改为存到系统「下载」根目录，文件名以 `AnyNight` 开头。
- Shizuku 页：删除「复制全部链接」，三个下载入口右侧各给 [复制] [打开]；步骤卡片右上角一个「复制全部」，一次复制标题 + 7 步 + 提示。

## 1.9.2 — 2026-11-10

Follow-up to 1.9.1: WeChat opened, but the share screen came up blank.

- 1.9.1 still switched the theme the moment the receiving app came to the front. A global
  configuration change recreates the activity that is drawing — WeChat's image-send screen
  reloaded and never got its image back, so it showed an empty white page.
- The receiving app is now treated as part of the share chain: once a chooser or dialog
  hands over to another app, nothing is switched until you actually leave that app. Sharing
  stays exactly in the theme you started from, and the restore happens when you go home or
  switch to something else.
- Added the dual-app (XSpace) picker to the pass-through list — it is another chooser that
  appears in the middle of the same flow.

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
