#
# vsock-server - StarryOS vsock 命令执行服务
#
# 功能：在 StarryOS 启动时监听 vsock 端口，接收并执行 OEQA 测试命令
#

SUMMARY = "StarryOS vsock command execution server for OEQA"
DESCRIPTION = "A vsock server that listens on port 5555 and executes commands \
sent by OEQA test framework. This allows automated testing without SSH."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/meta/COPYING.MIT;md5=3da9cfbcb788c80a0384361b4de20420"

# 源码文件（本地文件）
SRC_URI = "file://vsock-server.c \
           file://vsock-server.init \
          "

S = "${WORKDIR}"

# 依赖关系
DEPENDS = ""
RDEPENDS:${PN} = "initscripts"

# 继承 update-rc.d 类以自动配置启动脚本
inherit update-rc.d

# 启动脚本配置
INITSCRIPT_NAME = "vsock-server"
INITSCRIPT_PARAMS = "start 99 S . stop 01 0 1 6 ."

# ==================== 编译阶段 ====================
do_compile() {
    # 编译 vsock-server
    ${CC} ${CFLAGS} ${LDFLAGS} -o vsock-server vsock-server.c
}

# ==================== 安装阶段 ====================
do_install() {
    # 安装可执行文件
    install -d ${D}${sbindir}
    install -m 0755 vsock-server ${D}${sbindir}/vsock-server
    
    # 安装启动脚本
    install -d ${D}${sysconfdir}/init.d
    install -m 0755 vsock-server.init ${D}${sysconfdir}/init.d/vsock-server
}

# ==================== 文件分配 ====================
FILES:${PN} = "${sbindir}/vsock-server \
               ${sysconfdir}/init.d/vsock-server \
              "

