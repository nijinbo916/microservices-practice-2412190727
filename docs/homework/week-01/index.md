# 第 1 周作业：开发环境检查与微服务基本概念

- **姓名**：倪金博
- **学号**：2412190727
- **日期**：2026-09-27
- **操作系统**：Windows 10（64 位）

## 一、环境检查

以下为本机实际执行各命令的完整输出。

### 1. Java

```console
$ java --version
java 27 2026-09-15
Java(TM) SE Runtime Environment (build 27+35-2325)
Java HotSpot(TM) 64-Bit Server VM (build 27+35-2325, mixed mode, sharing)
```

### 2. Maven

```console
$ mvn --version
Apache Maven 3.9.16 (2bdd9fddda4b155ebf8000e807eb73fd829a51d5)
Maven home: D:\Program\apache-maven-3.9.16
Java version: 27, vendor: Oracle Corporation, runtime: C:\Program Files\Java\jdk-27
Default locale: zh_CN, platform encoding: UTF-8
OS name: "windows 10", version: "10.0", arch: "amd64", family: "windows"
```

### 3. Git

```console
$ git --version
git version 2.55.0.windows.3
```

### 4. Docker

```console
$ docker version
Client:
 Version:           29.8.0
 API version:       1.56
 Go version:        go1.26.8
 Git commit:        88096ef
 Built:             Thu Sep  3 21:53:38 2026
 OS/Arch:           windows/amd64
 Context:           desktop-linux

Server: Docker Desktop 4.92.0 (240144)
 Engine:
  Version:          29.8.0
  API version:      1.56 (minimum version 1.40)
  Go version:       go1.26.8
  Git commit:       3ce5872
  Built:            Thu Sep  3 21:51:20 2026
  OS/Arch:          linux/amd64
  Experimental:     false
 containerd:
  Version:          v2.3.5
  GitCommit:        1294c24a7da8e5a793ed378161673abe94118892
 runc:
  Version:          1.5.1
  GitCommit:        v1.5.1-0-g8f2685a4
 docker-init:
  Version:          0.19.0
  GitCommit:        de40ad0
```

### 5. Docker Compose

```console
$ docker compose version
Docker Compose version v5.5.1
```

### 环境检查小结

| 工具 | 版本 | 状态 |
| --- | --- | --- |
| Java | 27 | ✅ 正常 |
| Maven | 3.9.16 | ✅ 正常 |
| Git | 2.55.0.windows.3 | ✅ 正常 |
| Docker | 29.8.0（Docker Desktop 4.92.0） | ✅ 正常 |
| Docker Compose | v5.5.1 | ✅ 正常 |

所有命令的运行截图见 [screenshots/](screenshots/) 目录。

## 二、概念回答

### 1. 什么是微服务架构？

微服务架构是一种把一个大型系统拆分成一组小型、独立服务的组织方式。每个服务只负责一块边界清晰的业务（比如订单、库存、用户），可以独立开发、独立部署、独立扩容，服务之间通过网络接口（通常是 HTTP/RPC 或消息队列）互相调用。在我看来，它的核心思想不是"拆得越碎越好"，而是让每个服务都能由一个小团队独立负责、独立演进，从而降低整个系统的协作成本和发布风险。

### 2. 微服务和单体架构的主要区别是什么？

单体架构是把所有业务功能放进同一个工程、打包成同一个进程运行，模块之间直接方法调用，部署简单、开发上手快，但任何一个小改动都要整体重新构建和发布，系统变大后模块边界容易模糊，牵一发动全身。微服务则是把不同业务拆到独立进程里，各自有独立的代码库、数据库和发布节奏，优点是故障隔离好、可以按需扩容、技术选型灵活，代价是引入了网络调用、分布式事务、服务治理、运维监控等一堆单体中不存在的复杂度。简单说：单体赢在简单，难在失控；微服务赢在灵活，难在分布式带来的额外负担。

### 3. 为什么本课程先实现单体系统，再逐步拆分为微服务？

因为拆分的前提是先看懂业务边界。如果一上来就写微服务，很容易把边界划错——把本该内聚的功能拆散，或者把该分开的功能耦在一起，后期返工成本极高。先实现一个模块划分清晰的单体系统，可以先把业务需求、领域模型和模块边界想清楚、验证清楚；等业务稳定、边界明确之后，再顺着已有的模块边界逐个拆出去，每次只拆一块、随时能回退。这也是工业界常见的演进式做法：微服务是演化的结果，而不是起步时的必要条件。

### 4. 为什么作业需要提供可重复运行的测试或验证脚本？

因为"我这里能跑"不算证据。可重复运行的测试或验证脚本意味着任何人（包括批改作业的老师、未来的自己、团队的其他成员）拿到代码后，用一条固定的命令就能得到确定的验证结果，不需要依赖口头说明或手工操作。对我自己来说，它的价值在于每次提交代码前都能快速确认没有把已有功能改坏，也让"环境是否正确、功能是否正确"变成机器可以判断的事情，而不是靠人眼检查。这也是持续集成（CI）能工作的前提。

## 三、问题记录

### Docker 守护进程未启动

- **系统版本**：Windows 10（64 位），Docker Desktop 4.92.0 已安装
- **错误现象**：执行 `docker version` 时 Client 部分正常输出，但 Server 部分报错：
  `failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine; check if the path is correct and if the daemon is running: open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified.`
- **原因分析**：Docker Desktop 虽然安装了，但 GUI 程序没有启动，Windows 命名管道（named pipe）上不存在 Docker 守护进程的监听端点，所以客户端连不上。
- **解决过程**：手动启动 Docker Desktop，等待右下角鲸鱼图标显示 "Engine running" 后重新执行 `docker version`，Server 部分正常返回（见上方第 4 节输出）。
- **经验总结**：在 Windows 上 `docker` 命令能找到只说明客户端 CLI 存在，还必须确认 Docker Desktop（及底层 WSL2 虚拟机）处于运行状态，守护进程才能真正工作。

## 四、Git 提交记录

```
$ git log --oneline --graph
* 637a9c0 docs(week-01): 完成环境检查、概念回答与 Docker 问题记录
* 48d942b docs: 完善 README，补充课程信息、个人身份与仓库用途说明
* c609eb4 Create .gitkeep
* ccd34eb Create .gitkeep
* 86f0963 Create index.md
* 961cc6b Initial commit
```

提交记录截图见 [screenshots/](screenshots/) 目录。
