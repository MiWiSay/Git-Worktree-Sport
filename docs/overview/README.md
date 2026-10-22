# Git Worktree Sport Overview 素材

本目录维护插件的英文 Marketplace Overview、图文教程和媒体素材。Overview 本身采用静态 HTML，默认使用英语，不在页面内伪造语言切换控件；Marketplace 的多语言展示由平台页面能力决定。

## 文件说明

- `description.html`：复制到 JetBrains Marketplace 插件详情的 Overview/Description 区域的静态 HTML。
- `index.html`：本地预览用的完整图文教程，包含已批准的 Banner 和六张作者提供的参考截图。
- `assets/banner.png`：用户已批准的宣传 Banner。它是宣传插图，不是实际插件界面截图。
- `assets/*.png`：作者提供的界面参考截图；部分截图来自较早的双语言版本，页面已明确标注，不将旧截图伪装成当前版本。
- `make-media.java`：把原图排版为统一的 1600×1000 Marketplace 媒体幻灯片的 Java source-launch 工具，不属于发布素材。

## 生成媒体图

```powershell
& 'C:/Program Files/Android/Android Studio Panda 3/jbr/bin/java.exe' `
  '-Djava.awt.headless=true' docs/overview/make-media.java `
  docs/overview/assets outputs/Marketplace-Media
```

媒体图只做排版，不改变原始截图内容；Banner 也会按比例放入统一画布，不裁切文字。
