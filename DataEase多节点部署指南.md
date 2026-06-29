# DataEase 多节点部署指南

> 适用版本：DataEase v2.10.16  
> 部署模式：前端 `npm run dev` + 后端 Jar 包多节点

---

## 一、架构说明

```
                     ┌─────────────┐
                     │   Nginx /   │
                     │   APISIX    │  (反向代理 + 负载均衡)
                     └──────┬──────┘
                            │
              ┌─────────────┼─────────────┐
              │             │             │
         ┌────▼────┐  ┌────▼────┐  ┌────▼────┐
         │ Node 1  │  │ Node 2  │  │ Node N  │  (后端 Jar, 端口 8100)
         └────┬────┘  └────┬────┘  └────┬────┘
              │             │             │
              └─────────────┼─────────────┘
                            │
              ┌─────────────┼─────────────┐
              │             │             │
         ┌────▼────┐  ┌────▼────┐  ┌────▼────┐
         │  MySQL  │  │  Redis  │  │ Selenium│  (共享中间件)
         └─────────┘  └─────────┘  └─────────┘
                            │
                     ┌──────▼──────┐
                     │ 前端 Dev    │  (Vite, 端口 8080)
                     │ npm run dev │
                     └─────────────┘
```

**核心要点：**
- 所有后端节点共享同一个 MySQL 数据库和 Redis
- 使用 Nginx 或 APISIX 做反向代理，实现前端请求的负载均衡
- 前端 `npm run dev` 启动 Vite 开发服务器，代理到 Nginx 地址
- 定时任务只在一个节点执行（或使用 Quartz 分布式锁）

---

## 二、环境准备

### 2.1 服务器规划

> 以下提供两种部署方案，请根据实际情况选择。

#### 方案 A：多台服务器部署（生产推荐）

| 角色 | IP 地址 | 端口 | 说明 |
|------|---------|------|------|
| MySQL | 10.0.0.1 | 3306 | 共享数据库 |
| Redis | 10.0.0.1 | 6379 | 共享缓存 + Session |
| Nginx | 10.0.0.1 | 9080 | 负载均衡入口 |
| 后端 Node1 | 10.0.0.2 | 8100 | DataEase 后端实例 |
| 后端 Node2 | 10.0.0.3 | 8100 | DataEase 后端实例 |
| 前端 Dev | 本地开发机 | 8080 | Vite 开发服务器 |
| Selenium | 10.0.0.1 | 4444 | 定时报告截图（可选） |

#### 方案 B：同一台服务器部署多个 Jar 节点（开发/测试）

| 角色 | IP 地址 | 端口 | 说明 |
|------|---------|------|------|
| MySQL | 10.0.0.1 | 3306 | 共享数据库 |
| Redis | 10.0.0.1 | 6379 | 共享缓存 + Session |
| Nginx | 10.0.0.1 | 9080 | 负载均衡入口 |
| 后端 Node1 | 10.0.0.1 | **8101** | DataEase 后端实例 1 |
| 后端 Node2 | 10.0.0.1 | **8102** | DataEase 后端实例 2 |
| 前端 Dev | 本地开发机 | 8080 | Vite 开发服务器 |

> ⚠️ **同一台服务器部署的关键**：每个 Jar 节点必须使用**不同的端口**，否则会因端口冲突导致后续节点启动失败。

### 2.2 依赖软件版本

| 软件 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 21+ | 后端运行环境 |
| Node.js | 18+ / 23 | 前端开发环境 |
| MySQL | 8.0+ | 数据库 |
| Redis | 6.0+ | 缓存 + Session 共享 |
| Nginx | 1.20+ | 反向代理（可选，也可用 APISIX） |
| Maven | 3.8+ | 后端构建 |

---

## 三、数据库与中间件准备

### 3.1 创建 MySQL 数据库

```sql
-- 连接 MySQL
mysql -u root -p

-- 创建数据库
CREATE DATABASE IF NOT EXISTS dataeasev201 
  DEFAULT CHARACTER SET utf8mb4 
  DEFAULT COLLATE utf8mb4_general_ci;

-- 创建用户并授权（如不使用 root）
CREATE USER 'dataease'@'%' IDENTIFIED BY 'YourPassword123';
GRANT ALL PRIVILEGES ON dataeasev201.* TO 'dataease'@'%';
FLUSH PRIVILEGES;
```

### 3.2 配置 Redis

确保 Redis 监听所有网络接口：

```bash
# 编辑 redis.conf
bind 0.0.0.0
# 设置密码（推荐）
requirepass YourRedisPassword

# 重启 Redis
systemctl restart redis
```

### 3.3 创建数据目录

> ⚠️ **关键说明：哪些目录可以独立，哪些必须共享？**
>
> | 目录 | 必须共享？ | 原因 |
> |------|-----------|------|
> | `data/static-resource/` | ✅ **必须共享** | 用户上传的图片/附件，所有节点都可能被请求访问 |
> | `data/appearance/` | ✅ **必须共享** | 外观设置文件，所有节点需要读取 |
> | `data/exportData/` | ✅ **必须共享** | 导出文件可能在节点A生成，但下载请求落到节点B |
> | `data/font/` | ✅ **必须共享** | 上传的字体文件，所有节点需要读取 |
> | `data/plugin/` | ✅ **必须共享** | 插件文件，所有节点需要加载 |
> | `data/geo/` | ✅ **必须共享** | 自定义地图文件，所有节点需要读取 |
> | `data/map/` | ✅ **必须共享** | 地图数据，所有节点需要 |
> | `data/i18n/` | ✅ **必须共享** | 国际化文件，所有节点需要 |
> | `drivers/` | ✅ **必须共享** | 数据库驱动 jar，所有节点需要 |
> | `logs/` | ❌ 独立 | 每个节点独立日志，避免混乱 |
> | `cache/` | ❌ 独立 | EhCache 文件锁，必须独立 |
>
> **解决方案：`data/` 和 `drivers/` 使用共享存储，`logs/` 和 `cache/` 独立。**
>
> 以下提供两种共享存储方案：

---

#### 方案一：NFS 共享存储（多台服务器推荐）

**在共享存储服务器（如 10.0.0.1）上创建 NFS 共享目录：**

```bash
# ===== 在 NFS 服务器上执行 =====
sudo mkdir -p /nfs/dataease/{drivers,data/{map,static-resource,appearance,exportData,excel,i18n,plugin,geo,font}}

# 安装 NFS 服务
sudo yum install -y nfs-utils    # CentOS/RHEL
# 或
sudo apt install -y nfs-kernel-server  # Ubuntu/Debian

# 配置共享
echo "/nfs/dataease 10.0.0.0/24(rw,sync,no_root_squash)" | sudo tee -a /etc/exports
sudo exportfs -a
sudo systemctl enable --now nfs-server   # CentOS
# sudo systemctl enable --now nfs-kernel-server  # Ubuntu
```

