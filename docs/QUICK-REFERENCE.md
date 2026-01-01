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
| **工具链** | rustc-bin + rust-std-*-none | rustc-bin + libstd-rs + rust-cross |

---

## 🔧 工具链配方速查

### Bare-Metal（当前使用）

```bash
# 编译器
recipes-devtools/rust/rustc-bin_1.92.0.bb         # Rust 编译器
recipes-devtools/cargo/cargo-bin_1.92.0.bb        # Cargo 包管理器

# 标准库（按架构）
recipes-devtools/rust/rust-std-aarch64-none_1.92.0.bb      # ARM64
recipes-devtools/rust/rust-std-riscv64-none_1.92.0.bb      # RISC-V 64
recipes-devtools/rust/rust-std-loongarch64-none_1.92.0.bb  # LoongArch64
recipes-devtools/rust/rust-std-x86_64-none_1.92.0.bb       # x86_64

# SDK 打包（供开发者使用）
BBCLASSEXTEND = "native nativesdk"  # 每个配方都支持
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

### 验证工具链可用性
```bash
cd /home/yean/code/StarryYoctoProject/build
source ../poky/oe-init-build-env

# 运行检查脚本
../meta-starry/scripts/check-userspace-toolchain.sh
```

### 测试 Linux Userspace 工具链
```bash
# 1. 验证 libstd-rs 能否构建
bitbake libstd-rs -c fetch
bitbake libstd-rs -c compile

# 2. 验证 rust-cross
bitbake rust-cross -c do_rust_gen_targets

# 3. 检查生成的 target spec JSON
ls tmp/work/*/rust-cross/*/targets/*.json
cat tmp/work/*/rust-cross/*/targets/aarch64-poky-linux.json
```

### 创建第一个用户态应用（示例）
```bash
# 创建配方
mkdir -p ../meta-starry/recipes-extended/hello-starry
cat > ../meta-starry/recipes-extended/hello-starry/hello-starry_0.1.bb << 'EOF'
SUMMARY = "Hello StarryOS - First Rust userspace app"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=xxx"

SRC_URI = "git://github.com/your/hello-starry.git;protocol=https;branch=main"
SRCREV = "${AUTOREV}"

S = "${WORKDIR}/git"

inherit cargo

DEPENDS = "libstd-rs"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${B}/target/${RUST_TARGET_SYS}/release/hello-starry ${D}${bindir}/
}
EOF

# 构建
bitbake hello-starry
```

---

##  文档链接

- **Rust 开发指南**: [recipes-devtools/rust/README-rust.md](../recipes-devtools/rust/README-rust.md)
- **Linux Userspace 路线图**: [docs/USERSPACE-ROADMAP.md](USERSPACE-ROADMAP.md)
- **主 README**: [../READEME.md](../READEME.md)

---

##  常见问题

### Q: 为什么 bare-metal 不需要 libstd-rs？
**A:** Bare-metal 使用 Rust 官方预编译的 `rust-std-*-none`，不依赖操作系统。libstd-rs 是为 Linux 用户态准备的，需要链接 libc 和操作系统系统调用。

### Q: rust-cross 和 rust-cross-canadian 的区别？
**A:** 
- `rust-cross`: 本地交叉编译器（在构建机上使用）
- `rust-cross-canadian`: SDK 用交叉编译器（打包给开发者）

### Q: 现在可以删除 libstd-rs/rust-cross 吗？
**A:** ❌ 不建议删除。保留它们是为了：
1. 与 Poky 结构保持一致
2. 未来扩展 Linux userspace 时无需重新添加
3. 它们不会影响当前的 bare-metal 构建（已通过 INHIBIT_DEFAULT_RUST_DEPS 隔离）

---

**最后更新:** 2025-12-31  
**维护者:** meta-starry team
