# 简易 TCP 连接探测服务

基于 JDK 17 和 Spring Boot 实现的 TCP 连通性探测服务。调用方通过 HTTP 接口批量提交 `host:port`，服务使用 JDK 原生 `Socket` 并发建立 TCP 连接，并返回每个目标的独立结果。

已实现功能：

- 批量 TCP 连接探测，单批最多 50 个目标；
- 单目标连接超时；
- `CompletableFuture` 配合有界线程池并发执行；
- 进程内令牌桶限流；
- 常见网络错误分类；
- 请求参数校验和统一 HTTP 400 响应；
- 并发、限流、线程池饱和及 HTTP 接口自动化测试。

> TCP 连接成功只表示能够完成 TCP 连接建立，不代表 HTTP、数据库等应用层业务一定健康。

## 1. 环境要求

- JDK 17
- Maven 3.9+

确认 Maven 实际使用 JDK 17：

```powershell
mvn -v
```

输出中的 `Java version` 应为 17。

## 2. 构建与启动

运行全部测试：

```powershell
mvn clean test
```

开发模式启动：

```powershell
mvn spring-boot:run
```

默认监听 `8080` 端口。

也可以打包后启动：

```powershell
mvn clean package
java -jar target/tcp-probe-service-0.0.1-SNAPSHOT.jar
```

## 3. 接口说明

```text
POST /api/v1/tcp-probes
Content-Type: application/json
```

请求示例：

```json
{
  "targets": [
    {
      "id": "local-web",
      "host": "127.0.0.1",
      "port": 8080
    }
  ],
  "timeoutMs": 3000
}
```

参数约束：

| 参数 | 是否必填 | 约束 |
|---|---|---|
| `targets` | 是 | 不能为空，最多 50 个，元素不能为 `null` |
| `id` | 否 | 最长 64 个字符，响应中原样返回 |
| `host` | 是 | 不能为空，最长 253 个字符 |
| `port` | 是 | 1～65535 |
| `timeoutMs` | 否 | 100～10000ms，默认 3000ms |

## 4. 验证成功连接与目标失败

先在一个终端运行服务，再在第二个终端发送请求。

### Windows PowerShell

```powershell
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/tcp-probes' -ContentType 'application/json' -Body '{"targets":[{"id":"local-web","host":"127.0.0.1","port":8080},{"id":"invalid-domain","host":"not-exists.invalid","port":443}],"timeoutMs":3000}'
```

### Linux、macOS 或 Git Bash

```bash
curl -X POST http://localhost:8080/api/v1/tcp-probes \
  -H "Content-Type: application/json" \
  -d '{
    "targets": [
      {"id": "local-web", "host": "127.0.0.1", "port": 8080},
      {"id": "invalid-domain", "host": "not-exists.invalid", "port": 443}
    ],
    "timeoutMs": 3000
  }'
```

预期结果：

- `127.0.0.1:8080` 返回 `SUCCESS`；
- `not-exists.invalid:443` 返回 `DNS_ERROR`；
- 整个 HTTP 请求返回 200；
- `reachableCount` 为 1，`failedCount` 为 1。

响应结构示例，耗时数值以实际运行结果为准：

```json
{
  "total": 2,
  "reachableCount": 1,
  "failedCount": 1,
  "notExecutedCount": 0,
  "internalErrorCount": 0,
  "elapsedMs": 18,
  "results": [
    {
      "id": "local-web",
      "host": "127.0.0.1",
      "port": 8080,
      "reachable": true,
      "status": "SUCCESS",
      "connectLatencyMs": 2,
      "message": "TCP 连接成功"
    },
    {
      "id": "invalid-domain",
      "host": "not-exists.invalid",
      "port": 443,
      "reachable": false,
      "status": "DNS_ERROR",
      "connectLatencyMs": 8,
      "message": "域名无法解析"
    }
  ]
}
```

## 5. 验证请求参数错误

下面的请求包含空 host、非法端口和过小的超时时间：

### Windows PowerShell

Windows 10/11 自带 `curl.exe`。这里使用完整路径，避免 PowerShell 把 `curl` 解析为其他命令或当前 PATH 中找不到程序：

```powershell
'{"targets":[{"id":"bad-target","host":"","port":70000}],"timeoutMs":50}' | & 'C:\Windows\System32\curl.exe' -sS -i -X POST http://localhost:8080/api/v1/tcp-probes -H "Content-Type: application/json" --data-binary '@-'
```

### Linux、macOS 或 Git Bash

```bash
curl -sS -i -X POST http://localhost:8080/api/v1/tcp-probes \
  -H 'Content-Type: application/json' \
  --data-raw '{"targets":[{"id":"bad-target","host":"","port":70000}],"timeoutMs":50}'
```

参数 `-i` 会同时显示 HTTP 响应头和响应体。预期第一行包含 `HTTP/1.1 400`，响应结构类似：

```json
{
  "timestamp": "2026-10-28T06:00:00Z",
  "status": 400,
  "message": "请求参数校验失败",
  "details": [
    "targets[0].host: host 不能为空",
    "targets[0].port: port 不能大于 65535",
    "timeoutMs: timeoutMs 不能小于 100"
  ]
}
```

`details` 的排列顺序可能不同，不应依赖固定顺序。

## 6. 验证限流

先停止使用默认配置启动的服务，然后使用每秒 1 个令牌、桶容量 1 重新启动：

```powershell
mvn clean package
java -jar target/tcp-probe-service-0.0.1-SNAPSHOT.jar --probe.permits-per-second=1 --probe.burst-capacity=1
```

一次提交两个目标。

### Windows PowerShell

