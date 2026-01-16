# starry_git.bb
# StarryOS - 基于 ArceOS 的 Unix-like 操作系统
#
# 本配方复刻 StarryOS/Makefile 的构建行为
# 通过 arceos-features.bbclass 实现 Feature 自动推导
#
# 构建命令对照:
#   make                    -> bitbake starry (MACHINE=aarch64-qemu-virt)
#   make ARCH=riscv64       -> bitbake starry (MACHINE=riscv64-qemu-virt)
#   make ARCH=loongarch64   -> bitbake starry (MACHINE=loongarch64-qemu-virt)
#   make vf2                -> bitbake starry (MACHINE=riscv64-visionfive2)
#   make crosvm             -> bitbake starry (MACHINE=aarch64-crosvm)
#   make dice               -> bitbake starry (MACHINE=aarch64-dice)

SUMMARY = "StarryOS - Unix-like OS based on ArceOS"
DESCRIPTION = "StarryOS is a complete Unix-like operating system kernel built on \
the ArceOS modular kernel framework. ArceOS provides kernel modules (axhal, \
axruntime, axtask, axfs, axnet, etc.) while StarryOS adds the Unix-like \
system call layer, process management, and file system support."

SECTION = "kernel"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=175792518e4ac015ab6696d16c4f607e"

# ==================== 继承构建类 ====================
# arceos-features 继承 arceos，arceos 继承 rust-kernel
inherit arceos-features deploy

# ==================== 内核提供 ====================
# 提供虚拟内核，供 image 配方使用
PROVIDES += "virtual/kernel"
KERNEL_IMAGETYPE = "bin"
KERNEL_IMAGEDEST = "boot"

# ==================== Bare-metal 内核打包策略 ====================
PACKAGES = ""
ALLOW_EMPTY:${PN} = "1"

# 禁用 RPM/DEB/IPK 打包任务
deltask do_package
deltask do_package_write_rpm
deltask do_package_write_ipk
deltask do_package_write_deb
deltask do_packagedata
deltask do_package_qa

# ==================== 清空内核模块依赖 ====================
KERNELDEPMODDEPEND = ""

# ==================== 版本与源码 ====================
PV = "1.0+git${SRCPV}"

STARRY_GITHUB = "github.com/kylin-x-kernel"

SRC_URI = "\
    git://${STARRY_GITHUB}/StarryOS.git;protocol=https;branch=main;name=starry \
    git://${STARRY_GITHUB}/arceos.git;protocol=https;branch=dev;destsuffix=git/arceos;name=arceos \
    git://${STARRY_GITHUB}/axdriver_crates.git;protocol=https;branch=dev;destsuffix=git/local_crates/axdriver_crates;name=axdriver \
    git://${STARRY_GITHUB}/axplat_crates.git;protocol=https;branch=dev;destsuffix=git/local_crates/axplat_crates;name=axplat \
    git://${STARRY_GITHUB}/axplat-aarch64-crosvm-virt.git;protocol=https;branch=main;destsuffix=git/local_crates/axplat-aarch64-crosvm-virt;name=crosvm \
    git://${STARRY_GITHUB}/fdtree-rs.git;protocol=https;branch=main;destsuffix=git/local_crates/fdtree-rs;name=fdtree \
    git://${STARRY_GITHUB}/arm-gic.git;protocol=https;branch=main;destsuffix=git/local_crates/arm-gic;name=armgic \
    git://${STARRY_GITHUB}/page_table_multiarch.git;protocol=https;branch=dev;name=page_table_multiarch \
    file://0001-use-stable-rust-toolchain.patch \
"

# 固定 commit hash 
SRCREV_starry = "210fa36a628813e06d5419709e1b42ea371e9e25"
SRCREV_arceos = "eb7a020b7d9e2c506998c6dd8f1325df3e2bdc6d"
SRCREV_axdriver = "43feffe8b054984471544d423811e686179ec3ad"
SRCREV_axplat = "3000f4d52024a303261ccd1adef379684e2a9535"
SRCREV_crosvm = "3b9ef2651d840ab2ea4e57d16881c16d6aa8e3a8"
SRCREV_fdtree = "d69bcb0e04176a1c9863cb0f8951b755e45f4a4a"
SRCREV_armgic = "35bfb52d71f8e73344178c0537b918b8660b2305"
SRCREV_page_table_multiarch = "01df8185b2eb600525b810ca5817db73bb10199d"
SRCREV_FORMAT = "starry_arceos"

