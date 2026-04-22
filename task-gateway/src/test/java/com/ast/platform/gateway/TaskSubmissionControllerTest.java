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
class TaskSubmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldSubmitTaskAndReturnQueuedStatus() throws Exception {
        mockMvc.perform(post("/task/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-a",
                                  "taskType": "image-render",
                                  "bizKey": "biz-001",
                                  "requestId": "req-001",
                                  "workerGroup": "render-group",
                                  "tag": "default",
                                  "payload": "{\\"scene\\":1}",
                                  "callbackUrl": "http://callback.test/notify"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.taskId").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.idempotent").value(false))
                .andExpect(jsonPath("$.data.outboxStatus").value("SENT"));
    }

    @Test
    void shouldCancelTaskSuccessfully() throws Exception {
        MvcResult submitResult = mockMvc.perform(post("/task/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-cancel",
                                  "taskType": "cancel-test",
                                  "bizKey": "biz-cancel",
                                  "requestId": "req-cancel",
                                  "workerGroup": "cancel-group",
                                  "tag": "default",
                                  "payload": "{}",
                                  "callbackUrl": "http://callback.test/notify"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String taskId = extractTaskId(submitResult.getResponse().getContentAsString());

        mockMvc.perform(post("/task/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                  "tenantId": "tenant-cancel",
                                  "taskId": "%s",
                                  "reason": "user requested cancellation"
                                }
                                """, taskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldReturnExistingTaskWhenRequestIdRepeated() throws Exception {
        String requestBody = """
                {
                  "tenantId": "tenant-b",
                  "taskType": "video-encode",
                  "bizKey": "biz-100",
                  "requestId": "req-100",
                  "workerGroup": "video-group",
                  "tag": "hd",
                  "payload": "{\\"profile\\":\\"1080p\\"}",
                  "callbackUrl": "http://callback.test/notify"
                }
                """;

        MvcResult firstResult = mockMvc.perform(post("/task/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult secondResult = mockMvc.perform(post("/task/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotent").value(true))
                .andReturn();

        assertThat(secondResult.getResponse().getContentAsString())
                .contains(extractTaskId(firstResult.getResponse().getContentAsString()));
    }

    @Test
    void shouldReturnExistingTaskWhenBizKeyRepeated() throws Exception {
        mockMvc.perform(post("/task/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-c",
                                  "taskType": "doc-parse",
                                  "bizKey": "biz-200",
                                  "requestId": "req-200",
                                  "workerGroup": "doc-group",
                                  "tag": "standard",
                                  "payload": "{\\"docId\\":123}",
                                  "callbackUrl": "http://callback.test/notify"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/task/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-c",
                                  "taskType": "doc-parse",
                                  "bizKey": "biz-200",
                                  "requestId": "req-201",
                                  "workerGroup": "doc-group",
                                  "tag": "standard",
                                  "payload": "{\\"docId\\":123}",
                                  "callbackUrl": "http://callback.test/notify"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotent").value(true))
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    private String extractTaskId(String responseBody) {
        String marker = "\"taskId\":\"";
        int startIndex = responseBody.indexOf(marker);
        int endIndex = responseBody.indexOf('"', startIndex + marker.length());
        return responseBody.substring(startIndex + marker.length(), endIndex);
    }
}
