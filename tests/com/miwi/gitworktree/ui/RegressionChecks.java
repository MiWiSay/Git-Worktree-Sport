package com.miwi.gitworktree.ui;

import com.intellij.DynamicBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.miwi.gitworktree.model.WorktreeEntry;
import com.miwi.gitworktree.service.WorktreeRemovalPolicy;
import com.miwi.gitworktree.service.WorktreeListParser;
import com.miwi.gitworktree.service.WorktreePaths;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 直接执行生产语言/分组代码；仅 IDE 全局服务使用内存替身。 */
public final class RegressionChecks {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        LanguageCatalog catalog = LanguageCatalog.load();
        List<LanguageCatalog.Option> builtIns = catalog.options().stream().filter(o -> !o.followsIde()).toList();
        check(builtIns.size() >= 2, "Two configured JSON languages available");
        var first = builtIns.get(0);
        var second = builtIns.get(1);
        Map<String, String> packagedNames;
        try (var input = RegressionChecks.class.getResourceAsStream("/i18n/languages.json")) {
            packagedNames = FlatJson.parse(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        for (String id : packagedNames.keySet()) {
            Map<String, String> packaged;
            try (var input = RegressionChecks.class.getResourceAsStream("/i18n/" + id + ".json")) {
                packaged = FlatJson.parse(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
            var actualKeys = new java.util.LinkedHashSet<>(packaged.keySet());
            actualKeys.remove("__locale");
            check(actualKeys.equals(catalog.messages(first.id()).keySet()),
                    "Packaged language has a full translation set: " + id);
            for (String key : catalog.messages(first.id()).keySet()) {
                check(packaged.containsKey(key), "Packaged translation exists: " + id + "/" + key);
                String reference = catalog.messages(first.id()).get(key);
                java.util.function.Function<String, List<String>> placeholders = text ->
                        java.util.regex.Pattern.compile("\\{(?:\\d+|ideName)\\}").matcher(text)
                                .results().map(java.util.regex.MatchResult::group).sorted().toList();
                equal(placeholders.apply(packaged.get(key)), placeholders.apply(reference),
                        "Translation placeholders preserved: " + id + "/" + key);
            }
            check(packaged.values().stream().noneMatch(value -> value.contains("Android Studio")),
                    "Packaged language uses the actual IDE placeholder: " + id);
            equal(catalog.languageForLocale(Locale.forLanguageTag(packaged.get("__locale"))).id(), id,
                    "Configured locale resolves its language: " + id);
        }
        Path namesFile = Path.of(com.intellij.openapi.application.PathManager.getConfigPath(),
                "GitWorktreeSport", "languages", "languages.json");
        var legacyNames = new java.util.LinkedHashMap<String, String>();
        legacyNames.put(first.id(), "User-renamed baseline");
        legacyNames.put(second.id(), second.displayName());
        Files.writeString(namesFile, FlatJson.write(legacyNames));
        LanguageCatalog upgraded = LanguageCatalog.load();
        equal(upgraded.names().size(), packagedNames.size(), "Upgrades append newly bundled languages");
        equal(upgraded.languageName(first.id()), "User-renamed baseline", "Upgrade preserves renamed languages");
        equal(LanguageCatalog.load().names(), upgraded.names(), "Language upgrade is idempotent");
        Files.writeString(namesFile, FlatJson.write(packagedNames));
        equal(catalog.languageForLocale(Locale.forLanguageTag("zh-TW")).id(), "zh-TW",
                "Traditional Chinese country selection does not fall back to simplified");
        equal(catalog.languageForLocale(Locale.forLanguageTag("zh-Hant")).id(), "zh-TW",
                "Traditional Chinese script selection resolves the configured pack");
        equal(catalog.languageForLocale(Locale.forLanguageTag("pt-PT")).id(), "pt-BR",
                "Related locale uses the configured language fallback");
        check(!catalog.canDeleteLanguage(first.id()) && !catalog.canDeleteLanguage(second.id()),
                "Packaged baseline languages cannot be deleted");
        Path configuredSecondLanguage = Path.of(com.intellij.openapi.application.PathManager.getConfigPath(),
                "GitWorktreeSport", "languages", second.id() + ".json");
        Map<String, String> olderMessages = FlatJson.parse(Files.readString(configuredSecondLanguage));
        olderMessages.remove("settings.deleteLanguage");
        Files.writeString(configuredSecondLanguage, FlatJson.write(olderMessages));
        check(LanguageCatalog.load().messages(second.id()).containsKey("settings.deleteLanguage"),
                "New packaged labels fill missing keys in existing language files");
        for (var language : builtIns) {
            for (String key : List.of("button.openProject", "button.close", "error.openProject",
                    "confirm.forceDelete", "detail.created", "detail.openFolderButton",
                    "error.lockedProtected", "error.mainCannotUnlock", "progress.forceDelete",
                    "error.currentWorktreeProtected", "status.currentShort",
                    "status.mainLocked", "value.unavailable", "action.openWorktree", "action.copyValue",
                    "action.repair", "action.prune", "settings.manageLanguages", "settings.importInvalid",
                    "settings.deleteLanguage", "settings.deleteLanguageProtected", "confirm.deleteLanguage",
                    "settings.unsavedLanguageChanges", "settings.discardChanges", "settings.search",
                    "settings.searchClose", "settings.searchHint", "settings.searchIncludeKey",
                    "settings.searchMatchCount", "settings.searchNext", "settings.searchPrevious",
                    "settings.section.language", "settings.section.display", "settings.hideListHelp",
                    "settings.hideListHelpHint", "settings.showProtectedActions",
                    "settings.showProtectedActionsHint", "settings.protectedActionsList",
                    "confirm.showProtectedActions", "tree.help.worktreeItem", "tree.help.emptySpace",
                    "menu.protectedReason.main", "menu.protectedReason.mainLock",
                    "menu.protectedReason.current", "menu.protectedReason.locked",
                    "menu.protectedReason.unavailable",
                    "create.mode", "create.mode.existing", "create.mode.new", "create.mode.detached",
                    "create.mode.orphan", "create.branchName", "create.startPoint", "create.noCheckout",
                    "error.deleteStaleWorktree", "error.pruneHealthyWorktree")) {
                check(catalog.messages(language.id()).containsKey(key), "Message configured: " + key);
            }
        }
        Map<String, String> simplifiedChinese = catalog.messages("zh-CN");
        equal(simplifiedChinese.get("content.worktrees"), "工作区列表",
                "Simplified Chinese retains the latest user wording");
        equal(simplifiedChinese.get("action.delete"), "删除工作区",
                "Simplified Chinese consistently translates worktree actions as workspace");
        equal(simplifiedChinese.get("error.targetNotDirectory"), "目标路径已存在，但不是文件夹。",
                "Simplified Chinese target path error matches the supplied wording");
        check(simplifiedChinese.values().stream().noneMatch(value -> value.contains("工作树")),
                "Simplified Chinese does not retain the old worktree term");
        check(simplifiedChinese.entrySet().stream().noneMatch(entry ->
                        !List.of("content.worktrees", "error.selectWorktree", "lock.reason", "menu.worktreeSport",
                                "settings.title", "title.manager")
                                .contains(entry.getKey()) && entry.getValue().contains("Worktree")),
                "Simplified Chinese only retains Worktree in the bilingual label and product brand");
        PropertiesComponent properties = PropertiesComponent.getInstance();
        properties.values.remove("git.worktree.sport.showListHelp");
        properties.values.remove("git.worktree.sport.showProtectedActions");
        check(WorktreePreferences.showListHelp(), "Worktree list help is visible by default");
        check(!WorktreePreferences.showProtectedActions(), "Protected actions are hidden by default");
        WorktreePreferences.setShowListHelp(false);
        WorktreePreferences.setShowProtectedActions(true);
        check(!WorktreePreferences.showListHelp() && WorktreePreferences.showProtectedActions(),
                "Worktree display preferences persist independently");
        WorktreePreferences.setShowListHelp(true);
        WorktreePreferences.setShowProtectedActions(false);
        I18n.setLanguage(first.id());
        com.intellij.openapi.application.ApplicationNamesInfo.product = "IntelliJ IDEA";
        check(I18n.text("settings.languageHint").contains("IntelliJ IDEA"), "Actual IDE name replaces placeholder");
        check(!I18n.text("settings.languageHint").contains("{ideName}"), "IDE placeholder never leaks into UI");
        String productEditor = I18n.exportLanguageMessages(first.id());
        I18n.importLanguageMessages(first.id(), productEditor.replace("{ideName}", "Android Studio"));
        check(I18n.text("settings.languageHint").contains("IntelliJ IDEA"), "Existing user text also resolves actual IDE name");
        I18n.importLanguageMessages(first.id(), productEditor);
        com.intellij.openapi.application.ApplicationNamesInfo.product = "Test IDE";
        properties.setValue("git.worktree.sport.language", "system");
        properties.setValue("git.worktree.sport.customMessages", "action.refresh=Legacy custom\n");
        DynamicBundle.locale = Locale.forLanguageTag(first.localeTag());
        for (var language : builtIns) {
            I18n.setLanguage(language.id());
            equal(I18n.text("action.refresh"), catalog.messages(language.id()).get("action.refresh"), "Legacy custom text is not mixed into languages");
        }
        I18n.setLanguage(second.id());
        String editor = I18n.exportLanguageMessages(second.id());
        I18n.importLanguageMessages(second.id(), editor.replace("action.refresh=" + catalog.messages(second.id()).get("action.refresh"), "action.refresh=Edited second"));
        equal(I18n.language(), second.id(), "Saving language editor preserves selection");
        equal(I18n.text("action.refresh"), "Edited second", "Edited language applies immediately");
        check(I18n.exportLanguageMessages(second.id()).contains("action.refresh=Edited second"), "Export reflects saved value");
        boolean missingRejected = false;
        try { I18n.importLanguageMessages(second.id(), "action.refresh=Only one key\n"); }
        catch (IllegalArgumentException exception) { missingRejected = exception.getMessage().contains("Missing keys:"); }
        check(missingRejected, "Translation import reports missing required keys");
        boolean unknownRejected = false;
        try { I18n.importLanguageMessages(second.id(), editor + "unknown.key=value\n"); }
        catch (IllegalArgumentException exception) { unknownRejected = exception.getMessage().contains("Unknown keys:"); }
        check(unknownRejected, "Translation import rejects unknown keys");
        boolean duplicateRejected = false;
        try { LanguageCatalog.parseKeyValues("duplicate=one\nduplicate=two\n"); }
        catch (IllegalArgumentException exception) { duplicateRejected = exception.getMessage().contains("Duplicate key"); }
        check(duplicateRejected, "Text import rejects duplicate keys");
        I18n.setLanguage(first.id());
        equal(I18n.text("action.refresh"), catalog.messages(first.id()).get("action.refresh"), "Edits do not leak to another language");
        I18n.setLanguage(second.id());
        equal(I18n.text("action.refresh"), "Edited second", "Switching back preserves edits");
        equal(LanguageCatalog.load().messages(second.id()).get("action.refresh"), "Edited second", "Language JSON reloads from disk");
        I18n.importLanguageMessages(second.id(), editor);
        equal(I18n.text("action.refresh"), catalog.messages(second.id()).get("action.refresh"), "Original language text can be restored");

        Map<String, String> languageSessionSnapshot = I18n.snapshotLanguageFiles();
        String languageBeforeSession = I18n.language();
        String discardedLanguage = I18n.addLanguage();
        I18n.renameLanguage(discardedLanguage, "Discarded session language");
        I18n.deleteLanguage(discardedLanguage);
        I18n.restoreLanguageFiles(languageSessionSnapshot, languageBeforeSession);
        equal(I18n.snapshotLanguageFiles(), languageSessionSnapshot,
                "Discarding a language-edit session restores every language JSON file");
        check(!I18n.languageNames().containsKey(discardedLanguage),
                "Discarding a language-edit session removes languages created during that session");
        equal(I18n.language(), languageBeforeSession,
                "Discarding a language-edit session restores the selected language");

        String newLanguage = I18n.addLanguage();
        equal(I18n.languageName(newLanguage), "New 1", "New language gets the first generated name");
        equal(I18n.exportLanguageMessages(newLanguage), I18n.exportLanguageMessages(first.id()), "New language copies English base messages");
        I18n.renameLanguage(newLanguage, "Regression language");
        equal(I18n.languageName(newLanguage), "Regression language", "Renamed language is stored in the separate list");
        String namesText = I18n.exportLanguageNames();
        I18n.importLanguageNames(namesText);
        boolean missingLanguageRejected = false;
        try { I18n.importLanguageNames("unknown=Missing\n"); }
        catch (IllegalArgumentException exception) { missingLanguageRejected = exception.getMessage().contains("Missing keys:"); }
        check(missingLanguageRejected, "Language list import reports missing IDs");
        I18n.setLanguage(newLanguage);
        I18n.deleteLanguage(newLanguage);
        check(!I18n.languageNames().containsKey(newLanguage), "Deleting a custom language removes it from the language list");
        Path deletedLanguageFile = Path.of(com.intellij.openapi.application.PathManager.getConfigPath(),
                "GitWorktreeSport", "languages", newLanguage + ".json");
        check(!java.nio.file.Files.exists(deletedLanguageFile), "Deleting a custom language removes its translation JSON file");
        equal(I18n.language(), "system", "Deleting the active language returns selection to IDE default");

        DynamicBundle.locale = Locale.forLanguageTag(second.localeTag());
        I18n.setLanguage(catalog.ideDefault().id());
        equal(I18n.editableLanguageId(), second.id(), "IDE-default editing resolves the matching language");
        I18n.setLanguageMessagesText(I18n.editableLanguageId(), I18n.exportLanguageMessages(second.id())
                .replace("action.refresh=" + catalog.messages(second.id()).get("action.refresh"), "action.refresh=Follow IDE edit"));
        equal(I18n.language(), catalog.ideDefault().id(), "Editing IDE default preserves automatic selection");
        equal(I18n.text("action.refresh"), "Follow IDE edit", "Automatic selection uses matching language");
        DynamicBundle.locale = Locale.forLanguageTag(first.localeTag());
        equal(I18n.text("action.refresh"), catalog.messages(first.id()).get("action.refresh"), "Changing IDE locale does not leak edits");
        I18n.setLanguage(first.id());
        I18n.setLanguageMessagesText(first.id(), I18n.exportLanguageMessages(first.id())
                .replace("action.refresh=" + catalog.messages(first.id()).get("action.refresh"), "action.refresh=Branch '{0}'\\n{1}"));
        equal(I18n.format("action.refresh", "feature/a", "done"), "Branch 'feature/a'\ndone", "Message formatting preserves apostrophes and escaped newlines");
        equal(catalog.languageForLocale(Locale.forLanguageTag("x-unconfigured")).id(), first.id(), "Unknown locale follows the first configured fallback");

        Path parent = Path.of(System.getProperty("java.io.tmpdir"), "worktree-sport-regression").toAbsolutePath();
        String repo = parent.resolve("TalkInAs").toString();
        WorktreeEntry detached1 = entry(repo, parent.resolve("one/TalkInAs"), "(detached)", "1234567890abcdef", false);
        WorktreeEntry detached2 = entry(repo, parent.resolve("two/TalkInAs"), "(detached)", "abcdef1234567890", false);
        WorktreeEntry main = entry(repo, Path.of(repo), "release", "0123456789abcdef", true);
        WorktreeEntry current = entry(repo, parent.resolve("current-worktree"), "current", "def", false).withCurrentProject(true);
        WorktreeEntry linked = entry(repo, parent.resolve("TalkInAs-worktrees/feature"), "feature", "abc", false);
        WorktreeEntry locked = new WorktreeEntry(repo, parent.resolve("locked").toString(), "locked", "abc", false, false, false, true, false);
        List<WorktreeGroups.Group> groups = WorktreeGroups.group(List.of(detached1, detached2, main, linked));
        equal(groups.size(), 3, "Only duplicate display names grouped");
        equal(groups.get(0).entries().size(), 2, "Both worktrees retained under duplicate-name group");
        equal(groups.get(0).name(), "TalkInAs", "Group name remains concise");
        equal(WorktreeGroups.label(detached1, true), "TalkInAs  [12345678]", "First duplicate child has 8-character HEAD");
        equal(WorktreeGroups.label(detached2, true), "TalkInAs  [abcdef12]", "Second duplicate child has its own HEAD");
        equal(WorktreeGroups.label(main, false), "release", "Main item does not repeat repository prefix");
        equal(WorktreeGroups.label(linked, false), "TalkInAs-worktrees/feature", "Worktree parent folder and branch preserved");
        equal(WorktreeGroups.label(entry(repo, parent.resolve("TalkInAs-feature"), "feature/ui", "abc", false), false),
                "feature/ui", "Sibling worktree has no artificial repository prefix");
        equal(WorktreeGroups.label(entry(repo, parent.resolve("TalkInAs/child"), "feature/ui", "abc", false), false),
                "feature/ui", "Nested worktree omits repeated repository folder");
        equal(WorktreeGroups.label(entry(repo, parent.resolve("other/TalkInAs/child"), "feature", "abc", false), false),
                "feature", "Repeated parent name omitted outside repository");
        equal(WorktreeGroups.label(entry(repo, Path.of(repo), "TalkInAs/feature", "abc", true), false),
                "TalkInAs/feature", "Branch names containing repository name not damaged");
        equal(WorktreeGroups.label(entry(repo, parent.resolve("one/empty"), "", "", false), false),
                "empty", "Branchless worktree keeps folder name");
        var sameBranch = WorktreeGroups.group(List.of(main, entry(repo, parent.resolve("TalkInAs-release"), "release", "abcdef", false)));
        equal(sameBranch.size(), 1, "Removing prefixes preserves grouping by final visible name");
        equal(sameBranch.get(0).entries().size(), 2, "Both same-branch paths retained for context actions");
        equal(linked.shortHead(), "abc", "Short commit IDs not truncated incorrectly");
        check(!main.effectivelyLocked(), "Main worktree protection does not change the Git lock state");
        check(main.protectedFromMoveOrDelete(), "Main worktree cannot be moved or deleted");
        check(main.protectedFromPrune(), "Main worktree context cannot run prune");
        check(!main.protectedFromOpenWorktree(), "Main worktree can be opened when another worktree is active");
        check(main.withCurrentProject(true).protectedFromOpenWorktree(),
                "Currently open main worktree hides the duplicate open action");
        check(current.protectedFromMoveOrDelete() && current.protectedFromPrune(),
                "Currently open linked worktree is protected from high-risk operations");
        check(current.protectedFromGitLockChanges() && current.protectedFromOpenWorktree(),
                "Currently open linked worktree cannot be locked or opened again from its manager");
        check(!current.effectivelyLocked() && !current.locked(),
                "Current-project protection does not fake Git's lock state");
        equal(WorktreeGroups.countLabel(5), "5", "Worktree counts render without parentheses");
        check(locked.protectedFromMoveOrDelete(), "Git-locked worktree cannot be moved or deleted");
        check(locked.protectedFromPrune(), "Git-locked worktree cannot be pruned");
        check(!linked.protectedFromMoveOrDelete(), "Unlocked linked worktree remains manageable");
        Path pathFixture = Files.createTempDirectory("worktree-path-identity-");
        Path worktreeRoot = pathFixture.resolve("Repo");
        Path nestedProject = worktreeRoot.resolve("module");
        Files.createDirectories(nestedProject);
        WorktreeEntry openableMain = entry(repo, worktreeRoot, "main", "abc", true);
        check(openableMain.canOpenWorktree(), "An existing main worktree can be opened after switching elsewhere");
        check(!openableMain.withCurrentProject(true).canOpenWorktree(),
                "The worktree already open as the current project hides duplicate project opening");
        check(WorktreePaths.sameOrAncestor(worktreeRoot, nestedProject),
                "An IDE project nested below its worktree resolves to that worktree");
        check(!WorktreePaths.sameOrAncestor(nestedProject, worktreeRoot),
                "Path containment does not match in the reverse direction");
        Path missingWorktreePath = pathFixture.resolve("missing-worktree");
        WorktreeEntry missingWorktree = entry(repo, missingWorktreePath, "missing", "abc", false);
        check(!missingWorktree.canOpenFolder() && !missingWorktree.canOpenWorktree(),
                "Missing worktree paths expose neither folder nor project opening");
        WorktreeEntry bareWorktree = new WorktreeEntry(repo, pathFixture.toString(), "(bare)", "abc",
                false, false, true, false, false);
        check(bareWorktree.canOpenFolder() && !bareWorktree.canOpenWorktree(),
                "A bare repository may open as a folder but not as a checked-out project");
        Path alias = pathFixture.resolve("RepoAlias");
        try {
            Files.createSymbolicLink(alias, worktreeRoot);
            check(WorktreePaths.same(alias, worktreeRoot), "Symbolic-link aliases resolve to one worktree identity");
            check(WorktreePaths.sameOrAncestor(alias, nestedProject),
                    "A project opened through a worktree symlink resolves to the same protected worktree");
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException unavailable) {
            // Some Windows setups require Developer Mode or elevated privileges for symlink creation.
        }
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        Path caseVariant = pathFixture.resolve(windows ? "repo" : "Repo");
        if (windows) {
            check(WorktreePaths.same(caseVariant, worktreeRoot),
                    "Windows path identity follows the filesystem's case-insensitive semantics");
        } else {
            Path lowerCaseDirectory = pathFixture.resolve("repo");
            Files.createDirectories(lowerCaseDirectory);
            check(!WorktreePaths.same(lowerCaseDirectory, worktreeRoot),
                    "Case-sensitive filesystems keep differently cased worktree paths distinct");
        }
        List<WorktreeEntry> standardList = WorktreeListParser.parse(repo,
                "worktree " + repo + "\nHEAD abc123\nbranch refs/heads/main\n\n"
                        + "worktree " + pathFixture.resolve("linked") + "\nHEAD def456\nbranch refs/heads/feature\n");
        check(standardList.getFirst().main() && !standardList.get(1).main(),
                "The first checkout is protected as main in a standard repository");
        List<WorktreeEntry> bareList = WorktreeListParser.parse(pathFixture.resolve("bare.git").toString(),
                "worktree " + pathFixture.resolve("bare.git") + "\nHEAD abc123\nbare\n\n"
                        + "worktree " + pathFixture.resolve("linked") + "\nHEAD def456\nbranch refs/heads/feature\n");
        check(bareList.getFirst().bare() && !bareList.getFirst().main(),
                "A bare repository record is never classified as the main worktree");
        check(bareList.stream().noneMatch(WorktreeEntry::main),
                "A bare repository does not invent a protected main checkout");
        check(WorktreeRemovalPolicy.allowsForceRetry(new Exception("fatal: 'path' contains modified or untracked files, use --force to delete it")),
                "Git modified/untracked removal error offers force retry");
        check(!WorktreeRemovalPolicy.allowsForceRetry(new Exception("fatal: worktree is locked")),
                "Unrelated removal failures never offer force retry");
        Path createdFolder = java.nio.file.Files.createTempDirectory("worktree-created-time-");
        try {
            WorktreeEntry healthy = entry(repo, createdFolder, "feature/healthy", "abc", false);
            WorktreeEntry stale = entry(repo, createdFolder.resolve("missing-worktree"), "feature/stale", "def", false);
            WorktreeEntry missingButLocked = new WorktreeEntry(repo, createdFolder.resolve("locked-missing").toString(),
                    "feature/locked", "fed", false, false, false, true, true);
            equal(healthy.cleanupDeleteAction(), WorktreeEntry.CleanupDeleteAction.DELETE,
                    "A registered worktree with a present path exposes delete");
            equal(stale.cleanupDeleteAction(), WorktreeEntry.CleanupDeleteAction.PRUNE,
                    "A missing registered worktree exposes prune instead of delete");
            equal(missingButLocked.cleanupDeleteAction(), WorktreeEntry.CleanupDeleteAction.PROTECTED,
                    "Git-locked stale worktrees remain protected from cleanup and deletion");
            check(!WorktreeCreatedTime.format(createdFolder, "Unavailable").equals("Unavailable"),
                    "Worktree creation time falls back to filesystem directory metadata");
            check(WorktreeCreatedTime.format(createdFolder.resolve("missing"), "Unavailable").equals("Unavailable"),
                    "Missing worktree path reports unavailable creation time");
        } finally {
            java.nio.file.Files.deleteIfExists(createdFolder);
        }
        try (var paths = Files.walk(pathFixture)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
        System.out.println("PASS: " + assertions + " language and grouping assertions");
    }

    private static WorktreeEntry entry(String repository, Path path, String branch, String head, boolean main) {
        return new WorktreeEntry(repository, path.toString(), branch, head, main, branch.startsWith("("), false, false, false);
    }

    private static void equal(Object actual, Object expected, String label) {
        check(java.util.Objects.equals(actual, expected), label + "; expected=" + expected + "; actual=" + actual);
    }

    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
}
