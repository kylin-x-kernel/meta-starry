# Rust standard library for aarch64-unknown-none-softfloat
# 用于 SDK（非 native，供 SDK 打包）

SUMMARY = "Rust standard library for aarch64-unknown-none-softfloat (SDK target)"
HOMEPAGE = "https://www.rust-lang.org"
LICENSE = "MIT | Apache-2.0"
SECTION = "devel"

LIC_FILES_CHKSUM = " \
    file://LICENSE-MIT;md5=8a95ea02f2dd8c2953432d0ddd7cdd91 \
    file://LICENSE-APACHE;md5=22a53954e4e0ec258dfce4391e905dac \
"

PV = "1.92.0"

# 这个是给 SDK 用的，不是 native
# SDK 会把它打包到工具链里，供开发者在 PC 上交叉编译时使用
SRC_URI = "https://static.rust-lang.org/dist/rust-std-${PV}-aarch64-unknown-none-softfloat.tar.xz"
SRC_URI[sha256sum] = "0dc46fafaaa36f53eec49e14a69e1d6d9ac6f0b9624a01081ad311d8139a2be0"

S = "${WORKDIR}/rust-std-${PV}-aarch64-unknown-none-softfloat"

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

# 不需要 BBCLASSEXTEND，因为这个配方本身就是"target"（被 SDK 打包的）
