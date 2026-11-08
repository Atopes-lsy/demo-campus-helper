# 校园帮帮 — Docker 部署指南

> **版本**: 2.0 | **更新日期**: 2026-09-30

> **v2.0 相对 v1.0 的主要变更**
>
> - 编排新增 **Kafka** 与 **Elasticsearch** 两个服务（后端重建为事件驱动 + 全文检索）
> - **生产编排不再对宿主机暴露后端、MySQL、Redis、Kafka、ES 的端口**，只暴露前端 `8089`；
>   需要直连内部服务时改用 `docker-compose.dev.yml` 覆盖
> - 数据库结构改由 **Flyway** 在应用启动时迁移，`sql/init.sql` 与 `sql/migration_add_packages.sql` 均已删除
> - 部分环境变量改为**必填**（缺失会直接启动失败），不再提供会提交进仓库的默认密钥
> - 文中不再出现具体的密钥示例值，改为占位符 + 生成命令

---

## 环境要求

- Docker Desktop 最新版
- **端口 `8089` 未被占用**（生产编排只占用这一个；开发覆盖另需 `3306`、`6379`、`9200`、`9092`、`8088`）
- 内存建议给足：v1.0 写的 4GB 是在只有 MySQL + Redis 时的估算，
  现在还要跑 Kafka 与 Elasticsearch（ES 堆已限制为 256MB，见 `docker-compose.yml` 的 `ES_JAVA_OPTS`）

---

## 快速启动

```bash
cd campus-helper

# 1. 从模板创建 .env，并填写必填项（见下方「环境变量」）
cp .env.example .env

# 2. 一键启动所有服务（会先构建前后端镜像）
docker compose up -d

# 3. 等待各服务 healthy
docker compose ps

# 4. 打开浏览器
# http://localhost:8089
```

首次启动需要拉取 MySQL、Redis、Kafka、Elasticsearch 四个镜像并构建前后端两个镜像，视网络情况约 5-10 分钟。

---

## 服务说明

| 容器 | 镜像 | 宿主端口 | 说明 |
|------|------|----------|------|
| campus-mysql | mysql:8.0 | 无（仅容器网络） | 数据库，结构由 Flyway 在应用启动时迁移 |
| campus-redis | redis:7-alpine | 无（仅容器网络） | 分布式锁、缓存、消费端幂等、token 版本号 |
| campus-elasticsearch | elasticsearch:8.13.4 | 无（仅容器网络） | 全文检索、地理查询、BI 看板聚合 |
| campus-kafka | cp-kafka:8.3.0（KRaft 模式，无需 Zookeeper） | 无（仅容器网络） | 订单事件分发，单 topic 3 分区 |
| campus-backend | 本地构建 | 无（仅容器网络） | Spring Boot 3.3，由前端 Nginx 反代 |
| campus-frontend | 本地构建 | **8089 → 80** | Nginx + React 静态资源 |

后端通过 Nginx 的 `/api` 与 `/ws` 反代对外提供服务，自身不监听宿主机端口。
`campus-backend` 的启动依赖 MySQL / Redis / Kafka / Elasticsearch 的**健康检查通过**（`condition: service_healthy`），
不会出现「进程起来了但数据库还没就绪」的 502。

> ⚠️ 后端容器自身**没有** healthcheck，`/actuator/health` 也未引入——这是已知限制。

---

## 编排文件

| 文件 | 用途 |
|---|---|
| `docker-compose.yml` | 生产编排：6 个服务，只对外暴露前端 8089 |
| `docker-compose.dev.yml` | 开发覆盖：把 MySQL(3306)、Redis(6379)、ES(9200)、Kafka(9092)、后端(8088→8080) 开放到宿主机 |
| `docker-compose.debug.yml` | 仅后端调试：追加 JDWP `5005`，供 IDE 远程 attach |

---

## 环境变量

**必填**（缺失会直接启动失败，这是有意为之——避免把密钥默认值提交进仓库）

| 变量 | 说明 |
|---|---|
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 |
| `MYSQL_DATABASE` | 数据库名（默认 `campus_helper`） |
| `DB_USERNAME` / `DB_PASSWORD` | 后端连接数据库使用的账号 |
| `JWT_SECRET` | **至少 32 字节**。生成：`openssl rand -hex 32` |
| `PAYMENT_CALLBACK_SECRET` | 支付回调 HMAC 密钥，至少 16 位 |
| `DEEPSEEK_API_KEY` | 管理员 AI 助手。未配置时 AI 功能不可用，不影响订单核心流程 |

**可选**

| 变量 | 默认 | 说明 |
|---|---|---|
| `ADMIN_USERNAME` | `admin` | 管理员账号 |
| `ADMIN_PASSWORD` | `admin123` | 明文兜底，仅在未配 `ADMIN_PASSWORD_HASH` 时生效（启动会打 WARN） |
| `ADMIN_PASSWORD_HASH` | 空 | 配了它则优先走 BCrypt。生成：`htpasswd -bnBC 10 "" '你的密码' \| tr -d ':\n'` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | 本地直跑后端时才需要改；容器内走 `kafka:29092` |
| `SPRING_ELASTICSEARCH_URIS` | `http://localhost:9200` | 同上 |
| `DISPATCH_AUTO_ENABLED` | `false` | `true` = 下单后由系统按多因子评分自动派单；`false` = 订单进池子等跑腿员抢。两者并存，派单未命中时订单仍留在池中 |

