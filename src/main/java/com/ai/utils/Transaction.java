package com.ai.utils;

import com.ai.structs.AbstractStruct;
import com.ai.structs.item.Item;
import com.ai.types.ID;
import com.ai.types.arraytype.AbstractType;
import com.ai.types.vo.SubdocsEvent;
import com.ai.types.ytext.YTextUtils;
import com.ai.utils.codec.encoder.UpdateEncoder;
import com.ai.utils.codec.encoder.UpdateEncoderV1;
import com.ai.utils.codec.encoder.UpdateEncoderV2;
import com.ai.utils.structstore.StructStore;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

public class Transaction {
    public final Doc doc;
    public final DeleteSet deleteSet;
    public final Map<Integer, Integer> beforeState;
    public Map<Integer, Integer> afterState;
    public final Map<AbstractType<?>, Set<String>> changed;
    public final Map<AbstractType<?>, List<YEvent<?>>> changedParentTypes;
    public final List<AbstractStruct> mergeStructs;
    public final Object origin;
    public final Map<Object, Object> meta;
    public boolean local;
    public final Set<Doc> subdocsAdded;
    public final Set<Doc> subdocsRemoved;
    public final Set<Doc> subdocsLoaded;
    public boolean needFormattingCleanup;

    public Transaction(Doc doc, Object origin, boolean local) {
        this.doc = doc;
        this.deleteSet = new DeleteSet();
        this.beforeState = StructStore.getStateVector(doc.store);
        /*
          key: client
          value: clock
         */
        this.afterState = new HashMap<>();
        this.changed = new HashMap<>();
        this.changedParentTypes = new HashMap<>();
        this.mergeStructs = new ArrayList<>();
        this.origin = origin;
        this.meta = new HashMap<>();
        this.local = local;
        this.subdocsAdded = new HashSet<>();
        this.subdocsRemoved = new HashSet<>();
        this.subdocsLoaded = new HashSet<>();
        this.needFormattingCleanup = false;
    }

    public static boolean writeUpdateMessageFromTransaction(UpdateEncoder encoder, Transaction transaction) {
        if (transaction.deleteSet.clients.isEmpty() &&
                transaction.afterState.entrySet().stream()
                        .noneMatch(e -> !Objects.equals(transaction.beforeState.get(e.getKey()), e.getValue()))) {
            return false;
        }
        DeleteSet.sortAndMergeDeleteSet(transaction.deleteSet);
        Encoding.writeStructsFromTransaction(encoder, transaction);
        DeleteSet.writeDeleteSet(encoder, transaction.deleteSet);
        return true;
    }

    public static ID nextID(Transaction transaction) {
        Doc y = transaction.doc;
        return new ID(y.clientID, StructStore.getState(y.store, y.clientID));
    }

    public static void addChangedTypeToTransaction(Transaction transaction, AbstractType<?> type, String parentSub) {
        Item item = type._item;
        if (item == null ||
                (item.id.clock < transaction.beforeState.getOrDefault(item.id.client, 0) &&
                        !item.deleted())) {
            transaction.changed.computeIfAbsent(type, k -> new HashSet<>()).add(parentSub);
        }
    }

    private static int tryToMergeWithLefts(List<AbstractStruct> structs, int pos) {
        AbstractStruct right = structs.get(pos);
        AbstractStruct left = structs.get(pos - 1);
        int i = pos;
        int merged = 0;

        while (i > 0) {
            if (left.deleted() == right.deleted() && left.getClass() == right.getClass()) {
                if (left.mergeWith(right)) {
                    if (right instanceof Item && ((Item) right).parentSub != null &&
                            ((AbstractType<?>) ((Item) right).parent)._map.get(((Item) right).parentSub) == right) {
                        ((AbstractType<?>) ((Item) right).parent)._map.put(
                                ((Item) right).parentSub, (Item) left);
                    }
                    merged++;
                    i--;
                    if (i > 0) {
                        right = left;
                        left = structs.get(i - 1);
                    }
                    continue;
                }
            }
            break;
        }

        if (merged > 0) {
            structs.subList(pos + 1 - merged, pos + 1).clear();
        }
        return merged;
    }

