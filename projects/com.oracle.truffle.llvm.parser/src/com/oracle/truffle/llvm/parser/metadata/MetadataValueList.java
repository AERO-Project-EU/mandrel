/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser.metadata;

import com.oracle.truffle.llvm.parser.ValueList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MetadataValueList extends ValueList<MDBaseNode> {

    private static final ValueList.PlaceholderFactory<MDBaseNode> PLACEHOLDER_FACTORY = () -> new MDBaseNode() {
        @Override
        public void accept(MetadataVisitor visitor) {
            // TODO fail silently
            throw new IllegalStateException("Unresolved Forward Reference!");
        }

        @Override
        public void replace(MDBaseNode oldValue, MDBaseNode newValue) {
            // TODO fail silently
            throw new IllegalStateException("Unresolved Forward Reference!");
        }

        @Override
        public String toString() {
            return "Forward Referenced Metadata";
        }
    };

    private final MetadataValueList parent;

    private final Map<String, MDNamedNode> namedNodes;
    private final List<MDKind> kinds;

    public MetadataValueList() {
        this(null);
    }

    public MetadataValueList(MetadataValueList parent) {
        super(parent, PLACEHOLDER_FACTORY);
        this.parent = parent;
        this.namedNodes = new HashMap<>();
        this.kinds = new ArrayList<>();
    }

    public void addKind(MDKind newKind) {
        kinds.add(newKind);
    }

    public void addNamedNode(MDNamedNode namedNode) {
        namedNodes.put(namedNode.getName(), namedNode);
    }

    public MDNamedNode getNamedNode(String name) {
        if (parent != null) {
            final MDNamedNode parentResult = parent.getNamedNode(name);
            if (parentResult != null) {
                return parentResult;
            }
        }
        return namedNodes.get(name);
    }

    public MDBaseNode getNullable(long valueNumber, MDBaseNode dependent) {
        // offsets into the metadatalist are incremented by 1 so 0 can indicate a nullpointer
        if (valueNumber == 0L) {
            return MDVoidNode.INSTANCE;
        }

        final int index = (int) (valueNumber - 1);
        return getForwardReferenced(index, dependent);
    }

    public MDBaseNode getNonNullable(long index, MDBaseNode dependent) {
        return getForwardReferenced((int) index, dependent);
    }

    @Override
    public MDBaseNode getOrNull(int index) {
        final MDBaseNode node = super.getOrNull(index);
        return node != null ? node : MDVoidNode.INSTANCE;
    }

    public MDKind getKind(long id) {
        if (parent != null) {
            final MDKind kind = parent.getKind(id);
            if (kind != null) {
                return kind;
            }
        }

        for (MDKind kind : kinds) {
            if (kind.getId() == id) {
                return kind;
            }
        }

        return null;
    }

    private int nextArtificialKindId = -1;

    public MDKind findKind(String name) {
        for (MDKind kind : kinds) {
            if (kind.getName().equals(name)) {
                return kind;
            }
        }

        if (parent != null) {
            return parent.findKind(name);
        }

        final MDKind newKind = MDKind.create(nextArtificialKindId--, name);
        kinds.add(newKind);
        return newKind;
    }

    public void accept(MetadataVisitor visitor) {
        for (int i = 0; i < size(); i++) {
            final MDBaseNode value = getOrNull(i);
            if (value != null) {
                value.accept(visitor);
            }
        }
    }
}
