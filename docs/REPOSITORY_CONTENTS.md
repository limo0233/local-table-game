# 仓库内容说明

本仓库保存可持续维护和重建项目所需的源码、配置、测试与开发资料。

## 已纳入版本控制

- Android 宿主应用与本地 HTTP/WebSocket 服务源码；
- `server-core` 房间系统、各游戏状态机及自动化测试；
- React/TypeScript Web 客户端源码与依赖锁文件；
- Gradle Wrapper、构建配置和 Web 资源同步脚本；
- 当前 APK 使用的已构建离线 Web 资源；
- 架构、协议、开发交接与新增游戏开发文档。

## 未纳入版本控制

- `local.properties` 中的本机 Android SDK 路径；
- `.idea`、`.gradle`、`.kotlin`、`node_modules` 等本机缓存；
- `build`、`web/dist*` 等可重新生成的构建目录；
- APK、AAB、ZIP 和签名证书等二进制交付物。

本地历史交付物仍保存在原工作区 `E:\1workspace\local game`，但不进入 Git 历史。正式 APK 建议使用 GitHub Releases 发布，并在本地使用受保护的正式 keystore 签名。
