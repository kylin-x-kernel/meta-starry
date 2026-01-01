# StarryOS Linux Userspace 支持路线图

## 📋 当前状态（Phase 1 - Bare-Metal Kernel）

### ✅ 已完成
- [x] Bare-metal 内核构建（aarch64, riscv64, loongarch64, x86_64）
- [x] 预编译工具链（rustc-bin, cargo-bin 1.92.0）
- [x] Bare-metal stdlib（rust-std-*-none-native）
- [x] SDK 配置（bare-metal 开发工具链）
- [x] 构建辅助工具（axconfig-gen, cargo-axplat, cargo-binutils）

### 🔧 工具链架构
```
starry_git.bb (内核)
  ↓ inherit
arceos.bbclass (INHIBIT_DEFAULT_RUST_DEPS = "1")
  ↓ 直接依赖
rustc-bin-native + cargo-bin-native + rust-std-aarch64-none-native
```

---

## 🎯 Phase 2 - Linux Userspace 基础设施（准备中）

### 目标
让 StarryOS 内核能够运行普通的用户态应用程序（类似 Linux）。

### 架构变化
```
┌─────────────────────────┐
│  Rust Userspace App    │  ← 使用 std::fs, std::net, std::thread
│  (aarch64-unknown-      │
│   linux-gnu/musl)       │
├─────────────────────────┤
│  StarryOS 内核          │  ← 提供 syscall 接口（open/read/write/fork）
│  + lwext4 文件系统      │
├─────────────────────────┤
│      硬件 (ARM64)       │
└─────────────────────────┘
```

### 📦 需要的新配方

#### 2.1 Rust 标准库（Linux target）
**状态：已有，需验证**

- [ ] `libstd-rs_1.92.0.bb` - 从源码构建 Linux 用户态 Rust 标准库
- [ ] `rust-common.inc` - 生成 target spec JSON（TUNE_FEATURES → LLVM features）
- [ ] `rust-cross_1.92.0.bb` - 交叉编译器（为 Linux target）

**验证命令：**
```bash
# 检查配方是否能解析
bitbake -e libstd-rs | grep "^PV="
bitbake -e rust-cross | grep "^PN="

# 尝试构建（可能需要补充依赖）
bitbake libstd-rs -c compile
```

#### 2.2 C 标准库选择
**二选一：musl（推荐）或 glibc**

**Option A: musl（轻量级，适合嵌入式）**
- [ ] 验证 `meta-starry` 的 `TCLIBC = "musl"` 配置
- [ ] 确认 `musl` 配方可用（Poky 自带）
- [ ] 测试 `libstd-rs` 能否正确链接 musl

**Option B: glibc（兼容性好，体积大）**
- [ ] 修改 distro 配置为 `TCLIBC = "glibc"`
- [ ] 验证 `glibc` 配方可用

#### 2.3 系统调用接口（内核侧）
**需要在 StarryOS 内核中实现**

- [ ] 实现 POSIX 系统调用接口（参考 `arceos_posix_api`）
- [ ] 支持进程管理（fork/exec/wait）
- [ ] 支持文件系统（open/read/write，基于 lwext4）
- [ ] 支持网络（socket/bind/listen，基于 axnet）
- [ ] 支持线程（pthread，基于 axtask）

#### 2.4 示例用户态应用
- [ ] 创建简单的 Rust CLI 工具配方（如 `hello-userspace`）
- [ ] 创建文件系统测试工具（读写文件）
- [ ] 创建网络测试工具（HTTP 客户端/服务器）

### 📋 实施检查清单

#### Step 1: 验证 Linux Target 工具链
```bash
cd /home/yean/code/StarryYoctoProject/build

# 1. 检查 rust-cross 能否构建
bitbake rust-cross -c do_rust_gen_targets

# 2. 检查生成的 JSON 文件
ls tmp/work/*/rust-cross/*/targets/*.json
cat tmp/work/*/rust-cross/*/targets/aarch64-poky-linux.json

# 3. 验证 libstd-rs
bitbake libstd-rs -c fetch
bitbake libstd-rs -c compile
```

