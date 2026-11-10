# -*- coding: utf-8 -*-
import json
import os

BASE = os.path.dirname(os.path.abspath(__file__))

BODY = """## 中文

**修复 1.9.1 引入的问题：分享到微信后，微信的分享界面是一片空白。**

安装包约 322 KB，`minSdk 30`（Android 11+）。覆盖安装即可，设置和已选应用都在。

### 原因

1.9.1 修好了「微信起不来」，但还剩一步没做对：微信一到前台就切换主题。系统切换深浅色会触发全局配置变化，正在绘制的那一层界面会被重建——微信的图片发送界面重建后图片加载不回来，就剩下一个空白页。

### 这一版

- **分享流程里完全不切换。** 选择器把控制权交给另一个应用之后，那个应用在整段分享过程中都不动，主题保持你出发时的那一档；等真的离开它（回桌面或换到别的应用）才还原。
- 双开/工作资料的选择框（`com.miui.securitycore`）也加入穿透列表，它同样会出现在分享流程中间。

### 实测

小米 17 Ultra（HyperOS 4.0.7.0 / Android 17）：相册设为深色 → 分享面板保持深色 → 微信联系人选择界面正常显示、正常深色 → 返回 → 回桌面还原浅色。全程一次切换都没发生，界面没有重建。

### 校验

```
SHA-256  c3c8f0fb10a863ff628a16f5fb24ec712e5487c27336adf336e46ed4529c9b76
MD5      e5347f1e626412da05d54bcf8ceda6a1
```

国内镜像（随本 Release 自动生效）：

- https://gh-proxy.com/https://github.com/alienseed/AnyNight/releases/latest/download/app-v1.9.2.apk
- https://ghproxy.net/https://github.com/alienseed/AnyNight/releases/latest/download/app-v1.9.2.apk
- https://ghfast.top/https://github.com/alienseed/AnyNight/releases/latest/download/app-v1.9.2.apk

---

## English

**Fixes a 1.9.1 regression: after sharing to WeChat, WeChat's share screen was blank.**

~322 KB, `minSdk 30` (Android 11+). Installs straight over any 1.9.x build.

### Why

1.9.1 fixed "WeChat won't open", but still switched the theme the moment WeChat came to the front. A system light/dark change triggers a global configuration change, which recreates whatever is drawing — WeChat's image-send activity was recreated and never got its image back, leaving an empty page.

### This build

- **No switching at all during a share.** Once a chooser hands over to another app, that app is left alone for the whole chain; the theme stays exactly as it was when you started. The restore happens only when you actually leave it (home or another app).
- The dual-app / work-profile picker (`com.miui.securitycore`) is now passed through too — it shows up in the middle of the same flow.

### Tested

Xiaomi 17 Ultra (HyperOS 4.0.7.0 / Android 17): gallery set to dark → share sheet stays dark → WeChat's contact picker renders normally, in dark → back → home restores light. Zero switches during the flow, nothing recreated.

### Checksums

```
SHA-256  c3c8f0fb10a863ff628a16f5fb24ec712e5487c27336adf336e46ed4529c9b76
MD5      e5347f1e626412da05d54bcf8ceda6a1
```
"""


def main():
    payload = {
        "tag_name": "v1.9.2",
        "target_commitish": "master",
        "name": "AnyNight v1.9.2 — 分享不再空白 / no more blank share screen",
        "body": BODY,
        "draft": False,
        "prerelease": False,
    }
    out = os.path.join(BASE, "release.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False)
    print("written", os.path.getsize(out), "bytes")


if __name__ == "__main__":
    main()
