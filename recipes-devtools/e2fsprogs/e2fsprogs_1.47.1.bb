# e2fsprogs 1.47.1
# 基于 poky 原版配方，仅改用 tarball 下载和新版本

SUMMARY = "Ext2 Filesystem Utilities"
DESCRIPTION = "The Ext2 Filesystem Utilities (e2fsprogs) contain all of the standard utilities for creating, \
fixing, configuring , and debugging ext2 filesystems."
HOMEPAGE = "http://e2fsprogs.sourceforge.net/"

LICENSE = "GPL-2.0-only & LGPL-2.0-only & BSD-3-Clause & MIT"
LICENSE:e2fsprogs-dumpe2fs = "GPL-2.0-only"
LICENSE:e2fsprogs-e2fsck = "GPL-2.0-only"
LICENSE:e2fsprogs-mke2fs = "GPL-2.0-only"
LICENSE:e2fsprogs-tune2fs = "GPL-2.0-only"
LICENSE:e2fsprogs-badblocks = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://NOTICE;md5=d50be0580c0b0a7fbc7a4830bbe6c12b \
                      file://lib/ext2fs/ext2fs.h;beginline=1;endline=9;md5=596a8dedcb4e731c6b21c7a46fba6bef \
                      file://lib/e2p/e2p.h;beginline=1;endline=7;md5=8a74ade8f9d65095d70ef2d4bf48e36a \
                      file://lib/uuid/uuid.h.in;beginline=1;endline=32;md5=dbb8079e114a5f841934b99e59c8820a \
                      file://lib/uuid/COPYING;md5=58dcd8452651fc8b07d1f65ce07ca8af \
                      file://lib/et/et_name.c;beginline=1;endline=11;md5=ead236447dac7b980dbc5b4804d8c836 \
                      file://lib/ss/ss.h;beginline=1;endline=20;md5=6e89ad47da6e75fecd2b5e0e81e1d4a6"
SECTION = "base"
DEPENDS = "util-linux attr autoconf-archive"

# 使用 tarball 下载
SRC_URI = "https://www.kernel.org/pub/linux/kernel/people/tytso/e2fsprogs/v${PV}/e2fsprogs-${PV}.tar.xz"
SRC_URI[sha256sum] = "5a33dc047fd47284bca4bb10c13cfe7896377ae3d01cb81a05d406025d99e0d1"

S = "${WORKDIR}/e2fsprogs-${PV}"

inherit autotools gettext texinfo pkgconfig multilib_header update-alternatives

EXTRA_OECONF += "--libdir=${base_libdir} --sbindir=${base_sbindir} \
                --enable-elf-shlibs --disable-libuuid --disable-uuidd \
                --disable-libblkid --enable-verbose-makecmds \
                --with-crond-dir=no"

EXTRA_OECONF:darwin = "--libdir=${base_libdir} --sbindir=${base_sbindir} --enable-bsd-shlibs"

PACKAGECONFIG ??= ""
PACKAGECONFIG[fuse] = '--enable-fuse2fs,--disable-fuse2fs,fuse'

# make locale rules sometimes fire, sometimes don't as git doesn't preserve
# file mktime. Touch the files introducing non-determinism to the build
do_compile:prepend (){
	find ${S}/po -type f -name "*.po" -exec touch {} +
}

do_install () {
	oe_runmake 'DESTDIR=${D}' install
	oe_runmake 'DESTDIR=${D}' install-libs
	# We use blkid from util-linux now so remove from here
	rm -f ${D}${base_libdir}/libblkid*
	rm -rf ${D}${includedir}/blkid
	rm -f ${D}${base_libdir}/pkgconfig/blkid.pc
	rm -f ${D}${base_sbindir}/blkid
	rm -f ${D}${base_sbindir}/fsck
	rm -f ${D}${base_sbindir}/findfs

	# e2initrd_helper and the pkgconfig files belong in libdir
	if [ ! ${D}${libdir} -ef ${D}${base_libdir} ]; then
		install -d ${D}${libdir}
		mv ${D}${base_libdir}/e2initrd_helper ${D}${libdir}
		mv ${D}${base_libdir}/pkgconfig ${D}${libdir}
	fi

	oe_multilib_header ext2fs/ext2_types.h
	install -d ${D}${base_bindir}
	mv ${D}${bindir}/chattr ${D}${base_bindir}/chattr.e2fsprogs
	mv ${D}${bindir}/lsattr ${D}${base_bindir}/lsattr.e2fsprogs

	install -v -m 755 ${S}/contrib/populate-extfs.sh ${D}${base_sbindir}/
}

