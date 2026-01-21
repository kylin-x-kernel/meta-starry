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

inherit arceos-features deploy externalsrc

DEPENDS:append = " llvm-native"

# ==================== 内核提供 ====================
# 提供虚拟内核，供 image 配方使用
PROVIDES += "virtual/kernel"
KERNEL_IMAGETYPE = "bin"
KERNEL_IMAGEDEST = "boot"

PACKAGES = ""
ALLOW_EMPTY:${PN} = "1"

deltask do_package
deltask do_package_write_rpm
deltask do_package_write_ipk
deltask do_package_write_deb
deltask do_packagedata
deltask do_package_qa

# ==================== 清空内核模块依赖 ====================
KERNELDEPMODDEPEND = ""

python __anonymous() {
    import os
    
    builddir = d.getVar('TOPDIR')
    workspace_root = os.path.abspath(os.path.join(builddir, '..'))
    
    # 1. 优先使用用户配置的 EXTERNALSRC
    user_externalsrc = d.getVar('EXTERNALSRC')
    if user_externalsrc:
        bb.note(f"使用用户配置的 EXTERNALSRC: {user_externalsrc}")
        d.setVar('S', user_externalsrc)
        d.setVar('PV', "1.0+gitDEV")
        return
    
    # 2. 自动检测 repo 工作区
    if not os.path.exists(os.path.join(workspace_root, '.repo')):
        bb.fatal("未检测到 repo 工作区，且 EXTERNALSRC_pn-starry 未设置。请在 local.conf 中配置 EXTERNALSRC_pn-starry")
        return
    
    bb.note("=" * 60)
    bb.note("检测到 repo 工作区，启用 EXTERNALSRC 本地开发模式")
    
    starry_path = os.path.join(workspace_root, 'StarryOS')
    if not os.path.isdir(starry_path):
        bb.fatal(f"StarryOS 目录不存在: {starry_path}")
        return
    
    bb.note(f"  StarryOS: {starry_path}")
    d.setVar('EXTERNALSRC', starry_path)
    d.setVar('S', starry_path)
    d.setVar('PV', "1.0+gitDEV")
    bb.note("=" * 60)
}

do_compile[nostamp] = "1"
do_clean[cleandirs] = ""
do_cleansstate[cleandirs] = ""
do_cleanall[cleandirs] = ""
do_configure[cleandirs] = ""
do_compile[cleandirs] = ""
do_install[cleandirs] = ""
do_fetch[cleandirs] = ""
do_unpack[cleandirs] = ""
do_patch[cleandirs] = ""

do_unpack[cleandirs] = ""
do_cleansstate[noexec] = "1"
do_cleanall[noexec] = "1"
do_fetch[noexec] = "1"
do_unpack[noexec] = "1"
do_patch[noexec] = "1"
SOURCE_DATE_EPOCH = "1609459200"
do_configure[vardepsexclude] += "DATE TIME DATETIME"
do_compile[vardepsexclude] += "DATE TIME DATETIME"
do_install[vardepsexclude] += "DATE TIME DATETIME"
do_deploy[vardepsexclude] += "DATE TIME DATETIME"
do_populate_sysroot[vardepsexclude] += "DATE TIME DATETIME"
do_deploy_source_date_epoch[vardepsexclude] += "DATE TIME DATETIME"

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

require starry-targets.inc

# ==================== Cargo 配置 ====================

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
