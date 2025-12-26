# Rust standard library for aarch64-unknown-none-softfloat (bare-metal)
# Required for ArceOS/StarryOS bare-metal applications

SUMMARY = "Rust standard library for aarch64-unknown-none-softfloat"
HOMEPAGE = "https://www.rust-lang.org"
LICENSE = "MIT | Apache-2.0"
SECTION = "devel"

LIC_FILES_CHKSUM = " \
    file://LICENSE-MIT;md5=8a95ea02f2dd8c2953432d0ddd7cdd91 \
    file://LICENSE-APACHE;md5=22a53954e4e0ec258dfce4391e905dac \
"

PV = "1.92.0"

SRC_URI = "https://static.rust-lang.org/dist/rust-std-${PV}-aarch64-unknown-none-softfloat.tar.xz"
SRC_URI[sha256sum] = "0dc46fafaaa36f53eec49e14a69e1d6d9ac6f0b9624a01081ad311d8139a2be0"

S = "${WORKDIR}/rust-std-${PV}-aarch64-unknown-none-softfloat"

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
    
    # Remove files that conflict with rustc-bin-native and rust-std-native
    rm -f ${D}${prefix}/lib/rustlib/uninstall.sh
    rm -f ${D}${prefix}/lib/rustlib/install.log
    rm -f ${D}${prefix}/lib/rustlib/rust-installer-version
    rm -f ${D}${prefix}/lib/rustlib/components
    rm -f ${D}${prefix}/lib/rustlib/manifest-rust-std-*
}

BBCLASSEXTEND = ""
