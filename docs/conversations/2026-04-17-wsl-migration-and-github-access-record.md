# 2026-04-17 对话纪要：GitHub 访问修复与项目迁移到 WSL

## 1. 记录目的

本文用于整理本次围绕以下事项展开的完整对话过程，记录问题定位、处理步骤、关键决策以及当前结果，作为后续开发环境使用与仓库维护的依据：

- 确认当前代码是否为最新的 `master`。
- 修复当前环境对 GitHub 私有仓库的访问能力。
- 将本地仓库更新到远端最新 `master`。
- 将 Android 项目从 Windows 挂载盘迁移到 WSL 的 Linux 文件系统中，减少宿主机目录污染。

## 2. 对话背景

本次对话开始时，项目位于 Windows 挂载路径：

- `/mnt/d/data/AndroidWork/track-record-new`

用户首先希望确认当前仓库是否已经是最新的 `master` 分支代码。

在初步检查中确认：

- 当前本地分支为 `master`。
- 本地 `HEAD` 与本地记录的 `origin/master` 都指向提交 `13dedeb`。
- 工作区存在一处未提交改动，即 `AGENTS.md`。

但由于尚未真正同步远端，所以只能确认「本地记录的远端引用」与当前提交一致，不能直接确认 GitHub 上的远端 `master` 是否已经前进。

## 3. GitHub 访问问题排查过程

### 3.1 首次远端同步失败

在尝试执行 `git fetch origin master` 时，出现如下问题：

- `git@github.com: Permission denied (publickey)`

这说明当前环境虽然配置了 SSH 形式的远端地址，但并没有可用的 GitHub SSH 身份。

### 3.2 本地 SSH 环境检查

随后对 WSL 中的 SSH 环境进行了检查，确认：

- `~/.ssh` 中只有 `known_hosts`，没有私钥。
- `ssh-agent` 未连接，无法提供已加载密钥。
- 仓库远端地址为 `git@github.com:beyondwen/track-record-new.git`。

因此，SSH 路径的根因很明确：

- 没有 GitHub 可用私钥。
- 没有可用 agent。

### 3.3 HTTPS 备用路径检查

同时检查了 HTTPS 访问路径，发现又有另一层问题：

- 当前环境设置了 `HTTP_PROXY` / `HTTPS_PROXY` 指向 `127.0.0.1:7890`。
- 该代理当时未运行，导致 Git 通过 HTTPS 访问 GitHub 失败。
- 在绕过代理后，HTTPS 已可连通 GitHub，但私有仓库访问仍然缺少凭据。

这说明 HTTPS 方案在当时也不能直接用于访问当前仓库。

### 3.4 Windows 侧复用认证的尝试

为了避免重新生成密钥，也尝试过复用 Windows 侧环境能力，包括：

- 检查 Windows 侧是否存在 `.ssh` 目录。
- 检查 Windows 侧 `ssh-agent`。
- 通过 `cmd.exe` 间接调用 Windows 的 `ssh` / `git`。

排查结果显示：

- Windows 侧没有现成的 `.ssh` 目录。
- `ssh-agent` 中也没有可用密钥。
- Windows 侧进一步尝试连接 GitHub 时，曾走到 `Host key verification failed`，说明网络链路本身可到达，但身份和主机信任均未完整配置。

### 3.5 生成新的 GitHub SSH Key

在确认没有可复用的旧密钥之后，为当前项目生成了一把新的 SSH key，用于访问 GitHub：

- 初始生成位置：项目内 `.wsl-tools/ssh/id_ed25519`
- 公钥内容随后提供给用户，由用户手动添加到 GitHub `SSH and GPG keys`

后续又发现一个权限问题：

- 私钥位于 `/mnt/d/...` 挂载盘时，文件权限过宽（`0777`），OpenSSH 会拒绝使用。

因此又执行了进一步修正：

- 将私钥复制到 WSL 私有目录 `/home/wenha/.codex/memories/ssh/track-record-new-github`
- 将权限设置为 `0600`

随后通过该私钥成功完成远端抓取，并将仓库级 `core.sshCommand` 配置为：

```bash
ssh -o StrictHostKeyChecking=accept-new -i /home/wenha/.codex/memories/ssh/track-record-new-github -o IdentitiesOnly=yes
```

这一步意味着：

- 当前仓库后续访问 GitHub 将默认使用这把 SSH key。
- 不再依赖系统全局 SSH 配置是否完善。

## 4. 是否为最新 master 的结论

在完成 SSH 配置修复后，重新执行远端同步，得到结果：

- 本地原始 `HEAD`：`13dedeb`
- 远端最新 `origin/master`：`19ee454`

并且通过 `git log --left-right HEAD...origin/master` 确认：

- 当前本地 `master` 落后远端 `5` 个提交

结论是：

- 当时仓库**不是**最新的 `master`

## 5. 更新到最新 master 的过程

### 5.1 首次拉取失败

直接执行：

```bash
git pull --rebase origin master
```

时失败，原因是：

- 工作区存在未暂存改动 `AGENTS.md`

### 5.2 使用 autostash 安全更新

