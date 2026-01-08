#!/bin/bash
# 更新 Rust nightly 工具链到指定日期
# 用法：./update-rust-nightly.sh [YYYY-MM-DD]
#       如果不指定日期，默认使用今天

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RECIPE_FILE="$SCRIPT_DIR/../recipes-devtools/rust/rust-prebuilt-native_1.94.0.bb"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

info() { echo -e "${BLUE}[INFO]${NC} $1"; }
success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

# 检查配方文件
if [ ! -f "$RECIPE_FILE" ]; then
    error "找不到配方文件：$RECIPE_FILE"
fi

# 获取新日期
NEW_DATE="${1:-$(date +%Y-%m-%d)}"

# 验证日期格式
if ! date -d "$NEW_DATE" &>/dev/null; then
    error "无效的日期格式：$NEW_DATE（应为 YYYY-MM-DD）"
fi

# 获取当前日期
CURRENT_DATE=$(grep '^RUST_DATE = ' "$RECIPE_FILE" | cut -d'"' -f2)

info "Rust nightly 工具链更新"
echo "  当前版本：$CURRENT_DATE"
echo "  目标版本：$NEW_DATE"
echo ""

if [ "$CURRENT_DATE" = "$NEW_DATE" ]; then
    warn "已经是目标版本，无需更新"
    exit 0
fi

# 询问确认
read -p "确认更新？[y/N] " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    warn "用户取消"
    exit 0
fi

RUST_CHANNEL="nightly-$NEW_DATE"

info "获取新的 SHA256 校验和..."
echo ""

# 临时文件保存新的 SHA256
TMP_FILE=$(mktemp)

# Host 工具链
info "1. 获取 Host 工具链 SHA256..."
for host in x86_64-unknown-linux-gnu aarch64-unknown-linux-gnu; do
    arch=$(echo "$host" | cut -d'-' -f1)
    url="https://static.rust-lang.org/dist/rust-${RUST_CHANNEL}-${host}.tar.xz.sha256"
    
    info "  - 正在获取 $arch..."
    if sha256=$(curl -sS "$url" 2>/dev/null | awk '{print $1}'); then
        if [ -n "$sha256" ]; then
            success "    $arch: $sha256"
            echo "toolchain-${arch}=${sha256}" >> "$TMP_FILE"
        else
            error "    获取 $arch SHA256 失败：响应为空"
        fi
    else
        error "    获取 $arch SHA256 失败：无法访问 $url"
    fi
done

echo ""

# 目标标准库
info "2. 获取目标标准库 SHA256..."
for target in aarch64-unknown-none-softfloat riscv64gc-unknown-none-elf loongarch64-unknown-none-softfloat x86_64-unknown-none; do
    arch=$(echo "$target" | cut -d'-' -f1)
    url="https://static.rust-lang.org/dist/rust-std-${RUST_CHANNEL}-${target}.tar.xz.sha256"
    
    info "  - 正在获取 std-$arch..."
    if sha256=$(curl -sS "$url" 2>/dev/null | awk '{print $1}'); then
        if [ -n "$sha256" ]; then
            success "    std-$arch: $sha256"
            echo "std-${arch}=${sha256}" >> "$TMP_FILE"
        else
            error "    获取 std-$arch SHA256 失败：响应为空"
        fi
    else
        error "    获取 std-$arch SHA256 失败：无法访问 $url"
    fi
done

echo ""
info "3. 更新配方文件..."

# 备份原文件
cp "$RECIPE_FILE" "${RECIPE_FILE}.bak"
info "  - 已备份原文件：${RECIPE_FILE}.bak"

# 更新日期
sed -i "s/^RUST_DATE = .*/RUST_DATE = \"$NEW_DATE\"/" "$RECIPE_FILE"
info "  - 已更新 RUST_DATE"

# 更新 SHA256
while IFS='=' read -r key value; do
    # toolchain-x86_64=abc123 -> toolchain-x86_64
    escaped_key=$(echo "$key" | sed 's/[]\/$*.^[]/\\&/g')
    sed -i "s/^SRC_URI\[${escaped_key}\.sha256sum\] = .*/SRC_URI[${key}.sha256sum] = \"$value\"/" "$RECIPE_FILE"
    info "  - 已更新 SRC_URI[$key.sha256sum]"
done < "$TMP_FILE"

# 清理临时文件
rm -f "$TMP_FILE"

echo ""
success "✅ 更新完成！"
echo ""
info "下一步操作："
echo "  1. 查看更改："
echo "     git diff $RECIPE_FILE"
echo ""
echo "  2. 测试构建（清理缓存）："
echo "     cd ~/starry-workspace/build"
echo "     bitbake rust-prebuilt-native -c cleansstate"
echo "     bitbake rust-prebuilt-native"
echo "     bitbake starry"
echo ""
echo "  3. 如果测试成功，提交更改："
echo "     git add $RECIPE_FILE"
echo "     git commit -m 'rust: update nightly to $NEW_DATE'"
echo ""
echo "  4. 如果测试失败，回滚："
echo "     mv ${RECIPE_FILE}.bak $RECIPE_FILE"
echo ""
warn "注意：所有团队成员需要重新构建：bitbake starry -c cleansstate"

