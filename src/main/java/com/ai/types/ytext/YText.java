package com.ai.types.ytext;

import com.ai.myutils.Maps;
import com.ai.structs.*;
import com.ai.structs.item.AbstractContent;
import com.ai.structs.item.Item;
import com.ai.types.ID;
import com.ai.types.arraytype.AbstractType;
import com.ai.types.arraytype.ArraySearchMarker;
import com.ai.utils.*;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.encoder.UpdateEncoder;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.collections4.MapUtils;

import java.util.*;
import java.util.function.BiFunction;

import static com.ai.structs.ContentType.YTextRefID;
import static com.ai.types.ytext.ItemTextListPosition.updateCurrentAttributes;
import static com.ai.utils.Snapshot.isVisible;
import static com.ai.utils.Snapshot.splitSnapshotAffectedStructs;

/**
 * 文本类型实现，支持格式化文本和嵌入内容
 */
public class YText extends AbstractType<YTextEvent> {

    private List<Runnable> _pending;
    public List<ArraySearchMarker> _searchMarker;
    public boolean _hasFormatting;

    /**
     * 构造空文本
     */
    public YText() {
        this(null);
    }

    /**
     * 使用初始字符串构造文本
     *
     * @param initialText 初始文本内容
     */
    public YText(String initialText) {
        super();
        this._pending = initialText != null ?
                Collections.singletonList(() -> this.insert(0, initialText, null)) :
                new ArrayList<>();
        this._searchMarker = new ArrayList<>();
        this._hasFormatting = false;
    }

    /**
     * 获取文本长度
     *
     * @return 文本字符数
     */
    public int length() {
        if (this.doc == null) warnPrematureAccess();
        return this._length;
    }

    @Override
    public void _integrate(Doc y, Item item) {
        super._integrate(y, item);
        try {
            this._pending.forEach(Runnable::run);
        } catch (Exception e) {
            e.printStackTrace();
        }
        this._pending = null;
    }

    @Override
    public YText _copy() {
        return new YText();
    }

    @Override
    public YText clone() {
        YText text = new YText();
        text.applyDelta(this.toDelta());
        return text;
    }

    @Override
    public void _callObserver(Transaction transaction, Set<String> parentSubs) {
        super._callObserver(transaction, parentSubs);
        YTextEvent event = new YTextEvent(this, transaction, parentSubs);
        callTypeObservers(this, transaction, event);
        if (!transaction.local && this._hasFormatting) {
            transaction.needFormattingCleanup = true;
        }
    }

    /**
     * 获取纯文本内容
     *
     * @return 无格式文本字符串
     */
    @Override
    public String toString() {
        if (this.doc == null) warnPrematureAccess();
        StringBuilder str = new StringBuilder();
        Item n = this._start;
        while (n != null) {
            if (!n.deleted() && n.countable() && n.content instanceof ContentString) {
                str.append(((ContentString) n.content).str);
            }
            n = n.right;
        }
        return str.toString();
    }

    @Override
    public String toJSON() {
        return this.toString();
    }

    public void applyDelta(List<JSONObject> delta) {
        this.applyDelta(delta, new JSONObject());
    }

