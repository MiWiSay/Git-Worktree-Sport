# Git Worktree Sport
![](https://plugins.jetbrains.com/files/35384/63552-page/51475f4a-8e8b-4e23-8de2-081e8e4465ef)
**Languages:** [English](README.md) | [简体中文](README.zh-CN.md)

**JetBrains Marketplace:** [Git Worktree Sport](https://plugins.jetbrains.com/plugin/35384-git-worktree-sport) · [GitHub source](https://github.com/MiWiSay/Git-Worktree-Sport)

**Download:** [Git Worktree Sport 1.0.3 plugin ZIP](outputs/Git%20Worktree%20Sport-1.0.3.zip) · Install through **Settings → Plugins → Install Plugin from Disk...**.

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
![](https://plugins.jetbrains.com/files/35384/63552-page/5a109458-373d-4547-8d61-0e16cf766c16)

### Create from Git Branches

Choose a branch in the Git Branches popup and select **Create Worktree...**. The dialog suggests a sibling folder named from the repository and branch; edit the path or use Browse. The destination's parent directory must already exist, and the destination must be absent or empty.

Local branches are checked out directly. For a remote branch, the plugin reuses a same-named local branch when available; otherwise it creates a local tracking branch. Before creation, the plugin checks for conflicts:

- A missing folder with a stale Git registration can be cleaned with a confirmed `git worktree prune`, followed by a retry.
- If the branch is checked out in another worktree, the user can explicitly choose **Create with Force**. Git still decides whether the operation is possible.
- An already active worktree at the destination is not duplicated.
- A non-empty destination is never overwritten.

![](https://plugins.jetbrains.com/files/35384/63552-page/9701bb5a-73a9-4523-a610-1f9b05581b15)
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

![](https://plugins.jetbrains.com/files/35384/63552-page/680afe6d-9c75-4938-86c7-c3a5ee8ca572)
The main worktree's badge remains green when Git reports it as unlocked. The main worktree and each worktree currently open as an Android Studio project hide Prune, Move, Delete, Lock, and Unlock by default; service-level checks enforce the same protection. The settings dialog can show these protected entries as disabled items with a short reason, without enabling them. Current-project protection does not change the Git lock state shown in details. The manager does not run Git lock/unlock on the main worktree. A real Git lock is shown in blue. Other locked linked worktrees hide Move/Delete by default, and direct calls are rejected until unlocked.

### Language and Translation Settings

You can use the settings button next to the refresh button to follow the IDE language or choose the plugin language. Built-in languages include: **English, Simplified Chinese, Traditional Chinese, Japanese, Korean, German, French, Spanish, Brazilian Portuguese, Russian**.

The translation editor displays content in a table where keys cannot be modified, values can be edited, and it supports `Ctrl+F` searching, importing and exporting text in `key=value` format. It checks for duplicates, unknown, and missing keys during import. You can add a new language, change a language name, or delete user-added languages; unsaved edits can be reverted to restore changes from the current session.
![](https://plugins.jetbrains.com/files/35384/63552-page/64037262-81ef-44ca-8ede-30972190ce1c)
![](https://plugins.jetbrains.com/files/35384/63552-page/42db4b27-0573-48b3-bca2-b6ca18bd205f)
![](https://plugins.jetbrains.com/files/35384/63552-page/bc0062e9-beed-4b4b-889d-6a0ad23d15e0)

## Project Links

- Plugin marketplace: [Git Worktree Sport on JetBrains Marketplace](https://plugins.jetbrains.com/plugin/35384-git-worktree-sport)
- Source code & issues: [GitHub Repository](https://github.com/MiWiSay/Git-Worktree-Sport)
- License: [Apache-2.0](https://github.com/MiWiSay/Git-Worktree-Sport/blob/main/LICENSE)
