package com.carddemo.bff.domain.model;

/**
 * 可参与活动（marketing-service 权益视图）。
 */
public record OfferSummary(
        String campaignId,
        String name,
        boolean eligible,
        boolean enrolled) {
}
