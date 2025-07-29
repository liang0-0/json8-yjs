package com.ai.types.ytext;

import com.ai.structs.*;
import com.ai.structs.item.AbstractContent;
import com.ai.structs.item.Item;
import com.ai.types.ID;
import com.ai.types.arraytype.AbstractType;
import com.ai.types.arraytype.ArraySearchMarker;
import com.ai.utils.DeleteSet;
import com.ai.utils.Doc;
import com.ai.utils.structstore.StructStore;
import com.ai.utils.Transaction;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ai.types.ytext.ItemTextListPosition.insertAttributes;
import static com.ai.utils.structstore.StructStore.getItemCleanStart;

public class YTextUtils {
    public static void insertText(Transaction transaction, AbstractType<?> parent, ItemTextListPosition currPos,
                                  Object text, Map<String, Object> attributes) {
        // 处理当前属性
        currPos.currentAttributes.forEach((key, val) -> {
            if (!attributes.containsKey(key)) {
                attributes.put(key, null);
            }
        });

        Doc doc = transaction.doc;
        int ownClientId = doc.clientID;

        ItemTextListPosition.minimizeAttributeChanges(currPos, attributes);
        Map<String, Object> negatedAttributes = insertAttributes(transaction, parent, currPos, attributes);

        // 创建内容对象
        AbstractContent content;
        if (text instanceof String) {
            content = new ContentString((String) text);
        } else if (text instanceof AbstractType) {
            content = new ContentType((AbstractType<?>) text);
        } else {
            content = new ContentEmbed(text);
        }

        Item left = currPos.left;
        Item right = currPos.right;
        int index = currPos.index;

        if (parent._searchMarker != null) {
            ArraySearchMarker.updateMarkerChanges(parent._searchMarker, currPos.index, content.getLength());
        }

        // 创建并插入新项目
        right = new Item(ID.createID(ownClientId, StructStore.getState(doc.store, ownClientId)),
                left, left != null ? left.getLastId() : null,
                right, right != null ? right.id : null,
                parent, null, content);
        right.integrate(transaction, 0);

        currPos.right = right;
        currPos.index = index;
        currPos.forward();

        ItemTextListPosition.insertNegatedAttributes(transaction, parent, currPos, negatedAttributes);
    }

    public static void formatText(Transaction transaction, AbstractType parent,
                                  ItemTextListPosition currPos, int length,
                                  Map<String, Object> attributes) {
        Doc doc = transaction.doc;
        int ownClientId = doc.clientID;

        ItemTextListPosition.minimizeAttributeChanges(currPos, attributes);
        Map<String, Object> negatedAttributes = insertAttributes(transaction, parent, currPos, attributes);

        while (currPos.right != null && (length > 0 || (!negatedAttributes.isEmpty() &&
                        (currPos.right.deleted() || currPos.right.content instanceof ContentFormat)))) {

            if (!currPos.right.deleted()) {
                if (currPos.right.content instanceof ContentFormat) {
                    ContentFormat cf = (ContentFormat) currPos.right.content;
                    Object attr = attributes.get(cf.key);

                    if (attributes.containsKey(cf.key)) {
                        if (ItemTextListPosition.equalAttrs(attr, cf.value)) {
                            negatedAttributes.remove(cf.key);
                        } else {
                            if (length == 0) {
                                break;
                            }
                            negatedAttributes.put(cf.key, cf.value);
                        }
                        currPos.right.delete(transaction);
                    } else {
                        currPos.currentAttributes.put(cf.key, cf.value);
                    }
                } else {
                    if (length < currPos.right.length) {
                        getItemCleanStart(transaction, ID.createID(currPos.right.id.client, currPos.right.id.clock + length));
                    }
                    length -= currPos.right.length;
                }
            }
            currPos.forward();
        }

        if (length > 0) {
            StringBuilder newlines = new StringBuilder();
            for (int i = 0; i < length; i++) {
                newlines.append('\n');
            }

            currPos.right = new Item(ID.createID(ownClientId, StructStore.getState(doc.store, ownClientId)),
                    currPos.left, currPos.left != null ? currPos.left.getLastId() : null,
                    currPos.right, currPos.right != null ? currPos.right.id : null,
                    parent, null, new ContentString(newlines.toString()));
            currPos.right.integrate(transaction, 0);
            currPos.forward();
        }

        ItemTextListPosition.insertNegatedAttributes(transaction, parent, currPos, negatedAttributes);
    }

    public static int cleanupFormattingGap(Transaction transaction, Item start, Item curr,
                                           Map<String, Object> startAttributes,
                                           Map<String, Object> currAttributes) {
        Item end = start;
        Map<String, ContentFormat> endFormats = new HashMap<>();

        while (end != null && (!end.countable() || end.deleted())) {
            if (!end.deleted() && end.content instanceof ContentFormat) {
                ContentFormat cf = (ContentFormat) end.content;
                endFormats.put(cf.key, cf);
            }
            end = end.right;
        }

        int cleanups = 0;
        boolean reachedCurr = false;

        while (start != end) {
            if (curr == start) {
                reachedCurr = true;
            }

            if (!start.deleted()) {
                if (start.content instanceof ContentFormat) {
                    ContentFormat cf = (ContentFormat) start.content;
                    Object startAttrValue = startAttributes.getOrDefault(cf.key, null);

                    if (!endFormats.get(cf.key).equals(start.content) ||
                            Objects.equals(startAttrValue, cf.value)) {

                        start.delete(transaction);
                        cleanups++;

                        if (!reachedCurr &&
                                Objects.equals(currAttributes.getOrDefault(cf.key, null), cf.value) &&
                                !Objects.equals(startAttrValue, cf.value)) {

                            if (startAttrValue == null) {
                                currAttributes.remove(cf.key);
                            } else {
                                currAttributes.put(cf.key, startAttrValue);
                            }
                        }
                    }

                    if (!reachedCurr && !start.deleted()) {
                        ItemTextListPosition.updateCurrentAttributes(currAttributes, cf);
                    }
                }
            }
            start = start.right;
        }

        return cleanups;
    }

