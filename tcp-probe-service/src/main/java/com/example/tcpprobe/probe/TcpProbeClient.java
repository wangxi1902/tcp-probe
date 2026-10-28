package com.example.tcpprobe.probe;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

/**
 * 使用 JDK 原生 Socket 执行一次 TCP 连接探测。
 *
 * 这里只判断 TCP 三次握手能否完成，不发送任何应用层数据。
 * 因而“连接成功”不等同于 HTTP、MySQL 等上层业务一定健康。
 */
@Component
public class TcpProbeClient {

    /**
     * 尝试与一个目标建立 TCP 连接，不发送任何应用层数据。
     *
     * @param id 调用方提供的目标标识，可为空
     * @param host 目标主机名或 IP 地址
     * @param port 目标 TCP 端口
     * @param timeoutMs 连接建立阶段的超时时间，单位为毫秒
     * @return 成功结果或由网络异常转换得到的稳定失败结果
     */
    public ProbeResult probe(String id, String host, int port, int timeoutMs) {
        long startNanos = System.nanoTime();

        // try-with-resources 保证成功、失败或抛异常时 Socket 都会被关闭。
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return ProbeResult.success(id, host, port, elapsedMillis(startNanos));
        } catch (SocketTimeoutException exception) {
            return failure(id, host, port, ProbeStatus.TIMEOUT, startNanos, "在规定时间内未建立 TCP 连接");
        } catch (UnknownHostException exception) {
            return failure(id, host, port, ProbeStatus.DNS_ERROR, startNanos, "域名无法解析");
        } catch (NoRouteToHostException exception) {
            return failure(id, host, port, ProbeStatus.NETWORK_UNREACHABLE, startNanos, "目标网络不可达");
        } catch (ConnectException exception) {
            return failure(id, host, port, ProbeStatus.CONNECTION_REFUSED, startNanos, "目标拒绝连接");
        } catch (IOException exception) {
            // 不把底层异常原文返回给接口调用方，避免泄露不必要的内部网络信息。
            return failure(id, host, port, ProbeStatus.IO_ERROR, startNanos, "建立 TCP 连接时发生 I/O 错误");
        }
    }

    /** 将网络异常分类转换为统一结果，并记录已经消耗的连接时间。 */
    private ProbeResult failure(
            String id,
            String host,
            int port,
            ProbeStatus status,
            long startNanos,
            String message
    ) {
        return ProbeResult.failure(id, host, port, status, elapsedMillis(startNanos), message);
    }

    /** 计算从指定单调时钟起点开始经过的毫秒数。 */
    private long elapsedMillis(long startNanos) {
        // nanoTime 只用于计算时间间隔，不受系统时间被人工调整的影响。
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