#### Step 2: 选择 C 库并测试
```bash
# Option A: 使用 musl
echo 'TCLIBC = "musl"' >> conf/local.conf
bitbake musl

# Option B: 使用 glibc（默认）
bitbake glibc
```

#### Step 3: 创建测试用户态应用
```bash
# 创建配方 recipes-extended/hello-starry/hello-starry_0.1.bb
# 内容：简单的 Rust 应用，使用 std::fs
bitbake hello-starry
```

#### Step 4: 内核侧实现系统调用
```bash
# 修改 StarryOS 源码，启用 posix-api feature
# 在 starry_git.bb 中添加：
# CARGO_FEATURES:append = " posix-api"
```

---

##  Phase 3 - 完整用户态生态

### 目标
构建完整的用户态应用生态系统。

### 计划内容
- [ ] 集成 BusyBox（基础命令行工具）
- [ ] 支持动态链接（.so 库）
- [ ] 实现 init 进程
- [ ] 支持脚本解释器（sh, Python）
- [ ] 网络服务（sshd, httpd）
- [ ] 包管理器（opkg）

---

## 📚 技术参考

### Rust Target Triple 对比
| 场景 | Target Triple | 需要的库 | 配方 |
|------|--------------|---------|------|
| **Bare-Metal 内核** | `aarch64-unknown-none-softfloat` | core + alloc | `rust-std-aarch64-none-native` |
| **Linux Userspace** | `aarch64-unknown-linux-gnu` | std (依赖 glibc) | `libstd-rs` + `rust-cross` |
| **Linux Userspace (musl)** | `aarch64-unknown-linux-musl` | std (依赖 musl) | `libstd-rs` + `rust-cross` |

### 关键配方依赖链
```
用户态应用 (hello-starry.bb)
  ↓ DEPENDS
libstd-rs (Linux target Rust stdlib)
  ↓ DEPENDS
rust-cross (交叉编译器 + target JSON)
  ↓ DEPENDS
musl/glibc (C 标准库)
  ↓
StarryOS 内核（提供 syscall）
```

---

## 🔍 故障排查

### 问题 1: libstd-rs 构建失败
**症状：** `error: could not find system library 'c'`

**原因：** Rust std 依赖 libc，但 Yocto sysroot 中找不到

**解决：**
```bash
# 确保 DEPENDS 包含 C 库
# 在 libstd-rs.inc 中：
DEPENDS:append = " virtual/libc"
```

### 问题 2: rust-cross 生成的 JSON 不正确
**症状：** 编译时报错 `unknown target CPU`

**原因：** `rust-common.inc` 中 `llvm_cpu()` 函数未正确映射

**解决：**
```bash
# 检查 TARGET_LLVM_CPU 变量
bitbake -e rust-cross | grep "^TARGET_LLVM_CPU="

# 修改 rust-common.inc 的 CPU 映射表
```

### 问题 3: 用户态应用运行时找不到符号
**症状：** `undefined symbol: __libc_start_main`

**原因：** 动态链接器路径不正确

**解决：**
```bash
# 检查应用的动态链接器
readelf -l hello-starry | grep interpreter

# 确保 rootfs 中有正确的 /lib/ld-*.so
```

---

##  时间线

| 阶段 | 时间 | 里程碑 |
|------|------|--------|
| **Phase 1** | ✅ 已完成 | Bare-metal 内核可用 |
| **Phase 2.1** | Week 1-2 | 验证 libstd-rs/rust-cross 可构建 |
| **Phase 2.2** | Week 3-4 | 实现基础系统调用（open/read/write） |
| **Phase 2.3** | Week 5-6 | 第一个用户态应用成功运行 |
| **Phase 3** | Month 2-3 | 完整生态系统 |

---

##  贡献指南

修改清单：
1. 实现新的系统调用 → 更新 `StarryOS/arceos/api/arceos_posix_api/`
2. 添加用户态应用 → 创建 `meta-starry/recipes-extended/<app>/`
3. 修改工具链配置 → 更新 `meta-starry/recipes-devtools/rust/`
4. 更新此文档 → `git commit -m "docs: update userspace roadmap"`

---

**维护者：** yeanwang 
**最后更新：** 2025-12-31  
**状态：** Phase 1 完成，Phase 2 准备中
