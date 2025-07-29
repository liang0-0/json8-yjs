package com.ai.structs.item;

import com.ai.myutils.binary;
import com.ai.structs.*;
import com.ai.types.*;
import com.ai.types.arraytype.AbstractType;
import com.ai.types.arraytype.ArraySearchMarker;
import com.ai.types.vo.ItemDiffResult;
import com.ai.utils.DeleteSet;
import com.ai.utils.Doc;
import com.ai.utils.structstore.StructStore;
import com.ai.utils.Transaction;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.encoder.UpdateEncoder;
import com.ai.utils.undo.StackItem;
import com.ai.utils.undo.UndoManager;

import java.util.*;
import java.util.function.Function;

import static com.ai.types.ID.findRootTypeKey;

public class Item extends AbstractStruct {
    public ID origin;
    public Item left;
    public Item right;
    public ID rightOrigin;
    public Object parent; // AbstractType or ID
    public String parentSub;
    public ID redone;
    public AbstractContent content;
    public int info;

    @Override
    public String toString() {
        // if value is null, ignore it
        StringBuilder sb = new StringBuilder();
        if (origin != null) sb.append("origin=").append(origin).append(",");
        if (rightOrigin != null) sb.append("rightOrigin=").append(rightOrigin).append(",");
        if (parent != null) sb.append("parent=").append(parent).append(",");
        if (parentSub != null) sb.append("parentSub=").append(parentSub).append(",");
        if (redone != null) sb.append("redone=").append(redone).append(",");
        if (info != 0) sb.append("info=").append(info).append(",");
        sb.append("content=").append(content);
        return sb.toString();
    }

    public Item() {
        this(null, null, null, null, null, null, null, new ContentAny(new ArrayList<>()));
    }

    /**
     * @param id          {ID }
     * @param left        {Item | null}
     * @param origin      {ID | null}
     * @param right       {Item | null}
     * @param rightOrigin {ID | null}
     * @param parent      {AbstractType <any>|ID|null}  Is a type if integrated, is null if it is possible to copy parent from left or right, is ID before integration to search for it.
     * @param parentSub   {string | null}
     * @param content     {AbstractContent }
     */
    public Item(ID id, Item left, ID origin, Item right, ID rightOrigin,
                Object parent, String parentSub, AbstractContent content) {
        super(id, content.getLength());
        this.origin = origin;
        this.left = left;
        this.right = right;
        this.rightOrigin = rightOrigin;
        this.parent = parent;
        this.parentSub = parentSub;
        this.content = content;
        this.info = content.isCountable() ? binary.BIT2 : 0;
    }


    // Property accessors with original comments

    /**
     * This is used to mark the item as an indexed fast-search marker
     */
    public boolean marker() {
        return (info & binary.BIT4) > 0;
    }

    public void setMarker(boolean isMarked) {
        if (marker() != isMarked) info ^= binary.BIT4;
    }

    /**
     * If true, do not garbage collect this Item.
     */
    public boolean keep() {
        return (info & binary.BIT1) > 0;
    }

    public void setKeep(boolean doKeep) {
        if (keep() != doKeep) info ^= binary.BIT1;
    }

    public boolean countable() {
        return (info & binary.BIT2) > 0;
    }

    /**
     * Whether this item was deleted or not.
     */
    public boolean deleted() {
        return (info & binary.BIT3) > 0;
    }

    public void setDeleted(boolean doDelete) {
        if (deleted() != doDelete) info ^= binary.BIT3;
    }

    public void markDeleted() {
        this.info |= binary.BIT3;
    }


    // Navigation methods with original comments

    /**
     * Returns the next non-deleted item
     */
    public Item next() {
        Item n = right;
        while (n != null && n.deleted()) n = n.right;
        return n;
    }

    /**
     * Returns the previous non-deleted item
     */
    public Item prev() {
        Item n = left;
        while (n != null && n.deleted()) n = n.left;
        return n;
    }

    /**
     * Computes the last content address of this Item.
     */
    public ID getLastId() {
        // allocating ids is pretty costly because of the amount of ids created, so we try to reuse whenever possible
        return length == 1 ? id : new ID(id.client, id.clock + length - 1);
    }


    // Core functionality with original comments

