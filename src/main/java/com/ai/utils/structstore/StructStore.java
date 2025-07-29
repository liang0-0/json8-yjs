package com.ai.utils.structstore;

import com.ai.structs.AbstractStruct;
import com.ai.structs.GC;
import com.ai.structs.item.Item;
import com.ai.types.ID;
import com.ai.utils.Transaction;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class StructStore {
    public final Map<Integer, List<AbstractStruct>> clients = new HashMap<>();
    /**
     * @type {null | { missing: Map<number, number>, update: Uint8Array }}
     */
    public Structs pendingStructs;
    public int[] pendingDs;

    public static Map<Integer, Integer> getStateVector(StructStore store) {
        Map<Integer, Integer> sm = new HashMap<>();
        store.clients.forEach((client, structs) -> {
            AbstractStruct struct = structs.get(structs.size() - 1);
            sm.put(client, (struct.id.clock + struct.length));
        });
        return sm;
    }

    public static int getState(StructStore store, int client) {
        List<AbstractStruct> structs = store.clients.get(client);
        if (structs == null) return 0;
        AbstractStruct lastStruct = structs.get(structs.size() - 1);
        return lastStruct.id.clock + lastStruct.length;
    }

    public void integrityCheck() {
        clients.forEach((client, structs) -> {
            for (int i = 1; i < structs.size(); i++) {
                AbstractStruct l = structs.get(i - 1);
                AbstractStruct r = structs.get(i);
                if (l.id.clock + l.length != r.id.clock) {
                    throw new RuntimeException("Integrity check failed");
                }
            }
        });
    }

    public static void addStruct(StructStore store, AbstractStruct struct) {
        List<AbstractStruct> structs = store.clients.get(struct.id.client);
        if (CollectionUtils.isEmpty(structs)) {
            structs = new ArrayList<>();
            store.clients.put(struct.id.client, structs);
        } else {
            AbstractStruct lastStruct = structs.get(structs.size() - 1);
            if (lastStruct.id.clock + lastStruct.length != struct.id.clock) {
                throw new RuntimeException("Unexpected case");
            }
        }
        structs.add(struct);
    }

    /**
     * Perform a binary search on a sorted array
     *
     * @param structs {Array<Item|GC>}
     * @param clock   {number}
     * @return {number}
     */
    public static int findIndexSS(List<AbstractStruct> structs, long clock) {
        int left = 0;
        int right = structs.size() - 1;
        AbstractStruct mid = structs.get(right);
        int midclock = mid.id.clock;
        if (midclock == clock) return right;

        int midindex = (int) Math.floor((double) clock / (midclock + mid.length - 1) * right);
        while (left <= right) {
            mid = structs.get(midindex);
            midclock = mid.id.clock;
            if (midclock <= clock) {
                if (clock < midclock + mid.length) return midindex;
                left = midindex + 1;
            } else {
                right = midindex - 1;
            }
            midindex = (int) Math.floor((left + right) / 2.0);
        }
        throw new RuntimeException("Unexpected case");
    }

    public AbstractStruct find(ID id) {
        List<AbstractStruct> structs = clients.get(id.client);
        return structs.get(findIndexSS(structs, id.clock));
    }

    public static AbstractStruct getItem(StructStore store, ID id) {
        return store.find(id);
    }

    public static int findIndexCleanStart(Transaction transaction, List<AbstractStruct> structs, int clock) {
        int index = findIndexSS(structs, clock);
        AbstractStruct struct = structs.get(index);
        if (struct.id.clock < clock && struct instanceof Item) {
            AbstractStruct split = Item.splitItem(transaction, (Item) struct, clock - struct.id.clock);
            structs.add(index + 1, split);
            return index + 1;
        }
        return index;
    }

    public static AbstractStruct getItemCleanStart(Transaction transaction, ID id) {
        List<AbstractStruct> structs = transaction.doc.store.clients.get(id.client);
        return structs.get(findIndexCleanStart(transaction, structs, id.clock));
    }

    public static AbstractStruct getItemCleanEnd(Transaction transaction, StructStore store, ID id) {
        List<AbstractStruct> structs = store.clients.get(id.client);
        int index = findIndexSS(structs, id.clock);
        AbstractStruct struct = structs.get(index);
        if (id.clock != struct.id.clock + struct.length - 1 && !(struct instanceof GC)) {
            AbstractStruct split = Item.splitItem(transaction, (Item) struct, id.clock - struct.id.clock + 1);
            structs.add(index + 1, split);
        }
        return struct;
    }

    public void replaceStruct(StructStore store, AbstractStruct struct, AbstractStruct newStruct) {
        List<AbstractStruct> structs = store.clients.get(struct.id.client);
        structs.set(findIndexSS(structs, struct.id.clock), newStruct);
    }

    public static void iterateStructs(Transaction transaction, List<AbstractStruct> structs,
                                      int clockStart, int len, Consumer<AbstractStruct> f) {
        if (len == 0) return;
        int clockEnd = clockStart + len;
        int index = findIndexCleanStart(transaction, structs, clockStart);
        AbstractStruct struct;
        do {
            struct = structs.get(index++);
            if (clockEnd < struct.id.clock + struct.length) {
                findIndexCleanStart(transaction, structs, clockEnd);
            }
            f.accept(struct);
        } while (index < structs.size() && structs.get(index).id.clock < clockEnd);
    }

}