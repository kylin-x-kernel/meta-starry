require recipes-devtools/rust/rust-source_${PV}.inc
require recipes-devtools/rust/rust-snapshot_${PV}.inc

FILESEXTRAPATHS:prepend := "${THISDIR}/cargo-${PV}:"

require cargo-cross-canadian.inc
