package com.ast.platform.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TaskIdempotentSubmissionTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnSameTaskWhenDuplicateRequestId() throws Exception {
        String requestBody = """
                {
                  "tenantId": "tenant-idempotent-1",
                  "taskType": "idempotent-test",
                  "bizKey": "biz-idempotent-1",
                  "requestId": "req-idempotent-unique",
                  "workerGroup": "test-group",
                  "tag": "default",
                  "payload": "{\\"test\\":1}",
                  "callbackUrl": "http://callback.test/notify"
                }
                """;

        MvcResult firstResult = mockMvc.perform(post("/task/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.idempotent").value(false))
                .andReturn();

        String firstTaskId = extractTaskId(firstResult.getResponse().getContentAsString());

        MvcResult secondResult = mockMvc.perform(post("/task/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.idempotent").value(true))
                .andExpect(jsonPath("$.data.taskId").value(firstTaskId))
                .andReturn();

        String secondTaskId = extractTaskId(secondResult.getResponse().getContentAsString());
        assertThat(secondTaskId).isEqualTo(firstTaskId);
    }

    @Test
    void shouldReturnSameTaskWhenDuplicateBizKey() throws Exception {
        String firstRequest = """
                {
                  "tenantId": "tenant-idempotent-2",
                  "taskType": "bizkey-test",
                  "bizKey": "bizkey-unique-001",
                  "requestId": "req-bizkey-1",
                  "workerGroup": "test-group",
                  "tag": "default",
                  "payload": "{\\"data\\":1}",
                  "callbackUrl": "http://callback.test/notify"
                }
                """;

        MvcResult firstResult = mockMvc.perform(post("/task/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotent").value(false))
                .andReturn();

        String firstTaskId = extractTaskId(firstResult.getResponse().getContentAsString());

        String secondRequest = """
                {
                  "tenantId": "tenant-idempotent-2",
                  "taskType": "bizkey-test",
                  "bizKey": "bizkey-unique-001",
                  "requestId": "req-bizkey-2",
                  "workerGroup": "test-group",
                  "tag": "default",
                  "payload": "{\\"data\\":2}",
                  "callbackUrl": "http://callback.test/notify"
                }
                """;

        MvcResult secondResult = mockMvc.perform(post("/task/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotent").value(true))
                .andExpect(jsonPath("$.data.taskId").value(firstTaskId))
                .andReturn();

        String secondTaskId = extractTaskId(secondResult.getResponse().getContentAsString());
        assertThat(secondTaskId).isEqualTo(firstTaskId);
    }

    @Test
    void shouldAllowDifferentTenantsWithSameBizKey() throws Exception {
        String tenantARequest = """
                {
                  "tenantId": "tenant-a-bizkey",
                  "taskType": "multi-tenant-test",
                  "bizKey": "shared-bizkey",
                  "requestId": "req-tenant-a",
                  "workerGroup": "test-group",
                  "tag": "default",
                  "payload": "{}",
                  "callbackUrl": "http://callback.test/notify"
                }
                """;

        MvcResult tenantAResult = mockMvc.perform(post("/task/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tenantARequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotent").value(false))
                .andReturn();

        String tenantATaskId = extractTaskId(tenantAResult.getResponse().getContentAsString());

        String tenantBRequest = """
                {
                  "tenantId": "tenant-b-bizkey",
                  "taskType": "multi-tenant-test",
                  "bizKey": "shared-bizkey",
                  "requestId": "req-tenant-b",
                  "workerGroup": "test-group",
                  "tag": "default",
                  "payload": "{}",
                  "callbackUrl": "http://callback.test/notify"
                }
                """;

        MvcResult tenantBResult = mockMvc.perform(post("/task/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tenantBRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotent").value(false))
                .andReturn();

        String tenantBTaskId = extractTaskId(tenantBResult.getResponse().getContentAsString());
        assertThat(tenantATaskId).isNotEqualTo(tenantBTaskId);
    }

    @Test
    void shouldAllowDifferentTaskTypesWithSameBizKey() throws Exception {
        String typeARequest = """
                {
                  "tenantId": "tenant-type-test",
                  "taskType": "type-a",
                  "bizKey": "shared-bizkey-type",
                  "requestId": "req-type-a",
                  "workerGroup": "test-group",
                  "tag": "default",
                  "payload": "{}",
                  "callbackUrl": "http://callback.test/notify"
                }
                """;

        MvcResult typeAResult = mockMvc.perform(post("/task/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typeARequest))
                .andExpect(status().isOk())
                .andReturn();

        String typeATaskId = extractTaskId(typeAResult.getResponse().getContentAsString());

        String typeBRequest = """
                {
                  "tenantId": "tenant-type-test",
                  "taskType": "type-b",
                  "bizKey": "shared-bizkey-type",
                  "requestId": "req-type-b",
                  "workerGroup": "test-group",
                  "tag": "default",
                  "payload": "{}",
                  "callbackUrl": "http://callback.test/notify"
                }
                """;

        MvcResult typeBResult = mockMvc.perform(post("/task/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(typeBRequest))
                .andExpect(status().isOk())
                .andReturn();

        String typeBTaskId = extractTaskId(typeBResult.getResponse().getContentAsString());
        assertThat(typeATaskId).isNotEqualTo(typeBTaskId);
    }

    private String extractTaskId(String responseBody) {
        String marker = "\"taskId\":\"";
        int startIndex = responseBody.indexOf(marker);
        int endIndex = responseBody.indexOf('"', startIndex + marker.length());
        return responseBody.substring(startIndex + marker.length(), endIndex);
    }
}