**在每个后端节点上挂载 NFS：**

```bash
# ===== 在每个后端节点上执行 =====
sudo yum install -y nfs-utils    # CentOS
# sudo apt install -y nfs-common  # Ubuntu

# 创建本地目录结构
sudo mkdir -p /opt/dataease2.0/{cache,logs}
sudo mkdir -p /opt/apps/config

# 挂载 NFS 共享目录（data 和 drivers）
sudo mkdir -p /opt/dataease2.0/{drivers,data}
sudo mount -t nfs 10.0.0.1:/nfs/dataease/drivers /opt/dataease2.0/drivers
sudo mount -t nfs 10.0.0.1:/nfs/dataease/data /opt/dataease2.0/data

# 设置开机自动挂载
echo "10.0.0.1:/nfs/dataease/drivers /opt/dataease2.0/drivers nfs defaults 0 0" | sudo tee -a /etc/fstab
echo "10.0.0.1:/nfs/dataease/data /opt/dataease2.0/data nfs defaults 0 0" | sudo tee -a /etc/fstab

# 设置权限
sudo chmod -R 755 /opt/dataease2.0
sudo chmod -R 755 /opt/apps
```

#### 方案二：同一台服务器部署（共享 data 目录 + 独立 logs/cache）

同一台服务器上，`data/` 和 `drivers/` 共享，`logs/` 和 `cache/` 独立：

```bash
# ===== 共享目录（所有节点共用） =====
sudo mkdir -p /opt/dataease2.0/{drivers,data/{map,static-resource,appearance,exportData,excel,i18n,plugin,geo,font}}

# ===== Node1 独立目录 =====
sudo mkdir -p /opt/dataease2.0/node1/{cache,logs}
sudo mkdir -p /opt/apps/node1/config

# ===== Node2 独立目录 =====
sudo mkdir -p /opt/dataease2.0/node2/{cache,logs}
sudo mkdir -p /opt/apps/node2/config

# 设置权限
sudo chmod -R 755 /opt/dataease2.0
sudo chmod -R 755 /opt/apps
```

> 📁 **目录结构总览（方案二 — 同机部署）：**
> ```
> /opt/dataease2.0/
> ├── data/                    ← 所有节点共享
> │   ├── static-resource/     ← 图片上传
> │   ├── appearance/          ← 外观设置
> │   ├── exportData/          ← 导出文件
> │   ├── font/                ← 字体
> │   ├── plugin/              ← 插件
> │   ├── geo/                 ← 自定义地图
> │   ├── map/                 ← 地图数据
> │   ├── i18n/                ← 国际化
> │   └── excel/               ← Excel
> ├── drivers/                 ← 所有节点共享
> ├── node1/                   ← Node1 独占
> │   ├── cache/               ← EhCache 缓存
> │   └── logs/                ← 日志
> └── node2/                   ← Node2 独占
>     ├── cache/
>     └── logs/
> ```

---

## 四、后端构建与部署

### 4.1 构建后端 Jar 包

在项目根目录执行：

```bash
cd d:/work/xinjiang/dataease-2.10.16_xj

# 清理并构建（分布式模式），跳过测试
mvn clean package -Pdistributed -DskipTests
```

构建成功后，Jar 包位于：

```
core/core-backend/target/CoreApplication.jar
```

### 4.2 准备外部配置文件

#### 方案 A：多台服务器

创建 `/opt/apps/config/application.yml`（在每个后端节点上，内容相同）：

```yaml
server:
  port: 8100
  tomcat:
    connection-timeout: 70000
  servlet:
    context-path:

spring:
  servlet:
    multipart:
      max-file-size: 500MB
      max-request-size: 500MB
  datasource:
    url: jdbc:mysql://10.0.0.1:3306/dataeasev201?autoReconnect=false&useUnicode=true&characterEncoding=UTF-8&characterSetResults=UTF-8&zeroDateTimeBehavior=convertToNull&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: YourMySQLPassword
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  data:
    redis:
      host: 10.0.0.1
      port: 6379
      password: YourRedisPassword
      database: 0
      timeout: 3000
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
          max-wait: -1ms
  session:
    store-type: redis
    timeout: 86400

dataease:
  apisix-api:
    domain: http://apisix:9180
    key: DE_APISIX_KEY
  export:
    views:
      limit: 100000
    dataset:
      limit: 100000
  origin-list: "http://10.0.0.1:9080"
  login_timeout: 960
  selenium-server: http://10.0.0.1:4444/wd/hub
  dataease-servers: "10.0.0.2:8100,10.0.0.3:8100"
  cache:
    type: redis

task:
  executor:
    address: http://sync-task-actuator:9001
    log:
      path: /opt/dataease2.0/logs/sync-task/task-handler-log

logging:
  file:
    path: /opt/dataease2.0/logs
```

> ⚠️ **方案A中，`/opt/dataease2.0/data/` 和 `/opt/dataease2.0/drivers/` 通过 NFS 挂载为共享存储**，所有节点读写同一份数据，确保文件上传后所有节点都能访问。`/opt/dataease2.0/logs/` 是各节点本地的独立目录。

#### 方案 B：同一台服务器（每个节点独立配置文件 + 不同端口）

**Node1 配置** `/opt/apps/node1/config/application.yml`：

