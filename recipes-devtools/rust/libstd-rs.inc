SUMMARY = "Rust standard libaries"
HOMEPAGE = "http://www.rust-lang.org"
SECTION = "devel"
LICENSE = "MIT | Apache-2.0"
LIC_FILES_CHKSUM = "file://../../COPYRIGHT;md5=11a3899825f4376896e438c8c753f8dc"

RUSTLIB_DEP = ""
inherit cargo

DEPENDS:append:libc-musl = " libunwind"

# Embed bitcode in order to allow compiling both with and without LTO
RUSTFLAGS += "-Cembed-bitcode=yes"
# Needed so cargo can find libbacktrace
RUSTFLAGS += "-L ${STAGING_LIBDIR} -C link-arg=-Wl,-soname,libstd.so"

# Since Rust 1.85.0, HashMap::with_hasher is const-stable (#[rustc_const_stable]),
# which requires hashbrown's with_hasher to have #[rustc_const_stable_indirect].
# When building std with cargo (not bootstrap), we need this flag to make the
# compiler recognize the indirect const stability attribute properly.
# This is normally set by bootstrap via RUSTC_FORCE_UNSTABLE environment variable.
RUSTFLAGS += "-Zforce-unstable-if-unmarked"

S = "${RUSTSRC}/src/libstd"

CARGO_FEATURES ?= "panic-unwind backtrace"
CARGO_BUILD_FLAGS += "--features '${CARGO_FEATURES}'"
CARGO_VENDORING_DIRECTORY = "${RUSTSRC}/vendor"

do_compile:prepend () {
    export CARGO_TARGET_DIR="${B}"
    # For Rust 1.13.0 and newer
    export RUSTC_BOOTSTRAP="1"
}

do_install () {
    mkdir -p ${D}${rustlibdir}

    # With the incremental build support added in 1.24, the libstd deps directory also includes dependency
    # files that get installed. Those are really only needed to incrementally rebuild the libstd library
    # itself and don't need to be installed.
    rm -f ${B}/${TARGET_SYS}/${BUILD_DIR}/deps/*.d
    cp ${B}/${TARGET_SYS}/${BUILD_DIR}/deps/* ${D}${rustlibdir}
}
