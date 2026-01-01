Use lower-cased characters and do not include the reserved suffixes -native, -cross, -initial, or -dev casually (i.e. do not use them as part of your recipe name unless the string applies). 
The path to the per-recipe temporary work directory depends on the context in which it is being built. The quickest way to find this path is to use the bitbake-getvar utility:
bitbake-getvar -r basename WORKDIR

5.7 Patching Code
Sometimes it is necessary to patch code after it has been fetched. Any files mentioned in SRC_URI whose names end in .patch or .diff or compressed versions of these suffixes (e.g. diff.gz, patch.bz2, etc.) are treated as patches. The do_patch task automatically applies these patches.

The build system should be able to apply patches with the “-p1” option (i.e. one directory level in the path will be stripped off). If your patch needs to have more directory levels stripped off, specify the number of levels using the “striplevel” option in the SRC_URI entry for the patch. Alternatively, if your patch needs to be applied in a specific subdirectory that is not specified in the patch file, use the “patchdir” option in the entry.

As with all local files referenced in SRC_URI using file://, you should place patch files in a directory next to the recipe either named the same as the base name of the recipe (BP and BPN) or “files”.