# 去野 QuYe

> 周末别宅了，去野！

一个聚焦**城市周边户外活动与运动社交**的平台：发现附近营地、报名限量活动、发布图文动态、寻找运动搭子。

## 功能

| 模块 | 说明 | 技术要点 |
| :--- | :--- | :--- |
| 附近营地 | 按类型查看营地，支持按距离排序、滚动分页 | Redis **GEO**（`GEOSEARCH` / `GEORADIUS`） |
| 限量活动 | 周末营位/名额限时报名，一人限报一个 | Redis **Lua 脚本**保证原子性 + **Stream 消息队列**异步下单 |
| 分布式锁 | 防超卖、防重复报名 | **Redisson** 可重入锁 |
| 户外动态 | 发布图文动态、点赞、排行榜 | Redis **ZSet**（`ZADD` score 为时间戳） |
| 关注与推送 | 关注运动搭子，动态推送到粉丝收件箱 | Redis **Set**（关注）+ **ZSet**（Feed 流，滚动分页） |
| 每日打卡 | 连续打卡天数统计 | Redis **Bitmap**（`SETBIT` / `BITFIELD`） |
| 场馆缓存 | 缓存穿透 / 击穿 / 雪崩三种防护 | 空值缓存 + 互斥锁 + TTL 随机抖动 |
| 短信登录 | 验证码登录，Token 自动续期 | Redis Hash + 双拦截器 |

## 技术栈

- **后端**：Spring Boot 2.3.12、MyBatis-Plus、Spring Data Redis 2.6.2、Redisson、Hutool
- **存储**：MySQL 8、Redis 6.2
- **前端**：Vue 2 + Element UI（原生 HTML/JS，无构建步骤）
- **网关**：nginx（静态资源 + `/api` 反向代理）

## 目录结构

```
.
├── backend/     # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/dp/
│       │   ├── controller/   # 接口层
│       │   ├── service/      # 业务层
│       │   ├── mapper/       # MyBatis-Plus Mapper
│       │   ├── entity/       # 实体（Venue/Activity/Post/Follow/User ...）
│       │   ├── config/       # 拦截器、Redisson、异常处理
│       │   └── utils/        # Redis 常量、缓存客户端、ID 生成器、分布式锁
│       └── resources/
│           ├── application.yaml       # 配置（凭据用环境变量占位）
│           ├── db/quye.sql            # 建表 + 种子数据
│           ├── mapper/ActivityMapper.xml
│           └── seckill.lua            # 报名 Lua 脚本
└── frontend/    # 前端静态页面
    ├── index.html         # 首页（金刚区 + 动态流）
    ├── shop-list.html     # 场馆列表（按距离排序）
    ├── shop-detail.html   # 场馆详情 + 活动名额报名
    ├── info.html          # 个人主页（动态/战绩/粉丝/关注）
    ├── blog-*.html        # 动态详情 / 发布
    └── js, css, imgs
```

## 快速开始

### 1. 准备数据库

```bash
mysql -uroot -p -e "CREATE DATABASE quye DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql -uroot -p quye < backend/src/main/resources/db/quye.sql
```

### 2. 准备 Redis

```bash
# 报名用到的 Stream 消费组，必须先创建
redis-cli -a <password> XGROUP CREATE stream.orders g1 0 MKSTREAM
```

### 3. 配置本地凭据

仓库里 `application.yaml` 只保留占位符，真实凭据请**两种方式任选**：

**方式一（推荐）**：新建 `backend/src/main/resources/application-local.yaml`（已在 `.gitignore` 中）

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/quye?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: <你的 MySQL 密码>
  redis:
    host: <你的 Redis 地址>
    port: 6379
    password: <你的 Redis 密码>
```

**方式二**：设置环境变量 `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` / `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD`

图片上传目录默认 `F:/CODE/Redis/quye-web/nginx-1.18.0/html/web/imgs/`，可用环境变量 `QUYE_IMAGE_UPLOAD_DIR` 覆盖。

### 4. 启动后端

```bash
cd backend
mvn spring-boot:run        # 或直接在 IDE 中运行 QuYeApplication
```

后端监听 `8081`。

### 5. 启动前端

把 `frontend/` 放到 nginx 的 `html/web` 目录下，并加一段 `/api` 反向代理：

```nginx
server {
    listen 8080;

    location / {
        root html/web;
        index index.html;
    }

    location /api {
        default_type application/json;
        proxy_http_version 1.1;
        rewrite /api(/.*) $1 break;
        proxy_pass http://127.0.0.1:8081;
    }
}
```

访问 <http://localhost:8080>。

## 说明

- 图片上传目录、账号密码等均通过配置注入，仓库中不含任何真实凭据。
- 前端为原生 HTML/JS，`frontend/js/common.js` 里 `axios.defaults.baseURL = "/api"`，需配合上面的 nginx 代理使用。
