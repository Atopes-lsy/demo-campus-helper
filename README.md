# 校园帮帮 — 代领外卖/包裹平台

前后端分离的校园代领平台，支持外卖和包裹的代取配送。三角色（学生/跑腿员/管理员）完全隔离，含完整订单生命周期、自动派单、评分系统、收入统计、后台看板和管理员 AI 数据分析助手。

后端为事件驱动架构：订单状态变更与业务数据**同事务**写入 Outbox 表，再由调度器投递 Kafka，5 个独立消费者组分别驱动实时通知、Elasticsearch 同步、自动派单、异常检测与审计日志；消费端以 `(event_id, consumer_name)` 幂等表去重，实现「至少一次投递 + 消费端幂等」的最终一致。

## 快速启动（Docker）

```bash
# 1. 确保已安装 Docker Desktop

# 2. 复制 .env.example 为 .env，设置本机测试用密码、JWT_SECRET 和回调密钥
# 不要使用示例密钥部署；管理员密码以 .env 为准

# 3. 构建并启动所有服务
docker compose up -d --build

# 3. 等待约 1 分钟，打开浏览器
# http://localhost:8089
```

首次启动需下载镜像并构建，约 5-10 分钟。
生产配置只对宿主机开放前端 `8089`，后端、MySQL、Redis、Kafka、Elasticsearch 均仅在 Compose 网络内可访问。

## 默认账号

| 角色 | 登录信息 | 密码 |
|------|----------|------|
| 学生 | 学号 `20240101001` | `123456` |
| 跑腿员 | 手机号 `13900002001` | `123456` |
| 管理员 | `.env` 中的 `ADMIN_USERNAME` | `.env` 中配置的密码（无固定默认密码） |

## 本地开发

### 后端
```bash
cd backend
mvn spring-boot:run        # 端口 8080
```

### 前端
```bash
cd frontend
npm install
npm run dev                 # 端口 5173，/api 和 /ws 代理到 localhost:8080
```

### 数据库
```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d mysql redis kafka elasticsearch
```

需要直接访问各内部服务时使用开发覆盖配置：

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
```

仅在本地调试后端时再追加 `docker-compose.debug.yml`，开放 JDWP `5005`。

## 服务端口

| 服务 | 本地/开发覆盖 | 生产 Compose |
|------|------|--------|
| 前端 | `localhost:5173` | `localhost:8089` |
| 后端 API | `localhost:8080` / `localhost:8088` | 仅容器网络 |
| MySQL | `localhost:3306` | 仅容器网络 |
| Redis | `localhost:6379` | 仅容器网络 |
| Kafka | `localhost:9092` | 仅容器网络 |
| Elasticsearch | `localhost:9200` | 仅容器网络 |

## 测试

```bash
cd backend
mvn test                    # 单元测试
mvn -Pintegration verify    # Testcontainers 端到端测试，需要 Docker

cd ../frontend
npm run build               # TypeScript 检查与生产构建

cd ..
powershell -File campus-test.ps1 -AdminPassword '<本地管理员密码>'
```

PowerShell 可先设置 `$env:CAMPUS_HELPER_ADMIN_PASSWORD`，再运行 `./campus-test.ps1`，避免把密码写进命令历史。入口不依赖当前目录；`test-api.ps1` 随仓库交付。默认入口为 `http://localhost:8089`，可用 `-BaseUrl` 覆盖。脚本检查登录、匿名管理接口 401、看板和搜索，并通过 Compose 服务名核对 ES 原生计数；服务故障或数据结构错误会抛出异常。

可靠性专项：`mvn -Pintegration "-Dit.test=CampusHelperApplicationIT,DispatchCoexistenceIT" verify`。包括真实 MySQL 下 16 跑腿员竞争、30 轮自动派单与抢单竞争，以及暂停测试 Kafka 后恢复投递和重复事件去重。测试定义不代表已通过，实际执行结果见 [可靠性验收](docs/可靠性验收-2026-11-08.md)。

## 核心功能

