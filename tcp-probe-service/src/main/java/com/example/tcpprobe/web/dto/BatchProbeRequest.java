package com.example.tcpprobe.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量 TCP 探测请求。
 *
 * @param targets 需要探测的目标列表，不能为空且单批最多 50 个
 * @param timeoutMs 单目标连接超时，单位为毫秒；为空时使用服务端默认值
 */
public record BatchProbeRequest(
        @NotEmpty(message = "targets 不能为空")
        @Size(max = 50, message = "单次请求最多包含 50 个目标")
        List<@NotNull(message = "目标元素不能为 null") @Valid ProbeTargetRequest> targets,

        @Min(value = 100, message = "timeoutMs 不能小于 100")
        @Max(value = 10000, message = "timeoutMs 不能大于 10000")
        Integer timeoutMs
) {
}
