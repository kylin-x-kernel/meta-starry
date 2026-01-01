# ArceOS build system class
# Provides ArceOS-specific build logic (platform config, features, etc.)
#
# Inherits rust-kernel.bbclass for common Rust bare-metal kernel build
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

# Inherit common Rust kernel build infrastructure
inherit rust-kernel

# ArceOS-specific tool dependencies
DEPENDS:append = " \
    axconfig-gen-native \
    cargo-binutils-native \
    cmake-native \
"

# Set KERNEL_ARCH for rust-kernel.bbclass
KERNEL_ARCH = "${ARCEOS_ARCH}"

# Architecture and platform configuration
ARCEOS_ARCH ?= ""
ARCEOS_PLAT_PACKAGE ?= ""

# Export environment variables for ArceOS build
export ARCEOS_ARCH

# ==================== ArceOS Configuration ====================

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

# ==================== Build Tasks ====================

# Hook into rust-kernel.bbclass configure task to generate ArceOS config  
do_configure:append() {
    arceos_generate_config
}

# Override rust-kernel.bbclass compile to add ArceOS-specific logic
do_compile() {
    # Call rust-kernel setup
    rust_kernel_setup_toolchain
    
    export CARGO_BUILD_TARGET="${RUST_TARGET}"
    export AX_CONFIG_PATH="${S}/.axconfig.toml"
    export RUST_BACKTRACE=1
    
    # lwext4_rust needs musl toolchain to compile C code
    # Create wrappers for build scripts
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
    
    bbnote "Building ArceOS kernel for ${RUST_TARGET}"
    bbnote "Cargo manifest: ${S}/Cargo.toml"
    
    # Build using cargo
    cargo build \
        --manifest-path "${S}/Cargo.toml" \
        --target "${RUST_TARGET}" \
        --release \
        ${CARGO_FEATURES:+--features "${CARGO_FEATURES}"} \
        ${EXTRA_CARGO_FLAGS}
}
