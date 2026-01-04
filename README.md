# meta-starry

**StarryOS Yocto 构建系统** - 使用 BitBake 完全复刻 StarryOS 的 Makefile 构建逻辑

[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Yocto](https://img.shields.io/badge/yocto-kirkstone-green.svg)](https://www.yoctoproject.org/)
[![Rust](https://img.shields.io/badge/rust-nightly--1.94.0-orange.svg)](https://www.rust-lang.org/)

---

##  项目简介

`meta-starry` 是 StarryOS 的 Yocto Project 构建层，实现了starry内核的构建过程。

### 设计原则

-  **复刻 Makefile 逻辑**：完全保持与原始 Makefile 构建的一致性
-  **预编译工具链**：使用 Rust nightly 预编译工具链，构建速度快
-  **Bare-Metal First**：仅裸机内核，暂不包含用户态

### 与 Makefile 的对应关系

| Makefile 组件 | Yocto 对应 | 说明 |
|:--------------|:-----------|:------|
| `StarryOS/Makefile` | `starry_git.bb` | 内核主配方 |
| `arceos/Makefile` | `arceos.bbclass` | ArceOS 构建逻辑 |
| `scripts/make/features.mk` | `arceos-features.bbclass` | Cargo 特性解析 |
| `scripts/make/config.mk` | `arceos_generate_config()` | 平台配置生成 |
| `scripts/make/platform.mk` | Machine 配置文件 | 平台变量设置 |

---

## 快速开始

### 环境要求

- Ubuntu 24.04 或兼容系统
- Python 3.8+
- Git
- 基础构建工具（gcc, make 等）

### 1. 克隆仓库

```bash
mkdir -p ~/starry-workspace
cd ~/starry-workspace
git clone https://github.com/kylin-x-kernel/meta-starry.git
```

### 2. 自动设置依赖层

```bash
cd meta-starry
./setup-layers
```

这会自动克隆所有依赖的 Yocto 层：
```
~/starry-workspace/
  ├── meta-starry/          # 本项目
  ├── poky/                 # Yocto 核心
  └── meta-openembedded/    # OpenEmbedded 扩展
```

### 3. 初始化构建环境

```bash
cd ~/starry-workspace
source poky/oe-init-build-env build
```

首次初始化会自动复制 `meta-starry` 的示例配置：
- `conf/local.conf.sample` → `build/conf/local.conf`
- `conf/bblayers.conf.sample` → `build/conf/bblayers.conf`

### 4. 构建 StarryOS 内核

```bash
# 构建内核
bitbake starry
```

### 5. 构建产物

构建成功后，产物位于：
```
build/tmp-baremetal/deploy/images/aarch64-qemu-virt/
  ├── starry.elf  (86MB) - 包含 DWARF 调试信息的 ELF
  └── starry.bin  (38MB) - 二进制镜像
```

---

##  架构设计

### 核心分层架构

系统采用分层设计，从底层到顶层依次为：

1. **工具链层** - `rust-prebuilt-native`
   - Rust nightly 1.94.0 + LLVM tools
   - 提供编译器和二进制工具

2. **Rust 内核通用层** - `rust-kernel.bbclass`
   - 工具链配置、Cargo 环境
   - 通用 Rust 内核构建逻辑

3. **ArceOS 集成层** - `arceos.bbclass`
   - 平台配置、环境变量、musl 适配
   - ArceOS 特定构建逻辑

4. **特性解析层** - `arceos-features.bbclass`
   - Cargo features 自动生成
   - 复刻 Makefile 特性解析逻辑

5. **内核配方层** - `starry_git.bb`
   - StarryOS 主配方
   - 整合所有构建类

### 目录结构

```
meta-starry/
├── classes/                          # 构建类
│   ├── rust-kernel.bbclass          # Rust 内核通用构建
│   ├── arceos.bbclass                # ArceOS 特定逻辑
│   └── arceos-features.bbclass       # Cargo 特性解析
│
├── conf/                             # 配置文件
│   ├── layer.conf                    # 层配置
│   ├── templateconf.cfg              # 模板配置标记
│   ├── local.conf.sample             # 本地配置模板
│   ├── bblayers.conf.sample          # 层配置模板
│   ├── distro/                       # 发行版配置
│   │   ├── starryos.conf             # StarryOS 发行版
│   │   └── include/
│   │       ├── arceos-defaults.inc   # ArceOS 默认值
│   │       └── tclibc-none.inc       # Bare-metal C 库
│   └── machine/                      # 机器配置
│       ├── include/
│       │   └── arceos-machine-common.inc  # 机器配置公共部分
│       ├── aarch64-qemu-virt.conf    # ARM64 QEMU
│       ├── aarch64-raspi.conf        # ARM64 Raspberry Pi
│       ├── riscv64-qemu-virt.conf    # RISC-V 64 QEMU
│       ├── loongarch64-qemu-virt.conf # LoongArch 64 QEMU
│       └── x86_64-qemu-q35.conf      # x86_64 QEMU
│
├── recipes-devtools/                 # 开发工具
│   ├── rust/
│   │   ├── rust-prebuilt-native_1.94.0.bb  # 预编译工具链
│   │   └── README-rust.md            # Rust 工具链说明
│   ├── axconfig-gen/                 # 平台配置生成器
│   ├── cargo-binutils/               # Rust 二进制工具
│   └── flex/                         # Flex 依赖修复
│
├── recipes-kernel/                   # 内核配方
│   └── starryos/
│       ├── files/                    # 补丁文件
│       │   └── 0001-use-stable-rust-toolchain.patch
│       ├── starry_git.bb             # StarryOS 主配方
│       └── starry-targets.inc        # 多目标配置
│
├── recipes-extended/                 # 扩展配方
│   └── xz/                           # xz 工具修复
│
├── recipes-support/                  # 支持配方
│   └── attr/                         # attr 工具修复
│
├── files/                            # 辅助文件
│   └── musl-headers/                 # musl 头文件（lwext4 使用）
│
├── lib/                              # Python 库
│   └── crate.py                      # Crate 解析工具
│
├── docs/                             # 文档
│   └── learn.md                      # 学习文档
│
├── setup-layers                      # 环境设置脚本
├── setup-layers.json                 # 依赖层配置
└── README.md                         # 本文件
```

---

##  核心组件说明

### 1. Rust 工具链

**配方**: `recipes-devtools/rust/rust-prebuilt-native_1.94.0.bb`

- **版本**: Rust nightly 1.94.0 (2026-01-02)
- **来源**: Rust 官方预编译二进制
- **包含**:
  - `rustc` - Rust 编译器
  - `cargo` - 包管理器
  - LLVM 工具链（rust-objcopy, rust-objdump 等）
  - 4 个架构的 bare-metal std 库：
    - `aarch64-unknown-none-softfloat`
    - `riscv64gc-unknown-none-elf`
    - `loongarch64-unknown-none-softfloat`
    - `x86_64-unknown-none`

**优势**:
- ⚡ 快速：无需从源码编译（节省 1+ 小时）
-  稳定：版本锁定，构建可重现
-  完整：包含所有必需的工具和库

### 2. 构建类（Classes）

#### `rust-kernel.bbclass` - Rust 内核通用构建层

**职责**:
- 配置 Rust 工具链路径
- 设置 `RUSTC_BOOTSTRAP=1`
- 配置 Cargo 环境（`.cargo/config.toml`）
- 提供默认的 `do_configure`, `do_compile`, `do_install`

**变量**:
- `RUST_TARGET` - Rust 目标三元组
- `KERNEL_ARCH` - 内核架构

#### `arceos.bbclass` - ArceOS 集成层

**职责**:
- 生成 `.axconfig.toml` 平台配置文件
- 导出 ArceOS 环境变量
- 配置 musl 工具链 wrapper（lwext4_rust 需要）
- 设置 RUSTFLAGS（链接器脚本、DWARF 等）

**核心函数**:
- `arceos_generate_config()` - 生成平台配置
- `arceos_setup_musl_wrapper()` - musl 工具链适配

#### `arceos-features.bbclass` - Cargo 特性解析层

**职责**:
- 复刻 `features.mk` 逻辑
- 自动生成 `CARGO_FEATURES` 变量
- 支持 `defplat`/`myplat`, `dwarf`, `smp` 等特性

**输入变量**:
- `ARCEOS_PLAT_PACKAGE` - 平台包名
- `ARCEOS_SMP` - CPU 核心数
- `ARCEOS_DWARF` - 调试符号
- `ARCEOS_FEATURES` - 额外特性

**输出**:
- `CARGO_FEATURES` - 完整的 Cargo features 字符串

### 3. 内核配方

**配方**: `recipes-kernel/starryos/starry_git.bb`

**关键设置**:
```python
# Git 源
SRC_URI = "git://github.com/kylin-x-kernel/StarryOS.git;protocol=https;branch=main"

# 继承构建类
inherit arceos-features arceos deploy

# ArceOS 配置
ARCEOS_NO_AXSTD = "y"
ARCEOS_AX_LIB = "axfeat"
ARCEOS_DWARF = "y"
ARCEOS_SMP = "4"
```

**构建流程**:
1. `do_configure` - 生成 `.axconfig.toml`
2. `do_compile` - 执行 `cargo build --features "..."`
3. `do_install` - 安装 ELF + 生成二进制镜像
4. `do_deploy` - 部署到 deploy 目录

---

##  配置说明

### Machine 配置

每个架构都有独立的 machine 配置文件，对应 Makefile 的 `ARCH` 变量。

**示例**: `conf/machine/aarch64-qemu-virt.conf`
```python
# 架构设置
require conf/machine/include/arceos-machine-common.inc
require conf/machine/include/arm/arch-armv8a.inc

# ArceOS 变量（对应 Makefile）
ARCEOS_ARCH = "aarch64"
ARCEOS_PLATFORM = "aarch64-qemu-virt"
ARCEOS_PLAT_PACKAGE = "axplat-aarch64-qemu-virt"
RUST_TARGET = "aarch64-unknown-none-softfloat"

# SMP 配置
ARCEOS_SMP ?= "4"
```

### 本地配置

**文件**: `conf/local.conf.sample`

**关键设置**:
```python
# 机器和发行版
MACHINE = "aarch64-qemu-virt"
DISTRO = "starryos"

# 并行构建
BB_NUMBER_THREADS = "30"
PARALLEL_MAKE = "-j 30"

# 可选覆盖
ARCEOS_SMP ?= "4"
ARCEOS_LOG ?= "info"
ARCEOS_MEM ?= "1G"
```

---

##  构建目标

### 基本目标

```bash
# 构建内核
bitbake starry

# 清理构建
bitbake starry -c cleansstate

# 仅编译
bitbake starry -c compile

# 查看任务列表
bitbake starry -c listtasks
```

### 多架构构建

```bash
# ARM64
MACHINE=aarch64-qemu-virt bitbake starry

# RISC-V 64
MACHINE=riscv64-qemu-virt bitbake starry

# LoongArch 64
MACHINE=loongarch64-qemu-virt bitbake starry

# x86_64
MACHINE=x86_64-qemu-q35 bitbake starry
```

### 自定义构建

```bash
# 单核构建
echo 'ARCEOS_SMP = "1"' >> conf/local.conf
bitbake starry

# 修改日志级别
echo 'ARCEOS_LOG = "debug"' >> conf/local.conf
bitbake starry

# 调整内存大小
echo 'ARCEOS_MEM = "2G"' >> conf/local.conf
bitbake starry
```

---

##  开发指南

### 添加新的平台

1. 创建 machine 配置：`conf/machine/your-platform.conf`
2. 设置 `ARCEOS_PLATFORM` 和 `RUST_TARGET`
3. 构建：`MACHINE=your-platform bitbake starry`

### 修改构建逻辑

- **通用 Rust 内核逻辑** → 修改 `classes/rust-kernel.bbclass`
- **ArceOS 特定逻辑** → 修改 `classes/arceos.bbclass`
- **特性解析逻辑** → 修改 `classes/arceos-features.bbclass`

### 调试构建

```bash
# 查看构建日志
bitbake starry -c compile
less tmp-baremetal/work/*/starry/*/temp/log.do_compile

# 查看 Cargo features
bitbake starry -e | grep ^CARGO_FEATURES=

# 查看环境变量
bitbake starry -e | grep ^ARCEOS_
```

---

##  文档索引



---

##  贡献指南

### 提交代码修改

```bash
cd ~/starry-workspace/meta-starry
git add .
git commit -m "feat: add new feature"
git push
```

### 更新依赖层

只有在升级 Yocto 版本或添加新的外部层时才需要：

```bash
# 编辑 setup-layers.json
vim setup-layers.json

# 提交更新
git add setup-layers.json setup-layers
git commit -m "chore: update poky to new version"
git push
```

---

##  进阶文档

### 团队协作与构建加速
 **[团队共享与缓存配置](docs/team-sharing.md)**
- SSTATE_DIR 共享配置
- DL_DIR 共享配置（节省下载时间）
- sstate-cache 管理与清理

### 其他文档
-  [学习资源](docs/learn.md) - Yocto 学习路径

---

##  项目状态

### 当前阶段
✅ **Phase 1: Bare-Metal Kernel** (已完成)
- StarryOS 内核构建
- 多架构支持（aarch64, riscv64, loongarch64, x86_64）
- DWARF 调试支持
-  Makefile 构建逻辑

### 未来规划
 **Phase 2: Linux Userspace** (规划中)
- 用户态应用支持
- 完整的系统镜像
- 根文件系统



---

##  致谢

- [Yocto Project](https://www.yoctoproject.org/)
- [StarryOS](https://github.com/Starry-OS/StarryOS)
- [ArceOS](https://github.com/arceos-org/arceos)

---

## 📜 许可证

Apache License 2.0

---

**维护者**: @kylin-x-kernel  @yeanwang666   @guoweikang
**最后更新**: 2026-01-04
