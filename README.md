# Wit Living

城市生活发现与优惠券秒杀项目。围绕商户浏览、探店分享和优惠券下单，实践多级缓存、Redis 原子校验和消息队列异步处理。

![城市生活场景](frontend/imgs/generated/courtyard.jpg)

## 项目亮点

- **商户缓存**：Caffeine 本地缓存配合 Redis，结合布隆过滤器与逻辑过期控制无效访问、热点重建；RocketMQ 用于缓存失效补偿。
- **优惠券秒杀**：Redis Lua 原子校验库存及购买资格，RocketMQ 异步落单，Redisson 用户锁及数据库事务处理重复请求与库存竞争。
- **社交与发现**：签到、点赞、关注、附近商户与关注流，对应 Bitmap、Set、Sorted Set、GEO 等 Redis 数据结构。
- **独立视觉风格**：暖白、森林绿、珊瑚色的移动端界面，配套生成式场景图片；业务接口保持原有结构。

## 技术栈

Java 8 / Spring Boot 2.3 / MyBatis-Plus / MySQL / Redis / Caffeine / Guava / Redisson / RocketMQ / Vue 2 / Element UI / Nginx。

## 目录

```text
Wit_Living/
├── frontend/                     # 静态前端、样式及展示素材
├── src/main/java/com/witliving/
│   ├── controller/               # HTTP 接口
│   ├── service/                  # 业务、消息生产与消费、订单事务
│   ├── mapper/                   # 数据库访问
│   ├── entity/                   # 数据库实体
│   ├── dto/                      # 请求与响应对象
│   ├── config/                   # 缓存、锁、消息、拦截器配置
│   └── utils/                    # 缓存封装、ID、登录上下文等
├── src/main/resources/
│   ├── db/wit_living.sql          # 教学示例数据
│   ├── mapper/                   # SQL 映射
│   └── *.lua                     # 秒杀校验、回滚与锁释放
├── src/test/                     # 需连接中间件的测试
└── deploy/nginx/wit-living.conf   # 前端与 API 反向代理
```

## 本地运行

1. 准备 JDK 8、Maven、MySQL、Redis 和 RocketMQ（NameServer 与 Broker）。本项目没有内置这些服务。
2. 创建使用 utf8mb4 的 `wit_living` 数据库，再导入 `src/main/resources/db/wit_living.sql`。**脚本包含 DROP TABLE，仅在独立演示数据库执行。**
3. 在项目根目录启动后端。PowerShell 示例：

```powershell
$env:DB_PASSWORD = '替换为本机数据库密码'
$env:REDIS_HOST = '127.0.0.1'
# 有认证时再设置：$env:REDIS_PASSWORD = '替换为本机Redis密码'
$env:ROCKETMQ_NAME_SERVER = '127.0.0.1:9876'
mvn spring-boot:run
```

后端默认端口 `8081`。也可配置 `DB_URL`、`DB_USERNAME`、`REDIS_PORT`、`REDIS_DATABASE`。Redisson 与 Spring Redis 复用同一份连接配置。Spring 不会自动读取 .env 文件，请使用环境变量或不提交的 application-local.yaml 配合 local profile。

4. 将 `deploy/nginx/wit-living.conf` 引入 Nginx 的 `http {}`，检查 `root` 路径并重新加载，访问 `http://localhost:8080`。不要直接双击 HTML，前端依赖 `/api/` 反向代理。
5. 上传图片默认写到项目根目录的 `frontend/imgs/`；改变工作目录或部署路径时设置 `IMAGE_UPLOAD_DIR` 为对应目录，并让 Nginx 能读取它。

新副本的数据库默认名称和 RocketMQ Topic/消费组已独立命名为 `wit_living` 系列。不要与旧项目混用消息生产者、消费者。Redis Key 结构保留，运行两个副本时应使用独立 Redis 实例或数据库，避免测试数据相互影响。

## 构建与验证

```shell
mvn -DskipTests package
```

构建产物：`target/wit-living-0.0.1-SNAPSHOT.jar`。现有测试会连接中间件、部分会写入测试数据；仅在专用测试环境执行 `mvn test`。构建成功不代表完整业务联调通过。

## 当前边界

- 验证码为开发演示方案，不是生产短信服务；尚无完整支付、退款与超时关单链路。
- 秒杀返回订单号表示进入处理流程，不等于数据库订单已经落地。
- 消费幂等、失败重试不等于消息永不丢失；死信人工处理、可靠对账和更完整补偿仍需建设。
- 本地缓存跨实例失效与缓存最终一致性仍存在完善空间。
- 没有可复现压测报告，不宣称未经验证的 QPS 或生产级可用性。
- 数据及图片为演示用途；正式发布前应替换真实商户样例及检查数据授权。技术栈含较旧版本，生产部署需单独评估升级和安全加固。

## 发布说明

仓库不含原仓库 Git 历史、构建产物及本机 Maven 配置。示例用户手机号已替换为非真实占位号码。提交前检查数据库样例、个人信息、配置和授权，不提交真实密码、用户上传文件或日志。

当前已通过 Java 8 编译检查，尚未完成 MySQL、Redis、RocketMQ 的完整启动及业务联调；不代表开箱即用或生产可用。

代码来源与依赖归属见 [第三方说明](THIRD_PARTY_NOTICES.md)，生成图片说明见 [素材说明](frontend/imgs/generated/ASSETS.md)。
