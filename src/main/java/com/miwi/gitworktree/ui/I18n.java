package com.miwi.gitworktree.ui;

import com.intellij.DynamicBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationNamesInfo;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Runtime localization backed by user-editable per-language JSON files. */
public final class I18n {
    private static volatile LanguageCatalog catalog = LanguageCatalog.load();
    private static final String LANGUAGE_KEY = "git.worktree.sport.language";
    private static final CopyOnWriteArrayList<Runnable> LANGUAGE_LISTENERS = new CopyOnWriteArrayList<>();

    private I18n() {}

    public static String text(String key) {
        LanguageCatalog current = catalog;
        LanguageCatalog.Option option = effectiveOption(current, current.option(language()));
        String message = current.messages(option.id()).getOrDefault(key, key).replace("\\n", "\n");
        String ideName = ApplicationNamesInfo.getInstance().getFullProductName();
        // 旧版用户翻译也需要适配实际 IDE，不改写用户保存的其他文案。
        return message.replace("Android Studio", ideName).replace("{ideName}", ideName);
    }

    public static String format(String key, Object... arguments) {
        String template = text(key);
        // Treat apostrophes in translations as punctuation while retaining MessageFormat placeholders.
        return new MessageFormat(template.replace("'", "''"), selectedLocale()).format(arguments);
    }

    public static String language() {
        return catalog.option(PropertiesComponent.getInstance().getValue(LANGUAGE_KEY, "system")).id();
    }

    public static void setLanguage(String language) {
        PropertiesComponent.getInstance().setValue(LANGUAGE_KEY, catalog.option(language).id());
        notifyLanguageChanged();
    }

    public static List<LanguageCatalog.Option> languageOptions() { return catalog.options(); }

    public static String languageOptionLabel(LanguageCatalog.Option option) {
        return option.labelKey() == null ? option.displayName() : text(option.labelKey());
    }

    public static String editableLanguageId() {
        return effectiveOption(catalog, catalog.option(language())).id();
    }

    public static Map<String, String> languageNames() { return catalog.names(); }

    public static String languageName(String id) { return catalog.languageName(id); }

    public static String exportLanguageMessages(String id) { return catalog.exportMessages(id); }

    public static String exportLanguageNames() { return catalog.exportNames(); }

    public static Map<String, String> snapshotLanguageFiles() throws java.io.IOException {
        return catalog.snapshotFiles();
    }

    public static void restoreLanguageFiles(Map<String, String> snapshot, String selectedLanguage)
            throws java.io.IOException {
        catalog.restoreFiles(snapshot);
        reloadCatalog();
        PropertiesComponent.getInstance().setValue(LANGUAGE_KEY, catalog.option(selectedLanguage).id());
        notifyLanguageChanged();
    }

    public static void saveLanguageMessages(String id, Map<String, String> messages) throws java.io.IOException {
        catalog.saveMessages(id, messages);
        reloadCatalog();
        notifyLanguageChanged();
    }

    public static void importLanguageMessages(String id, String text) throws java.io.IOException {
        catalog.importMessages(id, text);
        reloadCatalog();
        notifyLanguageChanged();
    }

    public static void importLanguageNames(String text) throws java.io.IOException {
        catalog.importNames(text);
        reloadCatalog();
        notifyLanguageChanged();
    }

    public static void renameLanguage(String id, String name) throws java.io.IOException {
        catalog.rename(id, name);
        reloadCatalog();
        notifyLanguageChanged();
    }

    public static String addLanguage() throws java.io.IOException {
        String id = catalog.addLanguage();
        reloadCatalog();
        notifyLanguageChanged();
        return id;
    }

    public static boolean canDeleteLanguage(String id) { return catalog.canDeleteLanguage(id); }

    public static void deleteLanguage(String id) throws java.io.IOException {
        String selectedLanguage = language();
        catalog.deleteLanguage(id);
        reloadCatalog();
        if (id.equals(selectedLanguage)) {
            PropertiesComponent.getInstance().setValue(LANGUAGE_KEY, "system");
        }
        notifyLanguageChanged();
    }

    /** Compatibility entry point used by regression checks and text-import integrations. */
    public static void setLanguageMessagesText(String id, String value) throws java.io.IOException {
        importLanguageMessages(id, value);
    }

    public static String editableMessagesText(String id) { return exportLanguageMessages(id); }

    public static void addLanguageListener(Runnable listener) { LANGUAGE_LISTENERS.add(listener); }

    public static void removeLanguageListener(Runnable listener) { LANGUAGE_LISTENERS.remove(listener); }

    private static LanguageCatalog.Option effectiveOption(LanguageCatalog current, LanguageCatalog.Option option) {
        return option.followsIde() ? current.languageForLocale(DynamicBundle.getLocale()) : option;
    }

    private static Locale selectedLocale() {
        LanguageCatalog current = catalog;
        LanguageCatalog.Option option = effectiveOption(current, current.option(language()));
        return option.localeTag() == null || option.localeTag().isBlank()
                ? DynamicBundle.getLocale() : Locale.forLanguageTag(option.localeTag());
    }

    private static synchronized void reloadCatalog() { catalog = LanguageCatalog.load(); }

    private static void notifyLanguageChanged() { LANGUAGE_LISTENERS.forEach(Runnable::run); }
}
