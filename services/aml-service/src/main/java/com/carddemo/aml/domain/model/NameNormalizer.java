package com.carddemo.aml.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 名单姓名归一化：大写、标点与符号转空格、压缩内部空格、去首尾空格。
 */
public final class NameNormalizer {

    private static final Pattern PUNCTUATION_OR_SYMBOL = Pattern.compile("[\\p{P}\\p{S}]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0]+");

    private NameNormalizer() {
    }

    public static String normalize(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String upper = name.toUpperCase(Locale.ROOT);
        String withoutPunctuation = PUNCTUATION_OR_SYMBOL.matcher(upper).replaceAll(" ");
        return WHITESPACE.matcher(withoutPunctuation).replaceAll(" ").trim();
    }
}
