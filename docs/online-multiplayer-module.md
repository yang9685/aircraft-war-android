# Aircraft War 联机对战模块说明文档

## 1. 模块目标

本项目的联机对战模块用于支持两名玩家在局域网环境下进行实时对战。整体采用客户端-服务器（C/S）架构：

- Android 客户端负责本地游戏运行、联机大厅交互、分数上报、对手状态显示和结算展示。
- Java 服务端负责接收连接、按难度匹配玩家、创建房间、转发实时分数以及生成最终对战结果。

本模块的设计重点不是“完全同步双方飞机、子弹、敌机的全部状态”，而是实现一种轻量、稳定、适合作业演示的“结算型联机对战”：

- 同步内容包括：实时分数、对手是否结束、最终胜负结果。
- 不同步内容包括：飞机坐标、子弹轨迹、敌机位置等完整战场状态。

这样做的优点是开发复杂度较低、复用现有单机游戏引擎较多、网络稳定性更容易保障，适合课程作业场景。

## 2. 整体架构

联机模块主要分为六个部分：

1. 主菜单入口
2. 联机大厅
3. 客户端网络层
4. 游戏页联机控制层
5. 游戏循环与实时分数同步层
6. 服务端匹配与房间管理层

整体关系如下：

```text
MainActivity
    -> MultiplayerLobbyActivity
        -> SocketMatchClient
            <-> MatchServerApp
        -> MultiplayerSessionStore
            -> GameActivity
                -> FloatingJoystickGameSurfaceView
```

其中：

- `MainActivity` 负责联机入口。
- `MultiplayerLobbyActivity` 负责让玩家输入服务器地址、端口、昵称并发起匹配。
- `SocketMatchClient` 负责客户端 Socket 通信与协议解析。
- `MultiplayerSessionStore` 负责在页面跳转时临时保存已连接的联机会话。
- `GameActivity` 负责把本地游戏和网络同步逻辑连接起来。
- `FloatingJoystickGameSurfaceView` 负责本地游戏主循环，并在联机模式下定期上报分数。
- `MatchServerApp` 负责服务端匹配、房间管理、消息转发和最终结算。

## 3. 关键代码文件

以下文件是联机模块最核心的代码文件：

### 3.1 客户端文件

- `app/src/main/java/edu/hitsz/aircraftwar/MainActivity.java`
  - 主菜单入口，点击“联机对战”后进入联机大厅。

- `app/src/main/java/edu/hitsz/aircraftwar/MultiplayerLobbyActivity.java`
  - 联机大厅页面。
  - 负责读取玩家输入、建立连接、等待匹配、匹配成功后跳转游戏页。

- `app/src/main/java/edu/hitsz/aircraftwar/network/SocketMatchClient.java`
  - 联机客户端网络核心类。
  - 负责 Socket 建立、协议收发、消息解析和监听器回调。

- `app/src/main/java/edu/hitsz/aircraftwar/network/MultiplayerSessionStore.java`
  - 会话暂存类。
  - 负责在 `MultiplayerLobbyActivity` 和 `GameActivity` 之间交接已建立的连接对象。

- `app/src/main/java/edu/hitsz/aircraftwar/GameActivity.java`
  - 游戏页面控制层。
  - 负责在联机模式下接管 `SocketMatchClient`、处理实时分数同步、处理最终结算。

- `app/src/main/java/edu/hitsz/aircraftwar/FloatingJoystickGameSurfaceView.java`
  - 游戏主循环与渲染层。
  - 联机模式下负责定时回调分数变化，并在 HUD 中显示对手状态。

### 3.2 服务端文件

- `match-server/src/main/java/edu/hitsz/aircraftwar/server/MatchServerApp.java`
  - 联机服务端主程序。
  - 内部包含匹配器 `MatchCoordinator`、房间类 `BattleRoom`、连接类 `PlayerConnection`。

## 4. 核心设计思路

### 4.1 分层设计

联机模块没有把网络代码直接写进游戏引擎，而是采用了分层设计：

