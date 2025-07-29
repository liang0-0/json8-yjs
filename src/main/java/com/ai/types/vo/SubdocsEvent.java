package com.ai.types.vo;

import com.ai.utils.Doc;

import java.util.Set;

public class SubdocsEvent {
    public final Set<Doc> loaded;
    public final Set<Doc> added;
    public final Set<Doc> removed;

    public SubdocsEvent(Set<Doc> loaded, Set<Doc> added, Set<Doc> removed) {
        this.loaded = loaded;
        this.added = added;
        this.removed = removed;
    }
}