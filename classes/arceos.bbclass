# ArceOS build system class
# Provides common build logic for ArceOS-based kernels
#
# Usage:
#   inherit arceos
#
# Required machine variables:
#   ARCEOS_ARCH - Architecture (from machine config)
#   ARCEOS_PLAT_PACKAGE - Platform package name (e.g., axplat-loongarch64-qemu-virt)
#   RUST_TARGET - Rust target triple (e.g., loongarch64-unknown-none-softfloat)
#
# Optional variables:
#   ARCEOS_FEATURES - ArceOS features to enable
#   ARCEOS_SMP - Number of CPUs (default: 1)
#   ARCEOS_LOG - Log level (default: warn)
#   ARCEOS_PLAT_CONFIG - Path to pre-generated platform config file

# ArceOS 基于 Cargo 构建系统
# cargo.bbclass 会自动 inherit cargo_common，提供环境变量设置（包括 RUSTC_BOOTSTRAP）
inherit cargo

# 裸机内核不需要 cargo.bbclass 默认添加的 libc 版本 Rust 工具链
# cargo.bbclass 会添加 virtual/${TARGET_PREFIX}rust 和 ${RUSTLIB_DEP}
# RUSTLIB_DEP 在 rust-common.bbclass 中已设为空
# virtual/${TARGET_PREFIX}rust 会解析为 Poky 的 rust-cross，进而依赖 libstd-rs
# 通过设置 INHIBIT_DEFAULT_RUST_DEPS 阻止自动添加 Rust 工具链依赖
INHIBIT_DEFAULT_RUST_DEPS = "1"

# Architecture and platform configuration
# These can be set in machine/*.conf files or recipes
# Using ?= (weak assignment) allows machine config to override defaults
ARCEOS_ARCH ?= ""
ARCEOS_PLAT_PACKAGE ?= ""
RUST_TARGET ?= ""

# Rust target triple
RUST_TARGET_TRIPLE = "${RUST_TARGET}"

# Tool dependencies for ArceOS bare-metal build
DEPENDS:append = " \
    axconfig-gen-native \
    cargo-binutils-native \
    cmake-native \
"

# Bare-metal stdlib: 根据 ARCEOS_ARCH 动态选择
# 将来支持 Linux userspace 时，这里会根据 TCLIBC 切换到 libstd-rs
python __anonymous() {
    arch = d.getVar('ARCEOS_ARCH')
    if not arch:
        return
    
    # Bare-metal 目标的 rust-std
    # 格式: rust-std-{arch}-none-native
    arch_map = {
        'aarch64': 'rust-std-aarch64-none-native',
        'riscv64': 'rust-std-riscv64-none-native',
        'loongarch64': 'rust-std-loongarch64-none-native',
        'x86_64': 'rust-std-x86_64-none-native',
    }
    
    rust_std = arch_map.get(arch, 'rust-std-aarch64-none-native')
    d.appendVar('DEPENDS', f' {rust_std}')
}

# We need Rust toolchain but not the standard cross-compiler
DEPENDS:append = " cargo-bin-native rustc-bin-native"

# Bare-metal kernel doesn't need packaging - completely disable packaging tasks
PACKAGES = ""
RDEPENDS:${PN} = ""
ALLOW_EMPTY:${PN} = "1"

deltask package
deltask package_write_rpm
deltask package_write_ipk
deltask package_write_deb
deltask packagedata
deltask package_qa

# Export environment variables for build
export ARCEOS_ARCH
export RUST_TARGET_TRIPLE
export CARGO_BUILD_TARGET = "${RUST_TARGET_TRIPLE}"

def arceos_arch_map(d):
    """Map Yocto TARGET_ARCH to ArceOS ARCH"""
    arch = d.getVar('TARGET_ARCH')
    arch_map = {
        'x86_64': 'x86_64',
        'aarch64': 'aarch64',
        'riscv64': 'riscv64',
        'loongarch64': 'loongarch64',
    }
    mapped = arch_map.get(arch, arch)
    if mapped == arch and arch not in arch_map:
        bb.warn(f"Unknown architecture '{arch}' for ArceOS, using as-is")
    return mapped

