# 中间件与微服务规范


---

## 1. 技术栈总览

| 类别 | 选型 | 版本要求 | 说明 |
|------|------|----------|------|
| 语言 | Java | 17（根据实际情况选择即可） | 与父 POM 保持一致 |
| 框架 | Spring Boot | 3.3.x | 当前父 POM 版本 |
| 微服务 | Spring Cloud | 2023.0.x | 与 Boot 3.3 配套 |
| 阿里云组件 | Spring Cloud Alibaba | 2023.0.x | Nacos 等 |
| 注册/配置中心 | Nacos | 2.3.x | 服务注册发现 + 动态配置 |
| 服务调用 | OpenFeign | 随 Spring Cloud | 声明式 HTTP 客户端 |
| API 网关 | Spring Cloud Gateway | 随 Spring Cloud | 统一入口、鉴权、路由 |


**版本管理原则：** 在根 `pom.xml` 的 `<properties>` 中集中声明 `spring-cloud.version`、`spring-cloud-alibaba.version`，子模块禁止各自指定冲突版本。

---

## 2. Nacos 规范

### 2.1 职责划分

- **服务注册与发现：** 所有可独立部署的业务服务必须注册到 Nacos。
- **配置中心：** 环境相关配置、敏感信息、可动态刷新的业务参数放入 Nacos Config；本地 `application.yaml` 仅保留框架级默认配置与 Nacos 连接信息。

### 2.2 环境隔离

使用 Nacos **Namespace** 区分环境，命名约定：

| 环境 | Namespace ID | 说明 |
|------|--------------|------|
| 开发 | `dev` | 本地/联调 |
| 测试 | `test` | 测试环境 |
| 生产 | `prod` | 生产环境 |

### 2.3 配置文件约定

**Data ID 格式：** `{spring.application.name}-{profile}.yaml`

示例：`qa-report-service-dev.yaml`

**Group：** 统一使用 `DEFAULT_GROUP`，除非有明确的跨 Group 共享配置需求。

**本地 bootstrap / application 最小配置示例：**

```yaml
spring:
  application:
    name: qa-report-service
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:dev}
      config:
        server-addr: ${NACOS_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:dev}
        file-extension: yaml
        refresh-enabled: true
```

### 2.4 配置内容规范

- 敏感信息（数据库密码、密钥）不得提交到 Git；通过 Nacos 加密配置或环境变量注入。
- 需动态刷新的配置，对应 Bean 使用 `@RefreshScope`。
- 公共配置可抽取为 `qa-common.yaml`，各服务通过 `spring.config.import` 或 Nacos 共享配置引用。

### 2.5 服务注册

- 每个微服务引入 `spring-cloud-starter-alibaba-nacos-discovery`。
- 启动类添加 `@EnableDiscoveryClient`（Spring Cloud 2023 中部分场景可省略，但建议显式声明以保持可读性）。
- 健康检查依赖 Spring Boot Actuator，暴露 `health` 端点供 Nacos 感知实例状态。

---

## 3. OpenFeign 服务间调用规范

### 3.1 使用原则

- **模块间、服务间** HTTP 调用统一使用 **OpenFeign**，禁止在业务代码中直接使用 `RestTemplate` / `HttpClient` 调用内部服务（第三方外部 API 除外）。
- Feign 接口定义在**调用方**模块的 `client` 包下；若接口被多个服务复用，可下沉至 `qa-common` 的 `client` 包。
- 被调用方 Controller 的 URL 与 Feign 接口必须一一对应，路径风格保持一致。

### 3.2 依赖与启用

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
```

启动类：

```java
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.myenglish")
public class ReportApplication { }
```

### 3.3 Feign Client 定义规范

```java
package com.myenglish.report.client;

import com.myenglish.qacommon.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 调用用户服务
 */
@FeignClient(
    name = "qa-user-service",           // 必须与目标服务 spring.application.name 一致
    path = "/internal/users",           // 统一前缀，区分内部接口
    contextId = "userClient"            // 同一服务多个 Client 时必须指定
)
public interface UserClient {

    @GetMapping("/{id}")
    ApiResponse<UserDto> getById(@PathVariable("id") Long id);
}
```

**强制约定：**

| 项 | 规范 |
|----|------|
| `name` | 等于 Nacos 注册的服务名 |
| `contextId` | 同一 `name` 存在多个 Feign 接口时必填，避免 Bean 冲突 |
| 返回值 | 统一使用 `ApiResponse<T>` 包装，与 `qa-common` 保持一致 |
| 内部接口路径 | 使用 `/internal/` 前缀，与对外 REST API 区分 |
| 参数 | 简单类型、`@RequestBody`、`@PathVariable` 命名与 Controller 一致 |

### 3.4 超时与重试

在 Nacos 或本地配置中统一声明，**禁止**在单个 Feign 接口上散落硬编码超时：

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 3000
            read-timeout: 10000
          qa-user-service:           # 针对特定服务覆盖
            read-timeout: 5000
```

- 默认**不开启** Feign 重试；需重试时必须评估幂等性，并在规范中注明。
- 超时、熔断、降级配合 Sentinel 使用（见第 6 节）。

### 3.5 请求头传递

- 用户身份 Token、TraceId 等通过 **`RequestInterceptor`** 统一透传，禁止在每个 Feign 方法手动添加。
- 内部服务间调用可使用内部凭证（如 `X-Internal-Token`），在 Gateway 或拦截器中校验。

```java
@Component
public class FeignRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        // 透传 TraceId、Authorization 等
    }
}
```

### 3.6 错误处理

- Feign 调用返回的 `ApiResponse`，业务层必须检查 `code` 是否为 `ApiCode.SUCCESS`（200）。
- HTTP 4xx/5xx 或网络异常由全局 `ErrorDecoder` 或 `FallbackFactory` 统一处理，转换为业务可理解的异常或默认降级结果。

---


## 4. 数据库与缓存（参考约定）

> 具体 ORM、连接池选型可在业务模块落地时细化，以下为中间件层面的统一约定。

| 中间件 | 选型 | 规范 |
|--------|------|------|
| 关系型数据库 | MySQL 8.x |mysql-connector-j（BOM 管理） |
| MyBatis-Plus | 3.5.7|
| 缓存 | Redis 7.x | spring-boot-starter-data-redis（BOM 管理） |
| 连接池 | HikariCP | 随 Spring Boot 默认，参数在 Nacos 配置 |

---


## 附录：父 POM 依赖管理示例

在根 `pom.xml` 中统一管理版本（实施时可取消注释并调整）：

```xml
<properties>
    <java.version>17</java.version>
    <spring-cloud.version>2023.0.3</spring-cloud.version>
    <spring-cloud-alibaba.version>2023.0.1.2</spring-cloud-alibaba.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-alibaba-dependencies</artifactId>
            <version>${spring-cloud-alibaba.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```


