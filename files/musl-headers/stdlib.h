/* Minimal stdlib.h for lwext4 bare-metal compilation */
/* Compatible with lwext4's ulibc.c */

#ifndef _STDLIB_H
#define _STDLIB_H

#include <stddef.h>

void *malloc(size_t size);
void *calloc(size_t nmemb, size_t size);
void *realloc(void *ptr, size_t size);
void free(void *ptr);

void abort(void);
void exit(int status);

int atoi(const char *nptr);
long atol(const char *nptr);
long long atoll(const char *nptr);
long strtol(const char *nptr, char **endptr, int base);
unsigned long strtoul(const char *nptr, char **endptr, int base);
long long strtoll(const char *nptr, char **endptr, int base);
unsigned long long strtoull(const char *nptr, char **endptr, int base);

int abs(int j);
long labs(long j);

/* qsort comparison function types - compatible with lwext4's ulibc */
typedef int (*__compar_fn_t)(const void *, const void *);
typedef int (*__compar_d_fn_t)(const void *, const void *, void *);

void qsort(void *base, size_t nmemb, size_t size, __compar_fn_t compar);
void qsort_r(void *base, size_t nmemb, size_t size, __compar_d_fn_t compar, void *arg);

void *bsearch(const void *key, const void *base, size_t nmemb, size_t size,
              int (*compar)(const void *, const void *));

#endif /* _STDLIB_H */
