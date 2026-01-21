SUMMARY = "System libclang wrapper for bindgen"
DESCRIPTION = "Provides libclang.so from system installation"

LICENSE = "CLOSED"

inherit native

do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -d ${D}${libdir}
    
    # 复制系统 libclang（已确认在 /usr/lib/x86_64-linux-gnu）
    if [ -d /usr/lib/x86_64-linux-gnu ]; then
        cp -P /usr/lib/x86_64-linux-gnu/libclang*.so* ${D}${libdir}/ 2>/dev/null || true
    fi
    
    # 也尝试其他常见路径
    if [ -d /usr/lib ]; then
        cp -P /usr/lib/libclang*.so* ${D}${libdir}/ 2>/dev/null || true
    fi
    
    # 检查是否成功复制
    if ! ls ${D}${libdir}/libclang*.so* 1>/dev/null 2>&1; then
        bbfatal "No libclang found! Install: apt-get install -y libclang-dev"
    fi
    
    bbnote "Installed libclang to ${D}${libdir}"
}

ALLOW_EMPTY:${PN} = "1"
INSANE_SKIP:${PN} += "already-stripped ldflags file-rdeps"
