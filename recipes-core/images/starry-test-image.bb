# starry-test-image.bb
# StarryOS 测试发行版镜像

SUMMARY = "StarryOS Test Distribution - Complete OS Image"
DESCRIPTION = "A complete StarryOS-based operating system for testing, including \
kernel, userspace tools, debug utilities, and test framework."
LICENSE = "Apache-2.0"

inherit core-image

# ==================== 依赖关系 ====================
DEPENDS += "starry"

# ==================== 镜像特性（通用功能）====================
IMAGE_FEATURES += "\
    debug-tweaks \
    ssh-server-dropbear \
    tools-debug \
    tools-profile \
    package-management \
    bash-completion-pkgs \
"

# ==================== 安装的包 ====================
# StarryOS 特定功能（模块化管理）
IMAGE_INSTALL:append = " packagegroup-starrytest "

# ==================== 镜像配置 ====================
IMAGE_FSTYPES = "ext4 tar.gz"
IMAGE_ROOTFS_SIZE ?= "8192000"
IMAGE_ROOTFS_EXTRA_SPACE = "1048576"

# ==================== 测试框架 ====================
IMAGE_CLASSES += "testimage"
TEST_SUITES = "ping ssh"

# ==================== 内核配置 ====================
KERNEL_IMAGETYPE = "bin"
KERNEL_IMAGEDEST = "boot"

# ==================== 串口控制台 ====================
SERIAL_CONSOLES = "115200;ttyAMA0"

# ==================== Rootfs 后处理 ====================
ROOTFS_POSTPROCESS_COMMAND:append = " create_starry_os_release; "

create_starry_os_release() {
    cat > ${IMAGE_ROOTFS}/etc/starry-release << EOF
StarryOS Test Distribution
Version: ${DISTRO_VERSION}
Architecture: ${MACHINE_ARCH}
Platform: ${MACHINE}
Build Date: $(date -u +"%Y-%m-%d %H:%M:%S UTC")
Kernel: StarryOS
EOF

    cat > ${IMAGE_ROOTFS}/etc/motd << EOF

Welcome to StarryOS Test Distribution!

This is a complete operating system built with Yocto.
- Kernel: StarryOS (${MACHINE_ARCH})
- Distribution: ${DISTRO_NAME} ${DISTRO_VERSION}
- Built on: $(date -u +"%Y-%m-%d")

For more information, visit: https://github.com/kylin-x-kernel/meta-starry

EOF

    cat > ${IMAGE_ROOTFS}/etc/issue << EOF
StarryOS Test Distribution ${DISTRO_VERSION}
Kernel: \r on \m

EOF
}