- 🔐 **三角色登录注册** — 学生(学号+密码)、跑腿员(手机+密码)、管理员(admin/admin123)，登录后完全隔离
- 📦 **智能创建订单** — 包裹大小选择(小件¥4/中件¥5/大件¥8)，取件地址下拉，金额自动计算，支持多包裹
- 🚀 **完整订单生命周期** — 待接单→已接单→配送中→已送达→已完成，状态流转经状态机校验，非法跃迁被拒
- ⚙️ **自动派单引擎** — 多因子评分（距离 / 评分 / 负载，权重取自可配置的定价规则）为订单挑选最优跑腿员，订单粒度分布式锁防重复派单；与跑腿员抢单并存，由 `DISPATCH_AUTO_ENABLED` 开关控制，派单未命中时订单留在待接单池中
- ⚡ **抢单并发控制** — CAS 原子更新（`UPDATE ... WHERE runner_id IS NULL`）防同一订单被重复接单，冲突返回 409
- 🔎 **全文检索与附近订单** — Elasticsearch 带权重多字段检索（标题^3 / 描述^2 / 地址^2）、`geo_distance` 附近订单查询
- 🧭 **BI 看板** — 收入趋势、时段分布、地理热力图由 ES 聚合（`date_histogram` / `geohash_grid`）完成，单次请求返回多维度统计
- 🗄️ **缓存体系** — Cache-Aside 分级 TTL，写操作经事务提交后事件失效，含空值缓存与互斥重建
- ⭐ **订单评分系统** — 确认收货后自动弹出评分窗(1-5星+标签+文字)，评分实时更新跑腿员评分
- 🧩 **三方独立面板** — 学生(我的订单+新建)、跑腿员(可接订单+配送+收入统计)、管理员(全部管理+流水+看板)
- 💰 **收入统计** — 跑腿员总单数/总收入/今日收入/评分卡片
- 🛡️ **用户管理** — 管理员可管理跑腿员和学生，启用/停用账号
- 💳 **后台流水** — 订单创建自动生成支付记录，回调校验 HMAC 签名与时间戳以阻断重放
- 🚨 **异常预警** — 幽灵跑腿员检测、高取消率检测，支持手动触发扫描
- 🤖 **AI 助手** — 管理员专属，基于 DeepSeek(`deepseek-chat`) 的函数调用：运营决策问答、配送距离矩阵查询、跑腿员效率分析
- 🔔 **实时推送** — WebSocket(STOMP) 在连接帧校验 JWT 并按用户定向推送；订单页另有轮询兜底
- 🔑 **会话撤销** — JWT 携带 token 版本号，登出 / 改密 / 管理员踢下线即时使该用户全部已签发 token 失效
- 📍 **预估送达时间** — 创建时间+30分钟，超时标红提醒

## 关键配置

`DISPATCH_AUTO_ENABLED`（默认 `false`）控制下单后是否由系统自动派单：

- `true` — 订单创建后经 Outbox 触发派单消费者，按多因子评分为订单指派跑腿员
- `false` — 订单进入待接单池，等跑腿员自行抢单（引入开关前的原有行为）

两种方式并存：自动派单未找到合适跑腿员（都在忙，或最高分低于 `dispatch.min-score-threshold`）时，订单仍留在池中可被抢。

## 项目结构

```
campus-helper/
├── docker-compose.yml              # 生产编排：仅前端对宿主机开放
├── docker-compose.dev.yml          # 开发覆盖：开放 MySQL/Redis/Kafka/ES 端口
├── docker-compose.debug.yml        # 仅后端调试：开放 JDWP 5005
├── .env.example                    # 环境变量模板（.env 不入库）
├── backend/                        # Spring Boot 3.3，97 个 Java 源文件
│   ├── pom.xml
│   └── src/
│       ├── main/resources/db/migration/  # Flyway 迁移（V1 基线 / V2 Outbox / V3 索引）
│       ├── main/java/com/campus/helper/
│       │   ├── controller/         # 11 个 REST 控制器
│       │   ├── service/            # 14 个业务服务（派单/检索/BI/超时处理等）
│       │   ├── entity/             # 13 个实体
│       │   ├── mapper/             # 13 个 MyBatis-Plus Mapper
│       │   ├── dto/                # 请求/响应 DTO
│       │   ├── event/              # 事务性 Outbox、Kafka 配置与投递调度器
│       │   │   └── consumer/       # 5 个消费者组：通知 / ES 同步 / 派单 / 异常检测 / 审计
│       │   ├── cache/              # Cache-Aside 服务、键定义、提交后失效监听
│       │   ├── security/           # JWT、登录失败锁定、token 版本号撤销
│       │   ├── ai/                 # DeepSeek 客户端 + Prompt 模板 + 管理员工具定义
│       │   ├── config/             # 安全 / WebSocket / MyBatis / CORS 配置
│       │   ├── common/             # 统一响应、异常映射、状态流校验
│       │   ├── scheduler/          # 订单超时扫描
│       │   └── websocket/          # 实时推送
│       └── test/java/              # 12 个测试（单元测试 + Testcontainers 集成测试）
└── frontend/                       # React 18 + TypeScript + Ant Design 5，30 个 ts/tsx
    ├── vite.config.ts
    └── src/
        ├── pages/                  # auth / order / runner / admin / dashboard
        │                           # / transaction / ai / notification / payment
        ├── api/                    # API 请求层（10 个模块）
        ├── hooks/                  # useWebSocket
        ├── contexts/               # AuthContext
        ├── components/             # RoleGuard
        └── config/                 # 菜单配置
```

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | React 18, TypeScript 5.6, Ant Design 5, Vite 6 |
| 后端 | Spring Boot 3.3, MyBatis-Plus 3.5, Spring Security (JWT), WebSocket (STOMP) |
| 消息 | Kafka — 事务性 Outbox + 5 个消费者组，幂等表去重 |
| 检索 | Elasticsearch 8.13 — 全文检索 / 地理查询 / 看板聚合 |
| 数据 | MySQL 8.0（Flyway 版本化迁移）, Redis 7（Redisson：分布式锁与缓存） |
| AI | DeepSeek（`deepseek-chat`，函数调用） |
| 测试 | JUnit 5, Mockito, Testcontainers（真实 MySQL / Redis / Kafka / Elasticsearch 容器） |
| 部署 | Docker Compose, Nginx |

## 文档

- [技术文档](docs/校园代领平台-技术文档.md) — 完整 API 文档、数据库设计、业务流程
- [Docker 部署指南](docs/校园代领平台-Docker部署指南.md) — 容器管理、环境配置、常见问题
