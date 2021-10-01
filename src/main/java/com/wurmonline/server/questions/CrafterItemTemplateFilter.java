package com.wurmonline.server.questions;

public class CrafterItemTemplateFilter {
    private final String str;
    private final char first;
    private final char last;

    public CrafterItemTemplateFilter(String str) {
        str = str.trim().toLowerCase();

        if (str.length() == 0) {
            first = ' ';
            last = ' ';
        } else {
            if (str.startsWith("*")) {
                first = ' ';
                str = str.substring(1);
            } else {
                first = str.charAt(0);
            }

            if (str.endsWith("*")) {
                last = ' ';
                str = str.substring(0, str.length() - 1);
            } else {
                last = str.charAt(str.length() - 1);
            }
        }

        this.str = str;
    }

    public boolean matches(String matches) {
        if (str.isEmpty()) {
            return true;
        }

        matches = matches.toLowerCase();

        if (first != ' ' && matches.charAt(0) != first)
            return false;

        if (last != ' ' && matches.charAt(matches.length() - 1) != last)
            return false;

        return matches.contains(str);
    }
}
