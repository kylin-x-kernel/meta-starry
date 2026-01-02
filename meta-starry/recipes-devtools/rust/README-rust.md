# Rust 工具链与应用开发指南

## meta-starry 中的 Rust 支持

meta-starry 提供了**完全从源码构建**的 Rust 1.92.0 工具链，支持裸机内核（如 StarryOS）和未来可能的用户态应用开发。

### 当前实现状态

**✅ 已支持（源码构建）：**
- **rust-native**: rustc 1.92.0 + cargo（从 rustc-1.92.0-src.tar.xz 构建）
  - 包含 11 个工具：rustc, cargo, rustdoc, clippy, rustfmt, rust-analyzer 等
  - 使用 rust-llvm-native (LLVM 21.1.5) 作为后端
- **rust-std-{arch}-none-native**: 裸机标准库（从源码编译 library/core）
  - libcore, liballoc, libcompiler_builtins
  - 支持架构：aarch64, riscv64, loongarch64, x86_64
  - 目标规范：`{arch}-unknown-none-softfloat`
- **rust-kernel.bbclass**: 通用裸机内核构建基础类
- **arceos.bbclass**: ArceOS 特定构建类（继承 rust-kernel.bbclass）
- 多架构支持：aarch64, riscv64, loongarch64, x86_64

**⚠️ 当前未使用（为未来扩展保留）：**
- Linux 用户态 Rust 程序的标准库构建（`libstd-rs`）
- 交叉编译到 Linux 目标的 Rust 应用（`rust-cross`）

**📝 构建细节：**
- rust-std 使用 `RUSTC_BOOTSTRAP=1` 允许 stable 工具链使用 nightly 特性
- 自动链接到 rust-native 的 sysroot（通过 rust-kernel.bbclass）
- 编译时间：~8 秒（core + alloc + compiler_builtins）

### 架构限制

**支持的构建主机架构：**
- x86_64（主要测试平台）
- aarch64（理论支持）

**支持的目标架构：**
- 裸机目标：`*-unknown-none-*`（StarryOS 使用）
- Linux 目标：`*-unknown-linux-musl`（未来用户态应用）

---

## 构建 Rust 软件包的最佳实践

### 方案一：使用 cargo.bbclass（推荐）

大多数 Rust 项目使用 Cargo 作为构建工具，Yocto 提供了 `cargo.bbclass` 来简化集成。

**基本配方结构：**
```bash
# recipes-apps/my-rust-app/my-rust-app_1.0.bb
SUMMARY = "示例 Rust 应用"
LICENSE = "MIT"

SRC_URI = "git://github.com/example/my-app.git;protocol=https"
SRCREV = "${AUTOREV}"

inherit cargo

# cargo.bbclass 会自动：
# 1. 添加 rust 工具链依赖
# 2. 配置交叉编译环境
# 3. 执行 cargo build
# 4. 安装编译产物
```

**关键点：**
1. **继承 `cargo.bbclass`**：自动处理 Rust 构建环境
2. **依赖管理**：Yocto 会缓存和验证 crates.io 依赖
3. **交叉编译**：自动配置正确的 `--target` 和工具链路径

### 方案二：使用 cargo-bitbake 生成配方

对于复杂的 Rust 项目（有大量依赖），推荐使用 [cargo-bitbake](https://github.com/cardoe/cargo-bitbake) 工具：

**工作流程：**
```bash
# 1. 在 Rust 项目目录中生成配方
cargo bitbake

# 2. 生成的 .bb 文件会包含：
#    - 精确的依赖版本
#    - crates.io 索引快照
#    - 校验和（确保可重现构建）

# 3. 复制到 meta-starry 并根据注释调整
cp generated.bb meta-starry/recipes-apps/my-app/
```

**优势：**
- 自动生成所有依赖的 `SRC_URI`
- 锁定 crates.io 索引版本，确保构建可重现
- 包含完整的 SHA256 校验和

**注意：** 生成的配方需要根据其中的注释手动调整（如许可证、安装路径等）。

---

## 技术要点与注意事项

### 1. TARGET_SYS 必须与 BUILD_SYS 不同

**原因：** Rust 通过 target triple 跟踪编译选项，如果构建主机和目标使用相同的 triple，会导致配置冲突。

**解决方案：** Yocto 使用自定义的 target triple（如 `aarch64-oe-linux-musl`）而不是 Rust 原生的 triple（如 `aarch64-unknown-linux-musl`），确保构建环境和目标环境可区分。

**参考：** rust-lang/cargo#3349

### 2. 依赖 C 库的 Rust crate（`-sys` 包）

许多 Rust crate 通过 FFI 绑定 C 库（如 `openssl-sys`, `sqlite3-sys`），这类包需要特殊处理：

**在构建主机上：**
```bash
# recipes-apps/my-app/my-app.bb
DEPENDS += "openssl-native"  # 如果 build.rs 需要
```

**在目标设备上：**
```bash
RDEPENDS:${PN} += "openssl"  # 运行时依赖
```

**原理：** `-sys` crate 的 `build.rs` 会调用 `pkg-config` 查找 C 库，Yocto 需要确保：
1. 构建时：native 版本的库可用（供 build.rs 使用）
2. 运行时：target 版本的库安装到镜像中

### 3. 裸机目标 vs Linux 目标

**StarryOS（裸机内核）：**
```bash
# 不需要 libstd-rs
INHIBIT_DEFAULT_RUST_DEPS = "1"
DEPENDS += "rust-std-aarch64-none-native"
```

**Linux 用户态应用：**
```bash
# cargo.bbclass 自动添加
DEPENDS += "virtual/${TARGET_PREFIX}rust libstd-rs"
```

---


### 裸机标准库

为每个裸机目标提供专门的标准库配方：
```bash
recipes-devtools/rust/
├── rust-std-aarch64-none-native_1.92.0.bb
├── rust-std-riscv64-none-native_1.92.0.bb  # (如需要)
└── rust-std-loongarch64-none-native_1.92.0.bb  # (如需要)
```

---

## 未来扩展：用户态应用支持

当 meta-starry 需要添加运行在 Linux 系统上的 Rust 应用时（如网络服务、系统工具），执行以下步骤：

1. **确保 `libstd-rs` 配方正确配置**（当前已预留）
2. **创建应用配方**：
   ```bash
   recipes-apps/mqtt-bridge/mqtt-bridge.bb
   inherit cargo
   ```
3. **构建完整镜像**（包含内核 + rootfs + 应用）

---

## 许可证

与 Rust 项目保持一致
