/* Minimal stdio.h for lwext4 bare-metal compilation */
/* Compatible with lwext4's ulibc.c which provides its own stdout/stderr */

#ifndef _STDIO_H
#define _STDIO_H

#include <stddef.h>
#include <stdarg.h>

typedef struct _FILE FILE;

/* These are defined by lwext4's ulibc.c as "FILE *const" */
extern FILE *const stdin;
extern FILE *const stdout;
extern FILE *const stderr;

#define EOF (-1)

int printf(const char *format, ...);
int fprintf(FILE *stream, const char *format, ...);
int sprintf(char *str, const char *format, ...);
int snprintf(char *str, size_t size, const char *format, ...);

int vprintf(const char *format, va_list ap);
int vfprintf(FILE *stream, const char *format, va_list ap);
int vsprintf(char *str, const char *format, va_list ap);
int vsnprintf(char *str, size_t size, const char *format, va_list ap);

int puts(const char *s);
int fputs(const char *s, FILE *stream);
int putchar(int c);
int fputc(int c, FILE *stream);
int fflush(FILE *stream);

#endif /* _STDIO_H */
