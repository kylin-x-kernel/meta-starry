# Bare-metal / no-libc configuration
#
# Provides a minimal libc policy so that TCLIBC="none" can be selected without
# pulling any userspace C library recipes.

LIBCEXTENSION = ""
LIBCOVERRIDE = ":libc-none"

PREFERRED_PROVIDER_virtual/libc ?= ""
PREFERRED_PROVIDER_virtual/libiconv ?= ""
PREFERRED_PROVIDER_virtual/libintl ?= ""
PREFERRED_PROVIDER_virtual/crypt ?= ""

# Skip common libc implementations entirely
SKIP_RECIPE[musl] = "1"
SKIP_RECIPE[glibc] = "1"
SKIP_RECIPE[newlib] = "1"

# Nothing to stage into images
LIBC_DEPENDENCIES = ""
