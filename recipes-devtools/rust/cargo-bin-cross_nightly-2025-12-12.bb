
# Recipe for cargo nightly-2025-12-12
# Toolchain pinned to StarryOS/rust-toolchain.toml

def get_by_triple(hashes, triple):
    try:
        return hashes[triple]
    except:
        raise bb.parse.SkipRecipe("Unsupported triple: %s" % triple)

def cargo_md5(triple):
    HASHES = {
        "aarch64-unknown-linux-gnu": "ee9b0a43b8ebadd077435fb9c8dbb732",
        "arm-unknown-linux-gnueabi": "cb7100bf3b490c810d78202cd4d8e740",
        "arm-unknown-linux-gnueabihf": "9ce83cdc95ea5c7ed62d6c14549cfe2a",
        "armv7-unknown-linux-gnueabihf": "de125d054360772f491b3376d34daf1a",
        "i686-unknown-linux-gnu": "5cbeccee0a0aa5c0bed2b3728fa06c31",
        "x86_64-unknown-linux-gnu": "3b133569a712ca6b6aa329e99f3deba6",
    }
    return get_by_triple(HASHES, triple)

def cargo_sha256(triple):
    HASHES = {
        "aarch64-unknown-linux-gnu": "222b0e4500577374818d1c6190ee5907fd4f794c4fcc59fcb8692ce47c9c1d1c",
        "arm-unknown-linux-gnueabi": "2848cef5eec3d2ce156138fbcd3740714e25ff5915d4bc8be13de33c6d4ff2f4",
        "arm-unknown-linux-gnueabihf": "41264b5bd0a8c4aa99630cb47c6f86bbb8fffb9990d0553eb3bdc6b165b5107c",
        "armv7-unknown-linux-gnueabihf": "52d6b19cb2e99ccb7633b9d7a6e9aa837664e42dd0b8f64f8ef4a1ab8460fa83",
        "i686-unknown-linux-gnu": "99c333e5460bca7152daf8644e347aeaa5ecf04fed455d139b22a0e323382515",
        "x86_64-unknown-linux-gnu": "e08b63d8167a3afd163c4fc1298b2dff37f9fcc2befce36e277e84f24cc9717f",
    }
    return get_by_triple(HASHES, triple)

def cargo_url(triple):
    URLS = {
        "aarch64-unknown-linux-gnu": "https://static.rust-lang.org/dist/2025-12-12/cargo-nightly-aarch64-unknown-linux-gnu.tar.gz",
        "arm-unknown-linux-gnueabi": "https://static.rust-lang.org/dist/2025-12-12/cargo-nightly-arm-unknown-linux-gnueabi.tar.gz",
        "arm-unknown-linux-gnueabihf": "https://static.rust-lang.org/dist/2025-12-12/cargo-nightly-arm-unknown-linux-gnueabihf.tar.gz",
        "armv7-unknown-linux-gnueabihf": "https://static.rust-lang.org/dist/2025-12-12/cargo-nightly-armv7-unknown-linux-gnueabihf.tar.gz",
        "i686-unknown-linux-gnu": "https://static.rust-lang.org/dist/2025-12-12/cargo-nightly-i686-unknown-linux-gnu.tar.gz",
        "x86_64-unknown-linux-gnu": "https://static.rust-lang.org/dist/2025-12-12/cargo-nightly-x86_64-unknown-linux-gnu.tar.gz",
    }
    return get_by_triple(URLS, triple)

DEPENDS += "rust-bin-cross-${TARGET_ARCH} (= nightly-2025-12-12)"

LIC_FILES_CHKSUM = "\
    file://LICENSE-APACHE;md5=71b224ca933f0676e26d5c2e2271331c \
    file://LICENSE-MIT;md5=b377b220f43d747efdec40d69fcaa69d \
"

require recipes-devtools/rust/cargo-bin-cross.inc

