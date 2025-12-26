# LoongArch64 external musl toolchain (prebuilts from LoongsonLab, gcc 13.2.0)
#
# Download and unpack to /opt/loongarch64-linux-musl-cross, as described in
# StarryOS/arceos/README.md, then enable this tcmode via local.conf:
#   require conf/distro/include/tcmode-loongarch64-external.inc
#
# This bypasses building gcc/binutils inside Yocto and uses the external toolchain.

TOOLCHAIN_EXTERNAL_PREFIX ?= "loongarch64-linux-musl"
# Only override the target triple; keep native HOST_SYS/BUILD_SYS untouched.
TARGET_PREFIX:class-target = "${TOOLCHAIN_EXTERNAL_PREFIX}-"
TARGET_SYS:class-target = "${TOOLCHAIN_EXTERNAL_PREFIX}"

# external-toolchain-native installs into ${RECIPE_SYSROOT_NATIVE}${prefix}/loongarch64-linux-musl-cross
# Expose its bin/ early in PATH for dependent tasks.
PATH:prepend:class-target = "${STAGING_DIR_NATIVE}${prefix}/loongarch64-linux-musl-cross/bin:"

# Use external toolchain providers for gcc/binutils
PREFERRED_PROVIDER_virtual/${TARGET_PREFIX}gcc:loongarch64 = "loongarch64-musl-external-toolchain"
PREFERRED_PROVIDER_virtual/${TARGET_PREFIX}g++:loongarch64 = "loongarch64-musl-external-toolchain"
PREFERRED_PROVIDER_virtual/${TARGET_PREFIX}binutils:loongarch64 = "loongarch64-musl-external-toolchain"
PREFERRED_PROVIDER_virtual/${TARGET_PREFIX}compilerlibs:loongarch64 = "loongarch64-musl-external-toolchain"
# Avoid building internal libgcc/libssp; use external toolchain artifacts
PREFERRED_PROVIDER_virtual/libgcc:loongarch64 = "loongarch64-musl-external-toolchain"
PREFERRED_PROVIDER_virtual/libssp:loongarch64 = "loongarch64-musl-external-toolchain"
SKIP_RECIPE[libgcc-initial] = "1"
SKIP_RECIPE[libssp-nonshared] = "1"

# Keep musl as libc policy (can be overridden in distro if needed)
TCLIBC ?= "musl"

# Ensure tasks see the external toolchain binaries (prefer target sysroot location)
PATH:prepend:class-target = "${RECIPE_SYSROOT}/usr/loongarch64-linux-musl-cross/bin:"

# musl upstream不认 loongarch64-linux-musl 三元组，强制用裸机 softfloat 目标通过configure
EXTRA_OECONF:append:pn-musl = " --target=loongarch64-unknown-none-softfloat"
