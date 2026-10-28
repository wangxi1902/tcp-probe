package com.example.tcpprobe;

import com.example.tcpprobe.config.ProbeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * TCP 连接探测服务的 Spring Boot 启动入口。
 */
@SpringBootApplication
@EnableConfigurationProperties(ProbeProperties.class)
public class TcpProbeApplication {

    /**
     * 启动 Spring 容器和内嵌 Web 服务器。
     *
     * @param args 命令行启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(TcpProbeApplication.class, args);
    }
}
