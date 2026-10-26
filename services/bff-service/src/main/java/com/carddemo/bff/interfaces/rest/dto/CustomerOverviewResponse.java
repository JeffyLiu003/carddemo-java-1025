package com.carddemo.bff.interfaces.rest.dto;

import com.carddemo.bff.domain.model.CustomerOverview;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户 360 概览响应（docs/design/16-channel-and-quality.md §2.1）。
 * {@code degraded} 列出本次聚合中降级的下游服务名；为空表示全部下游正常。
 */
public record CustomerOverviewResponse(
        @Schema(description = "账户号", example = "00000000001") String accountId,
        Position position,
        List<Card> cards,
        Customer customer,
        List<Transaction> recentTransactions,
        List<Offer> offers,
        List<String> degraded) {

    public static CustomerOverviewResponse from(CustomerOverview overview) {
        return new CustomerOverviewResponse(
                overview.accountId(),
                Position.from(overview.position()),
                overview.cards().stream().map(Card::from).toList(),
                overview.customer() == null ? null : Customer.from(overview.customer()),
                overview.recentTransactions().stream().map(Transaction::from).toList(),
                overview.offers().stream().map(Offer::from).toList(),
                overview.degraded());
    }

    public record Position(
            String productCode,
            BigDecimal creditLimit,
            BigDecimal currentBalance,
            BigDecimal activeHoldAmount,
            BigDecimal availableCredit) {

        static Position from(com.carddemo.bff.domain.model.AccountPosition position) {
            return new Position(position.productCode(), position.creditLimit(),
                    position.currentBalance(), position.activeHoldAmount(),
                    position.availableCredit());
        }
    }

    public record Card(
            String cardNumber,
            String customerId,
            String embossedName,
            LocalDate expiryDate,
            String activeStatus) {

        static Card from(com.carddemo.bff.domain.model.CardSummary card) {
            return new Card(card.cardNumber(), card.customerId(), card.embossedName(),
                    card.expiryDate(), card.activeStatus());
        }
    }

    public record Customer(
            String customerId,
            String firstName,
            String lastName,
            String ssnMasked,
            Integer ficoScore) {

        static Customer from(com.carddemo.bff.domain.model.CustomerProfile customer) {
            return new Customer(customer.customerId(), customer.firstName(), customer.lastName(),
                    customer.ssnMasked(), customer.ficoScore());
        }
    }

    public record Transaction(
            String transactionId,
            BigDecimal amount,
            String description,
            LocalDateTime processedTimestamp) {

        static Transaction from(com.carddemo.bff.domain.model.RecentTransaction transaction) {
            return new Transaction(transaction.transactionId(), transaction.amount(),
                    transaction.description(), transaction.processedTimestamp());
        }
    }

    public record Offer(
            String campaignId,
            String name,
            boolean eligible,
            boolean enrolled) {

        static Offer from(com.carddemo.bff.domain.model.OfferSummary offer) {
            return new Offer(offer.campaignId(), offer.name(), offer.eligible(), offer.enrolled());
        }
    }
}
