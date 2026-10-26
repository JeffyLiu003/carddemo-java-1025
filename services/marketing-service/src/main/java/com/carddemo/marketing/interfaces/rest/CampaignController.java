package com.carddemo.marketing.interfaces.rest;

import com.carddemo.marketing.application.CampaignPerformanceView;
import com.carddemo.marketing.application.CampaignService;
import com.carddemo.marketing.application.EnrollmentOutcome;
import com.carddemo.marketing.application.EnrollmentService;
import com.carddemo.marketing.interfaces.rest.dto.CampaignListResponse;
import com.carddemo.marketing.interfaces.rest.dto.CampaignResponse;
import com.carddemo.marketing.interfaces.rest.dto.CreateCampaignRequest;
import com.carddemo.marketing.interfaces.rest.dto.EnrollRequest;
import com.carddemo.marketing.interfaces.rest.dto.EnrollmentListResponse;
import com.carddemo.marketing.interfaces.rest.dto.EnrollmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "营销活动", description = "活动创建/发布/暂停/结束、报名、活动效果")
@RestController
@RequestMapping("/api/v1/campaigns")
public class CampaignController {

    private final CampaignService campaigns;
    private final EnrollmentService enrollments;

    public CampaignController(CampaignService campaigns, EnrollmentService enrollments) {
        this.campaigns = campaigns;
        this.enrollments = enrollments;
    }

    @Operation(summary = "创建活动",
            description = "按 durationMonths 计算 endDate = startDate + N 月 - 1 天；新活动为 DRAFT。"
                    + "同 code 重复创建返回 409 CAMPAIGN_CODE_ALREADY_EXISTS。")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "白金卡 3 个月消费返现",
            value = """
                    {"code":"PLATINUM_3M_CASHBACK","name":"白金卡 3 个月消费返现",
                     "description":"活动期内消费 5% 返现，单笔最高 30，累计最高 300",
                     "startDate":"2026-10-01","durationMonths":3,
                     "eligibleProductCodes":["PLATINUM"],
                     "minTransactionAmount":50.00,"cashbackRate":0.05,
                     "maxCashbackPerTransaction":30.00,"maxCashbackPerCustomer":300.00,
                     "budget":50000.00,"enrollmentRequired":true}
                    """)))
    @PostMapping
    public ResponseEntity<CampaignResponse> create(@Valid @RequestBody CreateCampaignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CampaignResponse.from(campaigns.create(request.toCommand())));
    }

    @Operation(summary = "活动列表", description = "按创建时间倒序分页，可按 status 过滤")
    @GetMapping
    public CampaignListResponse list(@RequestParam(required = false) String status,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : size;
        return CampaignListResponse.from(campaigns.list(status, safePage, safeSize), safePage, safeSize);
    }

    @Operation(summary = "活动详情")
    @GetMapping("/{campaignId}")
    public CampaignResponse get(@PathVariable String campaignId) {
        return CampaignResponse.from(campaigns.get(campaignId));
    }

    @Operation(summary = "发布活动", description = "DRAFT/PAUSED → ACTIVE；非法迁移返回 409 CAMPAIGN_INVALID_TRANSITION")
    @PostMapping("/{campaignId}/activate")
    public CampaignResponse activate(@PathVariable String campaignId) {
        return CampaignResponse.from(campaigns.activate(campaignId));
    }

    @Operation(summary = "暂停活动", description = "ACTIVE → PAUSED；非法迁移返回 409")
    @PostMapping("/{campaignId}/pause")
    public CampaignResponse pause(@PathVariable String campaignId) {
        return CampaignResponse.from(campaigns.pause(campaignId));
    }

    @Operation(summary = "结束活动", description = "ACTIVE/PAUSED → ENDED；结束后不再参与返现评估")
    @PostMapping("/{campaignId}/end")
    public CampaignResponse end(@PathVariable String campaignId) {
        return CampaignResponse.from(campaigns.end(campaignId));
    }

    @Operation(summary = "报名",
            description = "幂等：已报名返回已有记录（200），首次报名 201。活动非 ACTIVE 409、产品不匹配 409、"
                    + "账户不存在 404、账户停用 409。")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "白金账户报名",
            value = """
                    {"accountId":"00000000006","customerId":"000000001","cardNumber":"3344556677889900"}
                    """)))
    @PostMapping("/{campaignId}/enrollments")
    public ResponseEntity<EnrollmentResponse> enroll(@PathVariable String campaignId,
                                                     @Valid @RequestBody EnrollRequest request) {
        EnrollmentOutcome outcome = enrollments.enroll(campaignId, request.accountId(),
                request.cardNumber(), request.customerId());
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(EnrollmentResponse.from(outcome.enrollment()));
    }

    @Operation(summary = "报名列表", description = "按报名时间倒序分页")
    @GetMapping("/{campaignId}/enrollments")
    public EnrollmentListResponse enrollments(@PathVariable String campaignId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : size;
        return EnrollmentListResponse.from(enrollments.list(campaignId, safePage, safeSize), safePage, safeSize);
    }

    @Operation(summary = "活动效果",
            description = "报名数、返现笔数/已入账/待入账、已入账返现合计、预算使用率、报名→返现转化率")
    @GetMapping("/{campaignId}/performance")
    public CampaignPerformanceView performance(@PathVariable String campaignId) {
        return campaigns.performance(campaignId);
    }
}
