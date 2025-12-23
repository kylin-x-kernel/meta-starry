#
# StarryOS needs the Rust core/std artifacts for the bare-metal target
# `aarch64-unknown-none-softfloat`.
#
# meta-rust-bin's upstream `rust-bin-cross_1.92.0.bb` doesn't include the
# precompiled std for this triple. We add the missing tarball explicitly so it
# gets unpacked into `WORKDIR/rust-std/` and installed alongside other targets.
#

SRC_URI:append:pn-rust-bin-cross-aarch64 = " \
    https://static.rust-lang.org/dist/rust-std-1.92.0-aarch64-unknown-none-softfloat.tar.gz;md5sum=fa3a9bf4dddab2944a0703ea1a2cd66e;sha256sum=911108d6f01ff1040ea3de993bcf3ea52fe19aee099516a607e9967ce4813bcb;subdir=rust-std \
"

def rust_std_md5(triple):
    HASHES = {
        "aarch64-unknown-linux-gnu": "cae9215a174f8c6082ef58a127eaaec4",
        "aarch64-unknown-linux-musl": "359b87444e9b2028303452e23da614ee",
        "aarch64-unknown-none-softfloat": "fa3a9bf4dddab2944a0703ea1a2cd66e",
        "arm-unknown-linux-gnueabi": "48731d16a3cd21a305bea25ca8a5a53b",
        "arm-unknown-linux-gnueabihf": "3218567e45383f599da88ffb031a699a",
        "armv5te-unknown-linux-gnueabi": "e9f7ab8adcf801f5ba25565a05583035",
        "armv5te-unknown-linux-musleabi": "e323b233dc7a283180898afbbe7f30da",
        "armv7-unknown-linux-gnueabihf": "9090b42d0288ee3ccf641570d67de691",
        "armv7-unknown-linux-musleabihf": "49d445e2fffb509587637c2b1e790e8a",
        "i686-unknown-linux-gnu": "69b5428986ef5de00ac2f90e57501a47",
        "powerpc-unknown-linux-gnu": "743945a11164f145e0b915e128082aa7",
        "x86_64-unknown-linux-gnu": "a97f19d70ec564f13c9a8ee0f28ca2d2",
        "riscv64gc-unknown-linux-gnu": "f1fde202ab58b411a9ebb1c942bb2d81",
        "thumbv7neon-unknown-linux-gnueabihf": "81b1ace53c67f65b52d7333988984266",
        "wasm32-unknown-unknown": "27130d3ac3d4d268e2523745cd96880a",
    }
    return get_by_triple(HASHES, triple)


def rust_std_sha256(triple):
    HASHES = {
        "aarch64-unknown-linux-gnu": "04a5c9883bce2c4e89d22dd68af1bdc29b741bb456c01f8839b759a163e8e9d4",
        "aarch64-unknown-linux-musl": "715fbcfd8712c723947a020d0371c8a1a21f7531f2b696aeaed50ac23ba675c9",
        "aarch64-unknown-none-softfloat": "911108d6f01ff1040ea3de993bcf3ea52fe19aee099516a607e9967ce4813bcb",
        "arm-unknown-linux-gnueabi": "56d8b0df4b716a69aeefc06fbd859461938d54bcac37d7eea25b07764f6e3c5c",
        "arm-unknown-linux-gnueabihf": "d0a355524cf219351f15d8984c102f6425bc2c6c1e14af9282e2db60a997a5e0",
        "armv5te-unknown-linux-gnueabi": "547a6282ed5896dbac1df388a116301cfedf41fc579fa25caa57b3ee7e333e19",
        "armv5te-unknown-linux-musleabi": "578a0eee2513520e4195e09c0657b074cc9f7bff215507ddd905cfe7b15bab77",
        "armv7-unknown-linux-gnueabihf": "2c44994e0982bdb60c132c40b481b3c6ca83a131f09d568831553e3e29384130",
        "armv7-unknown-linux-musleabihf": "fc5c4ca757599caab8e93000becb9d57587088d32dab5c4f3b253f00ec3a2fd6",
        "i686-unknown-linux-gnu": "f568a3c307fad16528bf5accecfdce9e77b6ebda3302ae7a79588adc10d2bd29",
        "powerpc-unknown-linux-gnu": "28320c60a2fb42756d7a825f44d35c12100492c44770b0658056e4c468974f86",
        "x86_64-unknown-linux-gnu": "ba4e0b4a60c082e0b1cc6284a38bb144844c92f1aab09732cd1183658e08a6e7",
        "riscv64gc-unknown-linux-gnu": "b15965fec2297deff49412cc0e22a005db5ff710ca77187058c4907f1e7dd467",
        "thumbv7neon-unknown-linux-gnueabihf": "c9de934e22d796a4f542783d60b43b62a213a15014bbea02ca9f9520abfec872",
        "wasm32-unknown-unknown": "4763b575ecceab7637557527a91af6c5c36816a68e3f2de1e18518dd15a63bcd",
    }
    return get_by_triple(HASHES, triple)
