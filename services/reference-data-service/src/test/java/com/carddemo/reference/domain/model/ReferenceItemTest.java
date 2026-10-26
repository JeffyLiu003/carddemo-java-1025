package com.carddemo.reference.domain.model;

import com.carddemo.reference.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferenceItemTest {

    @Test
    void normalizesDomainAndCodeToUpperCase() {
        ReferenceItem item = new ReferenceItem("item-1",
                new ReferenceItemDraft(" currency ", " usd ", " 美元 ", null, null));

        assertThat(item.getItemId()).isEqualTo("item-1");
        assertThat(item.getDomain()).isEqualTo("CURRENCY");
        assertThat(item.getCode()).isEqualTo("USD");
        assertThat(item.getLabel()).isEqualTo("美元");
        assertThat(item.getSortOrder()).isZero();
        assertThat(item.isActive()).isTrue();
        assertThat(item.getVersion()).isZero();
    }

    @Test
    void keepsSuppliedSortOrderAndActiveFlag() {
        ReferenceItem item = new ReferenceItem("item-2",
                new ReferenceItemDraft("PRODUCT_CATEGORY", "platinum", "白金卡", 30, false));

        assertThat(item.getDomain()).isEqualTo("PRODUCT_CATEGORY");
        assertThat(item.getCode()).isEqualTo("PLATINUM");
        assertThat(item.getSortOrder()).isEqualTo(30);
        assertThat(item.isActive()).isFalse();
    }

    @Test
    void rejectsMissingLabel() {
        assertThatThrownBy(() -> new ReferenceItem("item-3",
                new ReferenceItemDraft("CURRENCY", "JPY", "  ", null, null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("label");
    }

    @Test
    void rejectsMissingDomainOrCode() {
        assertThatThrownBy(() -> new ReferenceItem("item-4",
                new ReferenceItemDraft(null, "JPY", "日元", null, null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("domain");
        assertThatThrownBy(() -> new ReferenceItem("item-5",
                new ReferenceItemDraft("CURRENCY", "", "日元", null, null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("code");
    }

    @Test
    void rejectsNegativeSortOrder() {
        assertThatThrownBy(() -> new ReferenceItem("item-6",
                new ReferenceItemDraft("CURRENCY", "JPY", "日元", -1, null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("sortOrder");
    }

    @Test
    void updateChangesOnlyLabelSortOrderAndActive() {
        ReferenceItem item = new ReferenceItem("item-7",
                new ReferenceItemDraft("CURRENCY", "jpy", "日元", 40, true));

        item.applyUpdate(new ReferenceItemUpdate("日元（JPY）", 45, false));

        assertThat(item.getDomain()).isEqualTo("CURRENCY");
        assertThat(item.getCode()).isEqualTo("JPY");
        assertThat(item.getLabel()).isEqualTo("日元（JPY）");
        assertThat(item.getSortOrder()).isEqualTo(45);
        assertThat(item.isActive()).isFalse();
    }

    @Test
    void updateFallsBackToDefaultsWhenOmitted() {
        ReferenceItem item = new ReferenceItem("item-8",
                new ReferenceItemDraft("CURRENCY", "jpy", "日元", 40, false));

        item.applyUpdate(new ReferenceItemUpdate("日元", null, null));

        assertThat(item.getSortOrder()).isZero();
        assertThat(item.isActive()).isTrue();
    }
}
