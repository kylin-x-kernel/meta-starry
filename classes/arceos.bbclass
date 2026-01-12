# arceos.bbclass
# ArceOS 构建系统核心类
#
# 设计原则：
# - 负责 ArceOS 平台配置生成 (axconfig-gen)
# - 负责 lwext4_rust musl 工具链适配
# - 负责 ArceOS 环境变量设置
# - 继承 rust-kernel.bbclass 获取 Rust 工具链支持
#
# 不负责:
# - Feature 解析 (由 arceos-features.bbclass 处理)
# - 具体项目逻辑 (由 recipe 处理)
#
# Usage:
#   inherit arceos
#
# Required machine variables:
#   ARCEOS_ARCH        - Architecture (aarch64/riscv64/loongarch64/x86_64)
#   ARCEOS_PLATFORM    - Platform name (e.g., aarch64-qemu-virt)
#   ARCEOS_PLAT_PACKAGE - Platform package name (e.g., axplat-aarch64-qemu-virt)
#   RUST_TARGET        - Rust target triple
#
# Optional variables (with defaults from arceos-defaults.inc):
#   ARCEOS_MODE        - Build mode: release/debug (default: release)
#   ARCEOS_LOG         - Log level: off/error/warn/info/debug/trace (default: warn)
#   ARCEOS_SMP         - Number of CPUs (default: from platform config)
#   ARCEOS_MEM         - Memory size like "1G" (default: from platform config)
#   ARCEOS_DWARF       - Enable DWARF debug info: y/n (default: y)
#   ARCEOS_DEFCONFIG   - Custom defconfig path (advanced)
#   ARCEOS_EXTRA_CONFIG - Extra config file (advanced)

# ==================== 继承基础类 ====================
inherit rust-kernel

# ==================== 工具依赖 ====================
DEPENDS:append = " \
    axconfig-gen-native \
    cargo-binutils-native \
    cmake-native \
"

# ==================== 变量声明 ====================
# 平台相关 (必须由 machine.conf 设置)
ARCEOS_ARCH ?= ""
ARCEOS_PLATFORM ?= ""
ARCEOS_PLAT_PACKAGE ?= ""

# 构建选项 (有默认值，可覆盖)
ARCEOS_MODE ?= "release"
ARCEOS_LOG ?= "warn"
ARCEOS_SMP ?= ""
ARCEOS_MEM ?= ""
ARCEOS_DWARF ?= "y"

# 高级配置
ARCEOS_DEFCONFIG ?= ""
ARCEOS_EXTRA_CONFIG ?= ""

# 内部变量
ARCEOS_OUT_CONFIG = "${S}/.axconfig.toml"

# ==================== 关键环境变量导出 ====================
# 对应 arceos/Makefile 中的 export 语句

# RUSTC_BOOTSTRAP=1: 允许稳定 Rust 使用 nightly 特性
# 这是最关键的环境变量，必须在元数据级别设置
export RUSTC_BOOTSTRAP = "1"

# CARGO_FEATURES 由 arceos-features.bbclass 设置
export CARGO_FEATURES

# 设置 KERNEL_ARCH 供 rust-kernel.bbclass 使用
KERNEL_ARCH = "${ARCEOS_ARCH}"

# 导出 ARCEOS_ARCH 供 build.rs 使用
export ARCEOS_ARCH

# 导出 DWARF 供 axhal build.rs 生成链接器脚本使用
# 如果 ARCEOS_DWARF=y，axhal 会在链接器脚本中添加 DWARF 段定义
export DWARF = "${ARCEOS_DWARF}"

# ==================== Python 辅助函数 ====================

def arceos_mem_to_bytes(d):
    """
    将 ARCEOS_MEM (如 "1G") 转换为字节数
    对应 arceos/scripts/make/strtosz.py
    """
    import re
    mem_str = d.getVar('ARCEOS_MEM') or ''
    if not mem_str:
        return None
    
    match = re.match(r'^(\d+)([KMGT]?)$', mem_str.upper())
    if not match:
        bb.warn(f"Invalid ARCEOS_MEM format: {mem_str}")
        return None
    
    num = int(match.group(1))
    unit = match.group(2)
    
    multipliers = {
        '': 1,
        'K': 1024,
        'M': 1024 ** 2,
        'G': 1024 ** 3,
        'T': 1024 ** 4,
    }
    
    return num * multipliers.get(unit, 1)

