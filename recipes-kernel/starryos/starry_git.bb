SUMMARY = "StarryOS - Unix-like OS based on ArceOS"
DESCRIPTION = "StarryOS is a Unix-like operating system built on the ArceOS modular kernel framework"
SECTION = "kernel"
LICENSE = "GPL-3.0-only"
LIC_FILES_CHKSUM = "file://LICENSE;md5=175792518e4ac015ab6696d16c4f607e"

inherit arceos deploy
EXTRA_IMAGEDEPENDS = ""

# ==================== 版本与源码 ====================
PV = "1.0+git${SRCPV}"

# 下载 StarryOS 主仓库、ArceOS 和所有 local_crates submodules
SRC_URI = "git://github.com/kylin-x-kernel/StarryOS.git;protocol=https;branch=main;name=starry \
           git://github.com/kylin-x-kernel/arceos.git;protocol=https;branch=dev;name=arceos;destsuffix=git/arceos \
           git://github.com/kylin-x-kernel/axdriver_crates.git;protocol=https;branch=dev;name=axdriver;destsuffix=git/local_crates/axdriver_crates \
           git://github.com/kylin-x-kernel/axplat_crates.git;protocol=https;branch=dev;name=axplat;destsuffix=git/local_crates/axplat_crates \
           git://github.com/kylin-x-kernel/axplat-aarch64-crosvm-virt.git;protocol=https;branch=main;name=crosvm;destsuffix=git/local_crates/axplat-aarch64-crosvm-virt \
           git://github.com/kylin-x-kernel/fdtree-rs.git;protocol=https;branch=main;name=fdtree;destsuffix=git/local_crates/fdtree-rs \
           git://github.com/kylin-x-kernel/arm-gic.git;protocol=https;branch=main;name=armgic;destsuffix=git/local_crates/arm-gic"

# 使用固定的 commit hash（避免 AUTOREV 在解析时需要网络）
# 更新时需要手动修改这些 hash
SRCREV_starry = "210fa36a628813e06d5419709e1b42ea371e9e25"
SRCREV_arceos = "eb7a020b7d9e2c506998c6dd8f1325df3e2bdc6d"
SRCREV_axdriver = "43feffe8b054984471544d423811e686179ec3ad"
SRCREV_axplat = "3000f4d52024a303261ccd1adef379684e2a9535"
SRCREV_crosvm = "3b9ef2651d840ab2ea4e57d16881c16d6aa8e3a8"
SRCREV_fdtree = "d69bcb0e04176a1c9863cb0f8951b755e45f4a4a"
SRCREV_armgic = "35bfb52d71f8e73344178c0537b918b8660b2305"
SRCREV_FORMAT = "starry_arceos"

S = "${WORKDIR}/git"

# 允许网络访问下载依赖
do_configure[network] = "1"
do_compile[network] = "1"

# ==================== 平台配置 ====================
COMPATIBLE_MACHINE = "(aarch64-qemu-virt|riscv64-qemu-virt|loongarch64-qemu-virt|x86_64-qemu-q35)"

# ArceOS 配置（从 machine 继承 ARCEOS_PLAT_PACKAGE 和 RUST_TARGET）
ARCEOS_SMP = "4"
ARCEOS_LOG = "warn"

# Cargo features（对应 StarryOS 的 features）
CARGO_FEATURES = "qemu"

# ==================== 构建配置 ====================
# 添加 do_configure 来处理 Cargo 依赖
do_configure:prepend() {
    # 添加 [patch] 配置，将 git 依赖替换为本地路径
    # 只 patch 简单的依赖，避免 workspace 冲突
    # axplat_crates 是独立 workspace，让 Cargo 从网络下载
    if ! grep -q "# BitBake patch configuration" ${S}/Cargo.toml; then
        cat >> ${S}/Cargo.toml << 'EOF'

# BitBake patch configuration - replace git dependencies with local paths
[patch."https://github.com/kylin-x-kernel/arm-gic.git"]
arm-gic = { path = "local_crates/arm-gic" }
EOF
    fi
}