- 游戏层负责产生本地状态，例如分数、时长、游戏结束事件。
- 控制层负责把这些状态交给网络层。
- 网络层负责把本地事件转换成协议消息发送到服务器，并把服务器消息再回调给界面层。
- 服务端只负责匹配、转发和裁决，不负责运行完整游戏逻辑。

这种设计有两个明显好处：

- 第一，复用现有单机逻辑，不需要大幅改造原游戏引擎。
- 第二，联机问题和游戏问题可以分开排查，便于定位 Bug。

### 4.2 采用“结算型联机”而非“全状态同步联机”

如果要实现双方完全同步飞机位置、子弹、敌机、Boss 状态，就需要：

- 更复杂的同步协议
- 更高的实时性要求
- 更严格的一致性控制
- 更多的网络异常处理逻辑

因此本项目采用更适合作业的方案：

- 本地各自运行自己的游戏进程
- 实时同步分数
- 对局结束时由服务器统一裁决输赢

这样可以体现网络功能，同时保证项目复杂度可控。

## 5. 联机流程总览

整个联机流程可以分成五个阶段：

1. 进入联机大厅
2. 连接服务器并匹配
3. 匹配成功后进入对局
4. 对局过程中实时同步分数
5. 对局结束后统一结算

下面按时间线详细说明。

## 6. 从点击联机按钮到开始对局的调用链

### 6.1 进入联机大厅

在主菜单中，用户点击“联机对战”按钮后，会从 `MainActivity` 跳转到 `MultiplayerLobbyActivity`。

这一层的职责很简单：

- 提供联机入口
- 把单机模式和联机模式分开

### 6.2 联机大厅初始化

`MultiplayerLobbyActivity` 页面启动后，会完成以下初始化工作：

- 读取本地保存的服务器 IP 和端口
- 读取玩家名称
- 默认设置联机难度
- 设置“连接”“取消”按钮事件

联机大厅中最重要的函数是：

- `connectToMatchServer()`

这个函数完成的工作包括：

- 读取并校验 `host`、`port`、`playerName`
- 保存这些输入到 `AppPreferences`
- 创建新的 `SocketMatchClient`
- 注册当前页面作为 `SocketMatchClient.Listener`
- 调用 `matchClient.connect()` 发起连接

### 6.3 客户端建立连接

`SocketMatchClient.connect()` 不会直接在主线程中建立连接，而是会启动后台线程执行 `runConnectionLoop()`。

`runConnectionLoop()` 的主要逻辑如下：

1. 创建 `Socket`
2. 连接服务器
3. 初始化 `BufferedReader` 和 `PrintWriter`
4. 将客户端状态标记为已连接
5. 发送第一条协议消息 `JOIN|difficulty`
6. 持续阻塞读取服务端返回的消息

此时客户端已经进入匹配流程。

### 6.4 服务端接收连接并匹配

服务端 `MatchServerApp` 启动后，会监听指定端口。每来一个新的客户端连接，就创建一个 `PlayerConnection` 线程负责该客户端的通信。

当服务端收到：

```text
JOIN|NORMAL
```

这样的消息后，会调用 `MatchCoordinator.join(...)` 执行匹配逻辑：

- 如果当前难度还没有等待中的玩家，就把自己放进等待表，并回发 `WAIT`。
- 如果当前难度已经有一名等待玩家，就取出这名玩家，与当前玩家组成一个新的 `BattleRoom`。

匹配成功后，服务端会：

- 分配房间号 `roomId`
- 为两名玩家分配 `playerId`，分别为 1 和 2
- 向两名玩家分别发送 `START|roomId|playerId|difficulty`

### 6.5 匹配成功后进入游戏页

客户端收到 `START` 消息后，`SocketMatchClient.handleServerMessage()` 会解析协议，并通过监听器回调 `MultiplayerLobbyActivity.onMatchStarted(...)`。

`onMatchStarted(...)` 中最关键的两个动作是：

1. 调用 `MultiplayerSessionStore.setActiveClient(matchClient)` 保存当前已建立的连接对象
2. 启动 `GameActivity`

