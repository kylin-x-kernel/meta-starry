# LoongArch Architecture definition

DEFAULTTUNE ?= "loongarch64"
TUNE_ARCH = "${TUNE_ARCH:tune-${DEFAULTTUNE}}"
TUNE_PKGARCH = "${TUNE_PKGARCH:tune-${DEFAULTTUNE}}"
# 根据 TUNE_FEATURES 里的 ABI 标记追加编译器参数
TUNE_CCARGS:append = "${@bb.utils.contains('TUNE_FEATURES', 'loongarch64nf', ' -mabi=lp64', '', d)}"

# Fix: ld: unrecognized option '--hash-style=sysv'
LINKER_HASH_STYLE:libc-newlib = ""
# Fix: ld: unrecognized option '--hash-style=gnu'
LINKER_HASH_STYLE:libc-baremetal = ""

# Provide siteinfo entries for loongarch targets (endianness/bits)
SITEINFO_EXTRA_DATAFUNCS += "loongarch_siteinfo"
def loongarch_siteinfo(archinfo, osinfo, targetinfo, d):
    archinfo["loongarch64"] = "endian-little bit-64 loongarch-common"
    targetinfo["loongarch64-linux"] = "bit-64"
    targetinfo["loongarch64-linux-gnu"] = "bit-64"
    targetinfo["loongarch64-linux-musl"] = "bit-64"
    return archinfo, osinfo, targetinfo