    /**
     * 应用Delta格式的变更
     *
     * @param delta 变更操作列表
     * @param opts  选项，如是否清理输入
     */
    public void applyDelta(List<JSONObject> delta, JSONObject opts) {
        boolean sanitize = MapUtils.getBoolean(opts, "sanitize", true);

        if (this.doc != null) {
            Transaction.transact(this.doc, transaction -> {
                ItemTextListPosition currPos = new ItemTextListPosition(null, this._start, 0, new HashMap<>());
                for (int i = 0; i < delta.size(); i++) {
                    Map<String, Object> op = delta.get(i);
                    if (op.containsKey("insert")) {
                        Object insert = op.get("insert");
                        // 处理Quill的特殊换行情况
                        if (sanitize && insert instanceof String && i == delta.size() - 1
                                && currPos.right == null && ((String) insert).endsWith("\n")) {
                            insert = ((String) insert).substring(0, ((String) insert).length() - 1);
                        }
                        if (!(insert instanceof String) || !((String) insert).isEmpty()) {
                            Map<String, Object> attrs = (Map<String, Object>) op.getOrDefault("attributes", new HashMap<>());
                            YTextUtils.insertText(transaction, this, currPos, insert, attrs);
                        }
                    } else if (op.containsKey("retain")) {
                        int retain = (Integer) op.get("retain");
                        Map<String, Object> attrs = (Map<String, Object>) op.getOrDefault("attributes", new HashMap<>());
                        YTextUtils.formatText(transaction, this, currPos, retain, attrs);
                    } else if (op.containsKey("delete")) {
                        YTextUtils.deleteText(transaction, currPos, (Integer) op.get("delete"));
                    }
                }
                return null;
            });
        } else {
            this._pending.add(() -> this.applyDelta(delta, new JSONObject()));
        }
    }

    public List<JSONObject> toDelta() {
        return this.toDelta(null, null, null);
    }

    /**
     * 返回此YText类型的Delta表示
     *
     * @param snapshot       当前快照（可选）
     * @param prevSnapshot   先前快照（可选）
     * @param computeYChange 计算YChange的函数（可选）
     * @return Delta操作列表
     */
    public List<JSONObject> toDelta(Snapshot snapshot, Snapshot prevSnapshot, BiFunction<String, ID, Object> computeYChange) {
        if (this.doc == null) {
            warnPrematureAccess();
        }

        List<JSONObject> ops = new ArrayList<>();
        Map<String, Object> currentAttributes = new HashMap<>();
        Doc doc = this.doc;
        StringBuilder str = new StringBuilder();
        final Item[] ns = {this._start};

        // 内部方法：打包字符串到操作
        Runnable packStr = () -> {
            if (str.length() > 0) {
                JSONObject op = new JSONObject();
                op.put("insert", str.toString());

                if (!currentAttributes.isEmpty()) {
                    op.put("attributes", new HashMap<>(currentAttributes));
                }

                ops.add(op);
                str.setLength(0);
            }
        };

        // 内部方法：计算Delta
        Runnable computeDelta = () -> {
            Item n = ns[0];
            while (n != null) {
                if (isVisible(n, snapshot) || (prevSnapshot != null && isVisible(n, prevSnapshot))) {
                    AbstractContent content = n.content;

                    if (content instanceof ContentString) {
                        Map<String, Object> cur = (Map<String, Object>) currentAttributes.get("ychange");

                        if (snapshot != null && !isVisible(n, snapshot)) {
                            if (cur == null || !cur.get("user").equals(n.id.client) || !"removed".equals(cur.get("type"))) {
                                packStr.run();
                                Object ychange = computeYChange != null ? computeYChange.apply("removed", n.id) : Maps.of("type", "removed");
                                currentAttributes.put("ychange", ychange);
                            }
                        } else if (prevSnapshot != null && !isVisible(n, prevSnapshot)) {
                            if (cur == null || !cur.get("user").equals(n.id.client) || !"added".equals(cur.get("type"))) {
                                packStr.run();
                                Object ychange = computeYChange != null ? computeYChange.apply("added", n.id) : Maps.of("type", "removed");
                                currentAttributes.put("ychange", ychange);
                            }
                        } else if (cur != null) {
                            packStr.run();
                            currentAttributes.remove("ychange");
                        }

                        str.append(((ContentString) content).str);
                    } else if (content instanceof ContentType || content instanceof ContentEmbed) {
                        packStr.run();
                        JSONObject op = new JSONObject();
                        op.put("insert", content.getContent().get(0));

                        if (!currentAttributes.isEmpty()) {
                            op.put("attributes", new HashMap<>(currentAttributes));
                        }

                        ops.add(op);
                    } else if (content instanceof ContentFormat && isVisible(n, snapshot)) {
                        packStr.run();
                        updateCurrentAttributes(currentAttributes, (ContentFormat) content);
                    }
                }
                n = n.right;
            }
            packStr.run();
        };

        if (snapshot != null || prevSnapshot != null) {
            Transaction.transact(doc, transaction -> {
                if (snapshot != null) {
                    splitSnapshotAffectedStructs(transaction, snapshot);
                }
                if (prevSnapshot != null) {
                    splitSnapshotAffectedStructs(transaction, prevSnapshot);
                }
                computeDelta.run();
                return null;
            }, "cleanup");
        } else {
            computeDelta.run();
        }
        return ops;
    }

