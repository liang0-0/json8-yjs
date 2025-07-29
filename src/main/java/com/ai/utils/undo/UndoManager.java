package com.ai.utils.undo;

import com.ai.myutils.Maps;
import com.ai.myutils.observable.ObservableV2;
import com.ai.structs.item.Item;
import com.ai.types.ID;
import com.ai.types.arraytype.AbstractType;
import com.ai.types.vo.ItemDiffResult;
import com.ai.utils.*;
import com.ai.utils.structstore.StructStore;
import org.apache.commons.lang3.ObjectUtils;

import java.util.*;
import java.util.function.*;


public class UndoManager extends ObservableV2 {
    private final List<Object> scope;
    private final Doc doc;
    private final Predicate<Item> deleteFilter;
    private final Set<Object> trackedOrigins;
    private final Predicate<Transaction> captureTransaction;
    public final Stack<StackItem> undoStack;
    public final Stack<StackItem> redoStack;
    public boolean undoing;
    public boolean redoing;
    public StackItem currStackItem;
    private long lastChange;
    private final boolean ignoreRemoteMapChanges;
    private final int captureTimeout;
    private final Consumer<Transaction> afterTransactionHandler;


    public UndoManager(Object typeScope) {
        this(typeScope, null);
    }

    public UndoManager(Object typeScope, UndoManagerOptions options) {
        super();
        options = ObjectUtils.getIfNull(options, new UndoManagerOptions());
        this.scope = new ArrayList<>();
        this.doc = options.doc != null ? options.doc : determineDoc(typeScope);
        this.deleteFilter = ObjectUtils.getIfNull(options.deleteFilter, item -> true);
        this.trackedOrigins = new LinkedHashSet<>(options.trackedOrigins);
        this.trackedOrigins.add(this);
        this.captureTransaction = ObjectUtils.getIfNull(options.captureTransaction, transaction -> true);
        this.undoStack = new Stack<>();
        this.redoStack = new Stack<>();
        this.undoing = false;
        this.redoing = false;
        this.currStackItem = null;
        this.lastChange = 0;
        this.ignoreRemoteMapChanges = ObjectUtils.getIfNull(options.ignoreRemoteMapChanges, false);
        this.captureTimeout = ObjectUtils.getIfNull(options.captureTimeout, 500);

        addToScope(typeScope);

        this.afterTransactionHandler = transaction -> {
            if (!this.captureTransaction.test(transaction) ||
                    !this.scope.stream().anyMatch(type ->
                            transaction.changedParentTypes.containsKey(type) ||
                                    type.equals(this.doc)) ||
                    (!this.trackedOrigins.contains(transaction.origin) &&
                            (transaction.origin == null ||
                                    !this.trackedOrigins.contains(transaction.origin.getClass())))) {
                return;
            }

            boolean undoing = this.undoing;
            boolean redoing = this.redoing;
            List<StackItem> stack = undoing ? this.redoStack : this.undoStack;

            if (undoing) {
                stopCapturing();
            } else if (!redoing) {
                clear(false, true);
            }

            DeleteSet insertions = new DeleteSet();
            transaction.afterState.forEach((client, endClock) -> {
                int startClock = transaction.beforeState.getOrDefault(client, 0);
                int len = endClock - startClock;
                if (len > 0) {
                    DeleteSet.addToDeleteSet(insertions, client, startClock, len);
                }
            });

            long now = System.currentTimeMillis();
            boolean didAdd = false;

            if (this.lastChange > 0 && now - this.lastChange < this.captureTimeout &&
                    !stack.isEmpty() && !undoing && !redoing) {
                StackItem lastOp = stack.get(stack.size() - 1);
                lastOp.deletions = DeleteSet.mergeDeleteSets(Arrays.asList(lastOp.deletions, transaction.deleteSet));
                lastOp.insertions = DeleteSet.mergeDeleteSets(Arrays.asList(lastOp.insertions, insertions));
            } else {
                stack.add(new StackItem(transaction.deleteSet, insertions));
                didAdd = true;
            }

            if (!undoing && !redoing) {
                this.lastChange = now;
            }

            DeleteSet.iterateDeletedStructs(transaction, transaction.deleteSet, item -> {
                if (item instanceof Item && this.scope.stream().anyMatch(type ->
                        type.equals(transaction.doc) || IsParentOf.isParentOf((AbstractType<?>) type, (Item) item))) {
                    Item.keepItem((Item) item, true);
                }
            });

            StackItemEvent event = new StackItemEvent(
                    stack.get(stack.size() - 1),
                    transaction.origin,
                    undoing ? "redo" : "undo",
                    transaction.changedParentTypes
            );

            if (didAdd) {
                emit("stack-item-added", event, this);
            } else {
                emit("stack-item-updated", event, this);
            }
        };

        this.doc.on("afterTransaction", this.afterTransactionHandler);
        this.doc.on("destroy", (o) -> this.destroy());
    }

    private Doc determineDoc(Object typeScope) {
        if (typeScope instanceof Doc) {
            return (Doc) typeScope;
        } else if (typeScope instanceof AbstractType) {
            return ((AbstractType<?>) typeScope).doc;
        } else if (typeScope instanceof Collection) {
            return ((AbstractType<?>) ((Collection<?>) typeScope).iterator().next()).doc;
        }
        throw new IllegalArgumentException("Invalid typeScope");
    }

