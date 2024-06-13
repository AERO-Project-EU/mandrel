/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.graalvm.continuations;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

abstract class FrameRecordSerializer {
    final ObjectOutputStream out;
    final ObjectInputStream in;

    protected FrameRecordSerializer(ObjectOutputStream out) {
        this.out = out;
        this.in = null;
    }

    protected FrameRecordSerializer(ObjectInputStream in) {
        this.out = null;
        this.in = in;
    }

    public static FrameRecordSerializer forOut(int version, ObjectOutputStream out) throws FormatVersionException {
        if (version == FrameRecordSerializerV2.FORMAT_VERSION) {
            return new FrameRecordSerializerV2(out);
        }
        throw new FormatVersionException(version, ContinuationImpl.FORMAT_VERSION);
    }

    public static FrameRecordSerializer forIn(int version, ObjectInputStream in) throws FormatVersionException {
        if (version == FrameRecordSerializerV2.FORMAT_VERSION) {
            return new FrameRecordSerializerV2(in);
        }
        throw new FormatVersionException(version, ContinuationImpl.FORMAT_VERSION);
    }

    public abstract void writeRecord(ContinuationImpl.FrameRecord frameRecord) throws IOException;

    public abstract ContinuationImpl.FrameRecord readRecord() throws IOException, ClassNotFoundException;
}

final class FrameRecordSerializerV2 extends FrameRecordSerializer {
    static final int FORMAT_VERSION = 2;

    private static final int POOL_IDX_BITS = 16;
    private static final int NEW_POOL_MASK = 1 << (POOL_IDX_BITS - 1);
    private static final int POOL_IDX_MASK = NEW_POOL_MASK - 1;
    private static final int MAX_POOL_IDX = NEW_POOL_MASK - 1;

    final Map<String, Integer> stringPoolWrite = new HashMap<>();

    final List<String> stringPoolRead = new ArrayList<>();

    FrameRecordSerializerV2(ObjectOutputStream out) {
        super(out);
    }

    FrameRecordSerializerV2(ObjectInputStream in) {
        super(in);
    }

    @Override
    public void writeRecord(ContinuationImpl.FrameRecord frameRecord) throws IOException {
        assert out != null;
        /*
         * We serialize frame-at-a-time. Prims go first, then object pointers. Conceptually there
         * aren't two arrays, just one array of untyped slots, but we don't currently know the real
         * types of the slots, so have to serialize both arrays even though they'll contain quite a
         * few nulls. There are more efficient encodings available.
         */
        ContinuationImpl.FrameRecord cursor = frameRecord;
        assert cursor != null;
        while (cursor != null) {
            writeFrame(cursor);
            out.writeBoolean(cursor.next != null);
            cursor = cursor.next;
        }
    }

    @Override
    public ContinuationImpl.FrameRecord readRecord() throws IOException, ClassNotFoundException {
        assert in != null;
        try {
            ContinuationImpl.FrameRecord result = null;
            ContinuationImpl.FrameRecord last = null;
            do {
                /*
                 * We read the context classloader here because we need the classloader that holds
                 * the user's app. If we use Class.forName() in this code we get the platform
                 * classloader because this class is provided by the VM, and thus can't look up
                 * methods of user classes. If we use the classloader of the entrypoint it breaks
                 * for Generator and any other classes we might want to ship with the VM that use
                 * this API. So we need the user's app class loader. We could walk the stack to find
                 * it just like ObjectInputStream does, but we go with the context classloader here
                 * to make it easier for the user to control.
                 */
                ContinuationImpl.FrameRecord frame = readFrame(Thread.currentThread().getContextClassLoader());
                if (last == null) {
                    result = frame;
                } else {
                    last.next = frame;
                }
                last = frame;
            } while (in.readBoolean());
            return result;
        } catch (NoSuchMethodException e) {
            throw new IOException(e);
        }
    }

    private void writeFrame(ContinuationImpl.FrameRecord cursor) throws IOException {
        assert out != null;
        Method method = cursor.method;
        out.writeObject(cursor.pointers);
        out.writeObject(cursor.primitives);
        writeMethodNameAndTypes(method, cursor.pointers.length > 1 ? cursor.pointers[1] : null);
        out.writeInt(cursor.bci);
    }

    private void writeMethodNameAndTypes(Method method, Object receiver) throws IOException {
        assert out != null;
        if (receiver != null && method.getDeclaringClass() == receiver.getClass()) {
            /*
             * Some classes such as Lambda classes can't be looked up by name. This is a JVM
             * optimization designed to avoid contention on the global dictionary lock, but it means
             * we need another way to get the class for the method. Fortunately, lambdas always have
             * an instance, so we can read it out of the first pointer slot.
             */
            out.writeBoolean(true);
        } else {
            out.writeBoolean(false);
            if (method.getDeclaringClass().isHidden()) {
                throw new IOException("Can't serialize continuation with static frames from methods of hidden classes: %s.%s".formatted(method.getDeclaringClass().getName(), method.getName()));
            }
            writeString(method.getDeclaringClass().getName());
        }
        writeString(method.getName());
        writeClass(method.getReturnType());
        Class<?>[] paramTypes = method.getParameterTypes();
        out.writeByte(paramTypes.length);
        for (Class<?> p : paramTypes) {
            writeClass(p);
        }
    }