# Generate .axconfig.toml platform configuration file
arceos_generate_config() {
    local config_file="${S}/.axconfig.toml"
    
    if [ -n "${ARCEOS_PLAT_CONFIG}" ] && [ -f "${ARCEOS_PLAT_CONFIG}" ]; then
        # Use pre-defined configuration file
        bbnote "Using pre-defined platform config: ${ARCEOS_PLAT_CONFIG}"
        cp "${ARCEOS_PLAT_CONFIG}" "${config_file}"
        return
    fi
    
    # Check if this is StarryOS (has arceos submodule with configs)
    if [ -f "${S}/arceos/configs/defconfig.toml" ]; then
        bbnote "Detected StarryOS structure, generating config via axconfig-gen..."
        
        # StarryOS uses arceos/configs/defconfig.toml as base
        local starry_defconfig="${S}/arceos/configs/defconfig.toml"
        
        if [ ! -f "${starry_defconfig}" ]; then
            bbfatal "StarryOS defconfig not found: ${starry_defconfig}"
        fi
        
        # Generate .axconfig.toml using axconfig-gen (replacing make defconfig)
        # This replicates what arceos/scripts/make/config.mk does
        bbnote "Generating .axconfig.toml for StarryOS:"
        bbnote "  Base config: ${starry_defconfig}"
        bbnote "  Architecture: ${ARCEOS_ARCH}"
        bbnote "  Platform: ${ARCEOS_PLAT_PACKAGE}"
        
        # Find platform axconfig.toml
        local plat_config="${S}/local_crates/axplat_crates/platforms/${ARCEOS_PLAT_PACKAGE}/axconfig.toml"
        if [ ! -f "${plat_config}" ]; then
            bbfatal "Platform config not found: ${plat_config}"
        fi
        
        # Call axconfig-gen with defconfig + platform config (like Makefile does)
        bbnote "  Platform config: ${plat_config}"
        axconfig-gen \
            "${starry_defconfig}" \
            "${plat_config}" \
            -w "arch=\"${ARCEOS_ARCH}\"" \
            -w "platform=\"${ARCEOS_PLATFORM}\"" \
            -o "${config_file}" || \
            bbfatal "Failed to generate StarryOS platform config"
        
        # Override SMP if specified
        if [ -n "${ARCEOS_SMP}" ] && [ "${ARCEOS_SMP}" != "1" ]; then
            bbnote "Setting SMP to ${ARCEOS_SMP} CPUs"
            axconfig-gen "${config_file}" -w "plat.cpu-num=${ARCEOS_SMP}" -o "${config_file}" || \
                bbwarn "Failed to set SMP configuration"
        fi
        
        # Override LOG level if specified
        if [ -n "${ARCEOS_LOG}" ]; then
            bbnote "Setting log level to ${ARCEOS_LOG}"
            axconfig-gen "${config_file}" -w "log=\"${ARCEOS_LOG}\"" -o "${config_file}" || \
                bbwarn "Failed to set log level"
        fi
        
        # Override memory size if specified (in bytes)
        if [ -n "${ARCEOS_MEM}" ]; then
            bbnote "Setting memory size to ${ARCEOS_MEM}"
            axconfig-gen "${config_file}" -w "plat.phys-memory-size=${ARCEOS_MEM}" -o "${config_file}" || \
                bbwarn "Failed to set memory size"
        fi
        
        bbnote "Generated .axconfig.toml for StarryOS:"
        cat "${config_file}"
        return
    fi
    
    # If we reach here, neither pre-defined config nor StarryOS structure found
    bbfatal "Unable to generate config: not a StarryOS project and no pre-defined config provided"
}

