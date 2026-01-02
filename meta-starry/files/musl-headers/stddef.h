/* Minimal stddef.h for lwext4 bare-metal compilation */
/* Based on musl libc (MIT License) */

#ifndef _STDDEF_H
#define _STDDEF_H

#ifndef NULL
#define NULL ((void*)0)
#endif

typedef long ptrdiff_t;
typedef unsigned long size_t;
typedef int wchar_t;

#define offsetof(type, member) __builtin_offsetof(type, member)

#endif /* _STDDEF_H */