这里不重新连接服务器，而是把原来的 `SocketMatchClient` 交给游戏页继续使用。

## 7. 为什么需要 MultiplayerSessionStore

这是本模块中一个非常关键的辅助设计。

从大厅页跳转到游戏页时，如果直接销毁大厅中的网络对象，再在游戏页重新建立连接，会产生很多问题：

- 玩家会失去原来的房间上下文
- 已分配的 `playerId` 和 `roomId` 难以保留
- 匹配结果会丢失
- 页面切换期间容易出现断线

因此本项目采用静态会话暂存类 `MultiplayerSessionStore`：

- 在大厅页匹配成功后把 `SocketMatchClient` 放进去
- 在游戏页启动时再取出来继续使用

这样就实现了“页面变了，但网络连接不中断”的效果。

## 8. 游戏页如何接管联机会话

`GameActivity` 启动后，会读取联机相关参数：

- 是否为联机模式
- 玩家昵称
- 玩家编号 `playerId`
- 房间号 `roomId`
- 难度信息

如果当前是联机模式，则会：

1. 从 `MultiplayerSessionStore.getActiveClient()` 中取出客户端对象
2. 将 `GameActivity` 重新设置为当前的网络监听器
3. 调用 `gameSurfaceView.setOnlineBattle(true)` 开启联机 HUD 和联机分数同步逻辑

这一部分的作用是把“页面控制”和“网络通信”正式接起来。

## 9. 对局中的实时分数同步

### 9.1 本地游戏主循环

游戏的主循环位于 `FloatingJoystickGameSurfaceView.run()` 中。

这个循环负责：

- 处理摇杆控制
- 更新游戏引擎
- 检查碰撞和音效
- 绘制每一帧画面
- 判断是否进入 Game Over

在联机模式下，这个主循环还多做了一件事：

- 定时把当前分数和时长回调给 `GameActivity`

### 9.2 为什么要节流上报

如果每一帧都上报分数，会导致：

- 网络消息过多
- 服务端转发压力增大
- 客户端 UI 更新过于频繁

因此项目中采用了“节流上报”策略：

- 只有分数发生变化时才考虑上报
- 并且要求距离上次上报至少间隔 500ms

这样可以在“同步感”和“稳定性”之间取得平衡。

### 9.3 分数上报调用链

实时分数同步的调用链如下：

1. `FloatingJoystickGameSurfaceView.run()`
2. 检测到联机模式下分数发生变化
3. 调用 `gameSessionListener.onScoreChanged(score, durationSeconds, difficulty)`
4. `GameActivity.onScoreChanged(...)`
5. `matchClient.sendScore(score, durationSeconds)`
6. `SocketMatchClient.sendLine("SCORE|...")`
7. 服务端 `PlayerConnection.handleClientMessage(...)`
8. `BattleRoom.forwardScore(...)`
9. 服务端把分数转发给对手
10. 对手客户端收到 `SCORE|playerId|score|duration`
11. `SocketMatchClient.handleServerMessage(...)`
12. 回调 `GameActivity.onOpponentScoreUpdate(...)`
13. `gameSurfaceView.updateOpponentScore(score)`
14. HUD 刷新显示对手分数

### 9.4 HUD 如何显示对手信息

在 `FloatingJoystickGameSurfaceView.drawHud(...)` 中，联机模式会额外绘制一个“对手信息面板”。

显示内容包括：

- 对手当前分数
- 对手状态是“实时同步中”还是“已被击落”

这样用户在本地游戏过程中，就可以实时看到另一名玩家的大致进度。

## 10. 对局结束与统一结算

### 10.1 本地结束时的处理

当本地玩家死亡后，`FloatingJoystickGameSurfaceView` 会在游戏结束序列完成后回调：

- `onGameOver(score, durationSeconds, difficulty)`

`GameActivity.onGameOver(...)` 在联机模式下不会立刻展示普通单机结算框，而是：

