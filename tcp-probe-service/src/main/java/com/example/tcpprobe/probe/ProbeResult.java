package com.example.tcpprobe.probe;

/**
 * 单个目标的探测结果，也是批量响应中 {@code results} 数组的元素。
 *
 * @param id 调用方提供的目标标识，可为空，用于将结果与业务目标关联
 * @param host 被探测的主机名或 IP 地址
 * @param port 被探测的 TCP 端口
 * @param reachable 是否已经成功建立 TCP 连接
 * @param status 稳定的结果分类，不依赖操作系统的异常文本
 * @param connectLatencyMs 实际连接阶段耗时；任务未执行时为 {@code null}
 * @param message 面向调用方的简要结果说明
 */
public record ProbeResult(
        String id,
        String host,
        int port,
        boolean reachable,
        ProbeStatus status,
        Long connectLatencyMs,
        String message
) {

    /**
     * 创建一个连接成功的探测结果。
     *
     * @param id 目标标识
     * @param host 目标主机
     * @param port 目标端口
     * @param latencyMs 连接建立耗时，单位为毫秒
     * @return 状态为 {@link ProbeStatus#SUCCESS} 的结果
     */
    public static ProbeResult success(String id, String host, int port, long latencyMs) {
        return new ProbeResult(id, host, port, true, ProbeStatus.SUCCESS, latencyMs, "TCP 连接成功");
    }

    /**
     * 创建一个未成功建立连接或未实际执行的探测结果。
     *
     * @param id 目标标识
     * @param host 目标主机
     * @param port 目标端口
     * @param status 失败、拒绝执行或内部错误状态
     * @param connectLatencyMs 实际连接耗时；任务未执行时为 {@code null}
     * @param message 面向调用方的结果说明
     * @return 不可达的探测结果
     */
    public static ProbeResult failure(
            String id,
            String host,
            int port,
            ProbeStatus status,
            Long connectLatencyMs,
            String message
    ) {
        return new ProbeResult(id, host, port, false, status, connectLatencyMs, message);
    }
}
