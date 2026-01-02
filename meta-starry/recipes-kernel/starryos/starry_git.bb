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
           git://github.com/kylin-x-kernel/arm-gic.git;protocol=https;branch=main;name=armgic;destsuffix=git/local_crates/arm-gic \
           file://0001-use-stable-rust-toolchain.patch \
           "

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

# ==================== 平台配置 ====================
COMPATIBLE_MACHINE = "(aarch64-qemu-virt|riscv64-qemu-virt|loongarch64-qemu-virt|x86_64-qemu-q35)"

# ArceOS 配置继承自机器配置文件
# ARCEOS_SMP, ARCEOS_LOG, CARGO_FEATURES 使用机器默认值
# 如需覆盖，可在此设置：
# ARCEOS_SMP = "8"
# CARGO_FEATURES = "qemu driver-ixgbe"

# ==================== 构建配置 ====================
# Cargo.toml patch: 替换 git 依赖为本地路径
do_configure:prepend() {
    if ! grep -q "# BitBake patch configuration" ${S}/Cargo.toml; then
        cat >> ${S}/Cargo.toml << 'EOF'

# BitBake patch configuration - replace git dependencies with local paths
[patch."https://github.com/kylin-x-kernel/arm-gic.git"]
arm-gic = { path = "local_crates/arm-gic" }
EOF
    fi
}

# arceos.bbclass 已提供完整的 do_compile 实现（包括 lwext4_rust 工具链适配）

# rust-kernel.bbclass 提供默认 do_install（自动查找 ${PN}.elf = starry.elf）
# 这里扩展：生成裸机二进制镜像
do_install:append() {
    # rust-kernel.bbclass 已安装 starry.elf，这里生成 .bin
    rust-objcopy \
        --binary-architecture=${ARCEOS_ARCH} \
        ${D}/boot/starry.elf \
        --strip-all -O binary \
        ${D}/boot/starry.bin
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
