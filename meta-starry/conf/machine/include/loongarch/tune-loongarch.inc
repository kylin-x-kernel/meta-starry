require conf/machine/include/loongarch/arch-loongarch.inc

TUNEVALID[loongarch64] = "Enable 64-bit LoongArch optimizations"

TUNEVALID[loongarch64nf] = "Enable 64-bit LoongArch optimizations no floating point"

TUNEVALID[bigendian] = "Big endian mode"

AVAILTUNES += "loongarch64 loongarch64nf"

# Default

TUNE_FEATURES:tune-loongarch64 = "loongarch64"
TUNE_ARCH:tune-loongarch64 = "loongarch64"
TUNE_PKGARCH:tune-loongarch64 = "loongarch64"
PACKAGE_EXTRA_ARCHS:tune-loongarch64 = "loongarch64"

# No float
TUNE_FEATURES:tune-loongarch64nf = "${TUNE_FEATURES:tune-loongarch64nf} loongarch64nf"
TUNE_ARCH:tune-loongarch64nf = "loongarch64nf"
TUNE_PKGARCH:tune-loongarch64nf = "loongarch64nf"
PACKAGE_EXTRA_ARCHS:tune-loongarch64nf = "loongarch64nf"



