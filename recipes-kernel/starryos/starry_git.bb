SUMMARY = "StarryOS - Unix-like OS based on ArceOS"
DESCRIPTION = "StarryOS is a Unix-like operating system built on the ArceOS modular kernel framework"
SECTION = "kernel"
LICENSE = "GPL-3.0-only"
LIC_FILES_CHKSUM = "file://LICENSE;md5=175792518e4ac015ab6696d16c4f607e"

inherit arceos deploy

# ==================== 版本与源码 ====================
PV = "1.0+git${SRCPV}"

# 多仓开发：分别下载 StarryOS 和 ArceOS
SRC_URI = "git://github.com/Starry-OS/StarryOS.git;protocol=https;branch=main;name=starry \
           git://github.com/kylin-x-kernel/arceos.git;protocol=https;branch=dev;name=arceos;destsuffix=git/arceos"

# 使用最新提交
SRCREV_starry = "${AUTOREV}"
SRCREV_arceos = "${AUTOREV}"
SRCREV_FORMAT = "starry_arceos"

S = "${WORKDIR}/git"

# 允许网络访问下载依赖
do_compile[network] = "1"

# ==================== 平台配置 ====================
COMPATIBLE_MACHINE = "(aarch64-qemu-virt|riscv64-qemu-virt|loongarch64-qemu-virt|x86_64-qemu-q35)"

# ArceOS 配置（从 machine 继承 ARCEOS_PLAT_PACKAGE 和 RUST_TARGET）
ARCEOS_FEATURES = "fs ext4 net vsock multitask alloc-slab fp-simd defplat bus-pci"
ARCEOS_SMP = "4"
ARCEOS_LOG = "warn"

# Cargo features（对应 StarryOS 的 features）
CARGO_FEATURES = "qemu"

# ==================== 配置 ====================
# StarryOS 不需要生成 .axconfig.toml（直接用 cargo build）
# 覆盖 arceos.bbclass 的配置生成
do_configure:append() {
    # 跳过 arceos_generate_config（StarryOS 不需要）
    :
}

# ==================== 构建配置 ====================
do_compile() {
    export RUST_TARGET="${RUST_TARGET_TRIPLE}"
    export CARGO_BUILD_TARGET="${RUST_TARGET}"
    export RUSTC_BOOTSTRAP=1
    
    
    bbnote "Building StarryOS for ${RUST_TARGET} with features: ${CARGO_FEATURES}"
    
    # 直接使用 cargo build（不通过 Makefile）
    cargo build \
        --manifest-path ${S}/Cargo.toml \
        --target ${RUST_TARGET} \
        --release \
        --features "${CARGO_FEATURES}"
    
    # 检查编译产物
    if [ ! -f "${B}/target/${RUST_TARGET}/release/starry" ]; then
        bbfatal "Build failed: starry binary not found"
    fi
}

do_install() {
    install -d ${D}/boot
    
    # 1. 安装 ELF（带符号表，用于 GDB 调试）
    install -m 0755 \
        ${B}/target/${RUST_TARGET}/release/starry \
        ${D}/boot/starry.elf
    
    # 2. 生成裸机二进制镜像（QEMU + 真实硬件通用）
    rust-objcopy \
        --binary-architecture=${ARCEOS_ARCH} \
        ${D}/boot/starry.elf \
        --strip-all -O binary \
        ${D}/boot/starry.bin
    
    bbnote "Installed starry.elf and starry.bin to /boot"
}

do_deploy() {
    install -d ${DEPLOYDIR}
    
    # 部署裸机二进制（主要产物）
    install -m 0644 ${D}/boot/starry.bin ${DEPLOYDIR}/starry-${MACHINE}.bin
    
    # 部署 ELF（调试用）
    install -m 0644 ${D}/boot/starry.elf ${DEPLOYDIR}/starry-${MACHINE}.elf
    
    # 生成符号链接（方便 runqemu 使用）
    ln -sf starry-${MACHINE}.bin ${DEPLOYDIR}/starry.bin
    ln -sf starry-${MACHINE}.elf ${DEPLOYDIR}/starry.elf
    
    bbnote "Deployed to ${DEPLOYDIR}:"
    bbnote "  - starry-${MACHINE}.bin (bare-metal binary)"
    bbnote "  - starry-${MACHINE}.elf (with debug symbols)"
}

addtask deploy after do_install before do_build