```yaml
server:
  port: 8101    # ← 节点1使用 8101 端口
  tomcat:
    connection-timeout: 70000
  servlet:
    context-path:

spring:
  servlet:
    multipart:
      max-file-size: 500MB
      max-request-size: 500MB
  datasource:
    url: jdbc:mysql://10.0.0.1:3306/dataeasev201?autoReconnect=false&useUnicode=true&characterEncoding=UTF-8&characterSetResults=UTF-8&zeroDateTimeBehavior=convertToNull&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: YourMySQLPassword
    hikari:
      maximum-pool-size: 10    # ← 同机部署减小连接池，两个节点共享总连接
      minimum-idle: 3
  data:
    redis:
      host: 10.0.0.1
      port: 6379
      password: YourRedisPassword
      database: 0
      timeout: 3000
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
          max-wait: -1ms
  session:
    store-type: redis
    timeout: 86400

dataease:
  apisix-api:
    domain: http://apisix:9180
    key: DE_APISIX_KEY
  export:
    views:
      limit: 100000
    dataset:
      limit: 100000
  origin-list: "http://10.0.0.1:9080,http://localhost:8080"
  login_timeout: 960
  selenium-server: http://10.0.0.1:4444/wd/hub
  dataease-servers: "10.0.0.1:8101,10.0.0.1:8102"    # ← 列出所有节点
  cache:
    type: redis

task:
  executor:
    address: http://sync-task-actuator:9001
    log:
      path: /opt/dataease2.0/node1/logs/sync-task/task-handler-log    # ← 独立日志路径

logging:
  file:
    path: /opt/dataease2.0/node1/logs    # ← 独立日志路径

# 静态资源等文件路径（所有节点指向同一个共享目录）
dataease:
  path:
    static-resource: /opt/dataease2.0/data/static-resource/
    font: /opt/dataease2.0/data/font/
    exportData: /opt/dataease2.0/data/exportData/
    i18n: file:/opt/dataease2.0/data/i18n/custom

# EhCache 缓存目录不在此配置，而是通过启动命令的 JVM 参数设置：
#   -Ddataease.path.ehcache=/opt/dataease2.0/node1/cache
# 原因：ehcache.xml 的 ${...} 占位符只识别 JVM 系统属性，不识别 Spring yml 属性
```

> ⚠️ **重要**：
> - `dataease.path.*`（文件存储路径）所有节点必须指向**同一个共享目录**，确保上传的文件互通访问。
> - **EhCache 缓存目录必须每个节点不同**，通过 JVM 参数 `-Ddataease.path.ehcache=...` 设置（见启动命令）。
> - **原因**：`ehcache.xml` 中 `<persistence directory="${dataease.path.ehcache}" />` 使用的是 EhCache 3 原生的 `${...}` 占位符，它只识别 Java 系统属性（即 `-D` 参数），**不识别 Spring 的 yml 配置属性**。因此在 yml 里写 `dataease.path.ehcache` 是无效的。

**Node2 配置** `/opt/apps/node2/config/application.yml`：

```yaml
server:
  port: 8102    # ← 节点2使用 8102 端口
  tomcat:
    connection-timeout: 70000
  servlet:
    context-path:

spring:
  servlet:
    multipart:
      max-file-size: 500MB
      max-request-size: 500MB
  datasource:
    url: jdbc:mysql://10.0.0.1:3306/dataeasev201?autoReconnect=false&useUnicode=true&characterEncoding=UTF-8&characterSetResults=UTF-8&zeroDateTimeBehavior=convertToNull&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: YourMySQLPassword
    hikari:
      maximum-pool-size: 10
      minimum-idle: 3
  data:
    redis:
      host: 10.0.0.1
      port: 6379
      password: YourRedisPassword
      database: 0
      timeout: 3000
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
          max-wait: -1ms
  session:
    store-type: redis
    timeout: 86400

dataease:
  apisix-api:
    domain: http://apisix:9180
    key: DE_APISIX_KEY
  export:
    views:
      limit: 100000
    dataset:
      limit: 100000
  origin-list: "http://10.0.0.1:9080,http://localhost:8080"
  login_timeout: 960
  selenium-server: http://10.0.0.1:4444/wd/hub
  dataease-servers: "10.0.0.1:8101,10.0.0.1:8102"
  cache:
    type: redis

task:
  executor:
    address: http://sync-task-actuator:9001
    log:
      path: /opt/dataease2.0/node2/logs/sync-task/task-handler-log

logging:
  file:
    path: /opt/dataease2.0/node2/logs

# 静态资源等文件路径（与 Node1 指向同一个共享目录）
dataease:
  path:
    static-resource: /opt/dataease2.0/data/static-resource/
    font: /opt/dataease2.0/data/font/
    exportData: /opt/dataease2.0/data/exportData/
    i18n: file:/opt/dataease2.0/data/i18n/custom

# EhCache 缓存目录通过启动命令的 JVM 参数设置：
#   -Ddataease.path.ehcache=/opt/dataease2.0/node2/cache
```

> ⚠️ **同机部署差异总结：**
>
> | 配置项 | Node1 | Node2 | 说明 |
> |--------|-------|-------|------|
> | `server.port` | `8101` | `8102` | **必须不同**，否则端口冲突 |
> | `dataease-servers` | `10.0.0.1:8101,10.0.0.1:8102` | 同左 | 两个配置相同，列出所有节点 |
> | `logging.file.path` | `/opt/.../node1/logs` | `/opt/.../node2/logs` | 独立日志路径，避免文件锁冲突 |
> | `task.executor.log.path` | `/opt/.../node1/...` | `/opt/.../node2/...` | 独立任务日志 |
> | `spring.datasource.hikari.maximum-pool-size` | `10` | `10` | 减小连接池，防止总连接数过大 |
> | `spring.config.additional-location` | `/opt/apps/node1/config/` | `/opt/apps/node2/config/` | 各自独立的配置目录 |
> | **`-Ddataease.path.ehcache`** (JVM参数) | `/opt/.../node1/cache` | `/opt/.../node2/cache` | **必须不同**，否则 EhCache 文件锁冲突 |
> | **`dataease.path.*`** (文件存储) | `/opt/dataease2.0/data/...` | **完全相同** | **必须相同**，确保上传的文件所有节点都能访问 |
>
> ⚠️ **EhCache 文件锁问题（Linux/Windows 都会出现）**：DataEase 内部使用 EhCache 做本地缓存。`ehcache.xml` 中 `<persistence directory="${dataease.path.ehcache}" />` 定义了持久化目录。
>
> **关键发现**：EhCache 3 的 `${...}` 占位符只识别 **Java 系统属性**（即 `-D` JVM 参数），**不识别 Spring Boot 的 yml 配置属性**。因此在 yml 里配置 `dataease.path.ehcache` 是无效的，必须在启动命令中通过 `-Ddataease.path.ehcache=...` 设置。
>
> 同机部署时两个节点不能共用同一个 cache 目录，否则第二个节点启动报错：
> ```
> Persistence directory already locked by another process: D:\opt\dataease2.0\cache
> ```
> **解决**：在每个节点的启动命令中添加不同的 `-Ddataease.path.ehcache=...` 参数。

> **关键配置说明：**
> - `dataease-servers`: 列出所有后端节点的 IP:端口，用逗号分隔。这个配置用于节点间通信。
> - `origin-list`: 前端访问的入口地址，用于 CORS 跨域配置。
> - `session.store-type: redis`: 使用 Redis 共享 Session，确保多节点间 Session 一致。
> - `cache.type: redis`: 使用 Redis 作为共享缓存，确保各节点缓存数据一致。