S = "${WORKDIR}/git"

# ==================== 本地开发路径配置 ====================
# 优先使用本地路径（EXTERNALSRC），如果未配置则使用 SRC_URI 下载
# 在 local.conf 中设置以下变量启用本地开发模式

STARRY_LOCAL_PATH ?= ""
ARCEOS_LOCAL_PATH ?= ""
AXDRIVER_LOCAL_PATH ?= ""
AXPLAT_LOCAL_PATH ?= ""
ARMGIC_LOCAL_PATH ?= ""
FDTREE_LOCAL_PATH ?= ""
CROSVM_PLAT_LOCAL_PATH ?= ""
PAGE_TABLE_LOCAL_PATH ?= ""
AXCPU_LOCAL_PATH ?= ""
KERNEL_GUARD_LOCAL_PATH ?= ""

python __anonymous() {
    import os
    
    builddir = d.getVar('TOPDIR')
    workspace_root = os.path.abspath(os.path.join(builddir, '..'))
    
    if os.path.exists(os.path.join(workspace_root, '.repo')):
        bb.note("=" * 60)
        bb.note("检测到 repo 工作区，启用 EXTERNALSRC 本地开发模式")
        
        repos = {
            'STARRY_LOCAL_PATH': 'StarryOS',
            'ARCEOS_LOCAL_PATH': 'arceos',
            'AXDRIVER_LOCAL_PATH': 'axdriver_crates',
            'AXPLAT_LOCAL_PATH': 'axplat_crates',
            'ARMGIC_LOCAL_PATH': 'arm-gic',
            'FDTREE_LOCAL_PATH': 'fdtree-rs',
            'CROSVM_PLAT_LOCAL_PATH': 'axplat-aarch64-crosvm-virt',
            'PAGE_TABLE_LOCAL_PATH': 'page_table_multiarch',
            'AXCPU_LOCAL_PATH': 'axcpu',
            'KERNEL_GUARD_LOCAL_PATH': 'kernel_guard',
        }
        
        for var_name, repo_name in repos.items():
            current_value = d.getVar(var_name)
            if not current_value:
                repo_path = os.path.join(workspace_root, repo_name)
                if os.path.isdir(repo_path):
                    d.setVar(var_name, repo_path)
                    bb.note(f"  {repo_name}: {repo_path}")
            else:
                bb.note(f"  {repo_name}: {current_value} (用户覆盖)")
        
        bb.note("=" * 60)
    
    starry_path = d.getVar('STARRY_LOCAL_PATH')
    if starry_path and os.path.isdir(starry_path):
        d.setVar('EXTERNALSRC', starry_path)
        d.setVar('EXTERNALSRC_BUILD', starry_path)
        bb.note(f"使用本地源码: {starry_path}")
    else:
        bb.note("使用 SRC_URI 从远程下载源码")
}

# ==================== 平台兼容性 ====================
COMPATIBLE_MACHINE = "(aarch64-qemu-virt|riscv64-qemu-virt|loongarch64-qemu-virt|x86_64-qemu-q35)"

# ==================== StarryOS 特定配置 ====================
# 复刻 StarryOS/Makefile 的 export 变量

# 对应: export NO_AXSTD := y
ARCEOS_NO_AXSTD = "y"

# 对应: export AX_LIB := axfeat
ARCEOS_AX_LIB = "axfeat"

# 对应: export APP_FEATURES := qemu (默认值)
# 会被 starry-targets.inc 根据 MACHINE 覆盖
ARCEOS_APP_FEATURES ?= "qemu"

# 启用 backtrace 支持 (需要 ARCEOS_DWARF=y)
# axfeat/dwarf 会启用 axbacktrace/dwarf，在 panic 时显示堆栈
ARCEOS_EXTRA_FEATURES ?= "axfeat/dwarf"

# 引入多目标配置
require starry-targets.inc