为保留这处本地修改，改用：

```bash
git pull --rebase --autostash origin master
```

这次拉取成功完成，Git 自动执行了以下动作：

- 临时保存当前本地改动
- 将 `master` 快进到远端最新提交
- 自动恢复本地 `AGENTS.md` 修改

更新后确认：

- `HEAD` 变为 `19ee454`
- `master` 与 `origin/master` 一致
- 本地仍保留 `AGENTS.md` 的未提交改动

## 6. 关于将项目迁移到 WSL 的讨论

在 GitHub 访问修复并同步到最新代码后，用户进一步提出一个环境层面的需求：

- 希望尽量减少 Android 项目在 Windows 宿主机上的额外文件与缓存污染

围绕这个问题，进行了如下说明：

- 如果项目位于 WSL 的 Linux 文件系统，例如 `~/project/...`，那么项目内的构建产物、缓存和用户目录下的大部分 Linux 工具文件都会落在 WSL 中，而不会直接散落到 Windows 盘的项目目录旁。
- 但这并不等于宿主机完全不占空间，因为 WSL 文件最终仍保存在 Windows 的虚拟磁盘 `ext4.vhdx` 中。
- 若仍继续使用 Windows 版 Android Studio、Windows 版 SDK、Windows 版 Gradle/adb/emulator，那么宿主机依旧会产生对应缓存与工具文件。

最终结论是：

- 将项目迁移到 WSL 的 Linux 文件系统，可以明显减少 Windows 项目目录本身的杂文件与构建残留。
- 若后续希望进一步收敛污染范围，还应继续将 Android 构建工具链与缓存更多地落到 WSL 侧。

## 7. 项目迁移到 WSL 的执行过程

### 7.1 用户目标路径

用户明确提出：

- 将项目移动到 `/home/wenha/project/AndroidWork`
- 后续就在该目录下开发

在方案选择上，给出了两个选项：

1. 直接移动
2. 复制一份保留旧目录

用户明确选择：

- 直接移动

### 7.2 实际迁移过程

随后开始执行从：

- `/mnt/d/data/AndroidWork/track-record-new`

迁移到：

- `/home/wenha/project/AndroidWork/track-record-new`

由于这是一次跨文件系统移动，实际表现为：

- 先复制内容
- 再删除旧目录

该过程持续时间较长，期间多次确认新目录内容逐步增长，说明迁移在正常推进。

### 7.3 迁移后的补救同步

在主迁移完成后，检查发现新目录内仍有少量缺项，表现为 Git 视角下这些文件被标记为删除，包括：

- `AGENTS.md`
- `gradlew`
- `tools/decision-model/*`

进一步检查发现，这些内容仍然留在旧路径中，因此执行了补充同步，将缺失文件和目录从旧路径复制到新路径，最终恢复了新仓库的完整状态。

### 7.4 迁移完成后的新仓库状态

在新路径 `/home/wenha/project/AndroidWork/track-record-new` 中完成校验后，确认：

- Git 根目录正常。
- 远端仍为 `git@github.com:beyondwen/track-record-new.git`。
- 仓库级 `core.sshCommand` 配置仍然生效。
- 当前 `master` 与 `origin/master` 一致。
- 本地只剩用户原有的 `AGENTS.md` 一处未提交修改。

此时可以确认：

- 后续开发应以 `/home/wenha/project/AndroidWork/track-record-new` 作为唯一工作目录。

## 8. 旧路径清理情况

在迁移完成后，尝试删除旧路径：

- `/mnt/d/data/AndroidWork/track-record-new`

删除过程中出现了两类问题：

- 一部分残留是旧目录中的空占位文件，如 `.git`、`.codex`
- 最后剩下的是一个空目录壳，但由于当前会话仍与旧路径存在关联，删除时遇到权限/占用问题

因此在本次对话结束时的状态是：

- 旧路径的大部分内容已清空
- 可能仍残留一个空目录壳
- 但其中已经不再保留项目有效内容

## 9. 本次对话形成的关键结论

本次对话最终形成了以下明确结论：

- 当前仓库起初并不是最新的 `master`，而是落后远端 `5` 个提交。
- 当前仓库的 GitHub SSH 访问能力已经修复，可继续使用 SSH 推送与拉取。
- 仓库已经成功更新到远端最新 `master`。
- 项目主开发目录已经迁移到 WSL 路径：
  - `/home/wenha/project/AndroidWork/track-record-new`
- 未来应只在该 WSL 路径下继续开发和提交代码。

## 10. 后续建议

基于本次结果，后续建议优先做以下几件事：

1. 在 IDE、终端、脚本和构建工具配置中，统一切换到新路径 `/home/wenha/project/AndroidWork/track-record-new`。
2. 进一步评估是否将 Android SDK、Gradle 用户目录和其他缓存也尽量迁移或固定到 WSL 侧。
3. 在当前新路径下继续进行代码修改、提交和推送，不再回到 `/mnt/d/...` 旧路径工作。
4. 在当前会话完全结束后，如有必要，可再次确认旧路径空目录是否已被系统释放并彻底删除。
