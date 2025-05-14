# Gitlet 版本控制系统

## 项目简介

Gitlet 是一个简化版的 Git 版本控制系统，使用 Java 实现。它提供了 Git 的核心功能，包括版本控制、分支管理、合并操作等，适合用于学习版本控制系统的基本原理。

## 功能特性

### 基本命令
- `init` - 初始化一个新的版本库
- `add <file>` - 添加文件到暂存区
- `commit <message>` - 提交更改
- `rm <file>` - 移除文件
- `log` - 显示提交历史
- `global-log` - 显示所有提交历史
- `status` - 显示当前状态

### 分支与合并
- `branch <branch-name>` - 创建新分支
- `rm-branch <branch-name>` - 删除分支
- `checkout` - 切换分支或检出文件
- `merge <branch-name>` - 合并分支

### 高级功能
- `find <message>` - 根据提交信息查找提交
- `reset <commit-id>` - 重置到指定提交

## 快速开始

### 系统要求
- Java 8 或更高版本

### 安装与使用
1. 克隆仓库或下载源代码
2. 编译项目：
   ```bash
   javac gitlet/*.java
   ```
3. 运行命令：
   ```bash
   java gitlet.Main <command> [arguments]
   ```

## 命令详解

### 初始化仓库
```bash
java gitlet.Main init
```
在当前目录创建新的版本库。

### 添加文件
```bash
java gitlet.Main add <filename>
```
将文件添加到暂存区，准备提交。

### 提交更改
```bash
java gitlet.Main commit "commit message"
```
提交暂存区的更改，必须包含提交信息。

### 查看状态
```bash
java gitlet.Main status
```
显示当前分支、暂存文件和未跟踪文件的状态。

### 分支操作
```bash
java gitlet.Main branch <new-branch-name>
```
创建新分支。

```bash
java gitlet.Main checkout <branch-name>
```
切换到指定分支。

## 文件结构

```
.gitlet/
├── HEAD                # 当前分支指针
├── branches/           # 分支信息
├── objects/            # 对象存储
│   ├── blobs/          # 文件内容存储
│   └── commits/        # 提交对象存储
└── INDEX               # 暂存区信息
```

## 设计原理

Gitlet 实现了以下核心概念：
- **Blob** - 存储文件内容
- **Commit** - 存储版本快照
- **Tree** - 组织文件和目录结构
- **Branch** - 分支指针
- **HEAD** - 当前工作指针

