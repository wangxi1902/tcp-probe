package com.example.tcpprobe.web.dto;

import com.example.tcpprobe.probe.ProbeResult;

import java.util.List;

/**
 * 一次批量探测的汇总响应。
 *
 * @param total 请求中的目标总数
 * @param reachableCount 成功建立 TCP 连接的目标数
 * @param failedCount 已执行探测但连接失败的目标数
 * @param notExecutedCount 因限流或服务繁忙而未执行的目标数
 * @param internalErrorCount 出现非预期程序异常的目标数
 * @param elapsedMs 整个批次从提交到汇总完成的耗时，单位为毫秒
 * @param results 与请求目标顺序一致的逐目标结果
 */
public record BatchProbeResponse(
        int total,
        long reachableCount,
        long failedCount,
        long notExecutedCount,
        long internalErrorCount,
        long elapsedMs,
        List<ProbeResult> results
) {
}
