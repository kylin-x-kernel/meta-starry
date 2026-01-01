#!/bin/bash
# 验证 meta-starry 的 Linux Userspace 工具链是否就绪
# 用法: ./scripts/check-userspace-toolchain.sh

set -e

echo "========================================"
echo "meta-starry Linux Userspace 工具链检查"
echo "========================================"
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查是否在 Yocto 构建环境中
if [ -z "$BUILDDIR" ]; then
    echo -e "${RED}❌ 错误：未初始化 Yocto 构建环境${NC}"
    echo "请先运行: source poky/oe-init-build-env build"
    exit 1
fi

echo -e "${GREEN}✓${NC} Yocto 构建环境已就绪: $BUILDDIR"
echo ""

# 检查函数
check_recipe() {
    local recipe=$1
    local desc=$2
    
    echo -n "检查 $desc ($recipe)... "
    if bitbake -e $recipe &>/dev/null; then
        echo -e "${GREEN}✓ 可用${NC}"
        return 0
    else
        echo -e "${RED}✗ 不可用${NC}"
        return 1
    fi
}

check_variable() {
    local var=$1
    local recipe=$2
    local desc=$3
    
    echo -n "  检查 $desc ($var)... "
    local value=$(bitbake -e $recipe 2>/dev/null | grep "^$var=" | cut -d'"' -f2)
    if [ -n "$value" ]; then
        echo -e "${GREEN}✓${NC} $value"
        return 0
    else
        echo -e "${YELLOW}⚠ 未设置${NC}"
        return 1
    fi
}

echo "================================"
echo "1. Bare-Metal 工具链（当前使用）"
echo "================================"

check_recipe "rustc-bin-native" "Rust 编译器（预编译）"
check_recipe "cargo-bin-native" "Cargo 包管理器（预编译）"
check_recipe "rust-std-aarch64-none-native" "AArch64 Bare-Metal Stdlib"
check_recipe "rust-std-riscv64-none-native" "RISC-V 64 Bare-Metal Stdlib"
check_recipe "rust-std-loongarch64-none-native" "LoongArch64 Bare-Metal Stdlib"
check_recipe "rust-std-x86_64-none-native" "x86_64 Bare-Metal Stdlib"

echo ""
echo "================================"
echo "2. Linux Userspace 工具链（未来）"
echo "================================"

# 检查 libstd-rs
check_recipe "libstd-rs" "Linux 用户态 Rust 标准库"

# 检查 rust-cross
check_recipe "rust-cross" "Rust 交叉编译器"

# 检查 C 库
echo ""
echo "C 标准库选择:"
check_variable "TCLIBC" "starryos" "TCLIBC 设置"

TCLIBC=$(bitbake -e starryos 2>/dev/null | grep "^TCLIBC=" | cut -d'"' -f2)
if [ "$TCLIBC" = "musl" ]; then
    check_recipe "musl" "musl C 库"
elif [ "$TCLIBC" = "glibc" ]; then
    check_recipe "glibc" "glibc C 库"
else
    echo -e "${YELLOW}⚠ 未知的 TCLIBC: $TCLIBC${NC}"
fi

echo ""
echo "================================"
echo "3. SDK 配置"
echo "================================"

check_variable "TOOLCHAIN_HOST_TASK" "starryos" "SDK 主机端工具"
check_variable "TOOLCHAIN_TARGET_TASK" "starryos" "SDK 目标端库"

echo ""
echo "================================"
echo "4. 关键配置变量"
echo "================================"

check_variable "INHIBIT_DEFAULT_RUST_DEPS" "starryos" "禁用默认 Rust 依赖"
check_variable "RUSTLIB_DEP" "starryos" "Rust 库依赖"
check_variable "RUST_TARGET_TRIPLE" "starryos" "Rust 目标三元组"

echo ""
echo "================================"
echo "总结"
echo "================================"

echo ""
echo -e "${GREEN}✓ Bare-Metal 工具链完整${NC}"
echo "  - 可以构建 StarryOS 内核"
echo "  - 支持 aarch64, riscv64, loongarch64, x86_64"
echo ""

if bitbake -e libstd-rs &>/dev/null && bitbake -e rust-cross &>/dev/null; then
    echo -e "${GREEN}✓ Linux Userspace 工具链配方存在${NC}"
    echo "  - libstd-rs 和 rust-cross 配方可用"
    echo "  - 需要验证是否能成功构建（参考 docs/USERSPACE-ROADMAP.md）"
else
    echo -e "${YELLOW}⚠ Linux Userspace 工具链配方缺失${NC}"
    echo "  - 需要从 Poky 复制或创建 libstd-rs/rust-cross 配方"
fi

echo ""
echo -e "${YELLOW}📖 详细信息请参考:${NC}"
echo "  - Rust 开发指南: recipes-devtools/rust/README-rust.md"
echo "  - Userspace 路线图: docs/USERSPACE-ROADMAP.md"
echo ""
