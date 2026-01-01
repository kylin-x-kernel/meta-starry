# 快速参考：Bare-Metal vs Linux Userspace

##  当前状态（Phase 1 完成）

✅ **已实现：Bare-Metal 内核构建**

```bash
# 构建 StarryOS 内核
bitbake starryos

# 多架构支持
MACHINE=aarch64-qemu-virt bitbake starryos
MACHINE=riscv64-qemu-virt bitbake starryos
MACHINE=loongarch64-qemu-virt bitbake starryos
MACHINE=x86_64-qemu-q35 bitbake starryos
```

---

## 📊 架构对比

| 特性 | Bare-Metal（现在） | Linux Userspace（未来） |
|------|-------------------|----------------------|
| **编译目标** | `*-unknown-none-*` | `*-unknown-linux-*` |
| **Rust 库** | `core` + `alloc` | `std` (完整) |
| **C 库** | ❌ 无需 | ✅ musl/glibc |
| **系统调用** | ❌ 直接硬件 | ✅ syscall 接口 |
| **应用程序** | ❌ 无（内核即应用） | ✅ 独立可执行文件 |
| **文件系统** | ✅ lwext4（内核内） | ✅ lwext4（用户态访问） |
| **网络栈** | ✅ axnet（内核内） | ✅ axnet（用户态 socket） |
| **工具链** | rust-native + rust-std-*-none | rustc-bin + libstd-rs + rust-cross |

---

##  关键架构

### 为什么从源码构建 Rust 工具链？

**优势：**
- ✅ 完全控制构建配置和优化选项
- ✅ 与系统 LLVM 集成（避免重复依赖）
- ✅ 可自定义目标规范和特性
- ✅ 符合 Yocto 从源码构建的理念

**实现细节：**
```bash
# rust-llvm-native: 单独构建 LLVM 后端
# rust-native: 使用 rust-llvm + bootstrap 构建 rustc + cargo
# rust-std-{arch}-none-native: 直接编译 library/core

# 总构建时间（首次）：~30-60 分钟
# - rust-llvm: ~20 分钟
# - rust-native: ~8-10 分钟  
# - rust-std (每个架构): ~8 秒
```

### rust-kernel.bbclass 设计

通用裸机内核构建类，提供：
- 自动 Rust 工具链环境配置
- 自动链接 std 库到 sysroot
- Cargo 构建环境设置
- 默认 do_configure/compile/install

使用示例：
```bash
inherit rust-kernel

RUST_TARGET = "aarch64-unknown-none-softfloat"
KERNEL_ARCH = "aarch64"
# rust-kernel.bbclass 会自动添加 rust-native 和 rust-std-aarch64-none-native
```

---

## 🔧 工具链配方速查

### Bare-Metal（当前使用 - 源码构建）

```bash
# 编译器和工具（源码构建）
recipes-devtools/rust/rust-llvm-native_1.92.0.bb     # LLVM 21.1.5 后端
recipes-devtools/rust/rust-native_1.92.0.bb          # rustc + cargo + 9 个工具
recipes-devtools/rust/cargo-native_1.92.0.bb         # cargo 独立配方（继承 rust-native）

# 标准库（源码构建 - 按架构）
recipes-devtools/rust/rust-std-aarch64-none-native_1.92.0.bb      # ARM64
recipes-devtools/rust/rust-std-riscv64-none-native_1.92.0.bb      # RISC-V 64
recipes-devtools/rust/rust-std-loongarch64-none-native_1.92.0.bb  # LoongArch64
recipes-devtools/rust/rust-std-x86_64-none-native_1.92.0.bb       # x86_64

# 构建系统类
classes/rust-kernel.bbclass                          # 通用 Rust 裸机内核构建
classes/arceos.bbclass                               # ArceOS 特定构建（继承 rust-kernel）

# 编译输出
- libcore-*.rlib (~48MB)
- liballoc-*.rlib (~6.4MB)
- libcompiler_builtins-*.rlib (~2.6MB)
```

### Linux Userspace（未来扩展）

