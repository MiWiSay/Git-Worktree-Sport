package com.miwi.gitworktree.ui;

import com.intellij.openapi.application.PathManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Loads the language-name map and each translation from separate editable JSON files. */
final class LanguageCatalog {
    private static final String RESOURCE_ROOT = "/i18n/";
    private static final String LANGUAGE_LIST_FILE = "languages.json";
    private static final String IDE_OPTION_ID = "system";
    private static final String LOCALE_FIELD = "__locale";
    private static final String CUSTOM_CONFIG_DIRECTORY = "GitWorktreeSport";
    private static final String LANGUAGE_DIRECTORY = "languages";
    private static final String LANGUAGE_ID_PATTERN = "[A-Za-z0-9_-]{1,8}";

    private final Path languageDirectory;
    private final LinkedHashMap<String, String> names;
    private final LinkedHashMap<String, Map<String, String>> languageFiles;
    private final Set<String> bundledLanguageIds;
    private final String fallbackLanguage;

    private LanguageCatalog(Path languageDirectory,
                            LinkedHashMap<String, String> names,
                            LinkedHashMap<String, Map<String, String>> languageFiles,
                            Set<String> bundledLanguageIds) {
        this.languageDirectory = languageDirectory;
        this.names = names;
        this.languageFiles = languageFiles;
        this.bundledLanguageIds = Set.copyOf(bundledLanguageIds);
        if (names.isEmpty()) throw new IllegalStateException("Language list must contain at least one language.");
        this.fallbackLanguage = names.keySet().iterator().next();
        if (messages(fallbackLanguage).isEmpty()) throw new IllegalStateException("Fallback language has no messages.");
    }

