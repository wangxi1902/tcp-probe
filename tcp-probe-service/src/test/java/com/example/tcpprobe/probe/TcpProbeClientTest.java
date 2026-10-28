package com.example.tcpprobe.probe;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TcpProbeClientTest {

    private final TcpProbeClient probeClient = new TcpProbeClient();

    @Test
    void shouldReportSuccessWhenLocalServerAcceptsConnection() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            CompletableFuture<Void> acceptTask = CompletableFuture.runAsync(() -> acceptOnce(serverSocket));

            ProbeResult result = probeClient.probe(
                    "local-success",
                    "127.0.0.1",
                    serverSocket.getLocalPort(),
                    1000
            );

            assertThat(result.reachable()).isTrue();
            assertThat(result.status()).isEqualTo(ProbeStatus.SUCCESS);
            acceptTask.get(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void shouldReportConnectionRefusedWhenNoServerIsListening() throws Exception {
        int closedPort;
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            closedPort = serverSocket.getLocalPort();
        }

        ProbeResult result = probeClient.probe(
                "local-refused",
                "127.0.0.1",
                closedPort,
                1000
        );

        assertThat(result.reachable()).isFalse();
        assertThat(result.status()).isEqualTo(ProbeStatus.CONNECTION_REFUSED);
    }

    private void acceptOnce(ServerSocket serverSocket) {
        try (Socket ignored = serverSocket.accept()) {
            // 只需要完成一次 TCP accept，不需要交换应用层数据。
        } catch (IOException exception) {
            throw new IllegalStateException("测试服务器接收连接失败", exception);
        }
    }
}