    /**
     * Return the creator clientID of the missing op or define missing items and return null.
     */
    public Integer getMissing(Transaction transaction, StructStore store) {
        if (origin != null && origin.client != id.client &&
                origin.clock >= StructStore.getState(store, origin.client)) {
            return origin.client;
        }
        if (rightOrigin != null && rightOrigin.client != id.client &&
                rightOrigin.clock >= StructStore.getState(store, rightOrigin.client)) {
            return rightOrigin.client;
        }
        if (parent instanceof ID && id.client != ((ID) parent).client &&
                ((ID) parent).clock >= StructStore.getState(store, ((ID) parent).client)) {
            return ((ID) parent).client;
        }

        // We have all missing ids, now find the items
        if (origin != null) {
            left = (Item) StructStore.getItemCleanEnd(transaction, store, origin);
            origin = left.getLastId();
        }
        if (rightOrigin != null) {
            right = (Item) StructStore.getItemCleanStart(transaction, rightOrigin);
            rightOrigin = right.id;
        }
//        if ((left instanceof GC) || (right instanceof GC)) {
//            parent = null;
//        } else
        if (parent == null) {
            // only set parent if this shouldn't be garbage collected
            if (left != null) {
                parent = left.parent;
                parentSub = left.parentSub;
            } else if (right != null) {
                parent = right.parent;
                parentSub = right.parentSub;
            }
        } else if (parent instanceof ID) {
            AbstractStruct parentItem = StructStore.getItem(store, (ID) parent);
            if (parentItem instanceof GC) {
                parent = null;
            } else {
                parent = ((ContentType) ((Item) parentItem).content).type;
            }
        }
        return null;
    }

    @Override
    public void integrate(Transaction transaction, int offset) {
        if (offset > 0) {
            id = new ID(id.client, id.clock + offset);
            left = (Item) StructStore.getItemCleanEnd(transaction, transaction.doc.store,
                    new ID(id.client, id.clock - 1));
            origin = left.getLastId();
            content = content.splice(offset);
            length -= offset;
        }

        if (parent != null) {
            if ((left == null && (right == null || right.left != null)) ||
                    (left != null && left.right != right)) {

                Item leftTrace = left;
                Item o;

                if (leftTrace != null) {
                    o = leftTrace.right;
                } else if (parentSub != null) {
                    o = ((AbstractType<?>) parent)._map.get(parentSub);
                    while (o != null && o.left != null) o = o.left;
                } else {
                    o = ((AbstractType<?>) parent)._start;
                }

                Set<Item> conflictingItems = new LinkedHashSet<>();
                Set<Item> itemsBeforeOrigin = new LinkedHashSet<>();

                while (o != null && o != right) {
                    itemsBeforeOrigin.add(o);
                    conflictingItems.add(o);

                    if (ID.compareIDs(origin, o.origin)) {
                        // case 1
                        if (o.id.client < id.client) {
                            leftTrace = o;
                            conflictingItems.clear();
                        } else if (ID.compareIDs(rightOrigin, o.rightOrigin)) {
                            // this and o are conflicting and point to the same integration points
                            break;
                        }
                    } else if (o.origin != null && itemsBeforeOrigin.contains(StructStore.getItem(transaction.doc.store, o.origin))) {
                        // case 2
                        if (!conflictingItems.contains(StructStore.getItem(transaction.doc.store, o.origin))) {
                            leftTrace = o;
                            conflictingItems.clear();
                        }
                    } else {
                        break;
                    }
                    o = o.right;
                }
                left = leftTrace;
            }

            // reconnect left/right + update parent map/start if necessary
            if (left != null) {
                Item rightItem = left.right;
                right = rightItem;
                left.right = this;
            } else {
                Item r;
                if (parentSub != null) {
                    r = ((AbstractType<?>) parent)._map.get(parentSub);
                    while (r != null && r.left != null) r = r.left;
                } else {
                    r = ((AbstractType<?>) parent)._start;
                    ((AbstractType<?>) parent)._start = this;
                }
                right = r;
            }

            if (right != null) {
                right.left = this;
            } else if (parentSub != null) {
                // set as current parent value if right === null and this is parentSub
                ((AbstractType<?>) parent)._map.put(parentSub, this);
                if (left != null) {
                    // this is the current attribute value of parent. delete right
                    left.delete(transaction);
                }
            }

            // adjust length of parent
            if (parentSub == null && countable() && !deleted()) {
                ((AbstractType<?>) parent)._length += length;
            }

            StructStore.addStruct(transaction.doc.store, this);
            content.integrate(transaction, this);
            // add parent to transaction.changed
            Transaction.addChangedTypeToTransaction(transaction, (AbstractType<?>) parent, parentSub);

            if ((((AbstractType<?>) parent)._item != null &&
                    ((AbstractType<?>) parent)._item.deleted()) ||
                    (parentSub != null && right != null)) {
                // delete if parent is deleted or if this is not the current attribute value of parent
                delete(transaction);
            }
        } else {
            // parent is not defined. Integrate GC struct instead
            new GC(id, length).integrate(transaction, 0);
        }
    }