# Need to find the right mke2fs.conf file
e2fsprogs_conf_fixup () {
	for i in mke2fs mkfs.ext2 mkfs.ext3 mkfs.ext4; do
		create_wrapper ${D}${base_sbindir}/$i MKE2FS_CONFIG=${sysconfdir}/mke2fs.conf
	done
}

do_install:append:class-native() {
	e2fsprogs_conf_fixup
}

do_install:append:class-nativesdk() {
	e2fsprogs_conf_fixup
}

do_install:append:class-target() {
	mv ${D}${base_sbindir}/mke2fs ${D}${base_sbindir}/mke2fs.e2fsprogs
	mv ${D}${base_sbindir}/mkfs.ext2 ${D}${base_sbindir}/mkfs.ext2.e2fsprogs
	mv ${D}${base_sbindir}/tune2fs ${D}${base_sbindir}/tune2fs.e2fsprogs
}

RDEPENDS:e2fsprogs = "e2fsprogs-badblocks e2fsprogs-dumpe2fs"
RRECOMMENDS:e2fsprogs = "e2fsprogs-mke2fs e2fsprogs-e2fsck"

PACKAGES =+ "e2fsprogs-badblocks e2fsprogs-dumpe2fs e2fsprogs-e2fsck e2fsprogs-e2scrub e2fsprogs-mke2fs e2fsprogs-resize2fs e2fsprogs-tune2fs"
PACKAGES =+ "libcomerr libss libe2p libext2fs"

FILES:e2fsprogs-dumpe2fs = "${base_sbindir}/dumpe2fs"
FILES:e2fsprogs-resize2fs = "${base_sbindir}/resize2fs*"
FILES:e2fsprogs-e2fsck = "${base_sbindir}/e2fsck ${base_sbindir}/fsck.ext*"
FILES:e2fsprogs-e2scrub = "${base_sbindir}/e2scrub*"
FILES:e2fsprogs-mke2fs = "${base_sbindir}/mke2fs.e2fsprogs ${base_sbindir}/mkfs.ext* ${sysconfdir}/mke2fs.conf"
FILES:e2fsprogs-tune2fs = "${base_sbindir}/tune2fs.e2fsprogs ${base_sbindir}/e2label"
FILES:e2fsprogs-badblocks = "${base_sbindir}/badblocks"
FILES:libcomerr = "${base_libdir}/libcom_err.so.*"
FILES:libss = "${base_libdir}/libss.so.*"
FILES:libe2p = "${base_libdir}/libe2p.so.*"
FILES:libext2fs = "${libdir}/e2initrd_helper ${base_libdir}/libext2fs.so.*"
FILES:${PN}-dev += "${datadir}/*/*.awk ${datadir}/*/*.sed ${base_libdir}/*.so ${bindir}/compile_et ${bindir}/mk_cmds"

ALTERNATIVE:${PN} = "chattr lsattr"
ALTERNATIVE_PRIORITY = "100"
ALTERNATIVE_LINK_NAME[chattr] = "${base_bindir}/chattr"
ALTERNATIVE_TARGET[chattr] = "${base_bindir}/chattr.e2fsprogs"
ALTERNATIVE_LINK_NAME[lsattr] = "${base_bindir}/lsattr"
ALTERNATIVE_TARGET[lsattr] = "${base_bindir}/lsattr.e2fsprogs"

ALTERNATIVE:${PN}-doc = "fsck.8"
ALTERNATIVE_LINK_NAME[fsck.8] = "${mandir}/man8/fsck.8"

ALTERNATIVE:${PN}-mke2fs = "mke2fs mkfs.ext2"
ALTERNATIVE_LINK_NAME[mke2fs] = "${base_sbindir}/mke2fs"
ALTERNATIVE_LINK_NAME[mkfs.ext2] = "${base_sbindir}/mkfs.ext2"

ALTERNATIVE:${PN}-tune2fs = "tune2fs"
ALTERNATIVE_LINK_NAME[tune2fs] = "${base_sbindir}/tune2fs"

RDEPENDS:e2fsprogs-e2scrub = "bash"

BBCLASSEXTEND = "native nativesdk"
