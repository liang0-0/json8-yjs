package com.ai.types.ytext;

import com.ai.structs.ContentFormat;
import com.ai.structs.item.AbstractContent;
import com.ai.structs.item.Item;
import com.ai.types.ID;
import com.ai.types.arraytype.AbstractType;
import com.ai.types.arraytype.ArraySearchMarker;
import com.ai.utils.Doc;
import com.ai.utils.structstore.StructStore;
import com.ai.utils.Transaction;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ItemTextListPosition {
    public Item left;
    public Item right;
    public int index;
    public Map<String, Object> currentAttributes;

    public ItemTextListPosition(Item left, Item right, int index, Map<String, Object> currentAttributes) {
        this.left = left;
        this.right = right;
        this.index = index;
        this.currentAttributes = currentAttributes;
    }

    // const equalAttrs = (a, b) => a === b || (typeof a === 'object' && typeof b === 'object' && a && b && object.equalFlat(a, b))

    /**
     * 简化版属性比较，直接使用Map.equals()
     *
     * @param a 第一个对象(Map类型)
     * @param b 第二个对象(Map类型)
     * @return 如果对象相等或Map内容相同则返回true
     */
    public static boolean equalAttrs(Object a, Object b) {
        return Objects.equals(a, b);
    }

    /**
     * Only call this if you know that this.right is defined
     */
    public void forward() {
        if (this.right == null) {
            throw new RuntimeException("Unexpected case: right is null");
        }
        AbstractContent content = this.right.content;
        if (content instanceof ContentFormat) {
            if (!this.right.deleted()) {
                updateCurrentAttributes(this.currentAttributes, (ContentFormat) content);
            }
        } else {
            if (!this.right.deleted()) {
                this.index += content.getLength();
            }
        }
        this.left = this.right;
        this.right = this.right.right;
    }

    /**
     * @param transaction The transaction
     * @param pos         The current position
     * @param count       Steps to move forward
     * @return The updated position
     */
    public static ItemTextListPosition findNextPosition(Transaction transaction, ItemTextListPosition pos, int count) {
        while (pos.right != null && count > 0) {
            if (pos.right.content instanceof ContentFormat) {
                if (!pos.right.deleted()) {
                    updateCurrentAttributes(pos.currentAttributes, (ContentFormat) pos.right.content);
                }
            } else {
                if (!pos.right.deleted()) {
                    if (count < pos.right.length) {
                        // split right
                        StructStore.getItemCleanStart(transaction,
                                new ID(pos.right.id.client, pos.right.id.clock + count));
                    }
                    pos.index += pos.right.length;
                    count -= pos.right.length;
                }
            }
            pos.left = pos.right;
            pos.right = pos.right.right;
        }
        return pos;
    }

    /**
     * @param transaction     The transaction
     * @param parent          The parent type
     * @param index           The target index
     * @param useSearchMarker Whether to use search markers
     * @return The found position
     */
    public static ItemTextListPosition findPosition(Transaction transaction, AbstractType<?> parent, int index, boolean useSearchMarker) {
        Map<String, Object> currentAttributes = new HashMap<>();
        ArraySearchMarker marker = useSearchMarker ? ArraySearchMarker.findMarker(parent, index) : null;

        if (marker != null) {
            ItemTextListPosition pos = new ItemTextListPosition(marker.p.left, marker.p, marker.index, currentAttributes);
            return findNextPosition(transaction, pos, index - marker.index);
        } else {
            ItemTextListPosition pos = new ItemTextListPosition(null, parent._start, 0, currentAttributes);
            return findNextPosition(transaction, pos, index);
        }
    }

    /**
     * Negate applied formats
     *
     * @param transaction       The transaction
     * @param parent            The parent type
     * @param currPos           The current position
     * @param negatedAttributes The attributes to negate
     */
    public static void insertNegatedAttributes(Transaction transaction, AbstractType<?> parent,
                                               ItemTextListPosition currPos, Map<String, Object> negatedAttributes) {
        // check if we really need to remove attributes
        while (currPos.right != null &&
                (currPos.right.deleted() ||
                        (currPos.right.content instanceof ContentFormat &&
                                equalAttrs(negatedAttributes.get(((ContentFormat) currPos.right.content).key),
                                        ((ContentFormat) currPos.right.content).value)))) {
            if (!currPos.right.deleted()) {
                negatedAttributes.remove(((ContentFormat) currPos.right.content).key);
            }
            currPos.forward();
        }

        Doc doc = transaction.doc;
        int ownClientId = doc.clientID;
        for (Map.Entry<String, Object> entry : negatedAttributes.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            Item left = currPos.left;
            Item right = currPos.right;
            Item nextFormat = new Item(
                    new ID(ownClientId, StructStore.getState(doc.store, ownClientId)),
                    left,
                    left != null ? left.getLastId() : null,
                    right,
                    right != null ? right.id : null,
                    parent,
                    null,
                    new ContentFormat(key, val));
            nextFormat.integrate(transaction, 0);
            currPos.right = nextFormat;
            currPos.forward();
        }
    }

    /**
     * Update current attributes with format
     *
     * @param currentAttributes The current attributes map
     * @param format            The format to apply
     */
    public static void updateCurrentAttributes(Map<String, Object> currentAttributes, ContentFormat format) {
        String key = format.key;
        Object value = format.value;
        if (value == null) {
            currentAttributes.remove(key);
        } else {
            currentAttributes.put(key, value);
        }
    }

    /**
     * Minimize attribute changes
     *
     * @param currPos    The current position
     * @param attributes The attributes to check
     */
    public static void minimizeAttributeChanges(ItemTextListPosition currPos, Map<String, Object> attributes) {
        while (true) {
            if (currPos.right == null) {
                break;
            } else if (currPos.right.deleted() ||
                    (currPos.right.content instanceof ContentFormat &&
                            equalAttrs(attributes.getOrDefault(((ContentFormat) currPos.right.content).key, null),
                                    ((ContentFormat) currPos.right.content).value))) {
                // continue
            } else {
                break;
            }
            currPos.forward();
        }
    }

    /**
     * Insert attributes
     *
     * @param transaction The transaction
     * @param parent      The parent type
     * @param currPos     The current position
     * @param attributes  The attributes to insert
     * @return The negated attributes
     */
    public static Map<String, Object> insertAttributes(Transaction transaction, AbstractType<?> parent,
                                                       ItemTextListPosition currPos, Map<String, Object> attributes) {
        Doc doc = transaction.doc;
        int ownClientId = doc.clientID;
        Map<String, Object> negatedAttributes = new HashMap<>();

        // insert format-start items
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            Object currentVal = currPos.currentAttributes.getOrDefault(key, null);
            if (!equalAttrs(currentVal, val)) {
                // save negated attribute (set null if currentVal undefined)
                negatedAttributes.put(key, currentVal);
                Item left = currPos.left;
                Item right = currPos.right;
                currPos.right = new Item(
                        new ID(ownClientId, StructStore.getState(doc.store, ownClientId)),
                        left,
                        left != null ? left.getLastId() : null,
                        right,
                        right != null ? right.id : null,
                        parent,
                        null,
                        new ContentFormat(key, val));
                currPos.right.integrate(transaction, 0);
                currPos.forward();
            }
        }
        return negatedAttributes;
    }
}
