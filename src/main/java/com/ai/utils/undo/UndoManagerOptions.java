package com.ai.utils.undo;

import com.ai.structs.item.Item;
import com.ai.utils.Doc;
import com.ai.utils.Transaction;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

@Data
@Accessors(chain = true)
public class UndoManagerOptions {
    public int captureTimeout = 500;
    @ToString.Exclude
    public Predicate<Transaction> captureTransaction = tr -> true;
    @ToString.Exclude
    public Predicate<Item> deleteFilter = item -> true;
    public Set<Object> trackedOrigins = new LinkedHashSet<>(Collections.singleton(null));
    public boolean ignoreRemoteMapChanges = false;
    public Doc doc;
}
