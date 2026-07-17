# 校园帮帮 — 代领外卖/包裹平台

前后端分离的校园代领平台，支持外卖和包裹的代取配送。三角色（学生/跑腿员/管理员）完全隔离，含完整订单生命周期、评分系统、收入统计、后台管理和管理员 AI 数据分析助手。

## 快速启动（Docker）

```bash
# 1. 确保已安装 Docker Desktop

# 2. 一键启动所有服务
docker compose up -d

# 3. 等待约 1 分钟，打开浏览器
# http://localhost:8089
```

首次启动需下载镜像并构建，约 5-10 分钟。

## 默认账号

| 角色 | 登录信息 | 密码 |
|------|----------|------|
| 学生 | 学号 `20240101001` | `123456` |
| 跑腿员 | 手机号 `13900002001` | `123456` |
| 管理员 | 用户名 `admin` | `admin123` |

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
npm run dev                 # 端口 5173，/api 代理到 localhost:8088
```

### 数据库
```bash
docker compose up -d mysql redis
```

## 服务端口

| 服务 | 开发 | Docker |
|------|------|--------|
| 前端 | `localhost:5173` | `localhost:8089` |
| 后端 API | `localhost:8088` | `localhost:8088` |
| MySQL | `3306` | `3306` |
| Redis | `6379` | `6379` |

## 核心功能

- 🔐 **三角色登录注册** — 学生(学号+密码)、跑腿员(手机+密码)、管理员(admin/admin123)，登录后完全隔离
- 📦 **智能创建订单** — 包裹大小选择(小件¥4/中件¥5/大件¥8)，取件地址下拉，金额自动计算，支持多包裹
- 🚀 **完整订单生命周期** — 待接单→已接单→配送中→已送达→已完成，自动绑定跑腿员
- ⭐ **订单评分系统** — 确认收货后自动弹出评分窗(1-5星+标签+文字)，评分实时更新跑腿员评分
- 📊 **三方独立面板** — 学生(我的订单+新建)、跑腿员(可接订单+配送+收入统计)、管理员(全部管理+流水+看板)
- 💰 **收入统计** — 跑腿员总单数/总收入/今日收入/评分卡片
- 🛡️ **用户管理** — 管理员可管理跑腿员和学生，启用/停用账号
- 📈 **数据看板** — 今日订单/在线跑腿员/完成率/收入四大指标
- 💳 **后台流水** — 订单创建自动生成支付记录
- 🚨 **异常预警** — 幽灵跑腿员检测、高取消率检测，支持手动触发扫描
- 🤖 **AI 助手** — 管理员专属，基于 DeepSeek 的数据分析助手，支持运营决策问答、配送距离矩阵查询、跑腿员效率分析
- 🔄 **实时状态刷新** — 学生/跑腿员端自动轮询，第一时间看到订单状态变更
- 📍 **预估送达时间** — 创建时间+30分钟，超时标红提醒

## 项目结构

```
campus-helper/
├── docker-compose.yml
├── .env
├── sql/
│   ├── init.sql                    # 建库建表(10张) + 种子数据
│   └── migration_add_packages.sql  # 迁移脚本
├── backend/                        # Spring Boot 3.3
│   ├── pom.xml
│   └── src/main/java/com/campus/helper/
│       ├── controller/             # 9个REST控制器
│       ├── service/                # 9个业务服务
│       ├── entity/                 # 11个实体类
│       ├── mapper/                 # MyBatis-Plus Mapper
│       ├── dto/                    # 请求/响应DTO
│       ├── config/                 # CORS/MyBatis/WebSocket配置
│       ├── common/                 # 统一响应/异常/状态流
│       ├── ai/                     # DeepSeek客户端+Prompt模板+管理员工具定义
│       ├── scheduler/              # 定时任务
│       └── websocket/              # 实时推送
└── frontend/                       # React 18 + TypeScript + Ant Design 5
    ├── vite.config.ts
    └── src/
        ├── pages/
        │   ├── auth/               # 登录页
        │   ├── order/              # 订单(列表/新建/详情/可接)
        │   ├── runner/             # 跑腿员(注册/收入)
        │   ├── admin/              # 管理员(跑腿员管理/学生管理)
        │   ├── transaction/        # 后台流水
        │   ├── dashboard/          # 数据看板
        │   └── ai/                 # 管理员AI对话/异常预警
        ├── api/                    # API请求层(9个模块)
        ├── contexts/               # AuthContext
        ├── components/             # RoleGuard
        └── config/                 # 菜单配置
```

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | React 18, TypeScript, Ant Design 5, Vite 6 |
| 后端 | Spring Boot 3.3, MyBatis-Plus 3.5, WebSocket |
| 数据库 | MySQL 8.0, Redis 7 (Redisson) |
| AI | DeepSeek-V3（管理员数据分析） |
| 部署 | Docker Compose, Nginx |

## 文档

- [技术文档](./校园代领平台-技术文档.md) — 完整 API 文档、数据库设计、业务流程
- [Docker 部署指南](./校园代领平台-Docker部署指南.md) — 容器管理、环境配置、常见问题
