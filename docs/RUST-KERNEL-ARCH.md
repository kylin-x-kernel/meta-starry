# Rust 裸机内核构建架构

## 核心组件

### 工具链（源码构建）

```
rust-llvm-native (LLVM 21.1.5)
    ↓
rust-native (rustc 1.92.0 + cargo + 9 tools)
    ↓
rust-std-{arch}-none-native (core + alloc + compiler_builtins)
```

**构建时间：**
- rust-llvm-native: ~20 分钟
- rust-native: ~8-10 分钟
- rust-std-{arch}-none-native: ~8 秒/架构

**构建输出：**
- rustc, cargo, clippy, rustfmt, rust-analyzer 等 11 个工具
- libcore-*.rlib (~48MB)
- liballoc-*.rlib (~6.4MB)
- libcompiler_builtins-*.rlib (~2.6MB)

### 构建系统类

```
rust-kernel.bbclass (通用基础类)
    ↓
arceos.bbclass (ArceOS 特定)
    ↓
starry_git.bb (StarryOS 内核)
```

**rust-kernel.bbclass 职责：**
- ✅ 自动添加 rust-native 和 rust-std-{arch}-none-native 依赖
- ✅ 配置 Rust 工具链环境（PATH, RUSTC_BOOTSTRAP 等）
- ✅ 自动链接 std 库到 rust-native sysroot
- ✅ 生成 .cargo/config.toml
- ✅ 提供默认 do_configure/compile/install 任务
- ✅ 禁用不需要的打包任务

**arceos.bbclass 职责：**
- ✅ 生成 ArceOS 平台配置（.axconfig.toml）
- ✅ StarryOS 支持（自动检测 arceos submodule）
- ✅ lwext4_rust C 代码编译支持
- ✅ ArceOS features 和环境变量管理

## 使用示例

### 创建简单的 Rust 裸机内核

```bash
# recipes-kernel/simple-kernel/simple-kernel_0.1.bb

SUMMARY = "Simple Rust Bare-Metal Kernel"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=xxx"

SRC_URI = "git://github.com/your/simple-kernel.git;protocol=https;branch=main"
SRCREV = "${AUTOREV}"
S = "${WORKDIR}/git"

# 继承 rust-kernel.bbclass
inherit rust-kernel deploy

# 设置目标架构
RUST_TARGET = "aarch64-unknown-none-softfloat"
KERNEL_ARCH = "aarch64"

# 可选：Cargo features
CARGO_FEATURES = "qemu console"

# 安装内核
do_install() {
    install -d ${D}/boot
    install -m 0755 ${B}/target/${RUST_TARGET}/release/simple-kernel ${D}/boot/kernel.elf
}

# 部署到 DEPLOYDIR
do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 0755 ${D}/boot/kernel.elf ${DEPLOYDIR}/
}
addtask deploy after do_install
```

**rust-kernel.bbclass 自动处理：**
- DEPENDS += "rust-native rust-std-aarch64-none-native"
- 配置 Rust 环境变量
- 链接 std 库到 sysroot
- 执行 `cargo build --target aarch64-unknown-none-softfloat --release`

### 创建 ArceOS 内核

```bash
# recipes-kernel/my-arceos/my-arceos_0.1.bb

SUMMARY = "My ArceOS-based Kernel"
LICENSE = "GPL-3.0-only"
LIC_FILES_CHKSUM = "file://LICENSE;md5=xxx"

SRC_URI = "git://github.com/your/my-arceos.git;protocol=https;branch=main"
SRCREV = "${AUTOREV}"
S = "${WORKDIR}/git"

# 继承 arceos.bbclass (自动继承 rust-kernel)
inherit arceos deploy

# ArceOS 配置
ARCEOS_ARCH = "aarch64"
ARCEOS_PLAT_PACKAGE = "axplat-aarch64-qemu-virt"
RUST_TARGET = "aarch64-unknown-none-softfloat"
ARCEOS_SMP = "4"
ARCEOS_LOG = "warn"
CARGO_FEATURES = "qemu"

do_install() {
    install -d ${D}/boot
    install -m 0755 ${B}/target/${RUST_TARGET}/release/my-arceos ${D}/boot/my-arceos.elf
}

do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 0755 ${D}/boot/my-arceos.elf ${DEPLOYDIR}/
}
addtask deploy after do_install
```

**arceos.bbclass 额外处理：**
- 生成 .axconfig.toml 平台配置
- 检测 StarryOS 结构
- 配置 lwext4_rust 的 musl 工具链
- 导出 ArceOS 特定环境变量

## 架构优势

### 1. 分层设计
```
rust-kernel.bbclass (通用功能)
    ↑
arceos.bbclass (ArceOS 特定)
    ↑
其他内核 .bbclass (未来可扩展)
```

### 2. 自动化依赖
- 不需要手动添加 rust-native 或 rust-std
- 自动根据 KERNEL_ARCH 选择正确的 rust-std 包
- 自动链接 std 库到 sysroot

### 3. 环境隔离
- Rust 工具链独立于系统 Rust
- 每个架构的 std 库独立打包
- 裸机和 Linux 用户态工具链互不干扰

### 4. 可维护性
- 通用逻辑在 rust-kernel.bbclass
- 特定逻辑在 arceos.bbclass
- 配方文件简洁明了（<30 行）

## 构建流程

```
bitbake starry
    ↓
解析 starry_git.bb
    ↓
inherit arceos
    ↓
arceos.bbclass → inherit rust-kernel
    ↓
rust-kernel.bbclass 添加依赖:
    - rust-native
    - rust-std-aarch64-none-native
    ↓
do_configure:
    - rust_kernel_setup_toolchain()
    - arceos_generate_config()
    - rust_kernel_setup_cargo()
    ↓
do_compile:
    - cargo build --target aarch64-unknown-none-softfloat --release
    ↓
do_install:
    - 安装 starry.elf
    ↓
do_deploy:
    - 部署到 DEPLOYDIR
```

## 文件结构

```
meta-starry/
├── classes/
│   ├── rust-kernel.bbclass          # 通用 Rust 裸机内核基础类
│   └── arceos.bbclass                # ArceOS 特定构建类
├── recipes-devtools/rust/
│   ├── rust-llvm-native_1.92.0.bb    # LLVM 后端
│   ├── rust-native_1.92.0.bb         # rustc + cargo
│   ├── rust-std-aarch64-none-native_1.92.0.bb
│   ├── rust-std-riscv64-none-native_1.92.0.bb
│   ├── rust-std-loongarch64-none-native_1.92.0.bb
│   ├── rust-std-x86_64-none-native_1.92.0.bb
│   └── README-rust.md                # Rust 工具链说明
├── recipes-kernel/starryos/
│   └── starry_git.bb                 # StarryOS 配方
└── docs/
    ├── QUICK-REFERENCE.md            # 快速参考
    └── RUST-KERNEL-ARCH.md           # 本文档
```

---

**最后更新:** 2025-01-01  
**维护者:** meta-starry team