### 4.3 启动后端节点

#### 方案 A：多台服务器

```bash
# Node1 (10.0.0.2)
java -jar /opt/apps/app.jar \
  -Dfile.encoding=utf-8 \
  -Dloader.path=/opt/apps \
  -Ddataease.path.ehcache=/opt/dataease2.0/node1/cache \
  -Dspring.config.additional-location=/opt/apps/config/ \
  --server.port=8100

# Node2 (10.0.0.3)
java -jar /opt/apps/app.jar \
  -Dfile.encoding=utf-8 \
  -Dloader.path=/opt/apps \
  -Ddataease.path.ehcache=/opt/dataease2.0/node2/cache \
  -Dspring.config.additional-location=/opt/apps/config/ \
  --server.port=8100
```

#### 方案 B：同一台服务器

> 同一台服务器只需一个 Jar 包，通过不同端口和不同配置目录启动两个进程：

```bash
# Node1 (端口 8101)
java -jar /opt/apps/app.jar \
  -Dfile.encoding=utf-8 \
  -Dloader.path=/opt/apps \
  -Ddataease.path.ehcache=/opt/dataease2.0/node1/cache \
  -Dspring.config.additional-location=/opt/apps/node1/config/ \
  --server.port=8101

# Node2 (端口 8102) —— 另一个终端或后台运行
java -jar /opt/apps/app.jar \
  -Dfile.encoding=utf-8 \
  -Dloader.path=/opt/apps \
  -Ddataease.path.ehcache=/opt/dataease2.0/node2/cache \
  -Dspring.config.additional-location=/opt/apps/node2/config/ \
  --server.port=8102
```

### 4.4 创建 systemd 服务（可选，Linux 生产环境推荐）

> **systemd 服务不是必须的。** 它只是让进程在后台运行、开机自启、崩溃自动重启。如果只是开发/测试验证，直接用 `java -jar` 前台运行即可，无需配置此步骤。

#### 方案 A：多台服务器（每个节点一个服务）

```bash
sudo tee /etc/systemd/system/dataease.service << 'EOF'
[Unit]
Description=DataEase Backend Service
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/apps
ExecStart=/usr/bin/java -jar /opt/apps/app.jar \
  -Dfile.encoding=utf-8 \
  -Dloader.path=/opt/apps \
  -Ddataease.path.ehcache=/opt/dataease2.0/node1/cache \
  -Dspring.config.additional-location=/opt/apps/config/
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable dataease
sudo systemctl start dataease
```

#### 方案 B：同一台服务器（两个独立 service）

**Node1 服务** `/etc/systemd/system/dataease-node1.service`：

```bash
sudo tee /etc/systemd/system/dataease-node1.service << 'EOF'
[Unit]
Description=DataEase Backend Node1 (Port 8101)
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/apps
ExecStart=/usr/bin/java -jar /opt/apps/app.jar \
  -Dfile.encoding=utf-8 \
  -Dloader.path=/opt/apps \
  -Ddataease.path.ehcache=/opt/dataease2.0/node1/cache \
  -Dspring.config.additional-location=/opt/apps/node1/config/ \
  --server.port=8101
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF
```

**Node2 服务** `/etc/systemd/system/dataease-node2.service`：

```bash
sudo tee /etc/systemd/system/dataease-node2.service << 'EOF'
[Unit]
Description=DataEase Backend Node2 (Port 8102)
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/apps
ExecStart=/usr/bin/java -jar /opt/apps/app.jar \
  -Dfile.encoding=utf-8 \
  -Dloader.path=/opt/apps \
  -Ddataease.path.ehcache=/opt/dataease2.0/node2/cache \
  -Dspring.config.additional-location=/opt/apps/node2/config/ \
  --server.port=8102
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF
```

启动两个服务：

```bash
sudo systemctl daemon-reload
sudo systemctl enable dataease-node1 dataease-node2
sudo systemctl start dataease-node1 dataease-node2

# 分别查看状态
sudo systemctl status dataease-node1
sudo systemctl status dataease-node2

# 分别查看日志
sudo journalctl -u dataease-node1 -f
sudo journalctl -u dataease-node2 -f
```

### 4.5 验证后端启动

```bash
# 方案 A：检查不同服务器的节点
curl http://10.0.0.2:8100/de2api/health
curl http://10.0.0.3:8100/de2api/health

# 方案 B：检查同服务器不同端口的节点
curl http://10.0.0.1:8101/de2api/health
curl http://10.0.0.1:8102/de2api/health
```

---

## 五、Nginx 负载均衡配置

### 5.1 安装 Nginx

```bash
# CentOS/RHEL
sudo yum install -y nginx

# Ubuntu/Debian
sudo apt install -y nginx
```

### 5.2 配置 Nginx

#### 方案 A：多台服务器

编辑 `/etc/nginx/conf.d/dataease.conf`：

```nginx
upstream dataease_backend {
    least_conn;
    
    server 10.0.0.2:8100 weight=1 max_fails=3 fail_timeout=30s;
    server 10.0.0.3:8100 weight=1 max_fails=3 fail_timeout=30s;
    
    keepalive 32;
}

server {
    listen 9080;
    server_name your-domain.com;
    
    access_log /var/log/nginx/dataease_access.log;
    error_log /var/log/nginx/dataease_error.log;
    
    client_max_body_size 500M;
    client_body_timeout 120s;
    
    proxy_connect_timeout 60s;
    proxy_send_timeout 120s;
    proxy_read_timeout 120s;
    
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    
    location /de2api/ {
        proxy_pass http://dataease_backend/de2api/;
    }
    
    location /api/ {
        proxy_pass http://dataease_backend/;
    }
}
```

#### 方案 B：同一台服务器

编辑 `/etc/nginx/conf.d/dataease.conf`：

```nginx
upstream dataease_backend {
    # 同机部署：指向 localhost 的不同端口
    least_conn;
    
    server 127.0.0.1:8101 weight=1 max_fails=3 fail_timeout=30s;
    server 127.0.0.1:8102 weight=1 max_fails=3 fail_timeout=30s;
    
    keepalive 32;
}

server {
    listen 9080;
    server_name your-domain.com;
    
    access_log /var/log/nginx/dataease_access.log;
    error_log /var/log/nginx/dataease_error.log;
    
    client_max_body_size 500M;
    client_body_timeout 120s;
    
    proxy_connect_timeout 60s;
    proxy_send_timeout 120s;
    proxy_read_timeout 120s;
    
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    
    location /de2api/ {
        proxy_pass http://dataease_backend/de2api/;
    }
    
    location /api/ {
        proxy_pass http://dataease_backend/;
    }
}
```

