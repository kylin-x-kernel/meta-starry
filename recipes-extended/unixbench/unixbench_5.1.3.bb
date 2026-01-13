SUMMARY = "UnixBench system benchmark suite"
DESCRIPTION = "A comprehensive benchmark suite for Unix systems"
HOMEPAGE = "https://github.com/kdlucas/byte-unixbench"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/GPL-2.0-only;md5=801f80980d171dd6425610833a22dbe6"

SRC_URI = "git://github.com/kdlucas/byte-unixbench.git;protocol=https;branch=master \
           file://run-unixbench.sh"
SRCREV = "c7ea5d492b1f82d4ccd270fee27863425e436235"

S = "${WORKDIR}/git/UnixBench"

DEPENDS = "perl-native"
RDEPENDS:${PN} = "bash"(no compilation required)

do_compile() {
    export CC="${CC}"
    export CFLAGS="${CFLAGS}"
    export LDFLAGS="${LDFLAGS}"
    oe_runmake CC="${CC}"
}

do_install() {
    # 安装已编译的二进制文件
    install -d ${D}${datadir}/unixbench/pgms
    install -d ${D}${datadir}/unixbench/testdir
    install -d ${D}${datadir}/unixbench/tmp
    install -d ${D}${datadir}/unixbench/results
    
    install -m 0755 ${S}/pgms/arithoh ${D}${datadir}/unixbench/pgms/
    install -m 0755 ${S}/pgms/dhry2reg ${D}${datadir}/unixbench/pgms/
    install -m 0755 ${S}/pgms/whetstone-double ${D}${datadir}/unixbench/pgms/
    install -m 0755 ${S}/pgms/syscall ${D}${datadir}/unixbench/pgms/
    install -m 0755 ${S}/pgms/context1 ${D}${datadir}/unixbench/pgms/
    install -m 0755 ${S}/pgms/pipe ${D}${datadir}/unixbench/pgms/
    install -m 0755 ${S}/pgms/spawn ${D}${datadir}/unixbench/pgms/
    install -m 0755 ${S}/pgms/execl ${D}${datadir}/unixbench/pgms/
    
    # 安装运行脚本
    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/run-unixbench.sh ${D}${bindir}/run-unixbench
    
    # 替换占位符
    sed -i "s|@DATADIR@|${datadir}|g" ${D}${bindir}/run-unixbench
}

FILES:${PN} = "${datadir}/unixbench/pgms/* ${datadir}/unixbench/testdir ${datadir}/unixbench/tmp ${datadir}/unixbench/results ${bindir}/run-unixbench"
INSANE_SKIP:${PN} = "already-stripped ldflags debug-files"
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"

