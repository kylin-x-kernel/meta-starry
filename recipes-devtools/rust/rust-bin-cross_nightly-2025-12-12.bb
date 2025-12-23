
# Recipe for rust nightly-2025-12-12
# Toolchain pinned to StarryOS/rust-toolchain.toml

def get_by_triple(hashes, triple):
    try:
        return hashes[triple]
    except:
        raise bb.parse.SkipRecipe("Unsupported triple: %s" % triple)

def rust_std_md5(triple):
    HASHES = {
        "x86_64-unknown-linux-gnu": "e93067ce013f3569d253895cd98a55c6",
        "aarch64-unknown-linux-gnu": "1e27b74460cbbe94c3449e4f50fd24aa",
        "aarch64-unknown-none-softfloat": "f977d288f24d52215125e5aba7784329",
    }
    return get_by_triple(HASHES, triple)

def rust_std_sha256(triple):
    HASHES = {
        "x86_64-unknown-linux-gnu": "8ba4c20db4908814b7e52ad752c003ae44bb2f49c8865fb6b8a3ce862ee10064",
        "aarch64-unknown-linux-gnu": "82b391ffb51b0c7ae5959e3d28fe34cc2350deff49cdea43ba01f88c1b258eb3",
        "aarch64-unknown-none-softfloat": "c59cc7982a674760322154663decfd365bf5c2c9b68138c8c83133cfb0fcf14a",
    }
    return get_by_triple(HASHES, triple)

def rustc_md5(triple):
    HASHES = {
        "x86_64-unknown-linux-gnu": "a41aa6ce5b461a9bea5ed7e4de7bd255",
    }
    return get_by_triple(HASHES, triple)

def rustc_sha256(triple):
    HASHES = {
        "x86_64-unknown-linux-gnu": "289f44a4ce83ba4af5a4c7c890b7874e23a7b42d4f267f49fae92ddae3baf36a",
    }
    return get_by_triple(HASHES, triple)

# StarryOS uses a bare-metal target for QEMU aarch64.
EXTRA_RUST_TARGETS:append:aarch64 = " aarch64-unknown-none-softfloat"

LIC_FILES_CHKSUM = "\
    file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10 \
    file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302 \
"

require recipes-devtools/rust/rust-bin-cross.inc