> ⚠️ **同机部署 Nginx upstream 要点**：所有 `server` 指向 `127.0.0.1` 的不同端口（8101、8102），而不是相同的 8100 端口。

### 5.3 启动 Nginx

```bash
# 测试配置
sudo nginx -t

# 重载配置
sudo nginx -s reload

# 或直接启动
sudo systemctl start nginx
sudo systemctl enable nginx
```

---

## 六、前端开发环境配置

### 6.1 安装依赖

```bash
cd d:/work/xinjiang/dataease-2.10.16_xj/core/core-frontend

# 安装依赖
npm install
```

### 6.2 修改 Vite 代理配置

编辑 `core/core-frontend/config/dev.ts`，将代理目标指向 Nginx 负载均衡地址：

```typescript
export default {
  server: {
    proxy: {
      '/api/f': {
        target: 'http://10.0.0.1:9080',  // ← 改为 Nginx 地址
        changeOrigin: true,
        rewrite: path => path.replace(/^\/api\/f/, '')
      },
      '/api': {
        target: 'http://10.0.0.1:9080',  // ← 改为 Nginx 地址
        changeOrigin: true,
        rewrite: path => path.replace(/^\/api/, 'de2api')
      }
    },
    port: 8080
  }
}
```

> **说明：** 前端 dev 模式下的请求路径 `/api/xxx` 会被 Vite 代理到 Nginx `9080` 端口，Nginx 再将其负载均衡到后端多个节点。这样前端只需一个入口地址即可访问多节点后端。

### 6.3 启动前端开发服务器

```bash
cd core/core-frontend
npm run dev
```

启动后访问：`http://localhost:8080`

默认登录账号：`admin` / `DataEase@123456`

---

## 七、多节点部署验证

### 7.1 检查各节点是否正常

```bash
# 检查 Nginx 负载均衡是否工作
curl http://10.0.0.1:9080/de2api/health

# 多次请求，观察是否分发到不同节点
for i in {1..10}; do
  curl -s http://10.0.0.1:9080/de2api/health
  echo ""
done
```

### 7.2 验证 Session 共享

1. 在浏览器中访问 `http://localhost:8080`，登录系统
2. 停掉其中一个后端节点
3. 刷新页面，检查是否仍然保持登录状态
4. 如果登录状态保持，说明 Redis Session 共享生效

### 7.3 检查节点间通信

在后端日志中确认各节点能够互相发现：

```bash
# 查看日志
tail -f /opt/dataease2.0/logs/dataease/*.log | grep -i "server\|node\|cluster"
```

---

## 八、定时任务处理（重要）

多节点部署时，定时任务（如定时报告）默认会在所有节点上同时执行，需要处理：

### 方案一：使用 Quartz 分布式锁（推荐）

DataEase 使用 Quartz 做定时任务，配置 Quartz 使用数据库锁：

在 `application.yml` 中添加：

```yaml
spring:
  quartz:
    job-store-type: jdbc
    jdbc:
      initialize-schema: never
    properties:
      org:
        quartz:
          jobStore:
            isClustered: true
            clusterCheckinInterval: 20000
            driverDelegateClass: org.quartz.impl.jdbcjobstore.StdJDBCDelegate
          scheduler:
            instanceId: AUTO
```

### 方案二：只在指定节点执行

如果不需要定时任务高可用，可在非主节点上禁用定时任务：

```yaml
# 非主节点的 application.yml
spring:
  quartz:
    auto-startup: false
```

---

## 九、常见问题排查

### 9.1 后端启动失败

```bash
# 检查 Java 版本
java -version  # 需要 JDK 21+

# 检查数据库连接
mysql -h 10.0.0.1 -u root -p -e "SELECT 1"

# 检查 Redis 连接
redis-cli -h 10.0.0.1 -a YourRedisPassword ping

# 查看完整启动日志
journalctl -u dataease -f
```

### 9.2 前端代理不生效

```bash
# 确认 Nginx 端口可达
curl http://10.0.0.1:9080/de2api/health

# 检查 Vite 代理日志
# npm run dev 启动时，终端会显示代理请求日志
```

### 9.3 Session 丢失 / 频繁登出

- 检查 Redis 是否正常运行，所有节点是否连接同一个 Redis
- 检查 `spring.session.store-type` 是否设为 `redis`
- 检查 `dataease.cache.type` 是否设为 `redis`

### 9.4 CORS 跨域问题

确保 `application.yml` 中 `dataease.origin-list` 配置了前端访问的完整地址：

```yaml
dataease:
  origin-list: "http://10.0.0.1:9080,http://localhost:8080"
```

---

## 十、生产环境建议

### 10.1 JVM 参数优化

```bash
java -jar /opt/apps/app.jar \
  -Xms2g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/opt/dataease2.0/logs/ \
  -Dfile.encoding=utf-8 \
  -Dloader.path=/opt/apps \
  -Ddataease.path.ehcache=/opt/dataease2.0/node1/cache \
  -Dspring.config.additional-location=/opt/apps/config/
```

### 10.2 数据库连接池优化

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 30        # 根据节点数调整
      minimum-idle: 10
      idle-timeout: 300000
      max-lifetime: 1200000
      connection-timeout: 30000
```

### 10.3 日志配置

建议将每个节点的日志输出到不同目录或使用日志收集系统（如 ELK）：

```yaml
logging:
  file:
    path: /opt/dataease2.0/logs/node1
```

### 10.4 监控与健康检查

```bash
# 添加健康检查脚本
*/1 * * * * curl -f http://127.0.0.1:8100/de2api/health || systemctl restart dataease
```

---

## 附录：快速部署命令汇总

### 方案 A：多台服务器

```bash
# ===== 1. 构建后端 =====
cd d:/work/xinjiang/dataease-2.10.16_xj
mvn clean package -Pdistributed -DskipTests

# ===== 2. 部署到各节点 =====
scp core/core-backend/target/CoreApplication.jar root@10.0.0.2:/opt/apps/app.jar
scp core/core-backend/target/CoreApplication.jar root@10.0.0.3:/opt/apps/app.jar

# ===== 3. 在各节点启动 =====
ssh root@10.0.0.2 "systemctl restart dataease"
ssh root@10.0.0.3 "systemctl restart dataease"

# ===== 4. 重载 Nginx =====
ssh root@10.0.0.1 "nginx -s reload"

