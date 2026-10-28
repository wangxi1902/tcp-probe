package com.example.tcpprobe.probe;

/**
 * 对探测结果进行稳定分类，避免调用方依赖不同操作系统上的异常文本。
 */
public enum ProbeStatus {
    /** TCP 连接在超时时间内成功建立。 */
    SUCCESS,

    /** 在指定连接超时时间内没有建立 TCP 连接。 */
    TIMEOUT,

    /** 目标主机可达，但目标端口拒绝了连接。 */
    CONNECTION_REFUSED,

    /** 目标域名无法解析为 IP 地址。 */
    DNS_ERROR,

    /** 本机到目标地址之间不存在可用的网络路由。 */
    NETWORK_UNREACHABLE,

    /** 探测任务因超过令牌桶速率限制而没有执行。 */
    RATE_LIMITED,

    /** 探测任务因工作线程和等待队列均已占满而没有执行。 */
    SERVER_BUSY,

    /** Socket 连接过程中出现了未进一步分类的 I/O 错误。 */
    IO_ERROR,

    /** 执行探测时出现了非预期的程序异常。 */
    INTERNAL_ERROR
}
