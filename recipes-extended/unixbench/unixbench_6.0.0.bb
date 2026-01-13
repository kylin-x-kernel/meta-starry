SUMMARY = "UnixBench system benchmark suite"
DESCRIPTION = "A comprehensive benchmark suite for Unix systems"
HOMEPAGE = "https://github.com/kdlucas/byte-unixbench"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0-only;md5=801f80980d171dd6425610833a22dbe6"

SRC_URI = "git://github.com/kdlucas/byte-unixbench.git;protocol=https;branch=master"
SRCREV = "986b674a1e50eedd944bc08d6bdc8be925b2bc65"

S = "${WORKDIR}/git/UnixBench"

DEPENDS = "perl-native"

# UnixBench 运行时依赖
RDEPENDS:${PN} = "\
    bash \
    perl \
    perl-modules \
    coreutils \
    findutils \
    diffutils \
    grep \
    sed \
    gawk \
    make \
"

do_compile() {
    # 编译 UnixBench（静态链接）
    # 静态链接可以避免运行时的动态库依赖问题
    
    # 编译所有测试程序，使用追加方式添加 -static 选项
    # 不能直接覆盖 CFLAGS，因为 Makefile 中有 target-specific 的 CFLAGS 设置
    oe_runmake CC="${CC}" OPTON="-static"
}

do_install() {
    # 安装完整的 UnixBench 目录到 /usr/share/unixbench
    install -d ${D}${datadir}/unixbench
    
    # 复制整个 UnixBench 目录（保留原始结构）
    cp -r ${S}/* ${D}${datadir}/unixbench/
    
    # 确保 Run 脚本可执行
    chmod +x ${D}${datadir}/unixbench/Run
    
    # 确保所有编译的二进制文件可执行
    find ${D}${datadir}/unixbench/pgms -type f -exec chmod +x {} \;
    
    # 创建结果和临时目录（如果不存在）
    install -d ${D}${datadir}/unixbench/results
    install -d ${D}${datadir}/unixbench/tmp
    
    # 在 /usr/bin 创建包装脚本，方便从任意位置运行
    install -d ${D}${bindir}
    cat > ${D}${bindir}/unixbench << 'EOF'
#!/bin/sh
cd /usr/share/unixbench
exec ./Run "$@"
EOF
    chmod +x ${D}${bindir}/unixbench
}

FILES:${PN} = "\
    ${datadir}/unixbench \
    ${bindir}/unixbench \
"

# 跳过 QA 检查
INSANE_SKIP:${PN} += "already-stripped ldflags debug-files staticdev"
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"
INHIBIT_PACKAGE_STRIP = "1"

