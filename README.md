# Gitlet - 简化版 Git 的 Java 实现

此项目是 **UC Berkeley CS61B Spring 2021 Project 2: Gitlet** 课程项目实现的轻量的版本控制系统，复刻 Git 的核心功能。
此实现用 Java 完成，目标是理解版本控制系统的基本原理以及掌握数据结构设计的技巧。

---

## 项目简介
Gitlet 是一个简化版的版本控制系统，复刻了 Git 的核心功能，包括如下命令：
- `init` 仓库初始化
- `add` 添加文件到暂存区
- `commit` 提交commit
- `checkout` 检出特定文件或分支
- `branch`, `rm-branch` 分支管理
- `merge` 合并分支
- `log`, `global-log`, `graph-log` 查看日志
- `reset` 重置到特定commit
- `add-remote`, `rm-remote` 管理远程仓库
- `push` 向远程仓库推送
- `fetch` 从远程仓库获取进度分支
- `pull` 从远程仓库拉取

---

## 项目结构
```
.
├── gitlet/
│   ├── Main.java
│   ├── Repository.java
│   ├── Commit.java
│   ├── Utils.java
│   ├── GitletException.java
│   └── MakeFile
├── testing/
├── gitlet-design.md
├── Makefile
└── README.md
```

---

## 功能一览
| 功能 | 命令 | 描述 |
|------|------|------|
| 初始化仓库 | `init` | 在当前目录创建 `.gitlet` 仓库 |
| 添加文件 | `add <file>` | 将文件加入暂存区 |
| 提交更改 | `commit "msg"` | 将暂存区快照提交 |
| 查看历史 | `log` / `global-log` / `graph-log` | 打印当前分支/全局提交历史 |
| 分支管理 | `branch <name>` / `rm-branch <name>` | 创建/删除分支 |
| 检出文件/分支 | `checkout ...` | 从某个提交或分支恢复文件 |
| 合并分支 | `merge <branch>` | 将指定分支合并到当前分支 |
| 重置状态 | `reset <commit>` | 将 HEAD 指向指定提交 |
| 远程仓库管理 | `add-remote` / `rm-remote` | 创建/删除远程仓库 |
| 推送仓库 | `push <remote> <branch>` | 将当前分支推送至远程仓库的特定分支 |
| 获取仓库进度 | `fetch <remote> <branch>` | 获取远程仓库特定分支到本地新分支 |
| 拉取仓库 | `pull <remote> <branch>` | 拉取远程仓库特定分支合并到当前分支 |

---

## 安装与运行
1. 克隆仓库
    ```bash
    git clone https://github.com/yitian-chen/Gitlet-from-CS61B-SP21.git
    cd gitlet
    ```
2. 编译 Java 代码：
   ```bash
   javac gitlet/*.java
   ```
3. 将已编译文件复制至工作文件夹
   ```bash
   cp gitlet/*.class ~/example
   ```
4. 到工作文件夹中运行 Gitlet
   ```bash
   cd ~/example
   ```
5. 运行 Gitlet 示例
   ```bash
   java gitlet.Main init
   ```

---

## 使用示例
- 初始化项目
  ```bash
  java gitlet.Main init
  ```

- 添加并提交文件
  ```bash
  java gitlet.Main add example.txt
  java gitlet.Main commit "Add example.java"
  ```

- 查看历史
  ```bash
  java gitlet.Main log
  java gitlet.Main global-log
  ```

- 查看状态
  ```bash
  java gitlet.Main status
  ```

- 分支操作
  ```bash
  java gitlet.Main branch dev
  java gitlet.Main checkout dev
  ```

- 检出文件
  ```bash
  java gitlet.Main checkout -- example.txt
  java gitlet.Main checkout 7b3ad4r -- example.txt
  ```

- 合并分支
  ```bash
  java gitlet.Main merge dev
  ```

- 查看树状图形化历史
  ```bash
  java gitlet.Main graph-log
  ```

- 设置远程仓库
  ```bash
  java gitlet.Main add-remote R1 ../remote/.gitlet
  java gitlet.Main rm-remote R1
  ```

- 远程仓库推送与拉取
  ```bash
  java gitlet.Main push R1 master
  java gitlet.Main fetch R1 master
  java gitlet.Main pull R1 master
  ```

---

## 设计与实现要点
- 持久化存储：通过在.gitlet目录下序列化对象来存储，文件结构如下，具体描述可见设计文档
````
.gitlet/
├── HEAD
├── objects/
│   ├── commits/
│   └── blobs/
├── refs/
│   ├── heads/
│   └── remotes/
│       ├── (remote1)
│       └── ...
├── staging/
│   ├── add/
│   └── remove/
└── remote/
````
- 对象模型：维护Commit对象来表示提交节点、Blob对象来表示文件内容快照。
- 分支结构：用非扁平化的存储结构来区分本地分支与远程分支，用引用映射保存HEAD信息。
- SHA-1 Hash：对于Commit和Blob对象，采用 SHA-1 哈希值作为其唯一标识，从而实现去重和快速查找。
- 远程功能设计：实现 `push`, `fetch`, `pull` 等基本远程交互命令，模拟 Git 远程交互逻辑。
- 图形化日志：额外开发了`graph-log`命令，通过迭代与BFS算法实现将树状 commits 结构输出为图形化样式，使提交历史的结构一目了然。