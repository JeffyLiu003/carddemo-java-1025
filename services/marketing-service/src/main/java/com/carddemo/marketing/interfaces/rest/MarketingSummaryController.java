package com.carddemo.marketing.interfaces.rest;

import com.carddemo.marketing.application.CampaignService;
import com.carddemo.marketing.application.MarketingSummaryView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "营销看板", description = "首页看板汇总")
@RestController
@RequestMapping("/api/v1/marketing")
public class MarketingSummaryController {

    private final CampaignService campaigns;

    public MarketingSummaryController(CampaignService campaigns) {
        this.campaigns = campaigns;
    }

    @Operation(summary = "看板汇总",
            description = "activeCampaigns（进行中活动）、grantedRewards（已入账笔数）、"
                    + "cashbackTotal（已入账返现合计）、pendingRewards（待入账笔数）")
    @GetMapping("/summary")
    public MarketingSummaryView summary() {
        return campaigns.summary();
    }
}