    /**
     * Try to merge two items
     */
    public boolean mergeWith(Item rightItem) {
        if (getClass() != rightItem.getClass() ||
                !ID.compareIDs(rightItem.origin, getLastId()) ||
                right != rightItem ||
                !ID.compareIDs(rightOrigin, rightItem.rightOrigin) ||
                id.client != rightItem.id.client ||
                id.clock + length != rightItem.id.clock ||
                deleted() != rightItem.deleted() ||
                redone != null ||
                rightItem.redone != null ||
                content.getClass() != rightItem.content.getClass() ||
                !content.mergeWith(rightItem.content)) {
            return false;
        }

        List<ArraySearchMarker> searchMarker = ((AbstractType<?>) parent)._searchMarker;
        if (searchMarker != null) {
            for (ArraySearchMarker marker : searchMarker) {
                if (marker.p == rightItem) {
                    // right is going to be "forgotten" so we need to update the marker
                    marker.p = this;
                    // adjust marker index
                    if (!deleted() && countable()) {
                        marker.index -= length;
                    }
                }
            }
        }

        if (rightItem.keep()) {
            setKeep(true);
        }

        right = rightItem.right;
        if (right != null) {
            right.left = this;
        }
        length += rightItem.length;
        return true;
    }

    /**
     * Mark this Item as deleted.
     */
    @Override
    public void delete(Transaction transaction) {
        if (!deleted()) {
            AbstractType<?> parent = (AbstractType<?>) this.parent;
            // adjust the length of parent
            if (countable() && parentSub == null) {
                parent._length -= length;
            }
            markDeleted();
            DeleteSet.addToDeleteSet(transaction.deleteSet, id.client, id.clock, length);
            Transaction.addChangedTypeToTransaction(transaction, parent, parentSub);
            content.delete(transaction);
        }
    }

    public void gc(StructStore store, boolean parentGCd) {
        if (!deleted()) {
            throw new RuntimeException("Unexpected case");
        }
        content.gc(store);
        if (parentGCd) {
            store.replaceStruct(store, this, new GC(id, length));
        } else {
            content = new ContentDeleted(length);
        }
    }

    @Override
    public void write(UpdateEncoder encoder, int offset, Integer encodingRef) {
        ID origin = offset > 0 ? new ID(id.client, id.clock + offset - 1) : this.origin;
        ID rightOrigin = this.rightOrigin;
        String parentSub = this.parentSub;

        int info = (content.getRef() & binary.BITS5) |
                (origin == null ? 0 : binary.BIT8) | // origin is defined
                (rightOrigin == null ? 0 : binary.BIT7) | // right origin is defined
                (parentSub == null ? 0 : binary.BIT6); // parentSub is non-null

        encoder.writeInfo(info);
        if (origin != null) encoder.writeLeftID(origin);
        if (rightOrigin != null) encoder.writeRightID(rightOrigin);

        if (origin == null && rightOrigin == null) {
            Object parent = this.parent;
            if (parent instanceof AbstractType && ((AbstractType<?>) parent)._item != AbstractType.UNDEFINED_ITEM) {
                Item parentItem = ((AbstractType<?>) parent)._item;
                if (parentItem == null) {
                    // parent type on y._map
                    // find the correct key
                    String ykey = findRootTypeKey(((AbstractType<?>) parent));
                    encoder.writeParentInfo(true); // write parentYKey
                    encoder.writeString(ykey);
                } else {
                    encoder.writeParentInfo(false); // write parent id
                    encoder.writeLeftID(parentItem.id);
                }
            } else if (parent instanceof String) { // this edge case was added by differential updates
                encoder.writeParentInfo(true); // write parentYKey
                encoder.writeString((String) parent);
            } else if (parent instanceof ID) {
                encoder.writeParentInfo(false); // write parent id
                encoder.writeLeftID((ID) parent);
            } else {
                throw new RuntimeException("Unexpected case");
            }
            if (parentSub != null) {
                encoder.writeString(parentSub);
            }
        }
        content.write(encoder, offset);
    }

