package com.carddemo.notification.domain.service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板渲染：把 {@code {{name}}} 占位替换为 payload 中的同名变量。
 *
 * <p>约定：缺失变量（或值为 null）替换为空串——对外文案不留占位符，避免把内部变量名发给客户；
 * 占位符允许出现空白（{@code {{ name }}}），未知的空白/符号不视为占位符。
 */
public final class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}");

    private TemplateRenderer() {
    }

    public static String render(String template, Map<String, String> payload) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        Map<String, String> values = payload == null ? Map.of() : payload;
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }
}