1. 保存本地结果到 `localResult`
2. 通过 `matchClient.sendResult(score, durationSeconds)` 向服务器发送最终结果
3. 弹出一个等待对手完成本局的对话框

这样做是为了让服务端在拿到双方最终成绩后统一判断输赢。

### 10.2 服务端如何收集最终成绩

服务端收到：

```text
RESULT|score|duration
```

后，会调用 `BattleRoom.submitResult(...)`。

该函数的逻辑是：

- 先把当前玩家的最终分数和时长记录下来
- 立即给对手发送 `OPPONENT_RESULT|playerId|score|duration`
- 如果此时双方结果都已经收到，则开始生成最终比赛结果

### 10.3 胜负判定逻辑

当前项目中采用的胜负判定规则非常直接：

- 双方分数相等，则判平局
- 否则，分数更高的一方获胜

最终由服务端生成：

```text
MATCH_RESULT|winner|p1Score|p1Duration|p2Score|p2Duration
```

并同时发给两边客户端。

### 10.4 客户端展示最终结算

客户端收到 `MATCH_RESULT` 后，会进入 `GameActivity.onMatchResult(...)`。

这里会：

- 根据自己是 1 号还是 2 号玩家，确定本地结果和对手结果分别对应哪一组数据
- 更新 `opponentScore`、`opponentDurationSeconds`
- 设置对手状态为已结束
- 调用 `maybeShowMatchResult(...)`

最终弹出的结算框中会显示：

- 自己的分数
- 自己的存活时间
- 对手的分数
- 对手的存活时间
- 本局胜负结论

## 11. 网络协议设计

本项目使用的是基于 TCP Socket 的行文本协议：

- 每条消息一行
- 字段之间使用 `|` 分隔

协议设计如下。

### 11.1 联机对战协议

- `JOIN|DIFFICULTY`
  - 客户端加入指定难度的匹配队列

- `WAIT|message`
  - 服务端通知客户端当前正在等待另一名玩家

- `START|roomId|playerId|difficulty`
  - 服务端通知客户端匹配成功，可以开始游戏

- `SCORE|playerId|score|duration`
  - 服务端将一名玩家的实时分数转发给另一名玩家

- `RESULT|score|duration`
  - 客户端向服务端提交自己的最终成绩

- `OPPONENT_RESULT|playerId|score|duration`
  - 服务端通知对手“另一边已经结束”

- `MATCH_RESULT|winner|p1Score|p1Duration|p2Score|p2Duration`
  - 服务端同时向双方发送最终比赛结果

- `BYE`
  - 客户端主动断开连接

### 11.2 设计原因

之所以选择这种简单文本协议，而不是 JSON，主要有三个原因：

- 作业场景中协议字段少，消息结构固定，文本协议足够使用
- 调试方便，服务器控制台可以直接看到原始消息
- 实现成本低，更适合快速稳定落地

## 12. 线程模型

联机模块涉及多个线程，这一点对理解稳定性很重要。

### 12.1 Android 主线程

负责：

- 页面生命周期
- 控件更新
- 弹窗展示
- 监听器回调后的 UI 处理

### 12.2 游戏渲染线程

由 `FloatingJoystickGameSurfaceView.startLoop()` 启动。

负责：

- 驱动本地游戏主循环
- 更新游戏状态
- 绘制画面
- 检查何时上报分数
- 检查何时触发 Game Over 回调

### 12.3 Socket 读线程

由 `SocketMatchClient.connect()` 启动后台线程执行 `runConnectionLoop()`。

负责：

- 建立 TCP 连接
- 阻塞读取服务端消息
- 解析服务端协议

### 12.4 Socket 写线程

项目中专门使用了单线程 `ExecutorService` 来串行处理 Socket 写操作。

这样做的原因是：

- 避免在主线程直接执行网络 IO
- 避免多个线程并发写同一个 `writer`
- 减少时序竞争带来的断线问题

这也是联机模块稳定化过程中非常关键的一次改进。

## 13. 稳定性优化与问题修复