    static LanguageCatalog load() {
        try {
            Path languageDirectory = Path.of(PathManager.getConfigPath(), CUSTOM_CONFIG_DIRECTORY, LANGUAGE_DIRECTORY);
            Files.createDirectories(languageDirectory);
            Path languageList = languageDirectory.resolve(LANGUAGE_LIST_FILE);
            if (!Files.exists(languageList)) copyResource(LANGUAGE_LIST_FILE, languageList);
            Map<String, String> bundledNames = readPackagedMessagesFromFile(LANGUAGE_LIST_FILE);
            Set<String> bundledLanguageIds = new LinkedHashSet<>(bundledNames.keySet());

            LinkedHashMap<String, String> names = new LinkedHashMap<>(FlatJson.parse(Files.readString(languageList)));
            if (names.isEmpty()) throw new IllegalStateException("Language list must not be empty.");
            // 升级时追加新增内置语言，但保留用户的语言名称、顺序和自定义翻译。
            boolean addedLanguages = false;
            for (Map.Entry<String, String> entry : bundledNames.entrySet()) {
                if (!names.containsKey(entry.getKey())) {
                    names.put(entry.getKey(), entry.getValue());
                    addedLanguages = true;
                }
            }
            if (addedLanguages) writeAtomically(languageList, FlatJson.write(names));
            LinkedHashMap<String, Map<String, String>> files = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : names.entrySet()) {
                String id = entry.getKey();
                validateLanguageId(id);
                if (entry.getValue().isBlank()) throw new IllegalStateException("Language name must not be blank: " + id);
                Path file = languageFile(languageDirectory, id);
                if (!Files.exists(file)) {
                    try {
                        copyResource(id + ".json", file);
                    } catch (IOException missingPackagedLanguage) {
                        // A user-edited language list may outlive the default resources; seed it from English.
                        Map<String, String> fallback = files.get(names.keySet().iterator().next());
                        if (fallback == null) throw missingPackagedLanguage;
                        LinkedHashMap<String, String> copy = new LinkedHashMap<>(fallback);
                        copy.put(LOCALE_FIELD, "");
                        writeAtomically(file, FlatJson.write(copy));
                    }
                }
                LinkedHashMap<String, String> messages = new LinkedHashMap<>(readPackagedMessages(id));
                messages.putAll(FlatJson.parse(Files.readString(file)));
                files.put(id, Map.copyOf(messages));
            }
            return new LanguageCatalog(languageDirectory, names, files, bundledLanguageIds);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalStateException stateException) throw stateException;
            throw new IllegalStateException("Could not load language JSON files.", exception);
        }
    }

    List<Option> options() {
        List<Option> result = new ArrayList<>();
        result.add(new Option(IDE_OPTION_ID, null, "settings.language.system", null, true));
        names.forEach((id, name) -> result.add(new Option(id, name, null, localeTag(id), false)));
        return List.copyOf(result);
    }

    Option ideDefault() { return options().get(0); }

    Option option(String id) {
        if (id == null || IDE_OPTION_ID.equals(id)) return ideDefault();
        return names.containsKey(id) ? new Option(id, names.get(id), null, localeTag(id), false) : ideDefault();
    }

    Map<String, String> names() { return Collections.unmodifiableMap(new LinkedHashMap<>(names)); }

    String languageName(String id) { return names.get(id); }

    Map<String, String> messages(String id) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>(fallbackMessages());
        result.putAll(languageFiles.getOrDefault(id, Map.of()));
        result.remove(LOCALE_FIELD);
        return Map.copyOf(result);
    }

    Map<String, String> fallbackMessages() { return rawMessages(fallbackLanguage); }

    private Map<String, String> rawMessages(String id) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>(languageFiles.getOrDefault(id, Map.of()));
        result.remove(LOCALE_FIELD);
        return Map.copyOf(result);
    }

    Option languageForLocale(Locale locale) {
        Option languageMatch = null;
        Option genericMatch = null;
        Option scriptMatch = null;
        Option countryMatch = null;
        for (Option option : options()) {
            if (option.followsIde() || option.localeTag() == null || option.localeTag().isBlank()) continue;
            Locale configuredLocale = Locale.forLanguageTag(option.localeTag());
            if (configuredLocale.toLanguageTag().equalsIgnoreCase(locale.toLanguageTag())) return option;
            if (configuredLocale.getLanguage().equalsIgnoreCase(locale.getLanguage())) {
                if (languageMatch == null) languageMatch = option;
                if (configuredLocale.getCountry().isEmpty() && configuredLocale.getScript().isEmpty()) genericMatch = option;
                if (!locale.getCountry().isEmpty() && locale.getCountry().equalsIgnoreCase(configuredLocale.getCountry())) countryMatch = option;
                if (!locale.getScript().isEmpty() && locale.getScript().equalsIgnoreCase(configuredLocale.getScript()) && scriptMatch == null) {
                    scriptMatch = option;
                }
            }
        }
        Option selected = scriptMatch != null ? scriptMatch : countryMatch != null ? countryMatch
                : genericMatch != null ? genericMatch : languageMatch;
        return selected == null ? option(fallbackLanguage) : selected;
    }

    Map<String, String> editorMessages(String id) {
        return new TreeMap<>(messages(id));
    }

    String exportMessages(String id) { return exportKeyValues(editorMessages(id)); }

    void saveMessages(String id, Map<String, String> messages) throws IOException {
        requireLanguage(id);
        Set<String> expected = fallbackMessages().keySet();
        validateKeys(expected, messages.keySet());
        LinkedHashMap<String, String> updated = new LinkedHashMap<>();
        String locale = localeTag(id);
        if (locale != null && !locale.isBlank()) updated.put(LOCALE_FIELD, locale);
        new TreeMap<>(messages).forEach(updated::put);
        Path file = languageFile(languageDirectory, id);
        writeAtomically(file, FlatJson.write(updated));
        languageFiles.put(id, Map.copyOf(updated));
    }

    void importMessages(String id, String text) throws IOException {
        Map<String, String> imported = parseKeyValues(text);
        validateKeys(fallbackMessages().keySet(), imported.keySet());
        saveMessages(id, imported);
    }

    String exportNames() { return exportKeyValues(names); }

    Map<String, String> snapshotFiles() throws IOException {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        try (var files = Files.list(languageDirectory)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                snapshot.put(file.getFileName().toString(), Files.readString(file));
            }
        }
        return Collections.unmodifiableMap(snapshot);
    }

    void restoreFiles(Map<String, String> snapshot) throws IOException {
        Set<String> retainedFiles = new LinkedHashSet<>(snapshot.keySet());
        for (Map.Entry<String, String> file : snapshot.entrySet()) {
            String name = file.getKey();
            if (!name.matches("[A-Za-z0-9_-]{1,8}\\.json") && !LANGUAGE_LIST_FILE.equals(name)) {
                throw new IllegalArgumentException("Invalid language configuration file: " + name);
            }
            writeAtomically(languageDirectory.resolve(name), file.getValue());
        }
        try (var files = Files.list(languageDirectory)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                if (!retainedFiles.contains(file.getFileName().toString())) Files.deleteIfExists(file);
            }
        }
    }

    void importNames(String text) throws IOException {
        Map<String, String> imported = parseKeyValues(text);
        validateKeys(names.keySet(), imported.keySet());
        imported.forEach((id, name) -> {
            validateLanguageId(id);
            if (name.isBlank()) throw new IllegalArgumentException("Language name is empty for ID: " + id);
        });
        // The first configured language remains the fallback; importing names must not silently change it.
        names.replaceAll((id, previous) -> imported.get(id));
        persistNames();
    }

    void rename(String id, String name) throws IOException {
        requireLanguage(id);
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("Language name must not be empty.");
        names.put(id, normalized);
        persistNames();
    }

    String addLanguage() throws IOException {
        int sequence = 1;
        for (String id : names.keySet()) {
            if (id.startsWith("new-")) {
                try { sequence = Math.max(sequence, Integer.parseInt(id.substring(4)) + 1); }
                catch (NumberFormatException ignored) { }
            }
        }
        String id;
        do {
            id = "new-" + sequence++;
            validateLanguageId(id);
        } while (names.containsKey(id));

        LinkedHashMap<String, String> copy = new LinkedHashMap<>(languageFiles.get(fallbackLanguage));
        copy.put(LOCALE_FIELD, "");
        writeAtomically(languageFile(languageDirectory, id), FlatJson.write(copy));
        names.put(id, "New " + (sequence - 1));
        languageFiles.put(id, Map.copyOf(copy));
        persistNames();
        return id;
    }

    boolean canDeleteLanguage(String id) {
        return names.size() > 1 && names.containsKey(id) && !bundledLanguageIds.contains(id);
    }

    void deleteLanguage(String id) throws IOException {
        if (!canDeleteLanguage(id)) throw new IllegalArgumentException("This language is required and cannot be deleted: " + id);
        LinkedHashMap<String, String> previousNames = new LinkedHashMap<>(names);
        names.remove(id);
        try {
            persistNames();
            Files.deleteIfExists(languageFile(languageDirectory, id));
            languageFiles.remove(id);
        } catch (IOException exception) {
            names.clear();
            names.putAll(previousNames);
            try {
                persistNames();
            } catch (IOException restoreFailure) {
                exception.addSuppressed(restoreFailure);
            }
            throw exception;
        }
    }

    String parseAndSaveMessages(String id, String text) throws IOException {
        importMessages(id, text);
        return exportMessages(id);
    }

    private String localeTag(String id) { return languageFiles.getOrDefault(id, Map.of()).get(LOCALE_FIELD); }

    private void persistNames() throws IOException {
        writeAtomically(languageDirectory.resolve(LANGUAGE_LIST_FILE), FlatJson.write(names));
    }

    private void requireLanguage(String id) {
        if (!names.containsKey(id)) throw new IllegalArgumentException("Unknown language ID: " + id);
    }

    static Map<String, String> parseKeyValues(String text) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        String[] lines = text.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (line.isBlank()) continue;
            int separator = line.indexOf('=');
            if (separator <= 0) throw new IllegalArgumentException("Line " + (index + 1) + " must use key=value format.");
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).replace("\\n", "\n");
            if (result.putIfAbsent(key, value) != null) throw new IllegalArgumentException("Duplicate key: " + key);
        }
        return result;
    }

    static String exportKeyValuesForUi(Map<String, String> values) {
        StringBuilder result = new StringBuilder();
        values.forEach((key, value) -> result.append(key).append('=').append(value.replace("\n", "\\n")).append('\n'));
        return result.toString();
    }

    private static String exportKeyValues(Map<String, String> values) { return exportKeyValuesForUi(values); }

    private static void validateKeys(Set<String> expected, Set<String> actual) {
        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(actual);
        Set<String> unknown = new LinkedHashSet<>(actual);
        unknown.removeAll(expected);
        if (!missing.isEmpty() || !unknown.isEmpty()) {
            List<String> problems = new ArrayList<>();
            if (!missing.isEmpty()) problems.add("Missing keys: " + String.join(", ", missing));
            if (!unknown.isEmpty()) problems.add("Unknown keys: " + String.join(", ", unknown));
            throw new IllegalArgumentException(String.join("; ", problems));
        }
    }

    private static void validateLanguageId(String id) {
        if (id == null || !id.matches(LANGUAGE_ID_PATTERN)) {
            throw new IllegalArgumentException("Language file ID must be 1-8 letters, numbers, '_' or '-': " + id);
        }
    }

    private static Path languageFile(Path directory, String id) {
        validateLanguageId(id);
        return directory.resolve(id + ".json");
    }

    private static void copyResource(String resourceName, Path target) throws IOException {
        try (InputStream input = LanguageCatalog.class.getResourceAsStream(RESOURCE_ROOT + resourceName)) {
            if (input == null) throw new IOException("Missing packaged localization resource: " + resourceName);
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Map<String, String> readPackagedMessages(String id) throws IOException {
        return readPackagedMessagesFromFile(id + ".json");
    }

    private static Map<String, String> readPackagedMessagesFromFile(String file) throws IOException {
        try (InputStream input = LanguageCatalog.class.getResourceAsStream(RESOURCE_ROOT + file)) {
            if (input == null) return Map.of();
            return FlatJson.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static void writeAtomically(Path target, String contents) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    record Option(String id, String displayName, String labelKey, String localeTag, boolean followsIde) {}
}
