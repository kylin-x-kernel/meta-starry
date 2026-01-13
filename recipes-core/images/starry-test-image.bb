# starry-test-image.bb
# StarryOS 测试发行版镜像

require starry-minimal-image.bb

SUMMARY = "StarryOS Test Distribution with OEQA Test Suite"
DESCRIPTION = "StarryOS test image with complete test suite and OEQA support"

# ==================== 添加测试套件 ====================
IMAGE_INSTALL:append = " \
    stress-ng \
    vsock-server \
    starry-ci-tests \
    unixbench \
"

# ==================== 镜像配置 ====================
IMAGE_FSTYPES = "ext4 tar.gz"
IMAGE_ROOTFS_SIZE ?= "8192000"
IMAGE_ROOTFS_EXTRA_SPACE = "1048576"

QB_MEM = "-m 2G" 
QEMU_USE_KVM = ""

# ==================== 测试框架 ====================
inherit testimage

# ==================== vsock 配置 ====================
# 使用 vsock 通信，无需 SSH/网络
TEST_TARGET = "OEQemuVsockTarget"
TEST_TARGET_CID = "103"
TEST_TARGET_PORT = "5555"

# 可用套件：ci, unixbench, stress
# 可在 local.conf 中覆盖，或通过环境变量设置
TEST_SUITES ??= "ci unixbench stress"

# 测试超时配置
TEST_QEMUBOOT_TIMEOUT = "300"       
TEST_OVERALL_TIMEOUT = "3600"

# ==================== OEQA 启动提示匹配 ====================
# StarryOS 自动进入 shell，没有 login: 提示，需覆盖默认登录匹配规则
TESTIMAGE_BOOT_PATTERNS = "search_reached_prompt send_login_user search_login_succeeded search_cmd_finished"
TESTIMAGE_BOOT_PATTERNS[search_reached_prompt] = "root@starry:~#"
TESTIMAGE_BOOT_PATTERNS[send_login_user] = ""
TESTIMAGE_BOOT_PATTERNS[search_login_succeeded] = "root@starry:~#"
TESTIMAGE_BOOT_PATTERNS[search_cmd_finished] = "root@starry:~#"


# 使用 slirp 网络（StarryOS 内部仍需网络栈）
TEST_RUNQEMUPARAMS = "nographic slirp"

# ==================== 内核配置 ====================
KERNEL_IMAGETYPE = "bin"
KERNEL_IMAGEDEST = "boot"

# ==================== 串口控制台 ====================
SERIAL_CONSOLES = "115200;ttyAMA0"

# ==================== Rootfs 后处理 ====================
ROOTFS_POSTPROCESS_COMMAND:append = " create_starry_os_release; setup_vsock_autostart; setup_unixbench_env; "

# 配置 UnixBench 环境变量
setup_unixbench_env() {
    cat >> ${IMAGE_ROOTFS}${sysconfdir}/profile << 'UBEOF'

# ==== UnixBench 环境变量 ====
export UB_BINDIR=/usr/share/unixbench/pgms/
UBEOF
}

# 配置 vsock-server 自动启动
setup_vsock_autostart() {
    # 在 /etc/profile 中添加 vsock-server 启动命令
    # 这样当 StarryOS 启动 /bin/sh --login 时会自动启动 vsock-server
    cat >> ${IMAGE_ROOTFS}${sysconfdir}/profile << 'VSOCKEOF'

# ==== vsock-server 自动启动 ====
# 用于 OEQA 测试框架通过 vsock 与 StarryOS 通信
if [ -x /usr/sbin/vsock-server ]; then
    # 检查是否已经运行
    if ! pgrep -x vsock-server > /dev/null 2>&1; then
        echo "Starting vsock-server for OEQA testing..."
        /usr/sbin/vsock-server &
        sleep 1
        echo "vsock-server started (port 5555)"
    fi
fi
VSOCKEOF
}

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
