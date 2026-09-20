# 四国军棋 Online

这是对最初 Java Swing 四国军棋项目的一次渐进式产品化重构。经典 17×17 四方棋盘、南北对东西、暗棋、铁路、工兵、行营、总部和特殊棋子规则保持不变；联机层改为服务端权威的 JSON/WebSocket 协议，前端改为 React + TypeScript。

## 当前能力

- 六位房间号创建/加入，四人到齐后进入对局；
- 点击交换的正式布阵阶段，前端实时提示规则，服务端再次校验 25 枚棋子的完整布局；
- 每位玩家自动旋转为“自己在下方”的视角；
- 合法落点，以及落子后短暂显示的服务端权威实际行进路线；
- 服务端权威的 30 秒回合倒计时，累计 5 次超时自动出局；
- 队伍胜负结算，以及四人确认后在原房间重新布阵再战；
- 对手棋子始终脱敏，公开事件不包含交战棋子类型或“谁击败了谁”；
- Spring Boot 健康检查、输入长度限制、消息去重与聊天限速；
- 房间、座位凭证摘要和棋局状态落盘，服务重启后可继续原房间；
- 前后端单元测试及可直接容器化的部署入口。

## 项目结构

- `src/`：保留的旧版 Swing/TCP 源码，仅作为规则考据基线；
- `server/`：Java 21 + Spring Boot 权威规则与 WebSocket 服务；
- `web/`：React 19 + TypeScript + Vite 前端；
- `docs/`：旧版审计、产品需求、协议和分步路线图。

关键文档：

- [产品需求](docs/PRODUCT_REQUIREMENTS.md)
- [旧版审计](docs/LEGACY_AUDIT.md)
- [联机协议](docs/NETWORK_PROTOCOL.md)
- [分步路线](docs/REBUILD_ROADMAP.md)
- [设计验收](design-qa.md)
- [Playwright 验收](docs/PLAYWRIGHT_ACCEPTANCE.md)

## 本地运行

先启动服务端：

```bash
cd server
mvn spring-boot:run
```

另开一个终端启动前端：

```bash
cd web
npm ci
npm run dev -- --host 127.0.0.1 --port 4173
```

浏览器打开 `http://127.0.0.1:4173/`。纯界面演示可打开 `http://127.0.0.1:4173/?demo=1`；工兵路线、夺旗、无棋可走和五次超时的固定验收场景分别使用 `scenario=sapper-route`、`scenario=flag-capture`、`scenario=no-legal-move`、`scenario=timeout-elimination`。

## 验证

```bash
cd server && mvn test
cd web && npm run typecheck && npm test && npm run build && npm run test:sites
```

## 部署

安装 Docker Compose 后，在仓库根目录运行：

```bash
docker compose up --build
```

默认从 `http://localhost:8088` 访问。Compose 使用 `armychess-data` 数据卷保存未完成房间；直接部署服务端时可通过 `ARMYCHESS_DATA_DIR` 指定持久化目录。正式上线前需要设置真实域名、HTTPS/WSS、监控告警、隐私条款，并由仓库所有者选择开源许可证。