    private static void tryGcDeleteSet(DeleteSet ds, StructStore store, Predicate<Item> gcFilter) {
        for (Map.Entry<Integer, List<DeleteItem>> entry : ds.clients.entrySet()) {
            int client = entry.getKey();
            List<DeleteItem> deleteItems = entry.getValue();
            List<AbstractStruct> structs = store.clients.get(client);

            for (int di = deleteItems.size() - 1; di >= 0; di--) {
                DeleteItem deleteItem = deleteItems.get(di);
                long endDeleteItemClock = deleteItem.clock + deleteItem.len;

                for (int si = StructStore.findIndexSS(structs, deleteItem.clock);
                     si < structs.size() && structs.get(si).id.clock < endDeleteItemClock;
                     si++) {
                    AbstractStruct struct = structs.get(si);
                    if (deleteItem.clock + deleteItem.len <= struct.id.clock) {
                        break;
                    }
                    if (struct instanceof Item) {
                        Item item = (Item) struct;
                        if (item.deleted() && !item.keep() && gcFilter.test((Item) item)) {
                            item.gc(store, false);
                        }
                    }
                }
            }
        }
    }

    private static void tryMergeDeleteSet(DeleteSet ds, StructStore store) {
        ds.clients.forEach((client, deleteItems) -> {
            List<AbstractStruct> structs = store.clients.get(client);
            for (int di = deleteItems.size() - 1; di >= 0; di--) {
                DeleteItem deleteItem = deleteItems.get(di);
                int mostRightIndexToCheck = Math.min(
                        structs.size() - 1,
                        1 + StructStore.findIndexSS(structs, deleteItem.clock + deleteItem.len - 1));

                for (int si = mostRightIndexToCheck; si > 0; si--) {
                    AbstractStruct struct = structs.get(si);
                    if (struct.id.clock >= deleteItem.clock) {
                        si -= 1 + tryToMergeWithLefts(structs, si);
                    } else {
                        break;
                    }
                }
            }
        });
    }

    public static void tryGc(DeleteSet ds, StructStore store, Predicate<Item> gcFilter) {
        tryGcDeleteSet(ds, store, gcFilter);
        tryMergeDeleteSet(ds, store);
    }

