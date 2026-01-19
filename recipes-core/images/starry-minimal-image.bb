# starry-minimal-image.bb
# StarryOS 最小发行版镜像
#
# 设计原则：
# - 继承 core-image 类，复用 Yocto 标准镜像构建逻辑
# - 安装 packagegroup-core-boot，提供最小系统必需组件
# - 仅定义镜像特定配置（内容、大小、格式）
# - 发行版策略（TCLIBC）在 starryos.conf 中定义
# - 内核构建逻辑在 starry_git.bb 中定义
# - 机器配置在 aarch64-qemu-virt.conf 中定义

SUMMARY = "Minimal StarryOS Distribution Image"
DESCRIPTION = "A minimal but functional StarryOS-based Linux distribution. \
Includes StarryOS kernel, musl C library, BusyBox, and \
basic utilities. Suitable for embedded systems and testing."
LICENSE = "Apache-2.0"

# ==================== 继承镜像构建基类 ====================
# core-image 提供：
#   - 标准的 rootfs 结构 (/bin, /etc, /lib, /usr)
#   - 镜像构建任务（do_rootfs, do_image）
#   - IMAGE_FEATURES、IMAGE_INSTALL 等变量处理
inherit core-image

# extrausers 提供用户管理功能
inherit extrausers

# ==================== 依赖 StarryOS 内核 ====================
# 内核通过 virtual/kernel 机制自动引入（image.bbclass 处理）：
#   1. starryos.conf 设置: PREFERRED_PROVIDER_virtual/kernel = "starry"
#   2. image.bbclass 自动添加: do_build[depends] += "virtual/kernel:do_deploy"

# ==================== 移除不必要的依赖 ====================
DEPENDS:remove = "cross-localedef-native"

WARN_QA:pn-init-system-helpers:remove = "license-file-missing"
WARN_QA:pn-sysvinit-inittab:remove = "license-file-missing"

# ==================== 镜像配置====================

# 镜像格式：ext4（可挂载）+ tar.gz（便于分发）
IMAGE_FSTYPES = "ext4 tar.gz"

# ==================== ext4 文件系统参数 ====================
# lwext4_rust (StarryOS 的 ext4 实现) 需要特定的 ext4 格式：
#   -b 4096        : Block size 4096 字节
#   -O ^metadata_csum : 禁用元数据校验和
#   -O ^orphan_file   : 禁用 orphan 文件
#   -i 4096        : Inode 大小
# 参考: StarryOS/rootfs-aarch64.img 的格式
EXTRA_IMAGECMD:ext4 = "-b 4096 -O ^metadata_csum,^orphan_file -i 4096"

# 镜像大小：256MB（最小发行版，足够容纳基础工具）
IMAGE_ROOTFS_SIZE ?= "262144"

# 额外空间：32MB（预留给运行时生成的文件）
IMAGE_ROOTFS_EXTRA_SPACE = "32768"

# ==================== 镜像特性====================
# 通过 IMAGE_FEATURES 复用 Yocto 的标准功能定义
IMAGE_FEATURES += "\
    debug-tweaks \
"
# debug-tweaks 包含：
#   - 允许 root 空密码登录
#   - 启用串口 getty
#   - 包含基础调试符号

# ==================== 基础包安装 ====================
# 复用 packagegroup-core-boot
# 包含：init, busybox, base-files 等最小系统必需组件
IMAGE_INSTALL = "packagegroup-core-boot"

# ==================== 网络工具 ====================
IMAGE_INSTALL:append = " net-tools"

# ==================== 用户配置 ====================
EXTRA_USERS_PARAMS = "\
    usermod -d /root root; \
    usermod -s /bin/sh root; \
"
# 注意：
# - 生产环境应设置 root 密码：usermod -p '<encrypted-password>' root
# - 或者禁用 root 登录：usermod -L root
# - debug-tweaks 功能会允许空密码登录（仅用于开发）

KERNELDEPMODDEPEND = ""
do_rootfs[recrdeptask] = "do_package_write_rpm do_package_write_ipk do_package_write_deb"