```powershell
'{"targets":[{"id":"allowed","host":"127.0.0.1","port":8080},{"id":"limited","host":"127.0.0.1","port":8080}],"timeoutMs":1000}' | & 'C:\Windows\System32\curl.exe' -sS -i -X POST http://localhost:8080/api/v1/tcp-probes -H "Content-Type: application/json" --data-binary '@-'
```

### Linux、macOS 或 Git Bash

```bash
curl -sS -i -X POST http://localhost:8080/api/v1/tcp-probes \
  -H 'Content-Type: application/json' \
  --data-raw '{"targets":[{"id":"allowed","host":"127.0.0.1","port":8080},{"id":"limited","host":"127.0.0.1","port":8080}],"timeoutMs":1000}'
```

预期结果：

- 第一个目标消耗唯一令牌并执行探测；
- 第二个目标返回 `RATE_LIMITED`；
- `notExecutedCount` 为 1；
- 第二个目标的 `connectLatencyMs` 为 `null`，因为它没有进入 Socket 探测阶段。

限流结果示例：

```json
{
  "id": "limited",
  "host": "127.0.0.1",
  "port": 8080,
  "reachable": false,
  "status": "RATE_LIMITED",
  "connectLatencyMs": null,
  "message": "探测速率超过限制，请稍后重试"
}
```

## 7. 自动化测试

运行完整测试：

```powershell
mvn clean test
```

预期结果：

```text
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

测试覆盖：

| 测试类 | 覆盖内容 |
|---|---|
| `TcpProbeClientTest` | 本地连接成功、连接拒绝 |
| `TokenBucketRateLimiterTest` | 令牌耗尽后立即拒绝 |
| `TcpProbeServiceTest` | 分类统计、顺序保持、并发执行、线程池饱和、内部异常兜底 |
| `BatchProbeRequestValidationTest` | 目标元素不能为 `null` |
| `TcpProbeControllerTest` | HTTP 200、字段校验 400、空目标元素 400 |

测试使用本机临时 `ServerSocket`，不依赖公网环境。

### 7.1 单独验证并发执行

```powershell
mvn "-Dtest=TcpProbeServiceTest#shouldExecuteAllowedTargetsConcurrently" test
```

该测试让两个探测任务互相等待对方进入执行方法。只有两个任务确实并发执行，测试才能通过；如果实现退化为串行，第一个任务会等待超时并导致测试失败。

### 7.2 单独验证线程池饱和

```powershell
mvn "-Dtest=TcpProbeServiceTest#shouldReturnServerBusyWhenExecutorQueueIsFull" test
```

该测试占用唯一工作线程和唯一队列位置，再由 Service 提交新任务，验证新任务被转换为 `SERVER_BUSY`。

### 7.3 单独验证连接拒绝

```powershell
mvn "-Dtest=TcpProbeClientTest#shouldReportConnectionRefusedWhenNoServerIsListening" test
```

该测试使用本机临时端口验证 `CONNECTION_REFUSED`，不依赖外部服务。

### 7.4 测试中的模拟异常日志

`TcpProbeServiceTest` 会故意让 Mock 客户端抛出 `IllegalStateException: 模拟内部错误`，验证单目标内部异常会转换为 `INTERNAL_ERROR`，且不会导致整个批次失败。

因此测试期间出现包含“模拟内部错误”的 `ERROR` 日志属于预期行为。应以最后的 `Failures`、`Errors` 和 `BUILD SUCCESS/FAILURE` 为准。

如果 Windows 中文路径导致 Surefire 无法创建测试子进程，可以临时使用：

```powershell
mvn -DforkCount=0 test
```

## 8. 配置

默认配置位于 `src/main/resources/application.yml`：

```yaml
server:
  port: 8080

probe:
  default-timeout-ms: 3000
  worker-threads: 20
  queue-capacity: 200
  permits-per-second: 50
  burst-capacity: 50
```

| 配置项 | 说明 |
|---|---|
| `default-timeout-ms` | 单目标默认连接超时 |
| `worker-threads` | 同时执行的 Socket 连接数量 |
| `queue-capacity` | 等待执行的任务上限 |
| `permits-per-second` | 每秒补充的令牌数量 |
| `burst-capacity` | 允许积累的最大令牌数量 |

## 9. 结果字段

批次字段：

| 字段 | 含义 |
|---|---|
| `total` | 请求目标总数 |
| `reachableCount` | TCP 连接成功数量 |
| `failedCount` | 已执行但连接失败的数量 |
| `notExecutedCount` | 因限流或线程池繁忙而未执行的数量 |
| `internalErrorCount` | 出现非预期程序异常的数量 |
| `elapsedMs` | 整个批次耗时 |
| `results` | 与请求顺序一致的逐目标结果 |

单目标状态：

| 状态 | 含义 |
|---|---|
| `SUCCESS` | TCP 连接成功 |
| `TIMEOUT` | 连接超时 |
| `CONNECTION_REFUSED` | 目标端口拒绝连接 |
| `DNS_ERROR` | 域名无法解析 |
| `NETWORK_UNREACHABLE` | 目标网络不可达 |
| `RATE_LIMITED` | 因限流而未执行 |
| `SERVER_BUSY` | 因线程池饱和而未执行 |
| `IO_ERROR` | 其他 Socket I/O 错误 |
| `INTERNAL_ERROR` | 非预期程序异常 |

## 10. 推荐验收顺序

```powershell
mvn -v
mvn clean test
mvn clean package
java -jar target/tcp-probe-service-0.0.1-SNAPSHOT.jar
```

服务启动后，依次执行本文档中的：

1. 成功连接与 DNS 失败请求；
2. 参数校验失败请求；
3. 限流验证；
4. 并发和线程池饱和单项测试。