    private static void cleanupTransactions(List<Transaction> transactionCleanups, int i) {
        if (i < transactionCleanups.size()) {
            Transaction transaction = transactionCleanups.get(i);
            Doc doc = transaction.doc;
            StructStore store = doc.store;
            DeleteSet ds = transaction.deleteSet;
            List<AbstractStruct> mergeStructs = transaction.mergeStructs;

            try {
                DeleteSet.sortAndMergeDeleteSet(ds);
                transaction.afterState = StructStore.getStateVector(doc.store);
                doc.emit("beforeObserverCalls", transaction, doc);

                List<Runnable> callbacks = new ArrayList<>();

                // Observe events on changed types
                transaction.changed.forEach((type, subs) ->
                        callbacks.add(() -> {
                            if (type._item == null || !type._item.deleted()) {
                                type._callObserver(transaction, subs);
                            }
                        })
                );

                // Deep observe events
                callbacks.add(() -> {
                    transaction.changedParentTypes.forEach((type, events) -> {
                        if (!type._dEH.l.isEmpty() && (type._item == null || !type._item.deleted())) {
                            List<YEvent<?>> filteredEvents = events.stream()
                                    .filter(event ->
                                            event.target._item == null || !event.target._item.deleted())
                                    .collect(Collectors.toList());

                            filteredEvents.forEach(event -> {
                                event.currentTarget = type;
                                event.path = null;
                            });

                            filteredEvents.sort(Comparator.comparingInt(e -> e.getPath().size()));

                            EventHandler.callEventHandlerListeners(type._dEH, filteredEvents, transaction);
                        }
                    });
                });

                callbacks.add(() -> doc.emit("afterTransaction", transaction));

                callbacks.forEach(Runnable::run);

                if (transaction.needFormattingCleanup) {
                    YTextUtils.cleanupYTextAfterTransaction(transaction);
                }
            } finally {
                if (doc.gc) {
                    tryGcDeleteSet(ds, store, doc.gcFilter);
                }
                tryMergeDeleteSet(ds, store);

                transaction.afterState.forEach((client, clock) -> {
                    long beforeClock = transaction.beforeState.getOrDefault(client, 0);
                    if (beforeClock != clock) {
                        List<AbstractStruct> structs = store.clients.get(client);
                        int firstChangePos = Math.max(StructStore.findIndexSS(structs, beforeClock), 1);
                        for (int j = structs.size() - 1; j >= firstChangePos; ) {
                            j -= 1 + tryToMergeWithLefts(structs, j);
                        }
                    }
                });

                for (int j = mergeStructs.size() - 1; j >= 0; j--) {
                    AbstractStruct struct = mergeStructs.get(j);
                    int client = struct.id.client;
                    long clock = struct.id.clock;
                    List<AbstractStruct> structs = store.clients.get(client);
                    int replacedStructPos = StructStore.findIndexSS(structs, clock);

                    if (replacedStructPos + 1 < structs.size()) {
                        if (tryToMergeWithLefts(structs, replacedStructPos + 1) > 1) {
                            continue;
                        }
                    }
                    if (replacedStructPos > 0) {
                        tryToMergeWithLefts(structs, replacedStructPos);
                    }
                }

                if (!transaction.local &&
                        !Objects.equals(
                                transaction.afterState.get(doc.clientID),
                                transaction.beforeState.get(doc.clientID))) {
                    System.err.println("[yjs] Changed the client-id because another client seems to be using it.");
                    doc.clientID = Doc.generateNewClientId();
                }

                doc.emit("afterTransactionCleanup", transaction, doc);

                if (doc._observers.containsKey("update")) {
                    UpdateEncoderV1 encoder = new UpdateEncoderV1();
                    boolean hasContent = writeUpdateMessageFromTransaction(encoder, transaction);
                    if (hasContent) {
                        doc.emit("update", encoder.toUint8Array(), transaction.origin, doc, transaction);
                    }
                }

                if (doc._observers.containsKey("updateV2")) {
                    UpdateEncoderV2<?> encoder = new UpdateEncoderV2<>();
                    boolean hasContent = writeUpdateMessageFromTransaction(encoder, transaction);
                    if (hasContent) {
                        doc.emit("updateV2", encoder.toUint8Array(), transaction.origin, doc, transaction);
                    }
                }

                Set<Doc> subdocsAdded = transaction.subdocsAdded;
                Set<Doc> subdocsLoaded = transaction.subdocsLoaded;
                Set<Doc> subdocsRemoved = transaction.subdocsRemoved;

                if (!subdocsAdded.isEmpty() || !subdocsRemoved.isEmpty() || !subdocsLoaded.isEmpty()) {
                    subdocsAdded.forEach(subdoc -> {
                        subdoc.clientID = doc.clientID;
                        if (subdoc.collectionid == null) {
                            subdoc.collectionid = doc.collectionid;
                        }
                        doc.getSubdocs().add(subdoc);
                    });

                    subdocsRemoved.forEach(doc.getSubdocs()::remove);
                    doc.emit("subdocs", new SubdocsEvent(subdocsLoaded, subdocsAdded, subdocsRemoved), doc, transaction);
                    subdocsRemoved.forEach(Doc::destroy);
                }

                if (transactionCleanups.size() <= i + 1) {
                    doc._transactionCleanups = new ArrayList<>();
                    doc.emit("afterAllTransactions", doc, transactionCleanups);
                } else {
                    cleanupTransactions(transactionCleanups, i + 1);
                }
            }
        }
    }

    public static <T> T transact(Doc doc, Function<Transaction, T> f) {
        return transact(doc, f, null, true);
    }

    public static <T> T transact(Doc doc, Function<Transaction, T> f, Object origin) {
        return transact(doc, f, origin, true);
    }

    public static <T> T transact(Doc doc, Function<Transaction, T> f, Object origin, Boolean local) {
        if (null == local) local = true;

        List<Transaction> transactionCleanups = doc._transactionCleanups;
        boolean initialCall = false;
        T result;

        if (doc._transaction == null) {
            initialCall = true;
            doc._transaction = new Transaction(doc, origin, local);
            transactionCleanups.add(doc._transaction);
            if (transactionCleanups.size() == 1) {
                doc.emit("beforeAllTransactions", doc);
            }
            doc.emit("beforeTransaction", doc._transaction, doc);
        }

        try {
            result = f.apply(doc._transaction);
        } finally {
            if (initialCall) {
                boolean finishCleanup = doc._transaction == transactionCleanups.get(0);
                doc._transaction = null;
                if (finishCleanup) {
                    // The first transaction ended, now process observer calls.
                    // Observer call may create new transactions for which we need to call the observers and do cleanup.
                    // We don't want to nest these calls, so we execute these calls one after
                    // another.
                    // Also, we need to ensure that all cleanups are called, even if the
                    // observes throw errors.
                    // This file is full of hacky try {} finally {} blocks to ensure that an
                    // event can throw errors and also that the cleanup is called.
                    cleanupTransactions(transactionCleanups, 0);
                }
            }
        }

        return result;
    }
}
