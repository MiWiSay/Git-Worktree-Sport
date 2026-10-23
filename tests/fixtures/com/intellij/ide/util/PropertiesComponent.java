package com.intellij.ide.util;

import java.util.HashMap;
import java.util.Map;

/** 内存偏好存储替身，不读写真实 IDE 配置。 */
public final class PropertiesComponent {
    private static final PropertiesComponent INSTANCE = new PropertiesComponent();
    public final Map<String, String> values = new HashMap<>();

    public static PropertiesComponent getInstance() { return INSTANCE; }
    public String getValue(String key, String defaultValue) { return values.getOrDefault(key, defaultValue); }
    public void setValue(String key, String value) { values.put(key, value); }
}
