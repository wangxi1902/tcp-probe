package com.example.tcpprobe.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 单个 TCP 探测目标。
 *
 * @param id 可选的业务标识，响应会原样返回
 * @param host 目标主机名或 IP 地址
 * @param port 目标 TCP 端口，范围为 1～65535
 */
public record ProbeTargetRequest(
        @Size(max = 64, message = "目标 id 最长为 64 个字符")
        String id,

        @NotBlank(message = "host 不能为空")
        @Size(max = 253, message = "host 最长为 253 个字符")
        String host,

        @Min(value = 1, message = "port 不能小于 1")
        @Max(value = 65535, message = "port 不能大于 65535")
        int port
) {
}
