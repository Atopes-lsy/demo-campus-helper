# 校园帮帮 — 代领外卖/包裹平台

前后端分离的校园代领平台，支持外卖和包裹的代取配送。技术栈：React 18 + Spring Boot 3.3 + MySQL 8 + Redis + DeepSeek-V3。

## 快速启动（Docker）

```bash
# 1. 确保已安装 Docker Desktop
# 2. 设置 DeepSeek API Key（可选，用于 AI 功能）
#    编辑 .env，修改 DEEPSEEK_API_KEY

# 2. 一键启动
docker compose up -d

# 3. 打开浏览器
# http://localhost
```

首次启动需要下载镜像，约 5-10 分钟。之后只需几秒。

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
npm run dev                 # 端口 5173
```

### 数据库
需要本地 MySQL 8.0+ 和 Redis 7.0+，或直接用 Docker 启动：
```bash
docker compose up -d mysql redis
```

## 项目结构

```
campus-helper/
├── docker-compose.yml       # Docker 一键部署
├── .env                     # 环境变量
├── sql/init.sql             # 数据库初始化（10张表+测试数据）
├── backend/                 # Spring Boot 3.3 后端
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/campus/helper/
│       ├── entity/          # 实体类（10个）
│       ├── mapper/          # MyBatis-Plus Mapper
│       ├── service/         # 业务服务（含 AI 服务）
│       ├── controller/      # REST 控制器
│       ├── config/          # Spring 配置
│       ├── common/          # 通用工具类
│       ├── ai/              # DeepSeek 客户端 + Prompt
│       ├── scheduler/       # 定时任务
│       └── websocket/       # WebSocket 推送
└── frontend/                # React 18 前端
    ├── Dockerfile
    ├── nginx.conf
    └── src/
        ├── api/             # API 请求封装
        ├── pages/           # 页面组件
        │   ├── order/       # 订单管理
        │   ├── ai/          # AI 对话 + 异常预警
        │   └── dashboard/   # 数据看板
        ├── hooks/           # 自定义 Hooks
        └── utils/           # 工具函数 + 数据字典
```

## 核心功能

- 🍔 外卖/包裹代取订单管理
- 🤖 自动派单引擎（四维加权评分）
- 🔔 WebSocket 实时消息推送
- 💰 在线支付闭环
- 📊 运营数据看板
- 🤖 DeepSeek 智能客服
- 📈 智能动态定价
- 🚨 异常智能检测

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | React 18, TypeScript, Ant Design 5, Vite |
| 后端 | Spring Boot 3.3, MyBatis-Plus 3.5, WebSocket |
| 数据库 | MySQL 8.0, Redis 7 |
| AI | DeepSeek-V3 |
| 部署 | Docker Compose, Nginx |

## 文档

- [技术文档](./校园代领平台-技术文档.md)
- [Docker 部署指南](./校园代领平台-Docker部署指南.md)