    private void writeClass(Class<?> clazz) throws IOException {
        assert out != null;
        if (clazz.isPrimitive()) {
            if (clazz == int.class) {
                out.writeByte('I');
            } else if (clazz == boolean.class) {
                out.writeByte('Z');
            } else if (clazz == double.class) {
                out.writeByte('D');
            } else if (clazz == float.class) {
                out.writeByte('F');
            } else if (clazz == long.class) {
                out.writeByte('J');
            } else if (clazz == byte.class) {
                out.writeByte('B');
            } else if (clazz == char.class) {
                out.writeByte('C');
            } else if (clazz == short.class) {
                out.writeByte('S');
            } else if (clazz == void.class) {
                out.writeByte('V');
            } else {
                throw new RuntimeException("Should not reach here: " + clazz);
            }
        } else {
            out.writeByte('L');
            writeString(clazz.getName());
        }
    }

    private void writeString(String str) throws IOException {
        assert out != null;
        Integer idx = stringPoolWrite.get(str);
        if (idx == null) {
            idx = stringPoolWrite.size();
            if (idx == NEW_POOL_MASK) {
                // pick an existing entry and replace it
                Map.Entry<String, Integer> toReplace = stringPoolWrite.entrySet().iterator().next();
                idx = toReplace.getValue();
                stringPoolWrite.remove(toReplace.getKey());
            }
            stringPoolWrite.put(str, idx);
            assert idx <= MAX_POOL_IDX;
            out.writeChar(idx | NEW_POOL_MASK);
            out.writeUTF(str);
        } else {
            assert idx <= MAX_POOL_IDX;
            out.writeChar(idx);
        }
    }

    private ContinuationImpl.FrameRecord readFrame(ClassLoader classLoader)
                    throws IOException, ClassNotFoundException, NoSuchMethodException {
        assert in != null;
        Object[] pointers = (Object[]) in.readObject();
        long[] primitives = (long[]) in.readObject();
        // Slot zero is always primitive (bci), so this is in slot 1.
        Method method = readMethodNameAndTypes(classLoader, pointers.length > 1 ? pointers[1] : null);
        int bci = in.readInt();
        return new ContinuationImpl.FrameRecord(pointers, primitives, method, bci);
    }

    private Method readMethodNameAndTypes(ClassLoader classLoader, Object possibleThis)
                    throws IOException, ClassNotFoundException, NoSuchMethodException {
        assert in != null;
        Class<?> declaringClass;
        if (in.readBoolean()) {
            declaringClass = possibleThis.getClass();
        } else {
            declaringClass = Class.forName(readString(), false, classLoader);
        }
        String name = readString();
        Class<?> returnType = readClass(classLoader);

        int numArgs = in.readUnsignedByte();
        Class<?>[] argTypes = new Class<?>[numArgs];
        for (int i = 0; i < numArgs; i++) {
            argTypes[i] = readClass(classLoader);
        }

        for (Method method : declaringClass.getDeclaredMethods()) {
            if (!method.getName().equals(name)) {
                continue;
            }
            if (!Arrays.equals(method.getParameterTypes(), argTypes)) {
                continue;
            }
            if (!method.getReturnType().equals(returnType)) {
                continue;
            }
            return method;
        }

        throw new NoSuchMethodException("%s %s.%s(%s)".formatted(
                        returnType.getName(), declaringClass.getName(), name, String.join(", ", Arrays.stream(argTypes).map(Class::getName).toList())));
    }

    private Class<?> readClass(ClassLoader classLoader) throws IOException, ClassNotFoundException {
        assert in != null;
        int kind = in.readUnsignedByte();
        return switch (kind) {
            case 'I' -> int.class;
            case 'Z' -> boolean.class;
            case 'D' -> double.class;
            case 'F' -> float.class;
            case 'J' -> long.class;
            case 'B' -> byte.class;
            case 'C' -> char.class;
            case 'S' -> short.class;
            case 'V' -> void.class;
            case 'L' -> Class.forName(readString(), false, classLoader);
            default -> throw new IOException("Unexpected kind: " + kind);
        };
    }

    private String readString() throws IOException {
        assert in != null;
        int idx = in.readChar();
        if ((idx & NEW_POOL_MASK) != 0) {
            String value = in.readUTF();
            idx = idx & POOL_IDX_MASK;
            if (idx == stringPoolRead.size()) {
                stringPoolRead.add(value);
            } else {
                stringPoolRead.set(idx, value);
            }
            return value;
        } else {
            assert idx <= MAX_POOL_IDX;
            return stringPoolRead.get(idx);
        }
    }
}
