# 校园帮帮 — Docker 部署指南

## 环境要求

- Docker Desktop 最新版
- 至少 4GB 可用内存
- 端口 3306、6379、8088、8089 未被占用

## 快速启动

```bash
cd campus-helper

# 1. 编辑环境变量（可选）
# 编辑 .env，修改 MYSQL_ROOT_PASSWORD、DEEPSEEK_API_KEY 等

# 2. 一键启动所有服务
docker compose up -d

# 3. 等待启动完成（首次约 3-5 分钟）
docker compose ps

# 4. 打开浏览器
# http://localhost:8089   (前端页面)
```

## 服务说明

| 容器 | 镜像 | 端口映射 | 说明 |
|------|------|----------|------|
| campus-mysql | mysql:8.0 | 3306:3306 | 数据库，自动执行 init.sql |
| campus-redis | redis:7-alpine | 6379:6379 | 缓存/分布式锁 |
| campus-backend | 本地构建 | 8088:8080 | Spring Boot 后端 |
| campus-frontend | 本地构建 | 8089:80 | Nginx + React 静态资源 |

## 容器管理

```bash
# 查看运行状态
docker compose ps

# 查看日志
docker compose logs -f backend
docker compose logs -f frontend

# 重启服务
docker compose restart backend
docker compose restart frontend

# 重新构建
docker compose up -d --build

# 停止所有
docker compose down

# 停止并清理数据卷
docker compose down -v
```

## 数据库管理

```bash
# 连接 MySQL
docker exec -it campus-mysql mysql -uroot -pCampusHelper@2026! campus_helper

# 查看表结构
SHOW TABLES;
DESCRIBE order_info;

# 查看数据
SELECT * FROM student;
SELECT * FROM runner;
SELECT * FROM order_info ORDER BY created_at DESC LIMIT 5;
```

## 手动数据库迁移

如果已有数据需要添加新列：

```bash
docker exec campus-mysql mysql -uroot -pCampusHelper@2026! campus_helper < sql/migration_add_packages.sql
```

## 环境变量 (.env)

```
MYSQL_ROOT_PASSWORD=CampusHelper@2026!
MYSQL_DATABASE=campus_helper
DB_USERNAME=root
DB_PASSWORD=CampusHelper@2026!
DEEPSEEK_API_KEY=sk-your-deepseek-api-key-here
JWT_SECRET=campus-helper-jwt-secret-key-2026
WECHAT_MCH_ID=your-mch-id
WECHAT_API_KEY=your-api-key
ALIPAY_APP_ID=your-app-id
ALIPAY_PRIVATE_KEY=your-private-key
```

## 默认账号

| 角色 | 登录方式 | 默认密码 |
|------|----------|----------|
| 学生 | 学号：20240101001 | 123456 |
| 跑腿员 | 手机号：13900002001 | 123456 |
| 管理员 | admin | admin123 |

## 常见问题

### 端口被占用
```bash
# 查看端口占用
netstat -ano | findstr 8089
# 修改 docker-compose.yml 中的端口映射
```

### 数据库连接失败
```bash
# 确认 MySQL 已就绪
docker compose logs mysql | grep "ready for connections"
# 等待 healthcheck 通过后重试
```

### 前端 403 错误
后端刚启动需要约 10 秒完成初始化，等待后刷新页面即可。

### DeepSeek API 不可用
AI 助手、智能定价、异常分析功能需要有效的 DeepSeek API Key。不影响订单核心流程。
