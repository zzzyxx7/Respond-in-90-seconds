# 给 AI 端同学的后端联调说明（doc-fusion）

这份文档说明如何在本机启动 `doc-fusion`（Spring Boot 后端）并打通与 AI 端（`Respond-in-90-seconds`，FastAPI）联调。

> 约定端口：  
> - doc-fusion: `http://127.0.0.1:8081`（`application-dev.yml` 中 `server.port`）  
> - AI 端: `http://127.0.0.1:8000`

---

## 你需要准备什么

- **JDK 21**
- **Maven 3.9+**
- **Docker Desktop（推荐）**：用于一键启动 MySQL/Redis/RabbitMQ（如果你不想装 Docker，也可以本机装这三个依赖，见下文）。

---

## 方式 A（推荐）：用 Docker 一键起依赖 + 启动后端

### 1) 启动 MySQL/Redis/RabbitMQ

在 `doc-fusion` 项目根目录执行：

```bash
docker compose -f docker-compose.dev.yml up -d
```

启动后，默认会得到：

- **MySQL**：`127.0.0.1:3306`，用户 `root`，密码 `1234`，库 `mydatabase`
- **Redis**：`127.0.0.1:6379`
- **RabbitMQ**：`127.0.0.1:5672`（管理台 `http://127.0.0.1:15672`，账号/密码 `guest/guest`）

### 2) 初始化数据库表（两种方式二选一）

#### 方式 2.1：自动导入（推荐）

如果仓库里存在 `db/mydatabase_dump.sql`，`docker-compose.dev.yml` 会在 **MySQL 数据卷为空的首次启动** 自动导入它。

> 注意：如果你之前启动过 MySQL 容器（数据卷已经有数据），MySQL 初始化脚本不会再次执行。  
> 需要重置请执行：
>
> ```bash
> docker compose -f docker-compose.dev.yml down -v
> docker compose -f docker-compose.dev.yml up -d
> ```

#### 方式 2.2：手动建表

用你熟悉的 MySQL 客户端执行 `src/main/resources/schema.sql`。

> 若数据库已存在但缺少新字段（例如用户头像 `avatar_url`），请按项目 README/变更说明补充 `ALTER TABLE`。

### 3) 启动 doc-fusion

默认激活 **`dev` profile**。`application-dev.yml` 中 **`doc.rabbitmq` 已与 `docker-compose.dev.yml` 对齐为 `guest/guest`**，用 Docker 起的 RabbitMQ 可直接联调：

```bash
mvn -DskipTests spring-boot:run
```

（IntelliJ：Active profiles 填 `dev` 即可。）

若你本机 MQ 账号不是 guest，可二选一：**加 profile `docker`**（见 `application-docker.yml`）或 **环境变量** `DOC_RABBITMQ_USERNAME` / `DOC_RABBITMQ_PASSWORD` 覆盖 `doc.rabbitmq`。

启动成功后：

- OpenAPI：`http://127.0.0.1:8081/v3/api-docs`
- Swagger UI：`http://127.0.0.1:8081/swagger-ui/index.html`

---

## 方式 B：不使用 Docker（手动安装依赖）

你需要在本机安装并启动：

- MySQL 8.x（创建库 `mydatabase`，并执行 `schema.sql`）
- Redis
- RabbitMQ

并确保配置与项目默认值一致（见下方“配置说明”）。

---

## 启动 AI 端（Respond-in-90-seconds）

在 `Respond-in-90-seconds` 目录启动：

```bash
uvicorn api_server:app --host 0.0.0.0 --port 8000
```

Swagger：`http://127.0.0.1:8000/docs`

> doc-fusion 会调用 AI 端的 `/api/tasks/create`，并轮询 `/api/tasks/{task_id}`，下载 `/api/tasks/{task_id}/download/{kind}`。

---

## 配置说明：我本地的配置你能直接跑吗？

**可以**，前提是你的 MySQL/Redis/RabbitMQ 也在本机 `localhost`，端口/账号与默认值一致。

项目 `src/main/resources/application.yml` 里对依赖的默认值是：

- MySQL：
  - host: `localhost`
  - port: `3306`
  - database: `mydatabase`
  - username: `root`
  - password: `1234`
- Redis：`localhost:6379`
- RabbitMQ：`localhost:5672`（默认 `guest/guest`）
- AI 端：`ai.base-url=http://localhost:8000`

### 你需要“重新配置 Redis/MQ 吗？”

- **如果你使用本文提供的 `docker-compose.dev.yml`**：MySQL/Redis 端口与根配置一致即可；RabbitMQ **`dev` 已默认 guest**，直接 `mvn -DskipTests spring-boot:run` 即可。若你改过 MQ 用户，再用 **`dev,docker`** 或环境变量覆盖。
- **如果你本机端口/账号不同**：用环境变量覆盖：

可覆盖项（举例）：

```bash
set doc.datasource.host=127.0.0.1
set doc.datasource.port=3306
set doc.datasource.database=mydatabase
set doc.datasource.username=root
set doc.datasource.password=1234
set doc.redis.host=127.0.0.1
set doc.redis.port=6379
set doc.rabbitmq.host=127.0.0.1
set doc.rabbitmq.port=5672
set doc.rabbitmq.username=guest
set doc.rabbitmq.password=guest
```

（Windows PowerShell 用 `$env:doc.datasource.host="127.0.0.1"` 这种写法也可以）

---

## 联调验证（最短链路）

1. 启动 AI 端（8000）
2. 启动 doc-fusion（8081）
3. 用 Apifox/Swagger 跑：
   - 登录：`POST /api/auth/login`（默认会自动创建 `admin/admin123`）
   - 上传模板：`POST /api/templates/upload`（form-data，key=`file`）
   - 上传文档集：`POST /api/documents/upload`（form-data，key=`files`，可多文件）
   - 提交任务：`POST /api/fill/submit`（JSON，用 publicId）
   - 查任务：`GET /api/fill/tasks/public/{taskPublicId}`
   - 下载结果：`GET /api/fill/download/public/{taskPublicId}`

---

## 常见问题

### 1) RabbitMQ 连不上 / 认证失败（PLAIN login refused）

1. 确认 5672 可达，容器在跑：`docker ps`。
2. **Docker MQ 与 `application-dev.yml` 默认均为 `guest/guest`**；若仍认证失败，检查是否改过 `doc.rabbitmq` 或连到非本机 MQ（guest 限制见下条）。
3. **不要用「另一台机器的 IP」连 guest**：RabbitMQ 默认禁止 **guest 从非本机地址** 登录。应用与 MQ 须同机用 `127.0.0.1`/`localhost`，或新建非 guest 用户并授权 `/`。
4. **单机 Docker 一般与 Erlang cookie 无关**；若仍失败，把完整异常栈（含 `ACCESS_REFUSED` / `AuthenticationFailureException`）发给后端同学。
5. 启动失败且提示 **Bean / `AmqpTemplate`**：多数是 **RabbitMQ 未连上导致上下文未就绪**，先按本条把 MQ 调通。

> 当前版本填表任务依赖 **RabbitMQ 队列**（`AmqpTemplate` + 消费者），**没有 MQ 时应用无法正常跑通异步填表**；需先保证 MQ 可用，或后续再由项目组提供「无 MQ」开发模式（未实现前请以 Docker 起 MQ 为准）。

### 2) MySQL 表不存在 / 字段缺失

确保执行了 `src/main/resources/schema.sql`；若是增量开发中新增字段，请执行对应 `ALTER TABLE`。