# ===== 5. 启动前端 =====
cd core/core-frontend
npm run dev
```

### 方案 B：同一台服务器

```bash
# ===== 1. 构建后端 =====
cd d:/work/xinjiang/dataease-2.10.16_xj
mvn clean package -Pdistributed -DskipTests

# ===== 2. 部署 Jar 包（只需一个） =====
cp core/core-backend/target/CoreApplication.jar /opt/apps/app.jar

# ===== 3. 创建目录结构 =====
# 共享目录（所有节点共用）
mkdir -p /opt/dataease2.0/{drivers,data/{map,static-resource,appearance,exportData,excel,i18n,plugin,geo,font}}
# 独立目录
mkdir -p /opt/dataease2.0/node1/{cache,logs}
mkdir -p /opt/dataease2.0/node2/{cache,logs}
mkdir -p /opt/apps/node1/config /opt/apps/node2/config

# ===== 4. 分别放置配置文件 =====
# 将 node1 的 application.yml 放到 /opt/apps/node1/config/
# 将 node2 的 application.yml 放到 /opt/apps/node2/config/

# ===== 5. 启动两个节点 =====
systemctl start dataease-node1 dataease-node2

# ===== 6. 重载 Nginx =====
nginx -s reload

# ===== 7. 启动前端 =====
cd core/core-frontend
npm run dev

# ===== 8. 验证 =====
curl http://127.0.0.1:8101/de2api/health
curl http://127.0.0.1:8102/de2api/health
curl http://127.0.0.1:9080/de2api/health
```

---

## 附录 B：Windows 环境下多节点验证步骤

> 适用于在 Windows 开发机上同时启动多个后端 Jar 节点 + 前端 `npm run dev` 进行本地验证。

### B.1 前置条件

| 软件 | 说明 |
|------|------|
| JDK 21+ | 确保 `java -version` 正常 |
| Node.js 18+ | 确保 `node -v` 正常 |
| Maven 3.8+ | 确保 `mvn -v` 正常 |
| MySQL | 可连到远程 MySQL，或本地安装 |
| Redis | 可连到远程 Redis，或本地安装（[Memurai](https://www.memurai.com/) / WSL Redis） |
| Nginx | Windows 版 Nginx，或跳过直接用 Vite 代理到不同端口轮询 |

### B.2 目录规划

在项目目录下创建本地运行目录：

```powershell
# 在项目根目录执行（PowerShell）
cd d:\work\xinjiang\dataease-2.10.16_xj

# ===== 共享目录（所有节点共用，确保上传文件可互通访问）=====
mkdir -p local-deploy\shared\data\static-resource
mkdir -p local-deploy\shared\data\appearance
mkdir -p local-deploy\shared\data\exportData
mkdir -p local-deploy\shared\data\font
mkdir -p local-deploy\shared\data\plugin
mkdir -p local-deploy\shared\data\geo
mkdir -p local-deploy\shared\data\map
mkdir -p local-deploy\shared\data\i18n
mkdir -p local-deploy\shared\drivers

# ===== Node1 独立目录 =====
mkdir -p local-deploy\node1\config
mkdir -p local-deploy\node1\logs
mkdir -p local-deploy\node1\cache

# ===== Node2 独立目录 =====
mkdir -p local-deploy\node2\config
mkdir -p local-deploy\node2\logs
mkdir -p local-deploy\node2\cache
```

> 📁 **Windows 目录结构：**
> ```
> local-deploy/
> ├── shared/                  ← 所有节点共享
> │   ├── data/
> │   │   ├── static-resource/  ← 图片上传
> │   │   ├── appearance/       ← 外观设置
> │   │   ├── exportData/       ← 导出文件
> │   │   ├── font/             ← 字体
> │   │   ├── plugin/           ← 插件
> │   │   ├── geo/              ← 自定义地图
> │   │   ├── map/              ← 地图
> │   │   └── i18n/             ← 国际化
> │   └── drivers/              ← 数据库驱动
> ├── node1/                   ← Node1 独占
> │   ├── config/
> │   ├── logs/
> │   └── cache/               ← EhCache
> └── node2/                   ← Node2 独占
>     ├── config/
>     ├── logs/
>     └── cache/
> ```

### B.3 构建 Jar 包

```powershell
cd d:\work\xinjiang\dataease-2.10.16_xj

# 分布式模式构建
mvn clean package -Pdistributed -DskipTests
```

构建产物：`core\core-backend\target\CoreApplication.jar`

### B.4 准备配置文件

#### Node1 配置 `local-deploy\node1\config\application.yml`

```yaml
server:
  port: 8101
spring:
  datasource:
    url: jdbc:mysql://10.0.0.1:3306/dataeasev201?autoReconnect=false&useUnicode=true&characterEncoding=UTF-8&characterSetResults=UTF-8&zeroDateTimeBehavior=convertToNull&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: YourMySQLPassword
    hikari:
      maximum-pool-size: 10
      minimum-idle: 3
  data:
    redis:
      host: 10.0.0.1
      port: 6379
      password: YourRedisPassword
      database: 0
  session:
    store-type: redis
    timeout: 86400
  cache:
    jcache:
      config: classpath:ehcache.xml
    type: jcache

dataease:
  origin-list: "http://localhost:8080"
  login_timeout: 960
  dataease-servers: "127.0.0.1:8101,127.0.0.1:8102"
  cache:
    type: redis
  path:
    static-resource: d:/work/xinjiang/dataease-2.10.16_xj/local-deploy/shared/data/static-resource/
    font: d:/work/xinjiang/dataease-2.10.16_xj/local-deploy/shared/data/font/
    exportData: d:/work/xinjiang/dataease-2.10.16_xj/local-deploy/shared/data/exportData/
    i18n: file:d:/work/xinjiang/dataease-2.10.16_xj/local-deploy/shared/data/i18n/custom
    # EhCache 缓存目录通过 JVM 参数 -Ddataease.path.ehcache=... 设置，yml 中配置无效

logging:
  file:
    path: d:/work/xinjiang/dataease-2.10.16_xj/local-deploy/node1/logs
```

> ⚠️ 注意区分：
> - `dataease.path.*`（文件存储）→ 指向 `shared/` 共享目录
> - **EhCache 缓存目录** → 不在 yml 中配置，通过 JVM 参数 `-Ddataease.path.ehcache=...` 设置（见启动命令）

#### Node2 配置 `local-deploy\node2\config\application.yml`

```yaml
server:
  port: 8102