# ==================== 配置生成函数 ====================
# 复刻 arceos/scripts/make/config.mk 的 defconfig 逻辑

arceos_generate_config() {
    local config_file="${ARCEOS_OUT_CONFIG}"
    
    # ==================== 使用预定义配置文件 ====================
    if [ -n "${ARCEOS_PLAT_CONFIG}" ] && [ -f "${ARCEOS_PLAT_CONFIG}" ]; then
        bbnote "Using pre-defined platform config: ${ARCEOS_PLAT_CONFIG}"
        cp "${ARCEOS_PLAT_CONFIG}" "${config_file}"
        return
    fi
    
    # ==================== StarryOS 结构检测 ====================
    if [ ! -f "${S}/arceos/configs/defconfig.toml" ]; then
        bbfatal "Not a StarryOS project: ${S}/arceos/configs/defconfig.toml not found"
    fi
    
    bbnote "Generating .axconfig.toml (replicating make defconfig)..."
        
    # 确定 defconfig 路径
    local defconfig="${ARCEOS_DEFCONFIG}"
    if [ -z "${defconfig}" ]; then
        defconfig="${S}/arceos/configs/defconfig.toml"
    fi
        
    if [ ! -f "${defconfig}" ]; then
        bbfatal "defconfig not found: ${defconfig}"
        fi
        
    # ==================== 查找平台配置文件 ====================
    # 复刻 platform.mk 的 resolve_config 逻辑
    local plat_config=""
    
    # 尝试路径 1: local_crates/axplat_crates/platforms/
    if [ -f "${S}/local_crates/axplat_crates/platforms/${ARCEOS_PLAT_PACKAGE}/axconfig.toml" ]; then
        plat_config="${S}/local_crates/axplat_crates/platforms/${ARCEOS_PLAT_PACKAGE}/axconfig.toml"
    # 尝试路径 2: local_crates/<package>/axconfig.toml (如 axplat-aarch64-crosvm-virt)
    elif [ -f "${S}/local_crates/${ARCEOS_PLAT_PACKAGE}/axconfig.toml" ]; then
        plat_config="${S}/local_crates/${ARCEOS_PLAT_PACKAGE}/axconfig.toml"
    fi
    
    if [ -z "${plat_config}" ] || [ ! -f "${plat_config}" ]; then
        bbfatal "Platform config not found for ${ARCEOS_PLAT_PACKAGE}"
        fi
        
    bbnote "Configuration sources:"
    bbnote "  defconfig: ${defconfig}"
    bbnote "  platform:  ${plat_config}"
    
    # ==================== 构建 axconfig-gen 参数 ====================
    # 复刻 config.mk:3-7
    local config_args="${defconfig} ${plat_config}"
        
    # 添加额外配置文件
    if [ -n "${ARCEOS_EXTRA_CONFIG}" ] && [ -f "${ARCEOS_EXTRA_CONFIG}" ]; then
        config_args="${config_args} ${ARCEOS_EXTRA_CONFIG}"
        bbnote "  extra:     ${ARCEOS_EXTRA_CONFIG}"
    fi
    
    # 添加 -w 参数覆盖
    config_args="${config_args} -w 'arch=\"${ARCEOS_ARCH}\"'"
    config_args="${config_args} -w 'platform=\"${ARCEOS_PLATFORM}\"'"
    
    # ==================== 内存大小覆盖 ====================
    # 复刻 config.mk:9-12
        if [ -n "${ARCEOS_MEM}" ]; then
        # 使用 Python 转换内存大小
        local mem_bytes=$(python3 -c "
import re
mem_str = '${ARCEOS_MEM}'.upper()
match = re.match(r'^(\d+)([KMGT]?)$', mem_str)
if match:
    num = int(match.group(1))
    unit = match.group(2)
    mult = {'': 1, 'K': 1024, 'M': 1024**2, 'G': 1024**3, 'T': 1024**4}
    print(num * mult.get(unit, 1))
")
        if [ -n "${mem_bytes}" ]; then
            config_args="${config_args} -w 'plat.phys-memory-size=${mem_bytes}'"
            bbnote "  memory:    ${ARCEOS_MEM} (${mem_bytes} bytes)"
        fi
    fi
    
    # ==================== 执行 axconfig-gen ====================
    bbnote "Running: axconfig-gen ${config_args} -o ${config_file}"
    eval axconfig-gen ${config_args} -o "${config_file}" || \
        bbfatal "axconfig-gen failed"

    # ==================== SMP 覆盖 ====================
    # 复刻 config.mk 中 SMP 的处理 (生成后修改)
    if [ -n "${ARCEOS_SMP}" ]; then
        bbnote "Setting SMP: ${ARCEOS_SMP} CPUs"
        axconfig-gen "${config_file}" -w "plat.cpu-num=${ARCEOS_SMP}" -o "${config_file}" || \
            bbwarn "Failed to set SMP"
    fi
    
    # ==================== LOG 级别覆盖 ====================
    if [ -n "${ARCEOS_LOG}" ]; then
        bbnote "Setting LOG: ${ARCEOS_LOG}"
        axconfig-gen "${config_file}" -w "log=\"${ARCEOS_LOG}\"" -o "${config_file}" || \
            bbwarn "Failed to set LOG"
    fi
    
    bbnote "Generated ${config_file}:"
    head -30 "${config_file}"
    bbnote "... (truncated)"
}
    
# ==================== lwext4_rust musl 工具链适配 ====================
arceos_setup_musl_wrapper() {
    # lwext4_rust 的 build.rs 期望 ${ARCH}-linux-musl-gcc 工具链
    # 我们创建 wrapper 脚本来满足这个需求
    
    MUSL_WRAPPER_DIR="${WORKDIR}/musl-wrapper"
    MUSL_SYSROOT="${WORKDIR}/musl-sysroot"
    
    mkdir -p "${MUSL_WRAPPER_DIR}"
    mkdir -p "${MUSL_SYSROOT}/include"
    
    if [ ! -f "${MUSL_SYSROOT}/include/stdint.h" ]; then
        MUSL_HEADERS_SRC="${LAYERDIR_meta-starry}/files/musl-headers"
        if [ -d "${MUSL_HEADERS_SRC}" ]; then
            cp -r "${MUSL_HEADERS_SRC}"/* "${MUSL_SYSROOT}/include/"
            bbnote "Copied musl headers from ${MUSL_HEADERS_SRC}"
        else
            bbfatal "musl-headers not found at ${MUSL_HEADERS_SRC}"
        fi
    fi
    
    # 创建 gcc wrapper
    cat > "${MUSL_WRAPPER_DIR}/${TUNE_ARCH}-linux-musl-gcc" << 'EOFGCC'
#!/bin/bash
SYSROOT="__MUSL_SYSROOT__"
REAL_CC="__REAL_CC__"

for arg in "$@"; do
    if [ "$arg" = "-print-sysroot" ]; then
        echo "$SYSROOT"
        exit 0
    fi
done

exec $REAL_CC -ffreestanding -nostdinc -isystem "${SYSROOT}/include" "$@"
EOFGCC

    sed -i "s|__MUSL_SYSROOT__|${MUSL_SYSROOT}|g" "${MUSL_WRAPPER_DIR}/${TUNE_ARCH}-linux-musl-gcc"
    sed -i "s|__REAL_CC__|${CC}|g" "${MUSL_WRAPPER_DIR}/${TUNE_ARCH}-linux-musl-gcc"
    chmod +x "${MUSL_WRAPPER_DIR}/${TUNE_ARCH}-linux-musl-gcc"
    
    # 创建其他工具链接
    ln -sf "${TUNE_ARCH}-linux-musl-gcc" "${MUSL_WRAPPER_DIR}/${TUNE_ARCH}-linux-musl-cc"
    ln -sf "$(which ${AR})" "${MUSL_WRAPPER_DIR}/${TUNE_ARCH}-linux-musl-ar" 2>/dev/null || \
        ln -sf "$(which ar)" "${MUSL_WRAPPER_DIR}/${TUNE_ARCH}-linux-musl-ar"
    ln -sf "$(which ${RANLIB})" "${MUSL_WRAPPER_DIR}/${TUNE_ARCH}-linux-musl-ranlib" 2>/dev/null || \
        ln -sf "$(which ranlib)" "${MUSL_WRAPPER_DIR}/${TUNE_ARCH}-linux-musl-ranlib"
    
    export PATH="${MUSL_WRAPPER_DIR}:$PATH"
    export ARCH="${TUNE_ARCH}"
    
    bbnote "musl wrapper ready: ${MUSL_WRAPPER_DIR}"
}

# ==================== 构建任务 ====================

do_configure:append() {
    arceos_generate_config
}

do_compile() {
    # 调用父类工具链设置
    rust_kernel_setup_toolchain
    
    # ==================== 关键: RUSTC_BOOTSTRAP 必须显式 export ====================
    # 这允许稳定版 Rust 使用 nightly 特性 (如 maybe_uninit_slice)
    # 仅在 .cargo/config.toml [env] 中设置是不够的，必须在 shell 环境中 export
    export RUSTC_BOOTSTRAP=1
    
    # ==================== 环境变量设置 ====================
    # 复刻 arceos/Makefile:120-127 的 export 语句
    export CARGO_BUILD_TARGET="${RUST_TARGET}"
    export AX_CONFIG_PATH="${ARCEOS_OUT_CONFIG}"
    export AX_ARCH="${ARCEOS_ARCH}"
    export AX_PLATFORM="${ARCEOS_PLATFORM}"
    export AX_MODE="${ARCEOS_MODE}"
    export AX_LOG="${ARCEOS_LOG}"
    export AX_TARGET="${RUST_TARGET}"
    export RUST_BACKTRACE=1

    # Host tools for Rust build scripts
    if [ -z "${BUILD_CC}" ]; then
        export CC="gcc"
        export CXX="g++"
        export AR="ar"
    else
        export CC="${BUILD_CC}"
        export CXX="${BUILD_CXX}"
        export AR="${BUILD_AR}"
    fi

    # Rust build scripts invoke "cc" directly; provide a stable shim to gcc.
    if ! command -v cc >/dev/null 2>&1; then
        local hosttools_cc="${WORKDIR}/hosttools/cc"
        mkdir -p "$(dirname "${hosttools_cc}")"
        ln -sf "$(command -v gcc)" "${hosttools_cc}"
        export PATH="$(dirname "${hosttools_cc}"):${PATH}"
    fi
    
    # 网络配置 
    export AX_IP="${ARCEOS_IP}"
    export AX_GW="${ARCEOS_GW}"
    
    # ==================== musl 工具链适配 ====================
    arceos_setup_musl_wrapper
    
    # ==================== RUSTFLAGS 设置 ====================
    # 复刻 cargo.mk:20 的 RUSTFLAGS_LINK_ARGS
    LD_SCRIPT="${S}/target/${RUST_TARGET}/${ARCEOS_MODE}/linker_${ARCEOS_PLATFORM}.lds"
    export RUSTFLAGS="-C link-arg=-T${LD_SCRIPT} -C link-arg=-no-pie -C link-arg=-znostart-stop-gc"
    
    # 复刻 build.mk:37-39 的 DWARF 设置
    if [ "${ARCEOS_DWARF}" = "y" ]; then
        export RUSTFLAGS="${RUSTFLAGS} -C force-frame-pointers -C debuginfo=2 -C strip=none"
        export CARGO_PROFILE_RELEASE_DEBUG=2
        export CARGO_PROFILE_RELEASE_STRIP="none"
    fi
    
    bbnote "Build configuration:"
    bbnote "  RUST_TARGET:    ${RUST_TARGET}"
    bbnote "  ARCEOS_PLATFORM: ${ARCEOS_PLATFORM}"
    bbnote "  CARGO_FEATURES: ${CARGO_FEATURES}"
    bbnote "  RUSTFLAGS:      ${RUSTFLAGS}"
    
    # ==================== Cargo 构建 ====================
    # 复刻 cargo.mk:27-28 和 build.mk:51
    # 关键: 使用 cargo -C 而不是 --manifest-path
    TARGET_DIR="${S}/target"
    
    bbnote "Running: cargo -C ${S} build -Z unstable-options --target ${RUST_TARGET} --target-dir ${TARGET_DIR} --release"
    
    # 使用 env 确保 RUSTC_BOOTSTRAP 被传递给 cargo 及其子进程
    env RUSTC_BOOTSTRAP=1 cargo -C "${S}" build \
        -Z unstable-options \
        --target "${RUST_TARGET}" \
        --target-dir "${TARGET_DIR}" \
        --release \
        ${CARGO_FEATURES:+--features "${CARGO_FEATURES}"} \
        ${EXTRA_CARGO_FLAGS}
    
    bbnote "Build completed successfully"
}
