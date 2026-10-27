# MC AI Controller 第一版

这是一个 Minecraft 1.20.1 Forge 客户端控制器。它控制当前登录的客户端角色，不创建机器人账号，不需要屏幕识别，也不修改服务器或存档。

第一版支持：

- `前往坐标 100 70 -200`
- `跟随玩家 Mc_kuailewang`
- `停止任务`
- 游戏内按 `F8` 紧急停止并锁定控制
- 输入 `解除急停` 后允许接受新任务

控制器只连接 `127.0.0.1`。桥接模组首次启动时会生成随机令牌，控制端必须提供该令牌。程序不会模拟 Windows 键鼠；寻路和控制由当前 Minecraft 客户端中的 Baritone 完成。

## 兼容目标

- Minecraft 1.20.1
- Forge 47.3.0
- Java 17
- Baritone 1.10.5 Forge API

仓库只从以下官方来源取得依赖：

- Forge：`https://maven.minecraftforge.net`
- Baritone：`https://github.com/cabaletta/baritone/releases/tag/v1.10.5`

Baritone 文件的固定 SHA-1 为：

```text
36915642c33c21c9225f89e0c1c53804c4231c72  baritone-api-forge-1.10.5.jar
```

## 从 GitHub Actions 下载

打开仓库的 **Actions → Build Forge client mod → Run workflow**，构建成功后下载 `mc-ai-controller-windows-0.1.0` artifact。解压后应包含：

```text
mods/
  baritone-api-forge-1.10.5.jar
  mc_ai_controller-0.1.0.jar
controller/
  controller.ps1
  start-controller.bat
  backup-before-install.ps1
```

## 安装前备份

完全退出 Minecraft 和启动器。在 PowerShell 中进入解压后的 `controller` 目录，运行：

```powershell
.\backup-before-install.ps1 -GameDir 'D:\mc\.minecraft\versions\Bounty Hunter'
```

脚本会在游戏目录中新建带时间戳的备份目录，复制现有 `mods`、`config` 和 `options.txt`。确认它显示“备份完成”后再安装。

## 安装

将 artifact 的两个文件复制到：

```text
D:\mc\.minecraft\versions\Bounty Hunter\mods
```

需要复制：

```text
baritone-api-forge-1.10.5.jar
mc_ai_controller-0.1.0.jar
```

不要同时安装 Fabric、NeoForge、standalone 或其他版本的 Baritone。

## 第一次启动和检查

1. 用原来的 PCL 配置启动 `Bounty Hunter`。
2. 先停留在主菜单。
3. 检查 `logs\latest.log`，应该出现：

```text
MC AI Controller listening on 127.0.0.1:32123
```

4. 游戏目录中应该生成：

```text
config\mc_ai_controller.properties
```

如果游戏在主菜单前崩溃，请提供：

- `logs\latest.log`
- 最新的 `crash-reports\crash-*.txt`
- `mods` 中两个新增 jar 的完整文件名

## 启动控制器

进入一个允许测试的单人世界。双击：

```text
controller\start-controller.bat
```

第一次启动会询问游戏目录。输入实际目录即可，程序会在控制器目录中保存 `controller.properties`，不会把绝对路径写进程序代码。

## 测试顺序

### 1. 紧急停止

进入世界后先按 `F8`。聊天区应显示急停提示。控制器输入：

```text
前往坐标 0 80 0
```

应被拒绝。然后输入：

```text
解除急停
```

如果失败，请提供 `latest.log`，并说明 F8 是否与整合包内其他按键冲突。可以在 Minecraft 按键设置中更换该快捷键。

### 2. 短距离坐标寻路

站在安全平地，查看当前位置，选择 10 到 20 格外的可到达坐标并输入：

```text
前往坐标 X Y Z
```

角色应开始移动。随后输入：

```text
停止任务
```

角色应立即停止。第一版没有采集任务，但 Baritone 在普通寻路过程中可能按其默认设置处理挡路方块；首次测试请使用无障碍平地。

### 3. 跟随玩家

目标玩家必须在客户端当前加载范围内。输入：

```text
跟随玩家 玩家名
```

让目标缓慢移动，然后测试 `停止任务` 和 `F8`。找不到目标时控制器会明确返回错误。

## 出现问题时提供什么

每次问题发生后立即退出世界，再提供：

- `logs\latest.log`
- 有崩溃时提供最新的 `crash-reports\crash-*.txt`
- 控制器窗口的完整报错文字
- 执行的原始命令
- 问题发生在主菜单、单人世界还是服务器

分享前可遮盖用户名、服务器地址、聊天内容，以及 `config\mc_ai_controller.properties` 中的 `token`。不要公开令牌。

## 本地构建

安装 Java 17 后，在仓库根目录运行：

```powershell
.\windows\download-official-baritone.ps1
.\gradlew.bat build
```

生成文件位于：

```text
build\libs\mc_ai_controller-0.1.0.jar
```