# Setup cargo build environment
arceos_setup_cargo() {
    mkdir -p "${S}/.cargo"
    
    # Create base cargo config for bare-metal target
    # NOTE: Do NOT set [build] target here - it would force build scripts 
    # to compile for the target platform instead of the host.
    # We specify --target on the command line instead.
    cat > "${S}/.cargo/config.toml" <<EOF
[target.x86_64-unknown-linux-gnu]
# Build scripts use host platform
linker = "gcc"

[target.${RUST_TARGET_TRIPLE}]
# ArceOS uses custom linker script, no external linker needed
rustflags = [
    "-C", "link-arg=-nostartfiles",
    "-C", "link-arg=-no-pie",
]

# Git dependency path redirects will be appended by recipe
# [patch.crates-io]
# [patch."https://github.com/..."]
EOF

    bbnote "Created .cargo/config.toml for target ${RUST_TARGET_TRIPLE}"
}

# Hook into do_configure - run AFTER make clean
do_configure:append() {
    arceos_generate_config
    arceos_setup_cargo
}

# Default compile task for ArceOS/StarryOS projects
# Recipes can override this by defining their own do_compile
do_compile() {
    export RUSTC_BOOTSTRAP=1
    export CARGO_BUILD_TARGET="${RUST_TARGET_TRIPLE}"
    export AX_CONFIG_PATH="${S}/.axconfig.toml"
    export RUST_BACKTRACE=1
    
    # lwext4_rust 需要 musl 工具链来编译 C 代码
    # 创建符号链接，使用系统的 gcc（裸机编译，不需要 musl 库）
    mkdir -p "${WORKDIR}/musl-wrapper"
    if [ ! -e "${WORKDIR}/musl-wrapper/${TUNE_ARCH}-linux-musl-cc" ]; then
        ln -sf "$(which gcc)" "${WORKDIR}/musl-wrapper/${TUNE_ARCH}-linux-musl-cc"
        ln -sf "$(which g++)" "${WORKDIR}/musl-wrapper/${TUNE_ARCH}-linux-musl-c++"
        ln -sf "$(which ar)" "${WORKDIR}/musl-wrapper/${TUNE_ARCH}-linux-musl-ar"
        ln -sf "$(which as)" "${WORKDIR}/musl-wrapper/${TUNE_ARCH}-linux-musl-as"
        bbnote "Created musl toolchain wrappers for lwext4_rust"
    fi
    export PATH="${WORKDIR}/musl-wrapper:$PATH"
    export ARCH="${TUNE_ARCH}"
    
    # Export ArceOS features if specified
    if [ -n "${ARCEOS_FEATURES}" ]; then
        export FEATURES="${ARCEOS_FEATURES}"
        bbnote "ArceOS features: ${ARCEOS_FEATURES}"
    fi
    
    bbnote "Building with cargo for target: ${RUST_TARGET_TRIPLE}"
    bbnote "Manifest: ${S}/Cargo.toml"
    
    # Build using cargo with explicit target
    cargo build \
        --manifest-path "${S}/Cargo.toml" \
        --target "${RUST_TARGET_TRIPLE}" \
        --release \
        ${CARGO_FEATURES:+--features "${CARGO_FEATURES}"} \
        ${EXTRA_CARGO_FLAGS}
}

# Default install task for bare-metal kernels
# Recipes should override this to customize installation
do_install() {
    bbwarn "Using default arceos.bbclass do_install - recipes should override this"
    
    install -d ${D}/boot
    
    # Try to find the built kernel binary
    local kernel_bin="${B}/target/${RUST_TARGET_TRIPLE}/release/${PN}"
    
    if [ -f "${kernel_bin}" ]; then
        install -m 0755 "${kernel_bin}" "${D}/boot/${PN}.elf"
        bbnote "Installed ${PN}.elf to /boot"
    else
        bbwarn "Kernel binary not found at ${kernel_bin}"
    fi
}