# ==================== Cargo.toml 补丁 ====================
do_configure:prepend() {
    if [ -n "${EXTERNALSRC}" ]; then
        bbnote "EXTERNALSRC 模式：创建本地依赖符号链接"
        
        if [ -n "${ARCEOS_LOCAL_PATH}" ] && [ -d "${ARCEOS_LOCAL_PATH}" ]; then
            rm -rf ${S}/arceos
            ln -sf ${ARCEOS_LOCAL_PATH} ${S}/arceos
            bbnote "  arceos -> ${ARCEOS_LOCAL_PATH}"
        fi
        
        if [ -n "${AXDRIVER_LOCAL_PATH}" ] && [ -d "${AXDRIVER_LOCAL_PATH}" ]; then
            mkdir -p ${S}/local_crates
            rm -rf ${S}/local_crates/axdriver_crates
            ln -sf ${AXDRIVER_LOCAL_PATH} ${S}/local_crates/axdriver_crates
            bbnote "  axdriver_crates -> ${AXDRIVER_LOCAL_PATH}"
        fi
        
        if [ -n "${AXPLAT_LOCAL_PATH}" ] && [ -d "${AXPLAT_LOCAL_PATH}" ]; then
            mkdir -p ${S}/local_crates
            rm -rf ${S}/local_crates/axplat_crates
            ln -sf ${AXPLAT_LOCAL_PATH} ${S}/local_crates/axplat_crates
            bbnote "  axplat_crates -> ${AXPLAT_LOCAL_PATH}"
        fi
        
        if [ -n "${ARMGIC_LOCAL_PATH}" ] && [ -d "${ARMGIC_LOCAL_PATH}" ]; then
            mkdir -p ${S}/local_crates
            rm -rf ${S}/local_crates/arm-gic
            ln -sf ${ARMGIC_LOCAL_PATH} ${S}/local_crates/arm-gic
            bbnote "  arm-gic -> ${ARMGIC_LOCAL_PATH}"
        fi
        
        if [ -n "${FDTREE_LOCAL_PATH}" ] && [ -d "${FDTREE_LOCAL_PATH}" ]; then
            mkdir -p ${S}/local_crates
            rm -rf ${S}/local_crates/fdtree-rs
            ln -sf ${FDTREE_LOCAL_PATH} ${S}/local_crates/fdtree-rs
            bbnote "  fdtree-rs -> ${FDTREE_LOCAL_PATH}"
        fi
        
        if [ -n "${CROSVM_PLAT_LOCAL_PATH}" ] && [ -d "${CROSVM_PLAT_LOCAL_PATH}" ]; then
            mkdir -p ${S}/local_crates
            rm -rf ${S}/local_crates/axplat-aarch64-crosvm-virt
            ln -sf ${CROSVM_PLAT_LOCAL_PATH} ${S}/local_crates/axplat-aarch64-crosvm-virt
            bbnote "  axplat-aarch64-crosvm-virt -> ${CROSVM_PLAT_LOCAL_PATH}"
        fi
        
        if [ -n "${PAGE_TABLE_LOCAL_PATH}" ] && [ -d "${PAGE_TABLE_LOCAL_PATH}" ]; then
            mkdir -p ${S}/local_crates
            rm -rf ${S}/local_crates/page_table_multiarch
            ln -sf ${PAGE_TABLE_LOCAL_PATH} ${S}/local_crates/page_table_multiarch
            bbnote "  page_table_multiarch -> ${PAGE_TABLE_LOCAL_PATH}"
        fi
        
        if [ -n "${AXCPU_LOCAL_PATH}" ] && [ -d "${AXCPU_LOCAL_PATH}" ]; then
            mkdir -p ${S}/local_crates
            rm -rf ${S}/local_crates/axcpu
            ln -sf ${AXCPU_LOCAL_PATH} ${S}/local_crates/axcpu
            bbnote "  axcpu -> ${AXCPU_LOCAL_PATH}"
        fi
        
        if [ -n "${KERNEL_GUARD_LOCAL_PATH}" ] && [ -d "${KERNEL_GUARD_LOCAL_PATH}" ]; then
            mkdir -p ${S}/local_crates
            rm -rf ${S}/local_crates/kernel_guard
            ln -sf ${KERNEL_GUARD_LOCAL_PATH} ${S}/local_crates/kernel_guard
            bbnote "  kernel_guard -> ${KERNEL_GUARD_LOCAL_PATH}"
        fi
        
        return
    fi
    
    if ! grep -q "# BitBake patch configuration" ${S}/Cargo.toml; then
        bbnote "SRC_URI 模式：修改 Cargo.toml 使用下载的依赖"
        cat >> ${S}/Cargo.toml << 'EOF'

# BitBake patch configuration - replace git dependencies with local paths
[patch."https://github.com/kylin-x-kernel/arm-gic.git"]
arm-gic = { path = "local_crates/arm-gic" }
EOF
    fi
}

