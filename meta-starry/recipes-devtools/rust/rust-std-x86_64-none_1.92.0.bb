# Rust standard library for x86_64-unknown-none (bare-metal)
# Required for ArceOS/StarryOS bare-metal applications on x86_64

SUMMARY = "Rust standard library for x86_64-unknown-none (SDK target)"
HOMEPAGE = "https://www.rust-lang.org"
LICENSE = "MIT | Apache-2.0"
SECTION = "devel"

LIC_FILES_CHKSUM = " \
    file://LICENSE-MIT;md5=8a95ea02f2dd8c2953432d0ddd7cdd91 \
    file://LICENSE-APACHE;md5=22a53954e4e0ec258dfce4391e905dac \
"

PV = "1.92.0"

SRC_URI = "https://static.rust-lang.org/dist/rust-std-${PV}-x86_64-unknown-none.tar.xz"
SRC_URI[sha256sum] = "placeholder_update_after_download"

S = "${WORKDIR}/rust-std-${PV}-x86_64-unknown-none"

INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_SYSROOT_STRIP = "1"
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"
INSANE_SKIP:${PN} = "already-stripped ldflags"

do_install() {
    ./install.sh --prefix="${D}${prefix}" --disable-ldconfig
    
    # 清理冲突文件
    rm -f ${D}${prefix}/lib/rustlib/uninstall.sh
    rm -f ${D}${prefix}/lib/rustlib/install.log
    rm -f ${D}${prefix}/lib/rustlib/rust-installer-version
    rm -f ${D}${prefix}/lib/rustlib/components
    rm -f ${D}${prefix}/lib/rustlib/manifest-rust-std-*
}

BBCLASSEXTEND = "native"
