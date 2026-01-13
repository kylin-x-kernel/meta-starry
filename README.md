# meta-starry

**StarryOS Yocto 构建层** - 使用 BitBake 构建 StarryOS 裸机内核和完整发行版

[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Yocto](https://img.shields.io/badge/yocto-kirkstone-green.svg)](https://www.yoctoproject.org/)

---

## 项目简介

`meta-starry` 是 StarryOS 的 Yocto Project 构建层，提供：
- StarryOS 裸机内核构建
- 多架构支持（aarch64、riscv64、loongarch64、x86_64）
- 完整的测试发行版（内核 + rootfs + 工具链）

---

## 快速开始

### 环境要求

- **操作系统**: Ubuntu 24.04 或兼容系统
- **构建主机**: x86_64 或 aarch64
- **磁盘空间**: 至少 100GB

### 系统配置（首次使用必须）

Ubuntu 24.04 需要调整内核参数：

```bash
# 一键配置（复制整段执行）
cat << 'EOF' | sudo tee /etc/sysctl.d/99-yocto.conf
kernel.apparmor_restrict_unprivileged_userns = 0
fs.inotify.max_user_watches = 524288
fs.inotify.max_user_instances = 512
fs.inotify.max_queued_events = 32768
EOF

sudo sysctl -p /etc/sysctl.d/99-yocto.conf
```

### 1. 克隆项目

```bash
mkdir -p ~/starry-workspace
cd ~/starry-workspace
git clone https://github.com/kylin-x-kernel/meta-starry.git
```

### 2. 自动设置环境

```bash
cd meta-starry
./setup-layers
```

这会自动克隆依赖的 Yocto 层（poky、meta-openembedded）。

### 3. 初始化构建环境

```bash
cd ~/starry-workspace
source poky/oe-init-build-env build
```

**注意**：
- 首次运行会自动生成配置文件
- 每次打开新终端都需要重新运行此命令
- 成功后当前目录会切换到 `build/`

### 4. 构建

#### 方式 A：仅构建内核

```bash
bitbake starry
```

产物：`tmp-baremetal/deploy/images/aarch64-qemu-virt/starry-*.bin`

#### 方式 B：构建完整发行版

```bash
# 测试发行版（包含完整工具链）
bitbake starry-test-image

# 或最小发行版（快速测试）
bitbake starry-minimal-image
```

产物：`tmp-baremetal/deploy/images/aarch64-qemu-virt/starry-test-image-*.ext4`

### 5. 启动测试

```bash
# 启动发行版
runqemu starry-test-image nographic

# 登录（用户名：root，无密码）
root@starryos:~# uname -a
root@starryos:~# cat /etc/starry-release
```

---

## 常用命令

### 构建相关

```bash
# 查看所有可构建的目标
bitbake -s

# 构建指定配方
bitbake starry

# 仅执行特定任务
bitbake starry -c compile      # 仅编译
bitbake starry -c install      # 仅安装
bitbake starry -c deploy       # 仅部署
# 请注意这些都是按顺序的，指定后面的会强行自动构建前面的

# 强制重新执行任务
bitbake starry -c compile -f   # 强制重新编译
bitbake starry -c fetch -f     # 强制重新下载

# 查看配方的所有任务
bitbake starry -c listtasks

# 查看任务依赖关系
bitbake starry -c build -g
dot -Tpng pn-depends.dot -o depends.png
```

### 清理相关

```bash
# 清理单个配方（推荐）
bitbake starry -c cleansstate

# 清理所有构建产物（慎用）
bitbake starry -c cleanall

# 只清理工作目录（不清理 sstate）
bitbake starry -c clean

# 清理整个 tmp 目录（慎用）
rm -rf tmp-baremetal/
```

### 调试相关

```bash
# 查看配方的所有变量
bitbake starry -e

# 查看特定变量的值
bitbake starry -e | grep ^CARGO_FEATURES=
bitbake starry -e | grep ^ARCEOS_SMP=

# 查看构建日志
bitbake starry -c compile
less tmp-baremetal/work/*/starry/*/temp/log.do_compile

# 进入开发 Shell（调试编译问题）
bitbake starry -c devshell
# 进入源码目录，环境变量已配置好
# 可以手动运行 cargo build 等命令

# 进入 Python Shell（调试 BitBake 逻辑）
bitbake starry -c devpyshell
```

### 信息查询

```bash
# 查看层列表
bitbake-layers show-layers

# 查看配方所在的层
bitbake-layers show-recipes starry

# 查看配方的依赖
bitbake starry -g
cat pn-depends.dot

# 查看配方的文件
bitbake-layers show-appends starry

# 查看变量的来源
bitbake-getvar ARCEOS_SMP -r starry
```

### 镜像相关

```bash
# 列出所有可构建的镜像
ls meta-starry/recipes-core/images/

# 构建镜像并运行测试
bitbake starry-test-image -c testimage

# 查看镜像内容
ls tmp-baremetal/work/*/starry-test-image/*/rootfs/

# 解压 rootfs 查看
cd /tmp
tar xf ~/starry-workspace/build/tmp-baremetal/deploy/images/*/starry-test-image-*.tar.gz
ls -lh
```

---

## 测试指南

集成了基于 Yocto OEQA 的完整测试套件，包含 CI 功能测试、LTP 系统调用测试和 stress-ng 压力测试。

### 1. 自动化全量测试（推荐）

使用 BitBake 的 `testimage` 任务自动完成构建、启动和测试流程：

```bash
bitbake starry-test-image -c testimage
```

此过程会自动：
- 启动 QEMU (使用 slirp 网络)
- 通过 vsock 建立测试控制通道
- 执行 ptest (CI/Stress/Daily)
- 执行 LTP 系统调用测试
- 执行 stress-ng 压力测试 (CPU/Memory/IO)
- 生成测试报告

### 2. 手动运行测试

进入系统后手动执行测试命令：

```bash
# 1. 启动测试镜像
runqemu starry-test-image nographic slirp

# 2. 在 StarryOS 终端中运行测试：

# 运行 ptest 套件 (CI/Stress/Daily)
ptest-runner

# 运行 LTP 系统调用测试
/opt/ltp/runltp -f syscalls -s syscall_basic -q

# 运行 CPU 压力测试
stress-ng --cpu 2 --timeout 10s --metrics-brief
```

---

## 切换架构

### 方法 1：修改配置文件

编辑 `build/conf/local.conf`：

```bash
# ARM64（默认）
MACHINE ?= "aarch64-qemu-virt"

# RISC-V 64
MACHINE = "riscv64-qemu-virt"

# LoongArch 64
MACHINE = "loongarch64-qemu-virt"

# x86_64
MACHINE = "x86_64-qemu-q35"
```

### 方法 2：命令行指定

```bash
# 临时切换架构（不修改配置）
MACHINE=riscv64-qemu-virt bitbake starry

# 查看当前 MACHINE
bitbake-getvar MACHINE
```

---

## 自定义构建

在 `build/conf/local.conf` 中添加：

```bash
# CPU 核心数（默认 4）
ARCEOS_SMP = "8"

# 日志级别
ARCEOS_LOG = "debug"

# 内存大小
ARCEOS_MEM = "2G"

# 调试符号
ARCEOS_DWARF = "y"

# 并行构建（根据主机 CPU 核心数调整）
BB_NUMBER_THREADS = "16"
PARALLEL_MAKE = "-j 16"

# 共享下载目录（团队协作）
DL_DIR = "/mnt/shared/yocto-downloads"

# 共享 sstate 缓存（10-20 倍加速）
SSTATE_DIR = "/mnt/shared/sstate-cache-${BUILD_ARCH}"
```

---


## 目录结构

```
meta-starry/
├── classes/                     # BitBake 构建类
│   ├── rust-kernel.bbclass
│   ├── arceos.bbclass
│   └── arceos-features.bbclass
│
├── conf/                        # 配置文件
│   ├── distro/starryos.conf
│   ├── machine/                 # 5 种架构配置
│   └── local.conf.sample
│
├── recipes-kernel/              # 内核配方
│   └── starryos/starry_git.bb
│
├── recipes-core/                # 核心配方
│   ├── images/                  # 镜像配方
│   └── packagegroups/           # 包组定义
│
├── recipes-devtools/            # 开发工具
│   └── rust/                    # Rust 工具链
│
├── docs/                        # 详细文档
│   ├── interfaces.md            # 接口说明
│   ├── faq.md                   # 常见问题
│   ├── team-sharing.md          # 团队协作
│   └── ...
│
└── setup-layers                 # 环境设置脚本
```

---

## 文档索引

### 使用
- **[接口说明](docs/interfaces.md)** - Classes、变量、配置文件详解
- **[团队协作](docs/team-sharing.md)** - 缓存共享、构建加速

---

## 贡献指南

```bash
cd ~/starry-workspace/meta-starry
git add .
git commit -m "feat: add new feature"
git push
```

**代码规范**：
- 赋值运算符两侧有空格：`VARIABLE = "value"`
- 使用新语法：`:append` 而不是 `_append`
- 路径使用标准变量：`${D}${bindir}` 而不是 `/usr/bin`

---

## 许可证

Apache License 2.0

---

## 维护者

- @kylin-x-kernel
- @yeanwang666
- @guoweikang
- @sunhaosheng

**最后更新**：2026-01-09
