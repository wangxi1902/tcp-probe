package com.example.tcpprobe.web;

import com.example.tcpprobe.probe.ProbeResult;
import com.example.tcpprobe.service.TcpProbeService;
import com.example.tcpprobe.web.dto.BatchProbeRequest;
import com.example.tcpprobe.web.dto.BatchProbeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TcpProbeController.class)
class TcpProbeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TcpProbeService probeService;

    @Test
    void shouldReturnBatchResultForValidRequest() throws Exception {
        ProbeResult probeResult = ProbeResult.success("web", "127.0.0.1", 8080, 2);
        BatchProbeResponse response = new BatchProbeResponse(
                1,
                1,
                0,
                0,
                0,
                3,
                List.of(probeResult)
        );
        when(probeService.probeBatch(any(BatchProbeRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/tcp-probes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targets": [
                                    {"id": "web", "host": "127.0.0.1", "port": 8080}
                                  ],
                                  "timeoutMs": 1000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.reachableCount").value(1))
                .andExpect(jsonPath("$.results[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.results[0].connectLatencyMs").value(2));
    }

    @Test
    void shouldReturnBadRequestForInvalidTarget() throws Exception {
        mockMvc.perform(post("/api/v1/tcp-probes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targets": [
                                    {"id": "bad", "host": "", "port": 70000}
                                  ],
                                  "timeoutMs": 50
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请求参数校验失败"))
                .andExpect(jsonPath("$.details.length()").value(3));
    }

    @Test
    void shouldReturnBadRequestForNullTargetElement() throws Exception {
        mockMvc.perform(post("/api/v1/tcp-probes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targets": [null],
                                  "timeoutMs": 1000
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("targets[0]: 目标元素不能为 null"));
    }
}
