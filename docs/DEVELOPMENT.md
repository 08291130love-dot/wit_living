# 运行与开发说明

## 环境准备

- JDK 8、Maven、MySQL。
- 支持 GEOSEARCH 命令的 Redis；RocketMQ NameServer 和 Broker。
- Nginx 用于提供静态页面及 `/api/` 反向代理。

IDEA 中将 Project SDK、模块 SDK、Maven Runner 与启动配置 JRE 统一为 JDK 8。启动类为 `com.witliving.WitLivingApplication`，工作目录为项目根目录。

## 数据库初始化

创建使用 utf8mb4 的 `wit_living` 数据库，导入 [初始化脚本](../src/main/resources/db/wit_living.sql)。

**脚本含 DROP TABLE，仅在独立演示数据库执行，不要导入已有业务库。** 示例用户手机号是非真实占位号码，登录时请创建自己的测试用户。

## 连接配置

PowerShell 中设置环境变量，再启动后端：

```powershell
$env:DB_PASSWORD = '替换为本机数据库密码'
$env:REDIS_HOST = '127.0.0.1'
# Redis 有认证时再设置：$env:REDIS_PASSWORD = '替换为本机Redis密码'
$env:ROCKETMQ_NAME_SERVER = '127.0.0.1:9876'
mvn spring-boot:run
```

也可设置 `DB_URL`、`DB_USERNAME`、`REDIS_PORT`、`REDIS_DATABASE`。Redisson 与 Spring Redis 复用同一连接配置。

在 IDEA 启动时，将变量填写到运行配置的 Environment variables 中。Spring 不会自动读取 .env 文件；也可使用不提交的 `application-local.yaml` 并启用 local profile。真实密码不得提交到仓库。

## 前端访问

将 [Nginx 配置](../deploy/nginx/wit-living.conf) 引入 Nginx 的 `http {}`，修改 `root` 为本机 frontend 的绝对路径，校验并重新加载配置。

- 前端：`http://localhost:8080`
- 后端：`http://localhost:8081`
- 前端 `/api/` 请求由 Nginx 转发到后端，不要直接双击 HTML 运行。

图片默认写入项目根目录的 `frontend/imgs/`。部署时可通过 `IMAGE_UPLOAD_DIR` 指定目录，同时配置 Nginx 映射。真实上传文件应与版本控制分离。

## 数据与消息隔离

数据库默认名为 `wit_living`，RocketMQ Topic 和消费组使用 `wit_living` 前缀。避免混用其他项目的生产者、消费者；多个演示项目同时运行时，应使用独立 Redis 实例或数据库，避免 Key 冲突。

## 构建与验证

```shell
mvn -DskipTests package
```

构建产物为 `target/wit-living-0.0.1-SNAPSHOT.jar`。现有测试依赖中间件，部分会写入数据，仅在专用测试环境中执行 `mvn test`。

当前已通过 Java 8 编译检查，完整中间件启动、业务联调与压测仍需验证。编译结果不等同于端到端测试结果。

## 设计边界

- 验证码为开发演示方案；支付、退款和超时关单不在当前实现范围内。
- 秒杀返回订单号表示进入异步处理链路，不代表数据库落单完成。
- 消费重试与幂等不能替代可靠对账；死信处理、订单状态查询及补偿机制是后续迭代方向。
- 缓存二次删除是补偿措施，不保证强一致；跨实例本地缓存失效需要进一步完善。
- 尚无可复现的性能报告；生产部署前需另行完成依赖升级评估、安全加固和容量验证。

来源归属见 [第三方说明](../THIRD_PARTY_NOTICES.md)，图片为 AI 场景示意，详见 [素材说明](../frontend/imgs/generated/ASSETS.md)。
