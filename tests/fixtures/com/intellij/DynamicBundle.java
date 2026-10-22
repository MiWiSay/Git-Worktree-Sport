package com.intellij;

import java.util.Locale;

/** 仅供独立回归测试，避免启动或修改用户正在运行的 IDE。 */
public final class DynamicBundle {
    public static Locale locale = Locale.ROOT;

    public static Locale getLocale() { return locale; }
}