> **不要直接使用 `.env.example` 里的示例值上线**。该文件里的 `MYSQL_ROOT_PASSWORD` 等是便于本地起步的固定值，
> `JWT_SECRET` 与 `PAYMENT_CALLBACK_SECRET` 则刻意留空，必须自己生成。
>
> `.env.example` 里的 `WECHAT_*` / `ALIPAY_*` 目前**代码零引用**——项目未接入真实支付渠道，仅保留为占位。

---

## 容器管理

```bash
# 查看运行状态
docker compose ps

# 查看日志（排查启动问题时最常用）
docker compose logs -f backend
docker compose logs -f elasticsearch
docker compose logs -f kafka

# 重启单个服务
docker compose restart backend

# 改完代码后重新构建
docker compose up -d --build

# 停止所有服务
docker compose down

# 停止并删除数据卷（⚠️ 会清空数据库与 Kafka/ES 数据，下次启动重新迁移）
docker compose down -v
```

---

## 数据库管理

**结构由 Flyway 管理，不要手工改表**。应用启动时会自动执行 `backend/src/main/resources/db/migration/` 下的迁移：

| 脚本 | 内容 |
|---|---|
| `V1__baseline.sql` | 11 张业务表 + 种子数据 |
| `V2__event_outbox.sql` | `event_outbox` 与 `processed_event` |
| `V3__performance_indexes.sql` | 复合索引调整（含删除冗余单列索引） |

新增变更请**追加新版本脚本**（`V4__xxx.sql`），不要修改已执行过的脚本——Flyway 开启了 `validate-on-migrate`，
校验失败会拒绝启动。

```bash
# 连接 MySQL（密码为 .env 中的 MYSQL_ROOT_PASSWORD，回车后手动输入）
docker exec -it campus-mysql mysql -uroot -p campus_helper

# 查看表
SHOW TABLES;
DESCRIBE order_info;

# 查看迁移历史
SELECT version, description, success FROM flyway_schema_history;

# 查看待投递的事件（正常应很快清空；大量 PENDING 说明 Kafka 有问题）
SELECT status, COUNT(*) FROM event_outbox GROUP BY status;
```

> `event_outbox` 里出现 `DEAD` 状态说明事件重试超过上限，现场被保留供人工介入重放——
> 用 `SELECT * FROM event_outbox WHERE status='DEAD'` 查看原因（字段 `error_message`）。

---

## 默认账号

| 角色 | 登录方式 | 默认密码 |
|------|----------|----------|
| 学生 | 学号 `20240101001` | `123456` |
| 跑腿员 | 手机号 `13900002001` | `123456` |
| 管理员 | 用户名 `admin` | `admin123` |

种子口令在 `V1__baseline.sql` 中以 **BCrypt 哈希**存储，不是明文。

---

## 本地开发

```bash
# 1. 只起依赖服务（开发覆盖会开放各服务端口，便于本机直连）
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d mysql redis kafka elasticsearch

# 2. 后端直跑（读 ../.env，端口 8080）
cd backend && mvn spring-boot:run

# 3. 前端 Dev Server（端口 5173，/api 与 /ws 代理到 8080）
cd frontend && npm install && npm run dev
```

需要调试后端 JVM 时改用：`docker compose -f docker-compose.yml -f docker-compose.dev.yml -f docker-compose.debug.yml up -d`

---

## 测试

```bash
cd backend
mvn test                    # 单元测试，不需要 Docker
mvn -P integration verify   # 集成测试：Testcontainers 起真实 MySQL/Redis/Kafka/ES 容器
                            # ⚠️ 必须带 -P integration，否则不执行

cd ../frontend
npm run build               # TypeScript 检查 + 生产构建

cd ..
powershell -File campus-test.ps1   # 针对已启动环境的 API 冒烟测试
```

---

## 常见问题

### 启动直接失败，日志报某个变量必填
`JWT_SECRET` 与 `PAYMENT_CALLBACK_SECRET` 现在没有默认值，`.env` 里必须是真实值（不是 `.env.example` 的占位符）。
错误信息形如 `PAYMENT_CALLBACK_SECRET is required`。

### 端口被占用
```bash
netstat -ano | findstr 8089      # 生产只占这一个
# 开发覆盖还会占用 3306 / 6379 / 9200 / 9092 / 8088
```
改 `docker-compose.yml`（或对应的覆盖文件）里的端口映射即可。

### 后端一直重启，日志里有 Kafka 或 ES 连接失败
看依赖服务的健康状态：`docker compose ps`。首次启动 ES 通常要 30 秒以上，
Kafka 就绪也可能慢——`depends_on: service_healthy` 会让后端等到健康再启动，
但如果健康检查本身超时（ES 的 healthcheck 允许 10 次重试 × 15 秒），需要看 `docker compose logs elasticsearch`。

### 前端页面打开但接口 401
token 已过期或密钥变更过。重新登录即可。若刚改过 `JWT_SECRET`，**所有旧 token 会立即失效**，属于预期行为。

### 登录报 429「登录失败次数过多」
登录失败计数是账号维度的固定窗口（15 分钟内 5 次）。等窗口过期，或清掉 Redis 里的计数键。

### 页面数据不刷新 / 收不到实时通知
实时推送走 WebSocket(STOMP)，Nginx 已配置 `/ws` 反代。订单列表与详情页另有 2-3 秒轮询兜底，
所以即使 WebSocket 断了数据仍会更新，只是延迟变大。

### DeepSeek API 不可用
管理员 AI 助手、智能定价、异常分析需要有效的 Key。不影响订单、派单与支付核心流程。
