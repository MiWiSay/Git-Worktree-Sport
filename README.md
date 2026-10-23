# Git Worktree Sport

**Languages:** [English](README.md) | [简体中文](README.zh-CN.md)

**JetBrains Marketplace:** [Git Worktree Sport](https://plugins.jetbrains.com/plugin/35384-git-worktree-sport) · [GitHub source](https://github.com/MiWiSay/Git-Worktree-Sport)

Git Worktree Sport is an IntelliJ Platform plugin (built and verified against Android Studio) for creating and managing Git worktrees. It provides one tree for worktrees belonging to Git repositories in projects currently open in the IDE, and adds a branch action for creating a linked working directory. Git worktrees let you work on different branches in parallel without repeatedly switching the files in one checkout.

The plugin invokes the Git executable configured in the IDE and uses Git's built-in `worktree` commands. Git Worktree is free; the plugin does not require a paid service or include a purchase flow.

## Features

### Worktree tree

- Collects worktrees from repositories in projects currently open in the IDE.
- Groups entries by repository and shows the registered worktree count without parentheses. Duplicate display names within one repository are grouped under a shared name; expand to see each worktree with its own 8-character HEAD suffix. Unique entries keep the original hierarchy.
- Displays a compact status badge, including **Current project** for worktrees open in the IDE; expand a worktree to inspect its type, bare/locked state, branch, full HEAD, and full path.
- Shows the worktree folder's filesystem creation time and open buttons at the top of expanded details. Linked worktrees have side-by-side **Open Worktree** and **Open Folder** buttons; the already-open main worktree only has **Open Folder**. Right-clicking a detail row copies only that value, such as the full HEAD or path. Double-clicking tree nodes only expands or collapses them.
- Omits repeated repository-name prefixes from main and sibling worktree titles, while retaining useful container folder names and complete branch names.
- Marks missing or prunable worktree paths so stale registrations can be identified.
- Refresh reloads local Git metadata. It does not fetch remote branches or contact a server.

### Create from Git Branches

Choose a branch in the Git Branches popup and select **Create Worktree...**. The dialog suggests a sibling folder named from the repository and branch; edit the path or use Browse. The destination's parent directory must already exist, and the destination must be absent or empty.

Local branches are checked out directly. For a remote branch, the plugin reuses a same-named local branch when available; otherwise it creates a local tracking branch. Before creation, the plugin checks for conflicts:

- A missing folder with a stale Git registration can be cleaned with a confirmed `git worktree prune`, followed by a retry.
- If the branch is checked out in another worktree, the user can explicitly choose **Create with Force**. Git still decides whether the operation is possible.
- An already active worktree at the destination is not duplicated.
- A non-empty destination is never overwritten.

The path dialog remains open while the operation runs. Repeat input is disabled during progress; success closes the dialog, while an error leaves it available for correction and retry.

The success dialog offers **Open Project** and **Close** (the safe default). Open Project delegates to the IDE's standard project import/open flow, respecting its current/new-window prompt and saved preference. Close, Escape, or dismissing the dialog reveals the manager, expands the target's ancestors, selects the worktree by its full normalized path, and flashes it blue for two seconds. Cancelling project opening leaves the created directory intact and reveals it in the manager as well. Open failures are reported separately from creation. The right-click Open Folder action remains an operating-system file-manager action.

### Worktree actions

Right-click a worktree in the tree to access these operations:

- **Open Worktree** opens or imports the project through Android Studio's standard flow. **Open Folder** opens the directory in the operating system's file manager.
- **Lock Worktree** and **Unlock Worktree** set or remove Git's retention marker. They do not change file permissions.
- **Repair Worktree** asks Git to repair the administrative link between the selected worktree and its repository.
- **Prune stale records** first previews stale registrations with Git's dry-run mode, then asks for confirmation before removing only those registrations. It does not delete existing worktree folders.
- **Move Worktree** uses Git to move the linked worktree and update its registration. The destination must not exist and its parent must exist.
- **Delete Worktree** first uses normal Git removal. If Git reports modified or untracked files, a second warning explains that force removal permanently deletes the entire directory; only explicit confirmation runs `git worktree remove --force`.

The main worktree's badge remains green when Git reports it as unlocked. The main worktree and each worktree currently open as an Android Studio project hide Prune, Move, Delete, Lock, and Unlock; service-level checks enforce the same protection. Current-project protection does not change the Git lock state shown in details. The manager does not run Git lock/unlock on the main worktree. A real Git lock is shown in blue. Other locked linked worktrees hide Move/Delete, and direct calls are rejected until unlocked.

### Language settings

Use the compact settings button beside Refresh to choose the IDE default or a configured language. Translations are stored as separate JSON files; `languages.json` independently maps each short file ID (up to eight characters) to its display name. These editable files are stored under the IDE configuration directory. The translation editor uses native IDE themed tabs and a table whose keys are read-only and values are editable. Double-click a tab name to rename it; the trailing **+** creates a language named `New N` from the English fallback. Right-click a tab to delete a user-added language after confirmation; packaged baseline languages are protected. Canceling a changed translation session offers save, discard, or continue editing; discard restores the complete language-file snapshot, including added or deleted tabs. The separate language-list editor has its own table and add control.

Both editors support one-click text export to the clipboard and text import. Translation imports require every configured key, reject unknown or duplicate keys, and report missing keys before saving. Language-list imports require every existing ID and cannot rename IDs or remove language files.

## Getting Started

1. In Android Studio, open **Settings → Plugins → Install Plugin from Disk...**.
2. Select `outputs/Git Worktree Sport-1.0.1.zip` and restart the IDE.
3. Choose **Git → Worktree Sport → Refresh** to open the manager and load worktrees from open projects.
4. Expand a repository and worktree to inspect details. Right-click a worktree to manage it.
5. To create a worktree, select a branch from the Git Branches popup and choose **Create Worktree...**.

## Marketplace 发布素材

本工程的英文 Overview、图文教程、Marketplace 媒体图与发布指南统一输出到根目录 `outputs/`：

- `outputs/Git Worktree Sport-1.0.1.zip`：Android Studio 安装包。
- `outputs/Overview/`：静态英文 Overview 与教程预览。
- `outputs/Marketplace-Media/`：可上传到 Marketplace Media 的 1600×1000 图片。
- `outputs/Git Worktree Sport-1.0.1-发布指南.md`：手动上传步骤及发布前待确认项。
- `outputs/Git Worktree Sport-1.0.1-发布素材.zip`：上述发布素材集合，不是插件安装包。

`plugins.jetbrains.com/generator` 仅用于生成新插件工程，不是上传入口；发布请登录 JetBrains Marketplace 后从账号菜单选择 **Upload plugin**。发布前仍需由作者本人决定真实联系邮箱与许可证/EULA。
## Build

Target: Android Studio Panda 3 (`AI-262.9437.185.2621.16467767`), JDK 25, Gradle Wrapper 9.7.1. Windows is the verified runtime platform. macOS Android Studio is supported by the IDE, and this plugin uses cross-platform APIs, but macOS runtime testing remains outstanding.

Windows PowerShell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio Panda 3\jbr"
.\gradlew.bat clean buildPlugin verifyPluginProjectConfiguration verifyPluginStructure --no-daemon --console=plain
```

macOS (default IDE path `/Applications/Android Studio.app/Contents`):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew clean buildPlugin verifyPluginProjectConfiguration verifyPluginStructure --no-daemon --console=plain
```

Set `-PandroidStudioPath="/path/to/Android Studio.app/Contents"` or `ANDROID_STUDIO_PATH` if the IDE is installed elsewhere. The ZIP is generated in the project-root `outputs/` folder.

## Regression checks

On Windows, run `./tests/run-regression.ps1` to exercise the production localization and grouping code against in-memory IDE service fixtures, plus actual Swing tree/EDT/timer checks. It does not modify installed IDE settings or repositories. `tests/IconSmokeCheck.java` renders the actual SVG resources with the target IDE's SVG loader. The current PowerShell regression harness is Windows-specific; macOS runtime and UI behavior still require testing on a Mac.

## IDE-aware languages and empty-area creation

UI messages resolve `{ideName}` using the running IDE product name, including legacy user messages containing the former product name. Language packs are configured by JSON, not Java language enums. Upgrades append newly bundled packs without overwriting user translations or renamed language entries.

Right-click empty space in the worktree tree to open a scrollable, speed-searchable branch popup (18 visible rows). Local and remote branches are grouped by repository across open projects. Click a branch to open its Create Worktree submenu. Creation reuses the existing branch action and all its conflict/progress/success behavior. Right-clicking an actual node still uses its existing context menu.

Bundled languages: English, 简体中文, 繁體中文, 日本語, 한국어, Deutsch, Français, Español, Português (Brasil), Русский. Other IntelliJ-based IDEs require compatible platform builds and the Git plugin; only the configured Android Studio SDK has been compiled/tested in this delivery.