```bash
# 交叉编译工具链
recipes-devtools/rust/rust-cross_1.92.0.bb              # 交叉编译器
recipes-devtools/rust/rust-cross-canadian_1.92.0.bb     # SDK 用交叉编译器

# Linux 用户态标准库
recipes-devtools/rust/libstd-rs_1.92.0.bb               # 从源码构建
recipes-devtools/rust/libstd-rs.inc                     # 通用配置

# 配置文件
recipes-devtools/rust/rust-common.inc                   # Target spec 生成
```

---

##  下一步

### 构建 StarryOS 内核
```bash
cd /home/yean/code/StarryYoctoProject/build
source ../poky/oe-init-build-env

# 构建 StarryOS
bitbake starry

# 多架构构建
MACHINE=aarch64-qemu-virt bitbake starry
MACHINE=riscv64-qemu-virt bitbake starry
```

### 验证工具链
```bash
# 检查 rust-native
ls build/tmp-musl/sysroots-components/x86_64/rust-native/usr/bin/
# 应该看到: rustc, cargo, clippy-driver, rustdoc, rustfmt 等

# 检查 rust-std
ls build/tmp-musl/sysroots-components/x86_64/rust-std-aarch64-none-native/usr/lib/rustlib/aarch64-unknown-none-softfloat/lib/
# 应该看到: libcore-*.rlib, liballoc-*.rlib, libcompiler_builtins-*.rlib
```

### 创建自定义 Rust 裸机内核
```bash
# 创建配方
cat > ../meta-starry/recipes-kernel/my-kernel/my-kernel_0.1.bb << 'EOF'
SUMMARY = "My Rust Bare-Metal Kernel"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=xxx"

SRC_URI = "git://github.com/your/my-kernel.git;protocol=https;branch=main"
SRCREV = "${AUTOREV}"
S = "${WORKDIR}/git"

# 继承 rust-kernel.bbclass 获得 Rust 工具链支持
inherit rust-kernel deploy

RUST_TARGET = "aarch64-unknown-none-softfloat"
KERNEL_ARCH = "aarch64"
CARGO_FEATURES = "qemu"

do_install() {
    install -d ${D}/boot
    install -m 0755 ${B}/target/${RUST_TARGET}/release/my-kernel ${D}/boot/my-kernel.elf
}

do_deploy() {
    install -d ${DEPLOYDIR}
    install -m 0755 ${D}/boot/my-kernel.elf ${DEPLOYDIR}/
}
addtask deploy after do_install
EOF

# 构建
bitbake my-kernel
```

---

##  文档链接

- **Rust 开发指南**: [recipes-devtools/rust/README-rust.md](../recipes-devtools/rust/README-rust.md)
- **Linux Userspace 路线图**: [docs/USERSPACE-ROADMAP.md](USERSPACE-ROADMAP.md)
- **主 README**: [../READEME.md](../READEME.md)

---

##  常见问题

### Q: 为什么从源码构建而不是用预编译包？
**A:** 
1. 完全控制构建配置和优化
2. 与系统 LLVM 集成，避免重复依赖
3. 可自定义目标规范
4. 符合 Yocto 从源码构建的理念
5. 对于嵌入式系统，源码构建更可靠

### Q: rust-kernel.bbclass 和 arceos.bbclass 的区别？
**A:** 
- **rust-kernel.bbclass**: 通用 Rust 裸机内核构建基础类
  - 提供 Rust 工具链配置
  - Cargo 环境设置
  - 默认构建任务
- **arceos.bbclass**: ArceOS 特定构建类（继承 rust-kernel）
  - 添加 ArceOS 平台配置生成
  - StarryOS 支持
  - lwext4_rust C 代码编译支持

### Q: rust-std 为什么只编译 core？
**A:** 对于裸机目标（`*-unknown-none-*`），只需要 core + alloc。cargo 会在编译 core 时自动构建 alloc 和 compiler_builtins 作为依赖，所有 .rlib 文件都在 library/core 的输出目录中。

### Q: 如何添加新的裸机架构支持？
**A:** 
1. 创建 `rust-std-{arch}-none-native_1.92.0.bb`
2. 设置 `RUST_TARGET = "{arch}-unknown-none-softfloat"`
3. 其他从 rust-std-aarch64-none-native.bb 复制即可

---

**最后更新:** 2025-12-31  
**维护者:** meta-starry team
