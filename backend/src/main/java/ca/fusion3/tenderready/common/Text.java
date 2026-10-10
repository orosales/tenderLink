package ca.fusion3.tenderready.common;

import java.util.Locale;

public final class Text {
    private Text() {}

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Case- and whitespace-insensitive key used to compare places, services and qualification names. */
    public static String norm(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public static boolean sameKey(String a, String b) {
        return !norm(a).isEmpty() && norm(a).equals(norm(b));
    }

    public static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max);
    }
}