    public static void cleanupContextlessFormattingGap(Transaction transaction, Item item) {
        while (item != null && item.right != null &&
                (item.right.deleted() || !item.right.countable())) {
            item = item.right;
        }

        Set<String> attrs = new LinkedHashSet<>();
        while (item != null && (item.deleted() || !item.countable())) {
            if (!item.deleted() && item.content instanceof ContentFormat) {
                String key = ((ContentFormat) item.content).key;
                if (attrs.contains(key)) {
                    item.delete(transaction);
                } else {
                    attrs.add(key);
                }
            }
            item = item.left;
        }
    }

    /**
     * 清理YText格式属性（实验性功能，可能会被修改或移除）
     * 理想情况下不需要此方法，格式属性应在每次更改后自动清理
     * 此方法遍历整个YText类型两次并移除不必要的格式属性，对测试有帮助
     * 当确认YText类型按预期工作后，将不再导出此方法
     *
     * @param type YText类型
     * @return 清理的格式属性数量
     */
    public static int cleanupYTextFormatting(YText type) {
        AtomicInteger res = new AtomicInteger();

        Transaction.transact(type.doc, transaction -> {
            Item start = type._start;
            Item end = type._start;
            Map<String, Object> startAttributes = new HashMap<>();
            Map<String, Object> currentAttributes = new HashMap<>(startAttributes);

            while (end != null) {
                if (!end.deleted()) {
                    if (end.content instanceof ContentFormat) {
                        ItemTextListPosition.updateCurrentAttributes(currentAttributes, (ContentFormat) end.content);
                    } else {
                        res.addAndGet(cleanupFormattingGap(transaction, start, end,
                                startAttributes, currentAttributes));
                        startAttributes = new HashMap<>(currentAttributes);
                        start = end;
                    }
                }
                end = end.right;
            }
            return null;
        });

        return res.get();
    }

    /**
     * 事务处理器调用后执行格式属性清理
     *
     * @param transaction 事务对象
     */
    public static void cleanupYTextAfterTransaction(Transaction transaction) {
        // 需要完整清理的YText集合
        Set<YText> needFullCleanup = new LinkedHashSet<>();
        Doc doc = transaction.doc;

        // 检查是否有新插入的格式项
        for (Map.Entry<Integer, Integer> entry : transaction.afterState.entrySet()) {
            int client = entry.getKey();
            int afterClock = entry.getValue();
            int clock = transaction.beforeState.getOrDefault(client, 0);

            if (afterClock == clock) {
                continue;
            }

            List<AbstractStruct> clientStructs = doc.store.clients.get(client);
            StructStore.iterateStructs(transaction, clientStructs, clock, afterClock, item -> {
                if (!item.deleted() && item instanceof Item
                        && ((Item) item).content instanceof ContentFormat) {
                    needFullCleanup.add((YText) ((Item) item).parent);
                }
            });
        }

        // 在新事务中执行清理
        // 在新事务中执行清理
        Transaction.transact(doc, t -> {
            // 遍历已删除的结构
            DeleteSet.iterateDeletedStructs(transaction, transaction.deleteSet, item -> {
                // 检查是否需要跳过当前项
                if (item instanceof GC ||
                        !((YText) ((Item)item).parent)._hasFormatting ||
                        needFullCleanup.contains((YText) ((Item) item).parent)) {
                    return;
                }

                YText parent = (YText) ((Item) item).parent;
                if (((Item) item).content instanceof ContentFormat) {
                    needFullCleanup.add(parent);
                } else {
                    // 对于非格式化内容，执行无上下文的格式清理
                    cleanupContextlessFormattingGap(t, ((Item) item));
                }
            });

            // 对所有需要清理的YText执行完整清理
            for (YText yText : needFullCleanup) {
                cleanupYTextFormatting(yText);
            }
            return null;
        });
    }

    public static ItemTextListPosition deleteText(Transaction transaction,
                                                  ItemTextListPosition currPos, int length) {
        int startLength = length;
        Map<String, Object> startAttrs = new HashMap<>(currPos.currentAttributes);
        Item start = currPos.right;

        while (length > 0 && currPos.right != null) {
            if (!currPos.right.deleted()) {
                if (currPos.right.content instanceof ContentString ||
                        currPos.right.content instanceof ContentType ||
                        currPos.right.content instanceof ContentEmbed) {

                    if (length < currPos.right.length) {
                        getItemCleanStart(transaction,
                                ID.createID(currPos.right.id.client, currPos.right.id.clock + length));
                    }
                    length -= currPos.right.length;
                    currPos.right.delete(transaction);
                }
            }
            currPos.forward();
        }

        if (start != null) {
            cleanupFormattingGap(transaction, start, currPos.right, startAttrs, currPos.currentAttributes);
        }

        AbstractType parent = (AbstractType) (currPos.left != null ? currPos.left : currPos.right).parent;
        if (parent._searchMarker != null) {
            ArraySearchMarker.updateMarkerChanges(parent._searchMarker, currPos.index, -startLength + length);
        }

        return currPos;
    }
}
