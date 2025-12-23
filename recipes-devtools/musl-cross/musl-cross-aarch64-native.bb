
DESCRIPTION = "Prebuilt aarch64-linux-musl cross toolchain from musl.cc (for lwext4_rust build scripts)"
HOMEPAGE = "https://musl.cc/"
LICENSE = "CLOSED"

SRC_URI = "https://musl.cc/aarch64-linux-musl-cross.tgz;md5sum=a6bb806af217a91cf575e15163e8b12b;sha256sum=c909817856d6ceda86aa510894fa3527eac7989f0ef6e87b5721c58737a06c38"

S = "${WORKDIR}/aarch64-linux-musl-cross"

inherit native

SYSROOT_DIRS_NATIVE:append = " ${prefix}/aarch64-linux-musl-cross"

# This toolchain contains a mix of host binaries and target objects/libraries.
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_SYSROOT_STRIP = "1"

do_install() {
    install -d "${D}${prefix}"
    cp -a "${S}" "${D}${prefix}/"

    # Make toolchain binaries available in PATH for build scripts.
    install -d "${D}${bindir}"
    for tool in "${D}${prefix}/aarch64-linux-musl-cross/bin/"aarch64-linux-musl-*; do
        [ -f "$tool" ] || continue
        # Use a relative symlink to keep sysroot relocatable.
        ln -sf "../aarch64-linux-musl-cross/bin/$(basename "$tool")" "${D}${bindir}/$(basename "$tool")"
    done
}