deltask do_populate_sdk
deltask do_populate_sdk_ext
deltask do_sdk_depends

# ==================== 运行时配置 ====================
# 创建自定义的系统标识文件和启动脚本
# 使用 ROOTFS_POSTPROCESS_COMMAND
ROOTFS_POSTPROCESS_COMMAND:append = " create_starry_release; create_starry_network_info;"

create_starry_release() {
    cat > ${IMAGE_ROOTFS}${sysconfdir}/starry-release << EOF
StarryOS Minimal Distribution ${DISTRO_VERSION}
Built with Yocto Project (${DISTRO_CODENAME})
Kernel: StarryOS (Rust)
C Library: musl
Architecture: ${MACHINE}
Build Date: $(date -u +"%Y-%m-%d %H:%M:%S UTC")
EOF

    # 更新 /etc/os-release（如果存在）
    if [ -f ${IMAGE_ROOTFS}${sysconfdir}/os-release ]; then
        sed -i '/^PRETTY_NAME=/d' ${IMAGE_ROOTFS}${sysconfdir}/os-release
        echo 'PRETTY_NAME="StarryOS Minimal ${DISTRO_VERSION}"' >> ${IMAGE_ROOTFS}${sysconfdir}/os-release
    fi

    # 创建欢迎信息 /etc/motd
    cat > ${IMAGE_ROOTFS}${sysconfdir}/motd << 'EOFMOTD'

 ____  _                          ___  ____  
/ ___|| |_ __ _ _ __ _ __ _   _  / _ \/ ___| 
\___ \| __/ _` | '__| '__| | | || | | \___ \ 
 ___) | || (_| | |  | |  | |_| || |_| |___) |
|____/ \__\__,_|_|  |_|   \__, | \___/|____/ 
                          |___/              

StarryOS Minimal Distribution
Type 'cat /etc/starry-release' for system info.

EOFMOTD

    # 只在交互式登录 shell 中显示
    if [ -f ${IMAGE_ROOTFS}${sysconfdir}/profile ]; then
        if ! grep -q "cat.*motd\|/etc/motd" ${IMAGE_ROOTFS}${sysconfdir}/profile; then
            cat >> ${IMAGE_ROOTFS}${sysconfdir}/profile << 'PROFMOTD'

# Display /etc/motd on login (interactive shell only)
if [ -t 0 ] && [ -f /etc/motd ]; then
    cat /etc/motd
fi
PROFMOTD
        fi
    fi
}

# 创建网络信息显示脚本
create_starry_network_info() {
    # 创建启动时自动显示 IP 的脚本
    install -d ${IMAGE_ROOTFS}${sysconfdir}/init.d
    
    cat > ${IMAGE_ROOTFS}${sysconfdir}/init.d/starry-network-info.sh << 'EOF'
#!/bin/sh
# StarryOS Network Information Display
# 用于 OEQA 测试框架识别目标系统 IP

# 等待网络初始化
sleep 2

# StarryOS 默认使用 QEMU slirp 模式的固定 IP
STARRY_IP="10.0.2.15"
STARRY_GW="10.0.2.2"

# 以 OEQA 能识别的格式输出 IP 信息
echo "============================================"
echo "StarryOS Network Configuration"
echo "============================================"
echo "IP: ${STARRY_IP}"
echo "Gateway: ${STARRY_GW}"
echo "inet addr:${STARRY_IP}  Bcast:10.0.2.255  Mask:255.255.255.0"
echo "============================================"

EOF
    
    chmod +x ${IMAGE_ROOTFS}${sysconfdir}/init.d/starry-network-info.sh
    
    # 添加到 /etc/profile 使其在登录时自动执行
    cat >> ${IMAGE_ROOTFS}${sysconfdir}/profile << 'PROFILEEOF'

# 显示 StarryOS 网络信息（用于 OEQA 测试）
if [ -x /etc/init.d/starry-network-info.sh ]; then
    /etc/init.d/starry-network-info.sh
fi
PROFILEEOF
}



