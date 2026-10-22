package com.intellij.openapi.application;

/** Allows runtime product-name checks without starting an IDE. */
public final class ApplicationNamesInfo {
    public static String product = "Test IDE";
    public static ApplicationNamesInfo getInstance() { return new ApplicationNamesInfo(); }
    public String getFullProductName() { return product; }
}