在联机模块开发过程中，曾经遇到过一些典型问题，后续通过结构调整进行了修复。

### 13.1 Activity 切换导致连接丢失

问题表现：

- 联机大厅匹配成功后进入游戏页
- 如果直接销毁大厅监听关系，可能会出现页面切换瞬间消息丢失

解决方案：

- 使用 `MultiplayerSessionStore` 交接客户端对象
- 新 listener 绑定时调用 `dispatchSnapshot(...)` 把当前已知状态补发给新页面

这样即使在页面切换瞬间收到网络消息，也能在新页面中恢复状态。

### 13.2 写 Socket 时的线程安全问题

问题表现：

- 在分数变化、游戏结束、退出对局等时机，如果多个线程同时写 socket，容易出现时序问题

解决方案：

- 所有发送操作统一进入 `sendLine(...)`
- 使用单线程写执行器保证消息串行发出

### 13.3 断线竞态问题

问题表现：

- 一端断开后，另一端可能也会被异常关闭
- 服务端房间对象可能重复处理断线

解决方案：

- 在服务端 `BattleRoom` 中加入 `finished` 状态控制
- 断线后先标记房间已结束，再进行对端关闭和清理
- 在发送消息前检查连接状态

### 13.4 过度同步导致的性能问题

问题表现：

- 如果分数变化后立即高频发送，网络压力增大，UI 也容易抖动

解决方案：

- 对分数上报进行 500ms 节流
- 只在分数真正变化时发送更新

## 14. 设计优点与局限性

### 14.1 优点

- 架构简单清晰，便于课程讲解
- 复用现有单机游戏逻辑较多
- 服务端压力较小，只负责匹配与裁决
- 局域网环境下部署方便
- 网络层与游戏层解耦，后续调试和扩展比较方便

### 14.2 局限性

- 不是权威服务器架构，无法有效防作弊
- 双方战场并非完全同步，只同步分数和结算信息
- 依赖局域网环境，跨公网还需要进一步设计 NAT 穿透或公网服务器部署方案
- 当前胜负规则较简单，仅按分数比较

## 15. 课堂讲解时可以使用的总结表述

可以将本模块概括为以下几句话：

本项目的联机模块采用了基于 TCP Socket 的客户端-服务器架构。客户端分为大厅层、网络层、游戏控制层和游戏主循环层；服务端分为匹配器、房间和客户端连接处理器三部分。用户从联机大厅输入服务器参数后，客户端通过 `SocketMatchClient` 向服务端发送 `JOIN` 请求；服务端按难度完成匹配后，通知双方进入游戏。游戏过程中，客户端并不上传完整战场状态，而是以 500ms 为间隔同步分数与存活时间，服务端负责在房间内进行转发。某一方结束后，客户端向服务端提交最终结果，服务端在收到双方结果后统一计算胜负，并将最终结算发送给双方客户端显示。

## 16. 关键文件清单汇总

为了便于后续查看和讲解，下面给出联机模块关键文件的最终汇总：

- `app/src/main/java/edu/hitsz/aircraftwar/MainActivity.java`
- `app/src/main/java/edu/hitsz/aircraftwar/MultiplayerLobbyActivity.java`
- `app/src/main/java/edu/hitsz/aircraftwar/GameActivity.java`
- `app/src/main/java/edu/hitsz/aircraftwar/FloatingJoystickGameSurfaceView.java`
- `app/src/main/java/edu/hitsz/aircraftwar/network/SocketMatchClient.java`
- `app/src/main/java/edu/hitsz/aircraftwar/network/MultiplayerSessionStore.java`
- `match-server/src/main/java/edu/hitsz/aircraftwar/server/MatchServerApp.java`

如果需要继续讲“第二个网络功能”，则还可以补充以下文件：

- `app/src/main/java/edu/hitsz/aircraftwar/data/OnlineLeaderboardClient.java`
- `app/src/main/java/edu/hitsz/aircraftwar/LeaderboardActivity.java`
- `app/src/main/res/layout/activity_leaderboard.xml`

