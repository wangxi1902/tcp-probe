package com.example.tcpprobe.web;

import com.example.tcpprobe.service.TcpProbeService;
import com.example.tcpprobe.web.dto.BatchProbeRequest;
import com.example.tcpprobe.web.dto.BatchProbeResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TCP 探测的 HTTP 入口，只负责协议适配和参数校验。
 */
@RestController
@RequestMapping("/api/v1/tcp-probes")
public class TcpProbeController {

    /** 批量探测的业务编排服务。 */
    private final TcpProbeService probeService;

    /**
     * 创建 TCP 探测控制器。
     *
     * @param probeService 批量探测的业务编排服务
     */
    public TcpProbeController(TcpProbeService probeService) {
        this.probeService = probeService;
    }

    /**
     * 接收一个批量探测请求并同步等待批次结果。
     *
     * @param request 已通过 Bean Validation 校验的请求体
     * @return HTTP 200 及批量探测结果；参数错误由全局异常处理器返回 HTTP 400
     */
    @PostMapping
    public ResponseEntity<BatchProbeResponse> probe(@Valid @RequestBody BatchProbeRequest request) {
        // Controller 只处理 HTTP 输入输出，具体探测与并发编排交给 Service。
        return ResponseEntity.ok(probeService.probeBatch(request));
    }
}
