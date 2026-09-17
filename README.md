# 去野 QuYe · 城市周边户外活动与运动社交平台

> 周末别宅了，去野！一个基于 **Spring Boot + Redis** 的户外活动与运动社交平台，覆盖「附近营地/场馆 · 限量活动报名 · 图文动态 · 关注运动搭子 · 每日打卡」等场景，重点实践 **Redis 高并发、缓存优化、分布式锁、秒杀削峰** 等后端核心技术。

---

## 一、技术栈

<p align="center">

![Java](https://img.shields.io/badge/Java-8-green.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.3.12-green.svg)
![Spring MVC](https://img.shields.io/badge/Spring%20MVC-5.2.15-green.svg)
![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.4.3-green.svg)
![MySQL](https://img.shields.io/badge/MySQL-5.7-green.svg)
![Redis](https://img.shields.io/badge/Redis-green.svg)
![Spring Data Redis](https://img.shields.io/badge/Spring%20Data%20Redis-2.6.2-green.svg)
![Lettuce](https://img.shields.io/badge/Lettuce-6.1.6-green.svg)
![Redisson](https://img.shields.io/badge/Redisson-3.13.6-green.svg)
![Hutool](https://img.shields.io/badge/Hutool-5.7.17-green.svg)
![Lombok](https://img.shields.io/badge/Lombok-1.18.20-green.svg)
![Spring AOP](https://img.shields.io/badge/Spring%20AOP-2.3.12-green.svg)
![commons-pool2](https://img.shields.io/badge/commons--pool2-2.8.1-green.svg)

</p>

---

## 二、项目背景

户外活动与运动社交类平台在高并发场景下，普遍面临以下技术难题：

| 痛点 | 表现 | 后果 |
| --- | --- | --- |
| **数据库压力大** | 场馆详情、热门动态等高频读接口每次直查 MySQL | 接口慢、数据库成为瓶颈，难以支撑高并发 |
| **缓存三兄弟** | 缓存穿透、击穿、雪崩导致缓存形同虚设 | 大量请求瞬间打到数据库，甚至拖垮服务 |
| **限量活动超卖** | 周末营位/名额报名时库存超卖、同一用户重复报名 | 数据不一致、资损 |
| **分布式环境并发** | 多实例部署下本地锁失效 | 超卖、重复处理 |
| **海量用户/数据** | 关注关系、附近场馆、签到等实时计算成本高 | 查询慢、存储浪费 |

本项目在保留完整业务功能的基础上，围绕以上痛点，系统性引入 **Redis 缓存、Lua 脚本、分布式锁、Stream 消息队列、BitMap、GEO、ZSet/SortedSet** 等技术，把数据库从高并发压力中「解放」出来，形成一套可复用的 **缓存与高并发解决方案**。

---

## 三、核心功能 & 模块

### 1. 用户模块（`/user`）
- 手机号 + 短信验证码登录（验证码存 Redis，5 分钟有效）
- 登录态用 **UUID token + Redis Hash** 存储，实现无状态会话、集群共享
- 拦截器（`LoginInterceptor` / `RefreshTokenInterceptor`）统一鉴权与 token 续期
- 查询个人信息 / 个人主页、登出

### 2. 附近营地 / 场馆模块（`/shop`、`/shop-type`）
- 场馆详情查询（走缓存，含穿透/击穿处理）
- 按类型 / 名称查询、场馆信息更新（先更新 DB 再删缓存，保证一致性）
- **附近场馆**：基于 Redis **GEO** 按坐标 + 5km 半径距离排序分页

### 3. 户外动态 / 图文笔记模块（`/blog`）
- 发布动态、查询详情、热门动态（按点赞数排序）
- **点赞 / 取消点赞**：基于 Redis **ZSet**（score 为时间戳），支持查询点赞 Top5 用户
- **关注流 Feed**：发布时推送到所有粉丝收件箱（ZSet），滚动分页拉取

### 4. 评论模块（`/blog-comments`）
- 对动态发表评论、回复（支持一/二级评论结构）

### 5. 关注 / 运动搭子模块（`/follow`）
- 关注 / 取消关注（同时维护 MySQL 记录 + Redis **Set**）
- 判断是否关注、**共同关注**（两个 Set 求 `SINTER` 交集）

### 6. 限量活动 / 报名模块（`/voucher`、`/voucher-order`）
- 活动列表、新增限量活动（同时把库存预热到 Redis）
- **活动报名 / 秒杀下单**：Lua 脚本原子校验库存 + 一人一单，异步落库

### 7. 每日打卡模块（`/user/sign`）
- 每日打卡：基于 Redis **BitMap**（`SETBIT`）
- 连续打卡天数统计：基于 `BITFIELD` 位运算

### 8. 文件上传模块（`/upload`）
- 图片上传 / 删除

### 9. 通用基础设施（`utils`）
- `CacheClient`：缓存通用封装（穿透/击穿/雪崩三种策略）
- `RedisIdWorker`：Redis 自增 + 位运算实现**全局唯一 ID（雪花算法思想）**
- `SimpleRedisLock`：基于 `SETNX` + Lua 的分布式锁
- `UserHolder`：ThreadLocal 保存登录用户上下文

---

## 四、项目成果 / 优化数据

### 缓存与性能优化

| 优化项 | 解决痛点 | 技术方案 | 效果 |
| --- | --- | --- | --- |
| **缓存加速** | 高频读接口直查 MySQL | Cache Aside + TTL 过期 | **提性能**：热点数据查询从 DB（~10ms 级）降到 Redis（~0.1ms 级），接口耗时降低约 90%+，QPS 提升一个数量级 |
| **缓存穿透** | 恶意/无效请求绕过缓存打 DB | 空值缓存（TTL 1 分钟）+ 布隆过滤 | **降耗时**：不存在的数据不再穿透到数据库，DB 免于无效查询 |
| **缓存击穿** | 热点 key 失效瞬间大量请求并发打 DB | 互斥锁（SETNX，二次校验）/ 逻辑过期（线程池异步重建 + 返回旧值） | **提性能**：同一时刻仅 1 个线程重建缓存，其余线程等待或直接读旧值，DB 压力从 N 并发降到 1 |
| **缓存雪崩** | 大量 key 同一时刻集体失效 | 随机 TTL（在原 TTL 上浮动 5%~10%） | **提性能**：key 失效时间被摊平，避免批量失效导致的瞬时洪峰 |
| **读写一致性** | 更新后缓存与 DB 不一致 | 先更新 DB，再删除缓存 | **减成本**：避免脏读，省去复杂的双写同步机制 |

### 秒杀与并发控制

| 优化项 | 解决痛点 | 技术方案 | 效果 |
| --- | --- | --- | --- |
| **报名原子化** | 库存超卖 | Lua 脚本：扣库存 + 一人一单判断 + 下单原子执行 | **提性能 / 减成本**：库存判断与扣减全部在 Redis 内完成，杜绝超卖 |
| **一人一单** | 同一用户重复报名 | Redis **Set** 去重（`SISMEMBER`） | **减成本**：从源头拦截重复下单，避免资损 |
| **异步削峰** | 报名瞬间写库打爆 MySQL | Redis **Stream** 消息队列异步落库 | **提性能 / 减成本**：下单由同步写库改为异步消费，削峰解耦，DB 写入压力大幅下降 |
| **分布式锁** | 多实例下本地锁失效 | `SETNX` + Lua 原子解锁，升级 Redisson（可重入 + 看门狗续期） | **提性能 / 减成本**：集群环境保证互斥，避免锁误删与死锁 |

### 成本与效率优化

| 优化项 | 解决痛点 | 技术方案 | 效果 |
| --- | --- | --- | --- |
| **全局唯一 ID** | 分库分表后 DB 自增 ID 冲突 | Redis 自增 + 位运算（32 位时间戳 + 32 位序列号） | **提效率**：不依赖 DB，单日可生成 2³² 个 ID，支撑分布式扩展 |
| **打卡 BitMap** | 逐行存储打卡记录浪费空间 | Redis **BitMap**（`SETBIT`/`BITFIELD`） | **减成本**：一个月打卡仅 ~31bit（约 4 字节），存储降低 99%+ |
| **附近场馆** | DB 经纬度计算慢 | Redis **GEO** 半径搜索 + 距离排序 | **降耗时**：5km 内场馆检索由 Redis 直接完成，无需 DB 计算 |
| **共同关注** | 关系查询需多次 DB 关联 | Redis **Set 交集**（`SINTER`） | **降耗时**：一次 `SINTER` 得到共同关注集合 |
| **Feed 流** | 关注流全表扫描 | 推模式：发布时写入粉丝收件箱（ZSet） | **降耗时**：滚动分页 `ZRANGE` O(logN)，替代全表扫描 |
| **无状态会话** | 多实例下 Session 不共享 | 登录态存 Redis（token + Hash） | **提效率**：天然支持集群/水平扩展 |

> 以上「效果」为各技术方案在同类业务中的典型量级，用于直观说明优化方向；具体数值可结合压测环境进一步量化。

---

## 五、目录结构

```
~/
├── pom.xml                                # Maven 依赖管理
└── src/
    ├── main/
    │   ├── java/com/dp/
    │   │   ├── QuYeApplication.java       # 启动类（@MapperScan + @EnableAspectJAutoProxy）
    │   │   ├── config/                    # 配置：MVC、MyBatis-Plus、Redisson、全局异常
    │   │   ├── controller/                # 控制层（User/Venue/Post/Follow/Activity/Upload...）
    │   │   ├── dto/                       # 数据传输对象（Result、UserDTO、LoginFormDTO、ScrollResult）
    │   │   ├── entity/                    # 实体（Venue、Post、Activity、SeckillActivity、Follow、User...）
    │   │   ├── mapper/                    # MyBatis-Plus Mapper 接口
    │   │   ├── service/                   # 业务接口
    │   │   │   └── impl/                  # 业务实现
    │   │   └── utils/                     # 工具：CacheClient、RedisIdWorker、SimpleRedisLock、拦截器、常量
    │   └── resources/
    │       ├── application.yaml           # 主配置（datasource / redis / mybatis-plus）
    │       ├── application-local.yaml     # 本地凭据配置（已 gitignore，不提交）
    │       ├── db/quye.sql                # 建表 + 初始数据
    │       ├── mapper/                    # MyBatis XML
    │       ├── seckill.lua                # 秒杀原子脚本（扣库存 + 一人一单 + 入队）
    │       └── unlock.lua                 # 分布式锁安全释放脚本
    └── test/java/com/dp/                  # 单元测试（批量登录等）
```

---

## 快速开始

1. 准备环境：JDK 8、Maven、MySQL 5.7、Redis
2. 初始化数据库：执行 `src/main/resources/db/quye.sql`
3. 配置连接：复制 `application-local.yaml` 并填写真实的 MySQL / Redis 地址与密码（或通过环境变量 `DB_HOST`、`REDIS_HOST` 等覆盖）
4. 启动：运行 `QuYeApplication.main()`
5. 服务默认端口：`8081`
