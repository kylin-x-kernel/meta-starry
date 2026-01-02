#
# crate-fetch class
#
# Registers 'crate' method for Bitbake fetch2.
#
# Adds support for following format in recipe SRC_URI:
# crate://<packagename>/<version>
#

python () {
        import sys
        # 使用 meta-starry layer 的路径，而不是依赖未定义的 RUSTLAYER
        layerdir = d.getVar("LAYERDIR_meta-starry")
        if not layerdir:
            # 回退：从当前 bbclass 文件路径推导 layer 目录
            layerdir = os.path.dirname(d.getVar("FILE")).replace("/classes", "")
        sys.path.insert(0, layerdir + "/lib")
        import crate
        bb.fetch2.methods.append( crate.Crate() )

        # If we have local sources (e.g. devtool), we want to be able
        # to fetch crates at do_compile task.
        if d.getVar('EXTERNALSRC'):
            d.setVarFlag('do_compile', 'network', '1')

}
