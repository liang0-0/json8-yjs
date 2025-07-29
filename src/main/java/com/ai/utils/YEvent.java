package com.ai.utils;

import com.ai.structs.AbstractStruct;
import com.ai.structs.item.Item;
import com.ai.types.arraytype.AbstractType;

import java.util.*;

public class YEvent<T extends AbstractType<?>> {
    public final T target;
    public AbstractType<?> currentTarget;
    public final Transaction transaction;
    private Map<String, Object> changes;
    private Map<String, KeyChange> keys;
    private List<Delta> delta;
    List<Object> path;

    public static class KeyChange {
        public final String action; // "add", "update", or "delete"
        public final Object oldValue;

        public KeyChange(String action, Object oldValue) {
            this.action = action;
            this.oldValue = oldValue;
        }
    }

    public static class Delta {
        public Object insert;
        public Integer retain;
        public Integer delete;
        public Map<String, Object> attributes;

        public Delta(Object insert) {
            this.insert = insert;
        }

        public Delta(int retain) {
            this.retain = retain;
        }

        public Delta(Integer delete, boolean isDelete) {
            this.delete = delete;
        }
    }

    public YEvent(T target, Transaction transaction) {
        this.target = target;
        this.currentTarget = target;
        this.transaction = transaction;
    }

    public List<Object> getPath() {
        if (path == null) {
            path = getPathTo(currentTarget, target);
        }
        return path;
    }

    public boolean deletes(AbstractStruct struct) {
        return DeleteSet.isDeleted(transaction.deleteSet, struct.id);
    }

    public Map<String, KeyChange> getKeys() {
        if (keys == null) {
            if (transaction.doc._transactionCleanups.isEmpty()) {
                throw new IllegalStateException("You must not compute changes after the event-handler fired.");
            }
            Map<String, KeyChange> keys = new HashMap<>();
            AbstractType<?> target = this.target;
            Set<String> changed = (Set<String>) transaction.changed.get(target);
            if (changed != null) {
                for (String key : changed) {
                    if (key != null) {
                        Item item = (Item) target._map.get(key);
                        String action;
                        Object oldValue;
                        if (adds(item)) {
                            Item prev = item.left;
                            while (prev != null && adds(prev)) {
                                prev = prev.left;
                            }
                            if (deletes(item)) {
                                if (prev != null && deletes(prev)) {
                                    action = "delete";
                                    oldValue = prev.content.getContent().get(prev.content.getContent().size() - 1);
                                } else {
                                    continue;
                                }
                            } else {
                                if (prev != null && deletes(prev)) {
                                    action = "update";
                                    oldValue = prev.content.getContent().get(prev.content.getContent().size() - 1);
                                } else {
                                    action = "add";
                                    oldValue = null;
                                }
                            }
                        } else {
                            if (deletes(item)) {
                                action = "delete";
                                oldValue = item.content.getContent().get(item.content.getContent().size() - 1);
                            } else {
                                continue; // nop
                            }
                        }
                        keys.put(key, new KeyChange(action, oldValue));
                    }
                }
            }
            this.keys = keys;
        }
        return keys;
    }

    public List<Delta> getDelta() {
        return (List<Delta>) getChanges().get("delta");
    }

    public boolean adds(AbstractStruct struct) {
        return struct.id.clock >= transaction.beforeState.getOrDefault(struct.id.client, 0);
    }

    public Map<String, Object> getChanges() {
        if (changes == null) {
            if (transaction.doc._transactionCleanups.isEmpty()) {
                throw new IllegalStateException("You must not compute changes after the event-handler fired.");
            }
            AbstractType<?> target = this.target;
            Set<Item> added = new LinkedHashSet<>();
            Set<Item> deleted = new LinkedHashSet<>();
            List<Delta> delta = new ArrayList<>();
            Map<String, Object> changes = new HashMap<>();
            changes.put("added", added);
            changes.put("deleted", deleted);
            changes.put("delta", delta);
            changes.put("keys", getKeys());

            Set<String> changed = (Set<String>) transaction.changed.get(target);
            if (changed != null && changed.contains(null)) {
                Delta lastOp = null;
                for (Item item = target._start; item != null; item = item.right) {
                    if (item.deleted()) {
                        if (deletes(item) && !adds(item)) {
                            if (lastOp == null || lastOp.delete == null) {
                                if (lastOp != null) {
                                    delta.add(lastOp);
                                }
                                lastOp = new Delta(0, true);
                            }
                            lastOp.delete += item.length;
                            deleted.add(item);
                        }
                    } else {
                        if (adds(item)) {
                            if (lastOp == null || lastOp.insert == null) {
                                if (lastOp != null) {
                                    delta.add(lastOp);
                                }
                                lastOp = new Delta(new ArrayList<>());
                            }
                            ((List<Object>) lastOp.insert).addAll(item.content.getContent());
                            added.add(item);
                        } else {
                            if (lastOp == null || lastOp.retain == null) {
                                if (lastOp != null) {
                                    delta.add(lastOp);
                                }
                                lastOp = new Delta(0);
                            }
                            lastOp.retain += item.length;
                        }
                    }
                }
                if (lastOp != null && lastOp.retain == null) {
                    delta.add(lastOp);
                }
            }
            this.changes = changes;
        }
        return changes;
    }

    private static List<Object> getPathTo(AbstractType<?> parent, AbstractType<?> child) {
        List<Object> path = new ArrayList<>();
        while (child._item != null && !child.equals(parent)) {
            if (child._item.parentSub != null) {
                // parent is map-ish
                path.add(0, child._item.parentSub);
            } else {
                // parent is array-ish
                int i = 0;
                Item c = ((AbstractType<?>) child._item.parent)._start;
                while (c != child._item && c != null) {
                    if (!c.deleted() && c.countable()) {
                        i += c.length;
                    }
                    c = c.right;
                }
                path.add(0, i);
            }
            child = (AbstractType<?>) child._item.parent;
        }
        return path;
    }
}