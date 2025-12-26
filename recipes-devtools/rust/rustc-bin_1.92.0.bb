# Use prebuilt rustc 1.92.0 instead of building from source

SUMMARY = "Prebuilt Rustc, the Rust compiler"
HOMEPAGE = "https://www.rust-lang.org"
LICENSE = "MIT | Apache-2.0"
SECTION = "devel"

LIC_FILES_CHKSUM = " \
    file://LICENSE-MIT;md5=8a95ea02f2dd8c2953432d0ddd7cdd91 \
    file://LICENSE-APACHE;md5=22a53954e4e0ec258dfce4391e905dac \
"

PV = "1.92.0"

SRC_URI = "https://static.rust-lang.org/dist/rustc-${PV}-${BUILD_ARCH}-unknown-linux-gnu.tar.xz"
SRC_URI[sha256sum] = "78b2dd9c6b1fcd2621fa81c611cf5e2d6950690775038b585c64f364422886e0"

S = "${WORKDIR}/rustc-${PV}-${BUILD_ARCH}-unknown-linux-gnu"

# Prevent Yocto from stripping or modifying prebuilt binaries
# This is critical - patchelf/strip can break LLVM's internal structures
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_SYSROOT_STRIP = "1"
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"
INSANE_SKIP:${PN} = "already-stripped"

do_install() {
    ./install.sh --prefix="${D}${prefix}" --disable-ldconfig
}

BBCLASSEXTEND = "native nativesdk"
