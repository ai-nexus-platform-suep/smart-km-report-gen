# 报告生成组模块说明

## Nacos 配置

报告生成组目前包含多个 Spring Boot 服务，每个服务都需要在 Nacos 中创建独立配置。

公共示例文件：

```text
report-generation/nacos-config-example.properties
```

注意：该文件是配置模板集合，不要整份复制到同一个 Nacos Data ID 中。请按文件中的分块分别创建：

| Data ID | 端口 | 模块 | 用途 |
| --- | --- | --- | --- |
| `report-outline-docx` | `8080` | `report-outline-docx` | 大纲生成、确认保存、DOCX 导出、文件下载 |
| `report-content-flow` | `8081` | `report-content-flow` | 章节生成、SSE、章节内容保存、历史记录 |

两个 Data ID 均使用：

```text
Group: DEFAULT_GROUP
Format: Properties
```

本地启动前至少需要确认每个服务对应的 Nacos 配置中包含：

```properties
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/power_report?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
spring.datasource.username=your-mysql-username
spring.datasource.password=your-mysql-password

spring.data.redis.host=127.0.0.1
spring.data.redis.port=6379
```

如果缺少 `spring.datasource.url`，服务会启动失败并提示：

```text
Failed to configure a DataSource: 'url' attribute is not specified
```

## 启动命令

在仓库根目录执行：

```powershell
mvn -pl report-generation/report-outline-docx -am spring-boot:run
```

```powershell
mvn -pl report-generation/report-content-flow -am spring-boot:run
```

两个服务都依赖同一个数据库脚本：

```sql
SOURCE ./report-generation/database/schema_mysql.sql;
```