    public void insert(int index, String text) {
        insert(index, text, null);
    }

    /**
     * Insert text at a given index.
     *
     * @param {number}         index The index at which to start inserting.
     * @param {String}         text The text to insert at the specified position.
     * @param {TextAttributes} [attributes] Optionally define some formatting
     *                         information to apply on the inserted
     *                         Text.
     * @public
     */
    public void insert(int index, String text, final Map<String, Object> attributes) {
        if (text == null || text.isEmpty()) return;

        Doc y = this.doc;
        if (y != null) {
            Transaction.transact(y, transaction -> {
                ItemTextListPosition pos = ItemTextListPosition.findPosition(transaction, this, index, attributes == null);
                Map<String, Object> attributesMap = attributes;
                if (attributesMap == null) {
                    attributesMap = new HashMap<>(pos.currentAttributes);
                }
                YTextUtils.insertText(transaction, this, pos, text, attributesMap);
                return null;
            });
        } else {
            this._pending.add(() -> this.insert(index, text, attributes));
        }
    }

    public void insertEmbed(int index, Object embed, Map<String, Object> attributes) {
        Doc y = this.doc;
        if (y != null) {
            Transaction.transact(y, transaction -> {
                ItemTextListPosition pos = ItemTextListPosition.findPosition(transaction, this, index, attributes == null);
                YTextUtils.insertText(transaction, this, pos, embed, attributes != null ? attributes : new HashMap<>());
                return null;
            });
        } else {
            this._pending.add(() -> this.insertEmbed(index, embed,
                    attributes != null ? attributes : new HashMap<>()));
        }
    }


    public void delete(int index, int length) {
        if (length == 0) return;

        Doc y = this.doc;
        if (y != null) {
            Transaction.transact(y, transaction -> {
                YTextUtils.deleteText(transaction, ItemTextListPosition.findPosition(transaction, this, index, true), length);
                return null;
            });
        } else {
            this._pending.add(() -> this.delete(index, length));
        }
    }

    public void format(int index, int length, Map<String, Object> attributes) {
        if (length == 0) return;

        Doc y = this.doc;
        if (y != null) {
            Transaction.transact(y, transaction -> {
                ItemTextListPosition pos = ItemTextListPosition.findPosition(transaction, this, index, false);
                if (pos.right == null) return null;
                YTextUtils.formatText(transaction, this, pos, length, attributes);
                return null;
            });
        } else {
            this._pending.add(() -> this.format(index, length, attributes));
        }
    }

    public void removeAttribute(String attributeName) {
        if (this.doc != null) {
            Transaction.transact(this.doc, transaction -> {
                typeMapDelete(transaction, this, attributeName);
                return null;
            });
        } else {
            this._pending.add(() -> this.removeAttribute(attributeName));
        }
    }

    public void setAttribute(String attributeName, Object attributeValue) {
        if (this.doc != null) {
            Transaction.transact(this.doc, transaction -> {
                typeMapSet(transaction, this, attributeName, attributeValue);
                return null;
            });
        } else {
            this._pending.add(() -> this.setAttribute(attributeName, attributeValue));
        }
    }

    public Object getAttribute(String attributeName) {
        return typeMapGet(this, attributeName);
    }

    public Map<String, Object> getAttributes() {
        return typeMapGetAll(this);
    }

    @Override
    public void _write(UpdateEncoder encoder) {
        encoder.writeTypeRef(YTextRefID);
    }

    public static AbstractType<?> readYText(UpdateDecoder updateDecoder) {
        return new YText();
    }

}

