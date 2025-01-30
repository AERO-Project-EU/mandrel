/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
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
package com.oracle.truffle.espresso.substitutions;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.quick.invoke.inline.InlinedFrameAccess;
import com.oracle.truffle.espresso.nodes.quick.invoke.inline.InlinedMethodPredicate;

public abstract class JavaSubstitution extends SubstitutionProfiler {

    public static final class Factory {
        private final Object methodName;
        private final Object substitutionClassName;
        private final String returnType;
        private final String[] parameterTypes;
        private final boolean hasReceiver;

        private final LanguageFilter filter;
        private final byte flags;
        private final InlinedMethodPredicate guard;

        private final Constructor<? extends JavaSubstitution> constructor;

        public Factory(Object methodName,
                        Object substitutionClassName,
                        String returnType,
                        String[] parameterTypes,
                        boolean hasReceiver,
                        LanguageFilter filter,
                        byte flags,
                        InlinedMethodPredicate guard,
                        Constructor<? extends JavaSubstitution> constructor) {
            this.methodName = methodName;
            this.substitutionClassName = substitutionClassName;
            this.returnType = returnType;
            this.parameterTypes = parameterTypes;
            this.hasReceiver = hasReceiver;
            this.filter = filter;
            this.flags = flags;
            this.guard = guard;
            this.constructor = constructor;
        }

        public String[] getMethodNames() {
            return decodeNames(methodName);
        }

        public String[] substitutionClassNames() {
            return decodeNames(substitutionClassName);
        }

        public String returnType() {
            return returnType;
        }

        public String[] parameterTypes() {
            return parameterTypes;
        }

        public boolean hasReceiver() {
            return hasReceiver;
        }

        public boolean isValidFor(EspressoLanguage language) {
            return filter.isValidFor(language);
        }

        public boolean isTrivial() {
            return isFlag(SubstitutionFlag.IsTrivial);
        }

        public boolean inlineInBytecode() {
            return isTrivial() || isFlag(SubstitutionFlag.InlineInBytecode);
        }

        public InlinedMethodPredicate guard() {
            return guard;
        }

        public JavaSubstitution create() {
            try {
                return constructor.newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Failed substitution creation: ", e);
            }
        }

        private boolean isFlag(byte flag) {
            return (flags & flag) != 0;
        }

        private String[] decodeNames(Object encodedNames) {
            if (encodedNames instanceof String singleName) {
                return new String[]{singleName};
            } else if (encodedNames instanceof String[] names) {
                return names;
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("Unrecognized encoded names for substitution. " + encodedNames);
        }
    }

    private final byte flags;

    public JavaSubstitution(Factory factory) {
        this.flags = factory.flags;
    }

    public static Constructor<? extends JavaSubstitution> lookupConstructor(Class<? extends JavaSubstitution> cls) {
        try {
            return cls.getConstructor();
        } catch (NoSuchMethodException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("Failed to locate constructor for " + cls, e);
        }
    }

    public abstract Object invoke(Object[] args);

    @SuppressWarnings("unused")
    public void invokeInlined(VirtualFrame frame, int top, InlinedFrameAccess frameAccess) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw EspressoError.shouldNotReachHere("invokeInlined should not be reachable for non-inlinable substitutions.");
    }

    @Override
    public boolean canSplit() {
        return true;
    }

    // Generated in substitutions' classes
    @Override
    public abstract JavaSubstitution split();

    @Override
    public final boolean isTrivial() {
        return isFlag(SubstitutionFlag.IsTrivial);
    }

    private boolean isFlag(byte flag) {
        return (flags & flag) != 0;
    }
}