# arceos.bbclass 已提供默认 do_compile，这里只需覆盖特殊逻辑
do_compile() {
    # 确保使用 Yocto 提供的 Rust 工具链，而不是系统的
    # BitBake 会通过 recipe-sysroot-native 提供 rustc 和 cargo
    export PATH="${RECIPE_SYSROOT_NATIVE}/usr/bin:${PATH}"
    
    # 诊断：检查使用的工具链
    bbnote "Using rustc: $(which rustc)"
    bbnote "Using cargo: $(which cargo)"
    bbnote "Rustc version: $(rustc --version)"
    bbnote "Rustc sysroot: $(rustc --print sysroot)"
    
    export RUST_TARGET="${RUST_TARGET_TRIPLE}"
    export CARGO_BUILD_TARGET="${RUST_TARGET_TRIPLE}"
    export AX_CONFIG_PATH="${S}/.axconfig.toml"
    
    # lwext4_rust 的 C 代码需要 aarch64-linux-musl-cc
    # 创建 wrapper script（而不是符号链接），自动添加 sysroot 和 include 路径
    mkdir -p ${WORKDIR}/musl-wrapper
    
    # 提取编译器名称并找到完整路径
    CC_NAME=$(echo "${CC}" | awk '{print $1}')
    CC_FULLPATH=$(which ${CC_NAME})
    
    # 创建 aarch64-linux-musl-cc wrapper
    cat > ${WORKDIR}/musl-wrapper/${ARCEOS_ARCH}-linux-musl-cc << EOF
#!/bin/sh
exec "${CC_FULLPATH}" --sysroot=${STAGING_DIR_TARGET} -isystem ${STAGING_INCDIR} "\$@"
EOF
    chmod +x ${WORKDIR}/musl-wrapper/${ARCEOS_ARCH}-linux-musl-cc
    
    # 同时创建 aarch64-linux-musl-gcc 符号链接（lwext4 build.rs 需要）
    ln -sf ${ARCEOS_ARCH}-linux-musl-cc ${WORKDIR}/musl-wrapper/${ARCEOS_ARCH}-linux-musl-gcc
    
    # 提取 ar 路径
    AR_NAME=$(echo "${AR}" | awk '{print $1}')
    AR_FULLPATH=$(which ${AR_NAME})
    
    # 创建 ar wrapper
    cat > ${WORKDIR}/musl-wrapper/${ARCEOS_ARCH}-linux-musl-ar << EOF
#!/bin/sh
exec "${AR_FULLPATH}" "\$@"
EOF
    chmod +x ${WORKDIR}/musl-wrapper/${ARCEOS_ARCH}-linux-musl-ar
    
    export PATH="${WORKDIR}/musl-wrapper:$PATH"
    export ARCH="${ARCEOS_ARCH}"
    
    # 告诉 bindgen 使用正确的 sysroot（lwext4_rust 的 build.rs 需要）
    export BINDGEN_EXTRA_CLANG_ARGS="--sysroot=${STAGING_DIR_TARGET} -I${STAGING_INCDIR}"
    
    # 设置 cargo 环境变量（包括 RUSTC_BOOTSTRAP, HOST_CC 等）
    oe_cargo_fix_env
    
    bbnote "Building StarryOS for ${CARGO_BUILD_TARGET} with features: ${CARGO_FEATURES}"
    bbnote "Using cross compiler: ${CC}"
    
    # 使用标准的 oe_cargo_build（cargo.bbclass 提供）
    # CARGO_BUILD_TARGET 在 arceos.bbclass 中设置为 ${RUST_TARGET_TRIPLE}
    # CARGO_FEATURES 在 recipe 中设置
    oe_cargo_build
    
    # 检查编译产物
    if [ ! -f "${S}/target/${CARGO_BUILD_TARGET}/release/starry" ]; then
        bbfatal "Build failed: starry binary not found"
    fi
}

do_install() {
    install -d ${D}/boot
    
    # 1. 安装 ELF（带符号表，用于 GDB 调试）
    install -m 0755 \
        ${S}/target/${RUST_TARGET_TRIPLE}/release/starry \
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