    // 定义类型读取方法的引用列表
    private static final List<Function<UpdateDecoder, AbstractContent>> contentRefs = Arrays.asList(
            decoder -> { throw new RuntimeException("GC is not ItemContent"); }, // 0 - GC placeholder
        ContentDeleted::readContentDeleted, // 1
        ContentJSON::readContentJSON, // 2
        ContentBinary::readContentBinary, // 3
        ContentString::readContentString, // 4
        ContentEmbed::readContentEmbed, // 5
        ContentFormat::readContentFormat, // 6
        ContentType::readContentType, // 7
        ContentAny::readContentAny, // 8
        ContentDoc::readContentDoc, // 9
        decoder -> { throw new RuntimeException("Skip is not ItemContent"); } // 10 - Skip is not ItemContent
    );

    private static int counter = 1;

    public static AbstractContent readItemContent(UpdateDecoder decoder, int info) {
        // export const readItemContent = (decoder, info) => contentRefs[info & binary.BITS5](decoder)
        return contentRefs.get(info& binary.BITS5).apply(decoder);
    }

    /**
     * 跟踪重做项
     *
     * @param store 结构存储
     * @param id    起始ID
     * @return 包含找到的项和差异的对象
     */
    public static ItemDiffResult followRedone(StructStore store, ID id) {
        ID nextID = id;
        int diff = 0;
        Item item;

        do {
            if (diff > 0) {
                nextID = ID.createID(nextID.client, nextID.clock + diff);
            }
            item = (Item) StructStore.getItem(store, nextID);
            diff = nextID.clock - item.id.clock;
            nextID = item.redone;
        } while (nextID != null && item instanceof Item);

        return new ItemDiffResult(item, diff);
    }

    /**
     * 设置项及其所有父项的保持状态
     *
     * @param item 起始项
     * @param keep 是否保持
     */
    public static void keepItem(Item item, boolean keep) {
        while (item != null && item.keep() != keep) {
            item.setKeep(keep);
            if (item.parent instanceof AbstractType) {
                item = ((AbstractType<?>) item.parent)._item;
            } else {
                item = null;
            }
        }
    }

    /**
     * 分割项
     *
     * @param transaction 当前事务
     * @param leftItem    要分割的左侧项
     * @param diff        分割位置
     * @return 新创建的右侧项
     */
    public static Item splitItem(Transaction transaction, Item leftItem, int diff) {
        // 创建右侧项
        ID leftId = leftItem.id;
        Item rightItem = new Item(
                ID.createID(leftId.client, leftId.clock + diff),
                leftItem,
                ID.createID(leftId.client, leftId.clock + diff - 1),
                leftItem.right,
                leftItem.rightOrigin,
                leftItem.parent,
                leftItem.parentSub,
                leftItem.content.splice(diff)
        );

        // 复制属性
        if (leftItem.deleted()) {
            rightItem.markDeleted();
        }
        if (leftItem.keep()) {
            rightItem.setKeep(true);
        }
        if (leftItem.redone != null) {
            rightItem.redone = ID.createID(leftItem.redone.client, leftItem.redone.clock + diff);
        }

        // 更新左侧项
        leftItem.right = rightItem;

        // 更新右侧项的右侧项
        if (rightItem.right != null) {
            rightItem.right.left = rightItem;
        }

        // 将右侧项添加到合并结构列表
        transaction.mergeStructs.add(rightItem);

        // 更新父项的映射
        if (rightItem.parentSub != null && rightItem.right == null) {
            ((AbstractType<?>) rightItem.parent)._map.put(rightItem.parentSub, rightItem);
        }

        leftItem.length = diff;
        return rightItem;
    }

    /**
     * 检查项是否被撤销栈删除
     *
     * @param stack 撤销栈
     * @param id    项ID
     * @return 是否被删除
     */
    private static boolean isDeletedByUndoStack(List<StackItem> stack, ID id) {
        return stack.stream().anyMatch(s -> DeleteSet.isDeleted(s.deletions, id));
    }

