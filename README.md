# 轻量局域网聊天室 LanChat

一个基于Spring Boot + WebSocket的简单聊天室应用，支持实时消息发送、文件上传和消息撤回功能。
*AIGC，本项目99%部分的代码由AI完成，包括自述文件*

## 快速开始

### 1. 运行应用
**需要Java8**
```bash
java -jar LanChat.jar
```

### 2. 访问聊天室

打开浏览器访问：`http://localhost:8078`


## 功能特性

- ✅ **无需登录**：用户以IP地址作为身份标识
- ✅ **实时聊天**：基于WebSocket的实时消息传输
- ✅ **文件分享**：支持文件上传下载（最大10MB）
- ✅ **消息撤回**：可以撤回自己发送的消息
- ✅ **历史记录**：聊天记录永久保存，支持流式加载
- ✅ **离线运行**：完全本地化，无外部依赖
- ✅ **响应式设计**：支持桌面和移动设备

## 技术栈

- **后端**：Spring Boot 2.7.6 + WebSocket + JPA + H2 Database
- **前端**：Thymeleaf + 原生JavaScript + CSS
- **数据库**：H2内存数据库（文件持久化）
- **构建工具**：Maven

## 系统要求

- Java 8 或更高版本
- Maven 3.6 或更高版本

## 项目结构

```
LanChat/
├── src/main/java/cn/yaklo/lanchat/
│   ├── config/                      # 配置类
│   │   ├── NativeWebSocketConfig.java
│   │   └── WebSocketConfig.java
│   ├── controller/                  # 控制器
│   │   ├── ChatController.java
│   │   ├── FileController.java
│   │   ├── WebSocketController.java
│   │   └── WebSocketNativeController.java
│   ├── demos/                       # 演示代码
│   │   └── web/
│   │       ├── BasicController.java
│   │       ├── PathVariableController.java
│   │       └── User.java
│   ├── dto/                         # 数据传输对象
│   │   └── ChatMessageDto.java
│   ├── entity/                      # 实体类
│   │   ├── ChatFile.java
│   │   └── ChatMessage.java
│   ├── interceptor/                 # 拦截器
│   │   └── WebSocketHandshakeInterceptor.java
│   ├── repository/                  # 数据访问层
│   │   ├── ChatFileRepository.java
│   │   └── ChatMessageRepository.java
│   ├── service/                     # 服务层
│   │   ├── ChatService.java
│   │   ├── FileService.java
│   │   └── UserService.java
│   ├── util/                        # 工具类
│   │   └── IpUtil.java
│   └── LanChatApplication.java      # 启动类
├── src/main/resources/
│   ├── application.properties       # 配置文件
│   ├── static/                      # 静态资源
│   │   ├── index.html
│   │   └── test.html
│   └── templates/                   # 页面模板
│       └── chat.html                # 聊天室页面
├── data/                            # 运行时数据目录
│   ├── db/                          # 数据库文件
│   └── upfile/                      # 上传文件
└── pom.xml                          # Maven配置
```

## 配置说明

### 端口配置
默认端口为8078，可在`application.properties`中修改：

```properties
server.port=8078
```

### 消息限制
- 单条消息限制：128KB

### 文件上传配置
- 最大文件大小：100MB
- 上传目录：`./data/upfile`

### 数据库配置
- 数据库类型：H2
- 数据文件位置：`./data/db/chatdb`
- Web控制台：`http://localhost:8078/h2-console`

## 注意事项

1. **离线环境**：本项目无外部CDN依赖，所有资源内联，适合离线环境运行
2. **数据持久化**：聊天记录和上传文件均保存在本地`./data`目录下
3. **网络安全**：本项目设计为局域网使用，如需公网部署请添加安全认证
4. **浏览器兼容性**：支持现代浏览器的WebSocket功能
5. **文件安全**：上传文件仅做基础验证，生产环境建议增加安全检查

## 常见问题

### Q: 如何修改用户名显示？
A: 修改`UserService.java`中的`formatUserName`方法。

### Q: 如何调整消息加载数量？
A: 修改`ChatController.java`中的默认size参数。

### Q: 如何更改文件上传大小限制？
A: 修改`application.properties`中的`spring.servlet.multipart.max-file-size`。

### Q: 数据库密码是什么？
A: 用户名：san，密码：空（可在application.properties中修改）