spring:
  datasource:
    url: jdbc:mysql://10.0.0.1:3306/dataeasev201?autoReconnect=false&useUnicode=true&characterEncoding=UTF-8&characterSetResults=UTF-8&zeroDateTimeBehavior=convertToNull&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: YourMySQLPassword
    hikari:
      maximum-pool-size: 10
      minimum-idle: 3
  data:
    redis:
      host: 10.0.0.1
      port: 6379
      password: YourRedisPassword
      database: 0
  session:
    store-type: redis
    timeout: 86400
  cache:
    jcache:
      config: classpath:ehcache.xml
    type: jcache

dataease:
  origin-list: "http://localhost:8080"
  login_timeout: 960
  dataease-servers: "127.0.0.1:8101,127.0.0.1:8102"
  cache:
    type: redis
  path:
    static-resource: d:/work/xinjiang/dataease-2.10.16_xj/local-deploy/shared/data/static-resource/
    font: d:/work/xinjiang/dataease-2.10.16_xj/local-deploy/shared/data/font/
    exportData: d:/work/xinjiang/dataease-2.10.16_xj/local-deploy/shared/data/exportData/
    i18n: file:d:/work/xinjiang/dataease-2.10.16_xj/local-deploy/shared/data/i18n/custom
    # EhCache 缓存目录通过 JVM 参数 -Ddataease.path.ehcache=... 设置，yml 中配置无效

logging:
  file:
    path: d:/work/xinjiang/dataease-2.10.16_xj/local-deploy/node2/logs
```

> ⚠️ **关键**：`ehcache.xml` 中 `<persistence directory="${dataease.path.ehcache}" />` 使用的是 EhCache 3 原生 `${...}` 占位符，它**只识别 Java 系统属性**（`-D` 参数），**不识别 Spring yml 配置**。因此必须在启动命令中通过 `-Ddataease.path.ehcache=...` 设置，两个节点必须不同，否则第二个节点启动时报 `Persistence directory already locked`。

### B.5 启动后端节点（两个 PowerShell 窗口）

#### 窗口 1 — Node1（端口 8101）

```powershell
cd d:\work\xinjiang\dataease-2.10.16_xj

java -jar core\core-backend\target\CoreApplication.jar `
  -Dfile.encoding=utf-8 `
  -Dloader.path=. `
  -Ddataease.path.ehcache=d:/work/xinjiang/dataease-2.10.16_xj/local-deploy/node1/cache `
  -Dspring.config.additional-location=file:./local-deploy/node1/config/ `
  --server.port=8101
```

#### 窗口 2 — Node2（端口 8102）

```powershell
cd d:\work\xinjiang\dataease-2.10.16_xj

java -jar core\core-backend\target\CoreApplication.jar `
  -Dfile.encoding=utf-8 `
  -Dloader.path=. `
  -Ddataease.path.ehcache=d:/work/xinjiang/dataease-2.10.16_xj/local-deploy/node2/cache `
  -Dspring.config.additional-location=file:./local-deploy/node2/config/ `
  --server.port=8102
```

> ⚠️ **`-Ddataease.path.ehcache` 是解决同机多节点启动失败的关键参数。**
>
> 原因：`ehcache.xml` 中 `<persistence directory="${dataease.path.ehcache}" />` 定义了 EhCache 持久化目录。EhCache 3 的 `${...}` 占位符只识别 JVM 系统属性（`-D` 参数），yml 配置对其无效。如果两个节点共用同一个目录，第二个节点启动时会报错：
> ```
> Persistence directory already locked by another process: D:\opt\dataease2.0\cache
> ```
> 解决：在启动命令中通过 `-Ddataease.path.ehcache=...` 设置不同的路径。

> 启动后等待日志输出 `Started CoreApplication` 即表示启动成功。

### B.6 配置前端代理（两种方式二选一）

#### 方式一：Vite 直接轮询后端节点（无需 Nginx）

修改 `core\core-frontend\config\dev.ts`：

```typescript
export default {
  server: {
    proxy: {
      '/api/f': {
        target: 'http://127.0.0.1:8101',  // 固定指向 Node1
        changeOrigin: true,
        rewrite: path => path.replace(/^\/api\/f/, '')
      },
      '/api': {
        // 可改为指向 Node2 测试不同节点
        target: 'http://127.0.0.1:8101',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/api/, 'de2api')
      }
    },
    port: 8080
  }
}
```

#### 方式二：使用 Windows 版 Nginx 做负载均衡（更接近生产）

1. 下载 [Nginx for Windows](http://nginx.org/en/download.html)，解压到 `C:\nginx\`
2. 编辑 `C:\nginx\conf\nginx.conf`，在 `http` 块中添加：

```nginx
upstream dataease_backend {
    least_conn;
    server 127.0.0.1:8101;
    server 127.0.0.1:8102;
}