    /**
     * 重做项操作
     *
     * @param transaction            当前事务
     * @param item                   要重做的项
     * @param redoitems              重做项集合
     * @param itemsToDelete          待删除项集合
     * @param ignoreRemoteMapChanges 是否忽略远程映射变更
     * @param um                     撤销管理器
     * @return 重做后的项，如果无法重做则返回null
     */
    public static Item redoItem(Transaction transaction,
                                Item item,
                                Set<Item> redoitems,
                                DeleteSet itemsToDelete,
                                boolean ignoreRemoteMapChanges,
                                UndoManager um) {
        Doc doc = transaction.doc;
        StructStore store = doc.store;
        int ownClientID = doc.clientID;

        // 如果已经重做过，直接返回
        if (item.redone != null) {
            return (Item) StructStore.getItemCleanStart(transaction, item.redone);
        }

        if (!(item.parent instanceof AbstractType)) {
            return null;
        }
        Item parentItem = ((AbstractType<?>) item.parent)._item;

        // 确保父项也被重做
        if (parentItem != null && parentItem.deleted()) {
            if (parentItem.redone == null &&
                    (!redoitems.contains(parentItem) ||
                            redoItem(transaction, parentItem, redoitems, itemsToDelete, ignoreRemoteMapChanges, um) == null)) {
                return null;
            }
            while (parentItem.redone != null) {
                parentItem = (Item) StructStore.getItemCleanStart(transaction, parentItem.redone);
            }
        }
        AbstractType<?>  parentType = parentItem == null ? (AbstractType<?>) item.parent : ((ContentType) parentItem.content).type;

        Item left = null;
        Item right = null;

        if (item.parentSub == null) {
            // 数组项，插入到原位置
            left = item.left;
            right = item;

            // 查找左侧的重做项
            while (left != null) {
                Item leftTrace = left;
                // 跟踪重做直到父项匹配
                while (leftTrace != null &&
                        leftTrace.parent instanceof AbstractType &&
                        ((AbstractType<?>) leftTrace.parent)._item != parentItem) {
                    leftTrace = leftTrace.redone == null ? null : (Item) StructStore.getItemCleanStart(transaction, leftTrace.redone);
                }
                if (leftTrace != null &&
                        leftTrace.parent instanceof AbstractType &&
                        ((AbstractType<?>) leftTrace.parent)._item == parentItem) {
                    left = leftTrace;
                    break;
                }
                left = left.left;
            }

            // 查找右侧的重做项
            while (right != null) {
                Item rightTrace = right;
                // 跟踪重做直到父项匹配
                while (rightTrace != null &&
                        rightTrace.parent instanceof AbstractType &&
                        ((AbstractType<?>) rightTrace.parent)._item != parentItem) {
                    rightTrace = rightTrace.redone == null ? null : (Item) StructStore.getItemCleanStart(transaction, rightTrace.redone);
                }
                if (rightTrace != null &&
                        rightTrace.parent instanceof AbstractType &&
                        ((AbstractType<?>) rightTrace.parent)._item == parentItem) {
                    right = rightTrace;
                    break;
                }
                right = right.right;
            }
        } else {
            // 映射项
            right = null;
            if (item.right != null && !ignoreRemoteMapChanges) {
                left = item;
                // 迭代右侧项，当右侧项在待删除集合中时
                while (left != null && left.right != null &&
                        (left.right.redone != null ||
                                DeleteSet.isDeleted(itemsToDelete, left.right.id) ||
                                isDeletedByUndoStack(um.undoStack, left.right.id) ||
                                isDeletedByUndoStack(um.redoStack, left.right.id))) {
                    left = left.right;
                    // 跟随重做
                    while (left.redone != null) {
                        left = (Item) StructStore.getItemCleanStart(transaction, left.redone);
                    }
                }
                if (left.right != null) {
                    // 由于与其他客户端的变更冲突，无法重做此项
                    return null;
                }
            } else {
                left = parentType._map.getOrDefault(item.parentSub, null);
            }
        }

        // 创建重做项
        int nextClock = StructStore.getState(store, ownClientID);
        ID nextId = ID.createID(ownClientID, nextClock);
        Item redoneItem = new Item(
                nextId,
                left, left != null ? left.getLastId() : null,
                right, right != null ? right.id : null,
                parentType,
                item.parentSub,
                item.content.copy()
        );
        item.redone = nextId;
        keepItem(redoneItem, true);
        redoneItem.integrate(transaction, 0);
        return redoneItem;
    }

}

