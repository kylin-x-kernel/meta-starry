# Rust standard library for native (x86_64) builds
# Required for cargo to compile build scripts (build.rs)

SUMMARY = "Rust standard library for x86_64-unknown-linux-gnu"
HOMEPAGE = "https://www.rust-lang.org"
LICENSE = "MIT | Apache-2.0"
SECTION = "devel"

LIC_FILES_CHKSUM = " \
    file://LICENSE-MIT;md5=8a95ea02f2dd8c2953432d0ddd7cdd91 \
    file://LICENSE-APACHE;md5=22a53954e4e0ec258dfce4391e905dac \
"

PV = "1.92.0"

SRC_URI = "https://static.rust-lang.org/dist/rust-std-${PV}-${BUILD_ARCH}-unknown-linux-gnu.tar.xz"
SRC_URI[sha256sum] = "5f106805ed86ebf8df287039e53a45cf974391ef4d088c2760776b05b8e48b5d"

S = "${WORKDIR}/rust-std-${PV}-${BUILD_ARCH}-unknown-linux-gnu"

# Prevent Yocto from stripping or modifying prebuilt binaries
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_SYSROOT_STRIP = "1"
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"
INSANE_SKIP:${PN} = "already-stripped ldflags"

# This is a native-only package
inherit native

# Must be installed after rustc-bin-native
DEPENDS = "rustc-bin-native"

do_install() {
    # Install to the same location as rustc-bin
    ./install.sh --prefix="${D}${prefix}" --disable-ldconfig
    
    # Remove files that conflict with rustc-bin-native
    rm -f ${D}${prefix}/lib/rustlib/uninstall.sh
    rm -f ${D}${prefix}/lib/rustlib/install.log
    rm -f ${D}${prefix}/lib/rustlib/rust-installer-version
    rm -f ${D}${prefix}/lib/rustlib/components
    rm -f ${D}${prefix}/lib/rustlib/manifest-rust-std-*
}

BBCLASSEXTEND = ""