server {
    listen 9080;
    
    client_max_body_size 500M;
    
    location /de2api/ {
        proxy_pass http://dataease_backend/de2api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
    
    location /api/ {
        proxy_pass http://dataease_backend/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

3. 启动 Nginx：

```powershell
cd C:\nginx
start nginx

# 重载配置
nginx -s reload

# 停止
nginx -s stop
```

4. 修改 Vite 代理指向 Nginx：

```typescript
'/api': {
    target: 'http://127.0.0.1:9080',  // 指向 Nginx
    changeOrigin: true,
    rewrite: path => path.replace(/^\/api/, 'de2api')
}
```

### B.7 启动前端

```powershell
cd d:\work\xinjiang\dataease-2.10.16_xj\core\core-frontend

npm install   # 首次需要
npm run dev
```

访问：`http://localhost:8080`

默认登录：`admin` / `DataEase@123456`

### B.8 Windows 验证步骤

#### 1. 验证节点独立启动

```powershell
# PowerShell 中执行
Invoke-WebRequest -Uri http://127.0.0.1:8101/de2api/health | Select-Object StatusCode,Content
Invoke-WebRequest -Uri http://127.0.0.1:8102/de2api/health | Select-Object StatusCode,Content
```

两个都应返回 200。

#### 2. 验证 Nginx 负载均衡（如使用方式二）

```powershell
# 多次请求 Nginx，观察后端两个节点的日志是否都有请求
1..10 | ForEach-Object {
    Invoke-WebRequest -Uri http://127.0.0.1:9080/de2api/health | Select-Object StatusCode
}
```

#### 3. 验证单节点故障不影响服务

```powershell
# 1. 在浏览器登录 http://localhost:8080
# 2. 关掉 Node1 的 PowerShell 窗口（模拟节点宕机）
# 3. 刷新浏览器页面，检查是否能正常操作（请求应自动路由到 Node2）
# 4. 重新启动 Node1，确认服务恢复
```

#### 4. 验证端口占用情况

```powershell
# 查看端口占用
netstat -ano | findstr "8101"
netstat -ano | findstr "8102"
netstat -ano | findstr "8080"
netstat -ano | findstr "9080"
```

### B.9 Windows 一键启动脚本

创建 `local-deploy\start-all.bat`：

```bat
@echo off
echo ===== 启动 DataEase 多节点（Windows 本地验证）=====

REM 先创建共享目录（如果不存在）
mkdir local-deploy\shared\data\static-resource 2>nul
mkdir local-deploy\shared\data\appearance 2>nul
mkdir local-deploy\shared\data\exportData 2>nul
mkdir local-deploy\shared\data\font 2>nul
mkdir local-deploy\shared\data\plugin 2>nul
mkdir local-deploy\shared\data\geo 2>nul
mkdir local-deploy\shared\data\map 2>nul
mkdir local-deploy\shared\data\i18n 2>nul

echo [1/3] 启动后端 Node1 (端口 8101)...
start "DataEase-Node1" cmd /k "cd /d d:\work\xinjiang\dataease-2.10.16_xj && java -jar core\core-backend\target\CoreApplication.jar -Dfile.encoding=utf-8 -Ddataease.path.ehcache=d:/work/xinjiang/dataease-2.10.16_xj/local-deploy/node1/cache -Dspring.config.additional-location=file:./local-deploy/node1/config/ --server.port=8101"

echo [2/3] 启动后端 Node2 (端口 8102)...
start "DataEase-Node2" cmd /k "cd /d d:\work\xinjiang\dataease-2.10.16_xj && java -jar core\core-backend\target\CoreApplication.jar -Dfile.encoding=utf-8 -Ddataease.path.ehcache=d:/work/xinjiang/dataease-2.10.16_xj/local-deploy/node2/cache -Dspring.config.additional-location=file:./local-deploy/node2/config/ --server.port=8102"

echo [3/3] 等待后端启动后，启动前端...
timeout /t 30 /nobreak
start "DataEase-Frontend" cmd /k "cd /d d:\work\xinjiang\dataease-2.10.16_xj\core\core-frontend && npm run dev"

echo ===== 全部启动完成 =====
echo 后端 Node1: http://127.0.0.1:8101
echo 后端 Node2: http://127.0.0.1:8102
echo 前端页面:   http://localhost:8080
echo.
echo 按任意键退出...
pause >nul
```

创建 `local-deploy\stop-all.bat`：

```bat
@echo off
echo 停止所有 DataEase 进程...

taskkill /f /fi "WINDOWTITLE eq DataEase-Node1*"
taskkill /f /fi "WINDOWTITLE eq DataEase-Node2*"
taskkill /f /fi "WINDOWTITLE eq DataEase-Frontend*"

echo 已停止所有进程
pause
```

### B.10 文件上传共享存储说明

> 这是多节点部署中最容易踩的坑。

#### 为什么文件存储必须共享？

DataEase 的文件读写流程：

```
用户浏览器 → Nginx → (负载均衡) → Node1 或 Node2
                                    ↓
                              本地文件系统
```

- **上传场景**：用户在 Node1 上传图片 → 文件存到 Node1 的 `data/static-resource/` → 下次请求查看这张图片时，负载均衡可能路由到 Node2 → Node2 的 `data/static-resource/` 下没有这个文件 → **404 找不到**
- **导出场景**：定时任务在 Node1 生成导出文件 → 存到 Node1 的 `data/exportData/` → 用户点击下载，请求被路由到 Node2 → Node2 找不到文件 → **下载失败**

#### 需要共享的目录

| 目录 | 存储内容 | 不共享的后果 |
|------|---------|-------------|
| `data/static-resource/` | 仪表板中上传的图片 | 图片 404 |
| `data/appearance/` | 自定义外观/Logo | 外观不显示 |
| `data/exportData/` | 报表导出文件 | 下载失败 |
| `data/font/` | 上传的自定义字体 | 字体不生效 |
| `data/plugin/` | 安装的插件 | 插件不可用 |
| `data/geo/` | 自定义地图数据 | 地图不显示 |
| `data/map/` | 地图基础数据 | 地图功能异常 |
| `data/i18n/` | 自定义国际化文件 | 翻译不生效 |
| `drivers/` | 数据库驱动 jar | 数据源连接失败 |

#### 不需要共享的目录

| 目录 | 原因 |
|------|------|
| `nodeX/logs/` | 日志，每个节点独立记录 |
| `nodeX/cache/` | EhCache 本地缓存，文件锁冲突 |

#### Windows 验证建议

在 Windows 本地验证时，建议使用以下方式之一解决共享存储问题：

1. **直接共用目录**（最简单）：所有节点的 `dataease.path.*` 指向同一个 `local-deploy/shared/data/` 目录
2. **使用 Nginx 做静态资源代理**（更接近生产）：将静态资源请求由 Nginx 统一处理，后端只负责 API

### B.11 Windows 常见问题

| 问题 | 解决方法 |
|------|---------|
| `java` 不是内部命令 | 安装 JDK 21+，配置 `JAVA_HOME` 和 `PATH` 环境变量 |
| 端口 8101 被占用 | `netstat -ano \| findstr 8101` 找到 PID，`taskkill /pid <PID> /f` 杀掉 |
| 前端 `npm install` 报错 | 删除 `node_modules` 和 `package-lock.json`，重新 `npm install` |
| 后端启动报数据库连接失败 | 检查 MySQL 是否允许远程连接，`application.yml` 中数据库配置是否正确 |
| 后端启动报 Redis 连接失败 | 检查 Redis 是否启动，密码是否正确 |
| PowerShell 换行符报错 | 使用 cmd 或确保 PowerShell 中反引号 `` ` `` 正确（不要用 `\`） |
| Vite 代理不生效 | 检查 `dev.ts` 中 target 地址是否正确，确认后端已启动 |
| **第二个节点启动报错：`Persistence directory already locked`** | **EhCache 文件锁冲突**。在启动命令中添加 `-Ddataease.path.ehcache=<各自独立路径>` 参数。注意 yml 中配置无效，因为 EhCache 3 的 `${...}` 占位符只识别 JVM 系统属性 |
| **上传图片后另一个节点访问不到** | **文件存储目录未共享**。确保所有节点的 `dataease.path.static-resource` 指向同一个 `shared/data/` 目录 |
| **导出文件下载失败** | **文件存储目录未共享**。确保 `dataease.path.exportData` 指向共享目录 |
