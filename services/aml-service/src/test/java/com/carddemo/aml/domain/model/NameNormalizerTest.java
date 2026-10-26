package com.carddemo.aml.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 姓名归一化：大写、标点转空格、压缩空格、去首尾空格。
 */
class NameNormalizerTest {

    @Test
    void uppercasesAndTrims() {
        assertThat(NameNormalizer.normalize("  ivan petrov  ")).isEqualTo("IVAN PETROV");
    }

    @Test
    void collapsesInternalWhitespace() {
        assertThat(NameNormalizer.normalize("Ivan \t  Petrov")).isEqualTo("IVAN PETROV");
    }

    @Test
    void removesPunctuation() {
        assertThat(NameNormalizer.normalize("O'Brien, Ivan-Petrov.")).isEqualTo("O BRIEN IVAN PETROV");
    }

    @Test
    void returnsEmptyForBlankOrPunctuationOnlyInput() {
        assertThat(NameNormalizer.normalize(null)).isEmpty();
        assertThat(NameNormalizer.normalize("   ")).isEmpty();
        assertThat(NameNormalizer.normalize("...---")).isEmpty();
    }

    @Test
    void keepsUnicodeLetters() {
        assertThat(NameNormalizer.normalize("José  Müller")).isEqualTo("JOSÉ MÜLLER");
    }
}
