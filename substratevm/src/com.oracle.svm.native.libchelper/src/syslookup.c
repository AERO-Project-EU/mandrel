/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#ifdef _WIN64

#include <stdio.h>
#include <stdint.h>
#include <time.h>

// Forces generation of code on Windows for functions that are not available as regular symbols
static void* _syslookup_funcs[] = {
    // stdio.h
    &fprintf,
    &fprintf_s,
    &fscanf,
    &fscanf_s,
    &fwprintf,
    &fwprintf_s,
    &fwscanf,
    &fwscanf_s,
    &printf,
    &printf_s,
    &scanf,
    &scanf_s,
    &snprintf,
    &sprintf,
    &sprintf_s,
    &sscanf,
    &sscanf_s,
    &swprintf,
    &swprintf_s,
    &swscanf,
    &swscanf_s,
    &vfprintf,
    &vfprintf_s,
    &vfscanf,
    &vfscanf_s,
    &vfwprintf,
    &vfwprintf_s,
    &vfwscanf,
    &vfwscanf_s,
    &vprintf,
    &vprintf_s,
    &vscanf,
    &vscanf_s,
    &vsnprintf,
    &vsnprintf_s,
    &vsprintf,
    &vsprintf_s,
    &vsscanf,
    &vsscanf_s,
    &vswprintf,
    &vswprintf_s,
    &vswscanf,
    &vswscanf_s,
    &vwprintf,
    &vwprintf_s,
    &vwscanf,
    &vwscanf_s,
    &wprintf,
    &wprintf_s,
    &wscanf,
    &wscanf_s,

    // time.h
    &gmtime
};

#define N_SYSLOOKUP_FUNCS (sizeof(_syslookup_funcs) / sizeof(void*))

void* __svm_get_syslookup_func(int32_t i, int32_t n_expected) {
    if (n_expected != N_SYSLOOKUP_FUNCS || i < 0 || i >= N_SYSLOOKUP_FUNCS) {
        return NULL;
    }
    return _syslookup_funcs[i];
}

#endif
