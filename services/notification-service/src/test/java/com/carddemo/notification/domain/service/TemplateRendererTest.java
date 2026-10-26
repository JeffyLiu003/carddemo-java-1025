package com.carddemo.notification.domain.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模板渲染：{{name}} 占位替换；缺失变量（或 null 值）渲染为空串；允许占位内空白。
 */
class TemplateRendererTest {

    @Test
    void replacesEveryPlaceholderFromPayload() {
        String body = "尊敬的 {{name}}：卡号 {{cardNumber}} 的还款 {{amount}} 已到账。";

        String rendered = TemplateRenderer.render(body, Map.of(
                "name", "Alice", "cardNumber", "4000000000000001", "amount", "100.00"));

        assertThat(rendered).isEqualTo("尊敬的 Alice：卡号 4000000000000001 的还款 100.00 已到账。");
    }

    @Test
    void rendersMissingVariablesAsEmptyString() {
        String body = "尊敬的 {{name}}：验证码 {{code}}。";

        assertThat(TemplateRenderer.render(body, Map.of("name", "Alice")))
                .isEqualTo("尊敬的 Alice：验证码 。");
        assertThat(TemplateRenderer.render(body, Map.of())).isEqualTo("尊敬的 ：验证码 。");
        assertThat(TemplateRenderer.render(body, null)).isEqualTo("尊敬的 ：验证码 。");
    }

    @Test
    void rendersNullValueAsEmptyString() {
        Map<String, String> payload = new HashMap<>();
        payload.put("name", "Alice");
        payload.put("code", null);

        assertThat(TemplateRenderer.render("{{name}}/{{code}}!", payload)).isEqualTo("Alice/!");
    }

    @Test
    void supportsRepeatedPlaceholdersAndSurroundingWhitespace() {
        assertThat(TemplateRenderer.render("{{ name }}={{name}};{{ code }}", Map.of("name", "x", "code", "1")))
                .isEqualTo("x=x;1");
    }

    @Test
    void leavesUnknownBracesAndEmptyTemplatesAlone() {
        assertThat(TemplateRenderer.render("plain text", Map.of("name", "x"))).isEqualTo("plain text");
        assertThat(TemplateRenderer.render("{{unknown}}", Map.of())).isEmpty();
        assertThat(TemplateRenderer.render("{{ not answerable }}", Map.of())).isEqualTo("{{ not answerable }}");
        assertThat(TemplateRenderer.render(null, Map.of())).isEmpty();
        assertThat(TemplateRenderer.render("", Map.of())).isEmpty();
    }

    @Test
    void doesNotInterpretReplacementValueAsPattern() {
        assertThat(TemplateRenderer.render("{{name}}", Map.of("name", "$1 \\ $2")))
                .isEqualTo("$1 \\ $2");
    }
}
