package com.carddemo.workflow.interfaces.rest;

import com.carddemo.workflow.domain.model.WorkflowDefinition;
import com.carddemo.workflow.infrastructure.persistence.WorkflowDefinitionJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 工作流端到端用例：H2 + Flyway + MockMvc（设计 15 §5.2）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkflowDefinitionJpaRepository definitions;

    @Test
    void listsSeededDefinitionsAsStepsArray() throws Exception {
        mockMvc.perform(get("/api/v1/workflow-definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].definitionCode").value("DISPUTE_REVIEW"))
                .andExpect(jsonPath("$.items[0].steps", hasSize(2)))
                .andExpect(jsonPath("$.items[1].definitionCode").value("LIMIT_INCREASE"))
                .andExpect(jsonPath("$.items[1].name").value("Limit increase approval"))
                .andExpect(jsonPath("$.items[1].steps[0]").value("REQUEST"))
                .andExpect(jsonPath("$.items[1].steps[2]").value("APPROVE"))
                .andExpect(jsonPath("$.items[1].active").value(true))
                .andExpect(jsonPath("$.items[2].definitionCode").value("PAYMENT_REVIEW"));
    }

    @Test
    void createsPendingTask() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/workflow-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody("LIMIT_INCREASE", "limit-request-001", "ops.user")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/workflow-tasks/")))
                .andExpect(jsonPath("$.taskId").isNotEmpty())
                .andExpect(jsonPath("$.definitionCode").value("LIMIT_INCREASE"))
                .andExpect(jsonPath("$.businessRef").value("limit-request-001"))
                .andExpect(jsonPath("$.assignee").value("ops.user"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.comment").isEmpty())
                .andExpect(jsonPath("$.decidedAt").isEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();

        String taskId = read(created).get("taskId").asText();
        mockMvc.perform(get("/api/v1/workflow-tasks/" + taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createRejectsUnknownDefinition() throws Exception {
        mockMvc.perform(post("/api/v1/workflow-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody("NO_SUCH_FLOW", "ref-1", "ops.user")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKFLOW_DEFINITION_NOT_FOUND"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void createRejectsInactiveDefinition() throws Exception {
        definitions.save(new WorkflowDefinition("LEGACY_FLOW", "历史流程",
                List.of("REQUEST", "DECIDE"), false));

        mockMvc.perform(post("/api/v1/workflow-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody("LEGACY_FLOW", "ref-1", "ops.user")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKFLOW_DEFINITION_INACTIVE"));
    }

    @Test
    void createRejectsMissingBusinessReference() throws Exception {
        mockMvc.perform(post("/api/v1/workflow-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody("PAYMENT_REVIEW", " ", "ops.user")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0]").value(containsString("businessRef")));
    }

    @Test
    void approvesPendingTaskWithComment() throws Exception {
        String taskId = createTask("PAYMENT_REVIEW", "payment-001", "ops.user");

        mockMvc.perform(post("/api/v1/workflow-tasks/" + taskId + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"金额与订单一致\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.comment").value("金额与订单一致"))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void approvesPendingTaskWithoutRequestBody() throws Exception {
        String taskId = createTask("PAYMENT_REVIEW", "payment-002", "ops.user");

        mockMvc.perform(post("/api/v1/workflow-tasks/" + taskId + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.comment").isEmpty());
    }

    @Test
    void rejectsPendingTaskWithComment() throws Exception {
        String taskId = createTask("DISPUTE_REVIEW", "dispute-001", "risk.user");

        mockMvc.perform(post("/api/v1/workflow-tasks/" + taskId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"证据不足\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.comment").value("证据不足"))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty());
    }

    @Test
    void repeatedDecisionReturns409() throws Exception {
        String taskId = createTask("LIMIT_INCREASE", "limit-002", "ops.user");

        mockMvc.perform(post("/api/v1/workflow-tasks/" + taskId + "/approve"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/workflow-tasks/" + taskId + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"再来一次\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_ALREADY_DECIDED"));

        mockMvc.perform(post("/api/v1/workflow-tasks/" + taskId + "/reject"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_ALREADY_DECIDED"));
    }

    @Test
    void getAndDecisionReturn404ForUnknownTask() throws Exception {
        mockMvc.perform(get("/api/v1/workflow-tasks/unknown-task"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKFLOW_TASK_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/workflow-tasks/unknown-task/approve"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKFLOW_TASK_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/workflow-tasks/unknown-task/reject"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKFLOW_TASK_NOT_FOUND"));
    }

    @Test
    void listFiltersByStatusAndAssignee() throws Exception {
        String first = createTask("PAYMENT_REVIEW", "payment-101", "ops.user");
        createTask("PAYMENT_REVIEW", "payment-102", "ops.user");
        createTask("DISPUTE_REVIEW", "dispute-102", "risk.user");
        mockMvc.perform(post("/api/v1/workflow-tasks/" + first + "/approve"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/workflow-tasks")
                        .param("status", "PENDING")
                        .param("assignee", "ops.user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].businessRef").value("payment-102"));

        mockMvc.perform(get("/api/v1/workflow-tasks").param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].taskId").value(first));

        mockMvc.perform(get("/api/v1/workflow-tasks")
                        .param("assignee", "risk.user")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].assignee").value("risk.user"));

        mockMvc.perform(get("/api/v1/workflow-tasks")
                        .param("status", "PENDING")
                        .param("assignee", "nobody"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void listRejectsUnknownStatusAndOutOfRangePaging() throws Exception {
        mockMvc.perform(get("/api/v1/workflow-tasks").param("status", "DECIDED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/workflow-tasks").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/workflow-tasks").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/workflow-tasks").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void decisionRejectsCommentLongerThanFiveHundredCharacters() throws Exception {
        String taskId = createTask("LIMIT_INCREASE", "limit-003", "ops.user");

        mockMvc.perform(post("/api/v1/workflow-tasks/" + taskId + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0]").value(containsString("comment")));
    }

    private String createTask(String definitionCode, String businessRef, String assignee) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/workflow-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskBody(definitionCode, businessRef, assignee)))
                .andExpect(status().isCreated())
                .andReturn();
        return read(created).get("taskId").asText();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static String taskBody(String definitionCode, String businessRef, String assignee) {
        return """
                {"definitionCode":"%s","businessRef":"%s","assignee":"%s"}
                """.formatted(definitionCode, businessRef, assignee);
    }
}