# ==================== 安装 ====================
#  build.mk 的后处理逻辑
do_install() {
    install -d ${D}/boot
    
    # Cargo package name = "starry" (from Cargo.toml)
    local kernel_elf="${S}/target/${RUST_TARGET}/release/starry"
    
    if [ ! -f "${kernel_elf}" ]; then
        bbfatal "Kernel ELF not found: ${kernel_elf}"
    fi
    
    # 设置 LD_LIBRARY_PATH 以便 rust-objcopy 找到 LLVM 共享库
    export LD_LIBRARY_PATH="${STAGING_DIR_NATIVE}/usr/lib:${LD_LIBRARY_PATH}"
    
    # 设置 PATH 以找到 Rust 工具 (rust-objdump, rust-objcopy)
    export PATH="${STAGING_DIR_NATIVE}/usr/bin:${PATH}"
    
    # ==================== DWARF 嵌入处理 ====================
    # 复刻 arceos/scripts/make/dwarf.sh
    if [ "${ARCEOS_DWARF}" = "y" ]; then
        bbnote "Processing DWARF sections for backtrace support..."
        
        cd "${S}/target/${RUST_TARGET}/release"
        
        # DWARF 段列表（无点前缀）
        SECTIONS="debug_abbrev debug_addr debug_aranges debug_info debug_line debug_line_str debug_ranges debug_rnglists debug_str debug_str_offsets"
        
        # 检查 ELF 是否有标准 DWARF 信息（.debug_*）
        # 使用 rust-objcopy --dump-section 测试，因为可能没有 rust-objdump
        if rust-objcopy starry --dump-section .debug_info=/dev/null 2>/dev/null; then
            bbnote "Found standard DWARF sections (.debug_*), extracting..."
            
            # 1. 从标准 .debug_* 段提取数据
            for section in $SECTIONS; do
                rust-objcopy starry --dump-section .$section=$section.bin 2>/dev/null || touch $section.bin
            done
            
            # 2. 删除标准 DEBUG 段
            rust-objcopy starry --strip-debug
            
            # 3. 把数据更新到 ArceOS 自定义段（无点前缀，PT_LOAD）
            # 4. 然后重命名为 .debug_*，供 addr2line 使用
            CMD="rust-objcopy starry"
            for section in $SECTIONS; do
                if [ -s "$section.bin" ]; then
                    CMD="$CMD --update-section $section=$section.bin"
                    CMD="$CMD --rename-section $section=.$section"
                fi
            done
            eval $CMD
            
            # 清理临时文件
            for section in $SECTIONS; do
                rm -f $section.bin
            done
            
            bbnote "DWARF sections embedded successfully"
        else
            bbwarn "No DWARF sections found in ELF - backtrace will show addresses only"
        fi
        
        cd -
    fi
    
    # 安装 ELF (调试用)
    install -m 0755 "${kernel_elf}" "${D}/boot/${PN}.elf"
    bbnote "Installed: ${D}/boot/${PN}.elf"
    
    # 生成二进制镜像 (复刻 build.mk:66)
    rust-objcopy \
        --binary-architecture=${ARCEOS_ARCH} \
        "${kernel_elf}" \
        --strip-all -O binary \
        "${D}/boot/${PN}.bin"
    
    # 验证二进制镜像非空 (复刻 build.mk:67-70)
    if [ ! -s "${D}/boot/${PN}.bin" ]; then
        bbfatal "Empty kernel image generated, check build configuration"
    fi
    
    bbnote "Installed: ${D}/boot/${PN}.bin"
}

# ==================== 部署 ====================
do_deploy() {
    install -d ${DEPLOYDIR}
    
    # 部署二进制镜像 (主要产物)
    install -m 0644 ${D}/boot/${PN}.bin ${DEPLOYDIR}/${PN}-${MACHINE}.bin
    
    # 部署 ELF (调试用)
    install -m 0644 ${D}/boot/${PN}.elf ${DEPLOYDIR}/${PN}-${MACHINE}.elf
    
    # 创建符号链接 
    ln -sf ${PN}-${MACHINE}.bin ${DEPLOYDIR}/${PN}.bin
    ln -sf ${PN}-${MACHINE}.elf ${DEPLOYDIR}/${PN}.elf
    
    bbnote "Deployed to ${DEPLOYDIR}:"
    bbnote "  - ${PN}-${MACHINE}.bin (bare-metal binary)"
    bbnote "  - ${PN}-${MACHINE}.elf (ELF with debug symbols)"
}

addtask deploy after do_install before do_build
