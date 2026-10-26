# Git Worktree Sport
![](https://plugins.jetbrains.com/files/35384/63552-page/51475f4a-8e8b-4e23-8de2-081e8e4465ef)
**语言 / Languages:** [简体中文](README.zh-CN.md) | [English](README.md)

**JetBrains 插件广场：**[Git Worktree Sport](https://plugins.jetbrains.com/plugin/35384-git-worktree-sport) · [GitHub 源码](https://github.com/MiWiSay/Git-Worktree-Sport)

**下载：**[Git Worktree Sport 1.0.3 插件安装包](outputs/Git%20Worktree%20Sport-1.0.3.zip)，下载后在 IDE 的 **Settings → Plugins → Install Plugin from Disk...** 中安装。

Git Worktree Sport 是一个适用于 IntelliJ Platform IDE（已针对 Android Studio 构建并验证）的免费插件，用于创建和管理 Git 工作区（Git worktree）。它会汇总当前已打开 IDE 项目中的 Git 工作区，并可从分支列表创建新的关联工作区，让你同时处理多个分支，而无需反复切换同一份工作目录。

插件调用 IDE 配置的 Git 可执行程序，并使用 Git 内置的 `worktree` 命令；不需要付费服务，也不含购买流程。

## 功能

### 工作区树

- 汇总当前 IDE 已打开项目中各 Git 仓库的工作区，并按仓库分组。
- 显示工作区数量和精简状态；展开后可查看工作区类型、裸仓库/锁定状态、分支、完整 HEAD、完整路径和文件夹创建时间。
- 同名工作区会合并显示，展开后以 HEAD 前 8 位区分；缺失或可清理的路径会标记出来。
- 双击树节点只展开或折叠。右键详细信息行可仅复制对应值。
- 展开工作区后可打开工作区项目或文件夹；当前已在 IDE 打开的工作区不重复提供“打开工作区”操作。
- 刷新只重新读取本地 Git 元数据，不会获取远程分支或连接服务器。
![](https://plugins.jetbrains.com/files/35384/63552-page/5a109458-373d-4547-8d61-0e16cf766c16)

### 从分支创建工作区

在 IDE 的 **Git Branches** 分支弹窗中选择分支，然后点击 **Create Worktree...**。也可以在 Worktree Sport 列表空白处右击，从可搜索的仓库分支列表中选择。创建弹窗会建议目标目录；你可以自行修改或使用浏览按钮选择目录。目标父目录必须已存在，目标目录必须不存在或为空。

支持以下创建方式：

- **现有分支：**直接检出本地分支。选择远程分支时，若有同名本地分支则复用，否则创建跟踪分支。
- **新建分支：**填写新分支名，并可指定起点分支或提交。
- **分离 HEAD：**基于指定提交创建不关联分支的工作区。
- **孤儿分支：**创建不继承提交历史的新分支，具体取决于本机 Git 版本。
- **不检出文件：**高级选项，只登记工作区而不写出文件；默认关闭，孤儿分支模式下不可用。

![](https://plugins.jetbrains.com/files/35384/63552-page/9701bb5a-73a9-4523-a610-1f9b05581b15)
创建前会检查分支冲突、失效的 Git 登记、目标路径和非空目录。对于已在其他工作区检出的分支，需要用户明确确认后才会尝试强制创建；Git 仍会判断操作是否可行。插件不会覆盖非空目录。

操作期间弹窗会显示进度并禁用重复提交；成功后提供“打开项目”选项。关闭成功提示时，工作区列表会展开并以蓝色背景高亮新工作区两秒。

### 管理工作区

右击工作区可执行以下操作：

- **打开工作区：**使用 IDE 标准流程打开或导入项目。
- **打开文件夹：**在系统文件管理器中打开目录。
- **锁定 / 解锁：**设置或移除 Git 保留标记。锁定不会令文件只读，但可以避免 Git 修剪其登记。
- **修复：**让 Git 修复工作区与仓库之间的管理链接。
- **清理：**预览并移除失效工作区的登记信息，不删除仍存在的工作区文件夹。
- **移动：**调用 Git 移动关联工作区并更新登记。目标路径必须不存在，父目录必须存在。
- **删除：**通过 Git 移除关联工作区及其登记。如果存在修改或未跟踪文件，会先显示额外警告；只有明确确认后才会强制删除整个目录。

![](https://plugins.jetbrains.com/files/35384/63552-page/680afe6d-9c75-4938-86c7-c3a5ee8ca572)
主工作区以及当前 IDE 会话中已作为项目打开的工作区默认隐藏清理、移动、删除、锁定和解锁操作，底层服务也会再次校验。设置中可以显示这些受保护的菜单项，但它们会保持灰色禁用，并显示不可用原因；开启此选项不会解除保护。此保护不依赖分支名，也不会创建 Git 锁。其他已锁定的关联工作区同样不能移动或删除。

### 语言与翻译设置

使用刷新按钮旁的设置按钮，可跟随 IDE 语言或选择插件语言。当前内置语言：**英语、简体中文、繁体中文、日语、韩语、德语、法语、西班牙语、巴西葡萄牙语、俄语**。

翻译编辑器以表格展示内容，key 不可修改、value 可编辑，并支持 `Ctrl+F` 搜索、按 `key=value` 文本导入和导出。导入时会检查重复、未知及缺失 key。可以新增语言、修改语言名，或删除用户新增的语言；取消未保存的编辑可以整体还原本轮更改。
![](https://plugins.jetbrains.com/files/35384/63552-page/96492d3f-962e-458a-9f2f-3d5731365a48)
![](https://plugins.jetbrains.com/files/35384/63552-page/42db4b27-0573-48b3-bca2-b6ca18bd205f)
![](https://plugins.jetbrains.com/files/35384/63552-page/bc0062e9-beed-4b4b-889d-6a0ad23d15e0)

## 项目链接

- 插件广场：[Git Worktree Sport on JetBrains Marketplace](https://plugins.jetbrains.com/plugin/35384-git-worktree-sport)
- 源码与问题反馈：[GitHub 仓库](https://github.com/MiWiSay/Git-Worktree-Sport)
- 许可证：[Apache-2.0](https://github.com/MiWiSay/Git-Worktree-Sport/blob/main/LICENSE)