    public void addToScope(Object ytypes) {
        Set<Object> tmpSet = new LinkedHashSet<>(this.scope);
        List<Object> typesToAdd = ytypes instanceof Collection ?
                new ArrayList<>((Collection<?>) ytypes) :
                Collections.singletonList(ytypes);

        for (Object ytype : typesToAdd) {
            if (!tmpSet.contains(ytype)) {
                tmpSet.add(ytype);
                if (ytype instanceof AbstractType && !((AbstractType<?>) ytype).doc.equals(this.doc) ||
                        ytype instanceof Doc && !ytype.equals(this.doc)) {
                    System.err.println("[yjs#509] Not same Y.Doc");
                }
                this.scope.add(ytype);
            }
        }
    }

    public void addTrackedOrigin(Object origin) {
        this.trackedOrigins.add(origin);
    }

    public void removeTrackedOrigin(Object origin) {
        this.trackedOrigins.remove(origin);
    }

    public void clear(boolean clearUndoStack, boolean clearRedoStack) {
        if ((clearUndoStack && canUndo()) || (clearRedoStack && canRedo())) {
            this.doc.transact(tr -> {
                if (clearUndoStack) {
                    this.undoStack.forEach(item -> clearUndoManagerStackItem(tr, this, item));
                    this.undoStack.clear();
                }
                if (clearRedoStack) {
                    this.redoStack.forEach(item -> clearUndoManagerStackItem(tr, this, item));
                    this.redoStack.clear();
                }
                emit("stack-cleared", Maps.of("undoStackCleared", clearUndoStack, "redoStackCleared", clearRedoStack));
                return null;
            }, this);
        }
    }

    public void stopCapturing() {
        this.lastChange = 0;
    }

    public StackItem undo() {
        this.undoing = true;
        try {
            return popStackItem(this, this.undoStack, "undo");
        } finally {
            this.undoing = false;
        }
    }

    public StackItem redo() {
        this.redoing = true;
        try {
            return popStackItem(this, this.redoStack, "redo");
        } finally {
            this.redoing = false;
        }
    }

    public boolean canUndo() {
        return !this.undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !this.redoStack.isEmpty();
    }

    @Override
    public void destroy() {
        this.trackedOrigins.remove(this);
        this.doc.off("afterTransaction", this.afterTransactionHandler);
        super.destroy();
    }

    private static void clearUndoManagerStackItem(Transaction tr, UndoManager um, StackItem stackItem) {
        DeleteSet.iterateDeletedStructs(tr, stackItem.deletions, item -> {
            if (item instanceof Item && um.scope.stream().anyMatch(type ->
                    type.equals(tr.doc) ||
                            IsParentOf.isParentOf((AbstractType<?>) type, (Item) item))) {
                Item.keepItem((Item) item, false);
            }
        });
    }

    private static StackItem popStackItem(UndoManager um, Stack<StackItem> stack, String eventType) {
        // Keep a reference to the transaction, so we can fire the event with the changedParentTypes
        Transaction[] _tr = {null};
        Doc doc = um.doc;
        List<Object> scope = um.scope;

        Transaction.transact(doc, transaction -> {
            while (!stack.isEmpty() && um.currStackItem == null) {
                StructStore store = doc.store;
                StackItem stackItem = stack.pop();
                Set<Item> itemsToRedo = new LinkedHashSet<>();
                List<Item> itemsToDelete = new ArrayList<>();
                boolean performedChange = false;

                DeleteSet.iterateDeletedStructs(transaction, stackItem.insertions, struct -> {
                    if (struct instanceof Item) {
                        Item item = (Item) struct;
                        if (item.redone != null) {
                            ItemDiffResult result = Item.followRedone(store, item.id);
                            item = result.item;
                            if (result.diff > 0) {
                                item = (Item) StructStore.getItemCleanStart(
                                        transaction,
                                        ID.createID(item.id.client, item.id.clock + result.diff)
                                );
                            }
                        }
                        Item finalItem = item;
                        if (!item.deleted() && scope.stream().anyMatch(type -> type.equals(transaction.doc)
                                || IsParentOf.isParentOf((AbstractType<?>) type, finalItem))) {
                            itemsToDelete.add(item);
                        }
                    }
                });

                DeleteSet.iterateDeletedStructs(transaction, stackItem.deletions, struct -> {
                    if (struct instanceof Item
                            && scope.stream().anyMatch(type -> type.equals(transaction.doc) || IsParentOf.isParentOf((AbstractType<?>) type, (Item) struct))
                            // Never redo structs in stackItem.insertions because they were created and deleted in the same capture interval.
                            && !DeleteSet.isDeleted(stackItem.insertions, struct.id)) {
                        itemsToRedo.add((Item) struct);
                    }
                });

                for (Item struct : itemsToRedo) {
                    performedChange = Item.redoItem(transaction, struct, itemsToRedo,stackItem.insertions, um.ignoreRemoteMapChanges, um) != null || performedChange;
                }
                // We want to delete in reverse order so that children are deleted before
                // parents, so we have more information available when items are filtered.
                for (int i = itemsToDelete.size() - 1; i >= 0; i--) {
                    Item item = itemsToDelete.get(i);
                    if (um.deleteFilter.test(item)) {
                        item.delete(transaction);
                        performedChange = true;
                    }
                }

                um.currStackItem = performedChange ? stackItem : null;
            }

            transaction.changed.forEach((type, subProps) -> {
                // destroy search marker if necessary
                if (subProps.contains(null) && type._searchMarker != null) {
                    type._searchMarker.clear();
                }
            });

            _tr[0] = transaction;
            return null;
        }, um);

        StackItem res = um.currStackItem;
        if (res != null) {
            Map<AbstractType<?>, List<YEvent<?>>> changedParentTypes = _tr[0].changedParentTypes;
            um.emit("stack-item-popped", new StackItemEvent(res, um, eventType, changedParentTypes), um);
            um.currStackItem = null;
        }
        return res;
    }
}

