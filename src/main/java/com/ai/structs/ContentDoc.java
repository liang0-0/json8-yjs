package com.ai.structs;

import com.ai.structs.item.AbstractContent;
import com.ai.structs.item.Item;
import com.ai.utils.Doc;
import com.ai.utils.DocOptions;
import com.ai.utils.structstore.StructStore;
import com.ai.utils.Transaction;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.encoder.UpdateEncoder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class ContentDoc extends AbstractContent {
    public Doc doc;
    public Map<String, Object> opts;

    public ContentDoc(Doc doc) {
        if (doc._item != null) {
            System.err.println("This document was already integrated as a sub-document. You should create a second instance instead with the same guid.");
        }
        this.doc = doc;
        this.opts = new HashMap<>();

        if (!doc.gc) {
            opts.put("gc", false);
        }
        if (doc.autoLoad) {
            opts.put("autoLoad", true);
        }
        if (doc.meta != null) {
            opts.put("meta", doc.meta);
        }
    }

    public int getLength() {
        return 1;
    }

    @Override
    public List<Object> getContent() {
        return Collections.singletonList(doc);
    }

    public boolean isCountable() {
        return true;
    }

    public ContentDoc copy() {
        return new ContentDoc(createDocFromOpts(doc.guid, opts));
    }

    public ContentDoc splice(int offset) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean mergeWith(AbstractContent right) {
        return false;
    }

    public void integrate(Transaction transaction, Item item) {
        doc._item = item;
        transaction.subdocsAdded.add(doc);
        if (doc.shouldLoad) {
            transaction.subdocsLoaded.add(doc);
        }
    }

    public void delete(Transaction transaction) {
        if (transaction.subdocsAdded.contains(doc)) {
            transaction.subdocsAdded.remove(doc);
        } else {
            transaction.subdocsRemoved.add(doc);
        }
    }

    public void gc(StructStore store) {
    }

    public void write(UpdateEncoder encoder, int offset) {
        encoder.writeString(doc.guid);
        encoder.writeAny(opts);
    }

    public int getRef() {
        return 9;
    }

    public static Doc createDocFromOpts(String guid, Map<String, Object> opts) {
        // 创建合并后的选项Map
        Map<String, Object> mergedOpts = new HashMap<>();

        // 添加所有传入选项（如果opts不为null）
        if (opts != null) {
            mergedOpts.putAll(opts);
        }

        // 设置guid和shouldLoad值（按照JS的||逻辑顺序）
        mergedOpts.put("guid", guid);

        // 处理shouldLoad/autoLoad逻辑
        boolean shouldLoad = false;
        if (mergedOpts.containsKey("shouldLoad")) {
            shouldLoad = toBoolean(mergedOpts.get("shouldLoad"));
        } else if (mergedOpts.containsKey("autoLoad")) {
            shouldLoad = toBoolean(mergedOpts.get("autoLoad"));
        }
        mergedOpts.put("shouldLoad", shouldLoad);

        // 转换为DocOpts对象
        DocOptions docOptions = new DocOptions().setGuid(guid);
        if (mergedOpts.containsKey("gc")) docOptions.gc = toBoolean(mergedOpts.get("gc"));
        if (mergedOpts.containsKey("gcFilter")) docOptions.gcFilter = (Predicate<Item>) mergedOpts.get("gcFilter");
        //noinspection SpellCheckingInspection
        if (mergedOpts.containsKey("collectionid"))
            //noinspection SpellCheckingInspection
            docOptions.collectionid = mergedOpts.get("collectionid").toString();
        if (mergedOpts.containsKey("meta")) docOptions.meta = mergedOpts.get("meta");
        if (mergedOpts.containsKey("autoLoad")) docOptions.autoLoad = toBoolean(mergedOpts.get("autoLoad"));
        docOptions.shouldLoad = shouldLoad;

        return new Doc(docOptions);
    }

    /**
     * 安全地将各种类型的值转换为布尔值
     * 支持多种常见格式和特殊字符串表示
     *
     * @param value 要转换的值，可以是多种类型
     * @return 转换后的布尔值
     */
    private static boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }

        // 处理字符串类型的特殊值
        if (value instanceof String) {
            String strValue = ((String) value).trim().toLowerCase();
            switch (strValue) {
                case "true":
                case "1":
                    return true;
                case "false":
                case "0":
                case "null":
                case "undefined":
                    return false;
                default:
                    // 非空字符串默认返回true
                    return !strValue.isEmpty();
            }
        }

        // 处理数值类型
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0;
        }

        // 处理布尔类型
        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        return true;
    }

    public static ContentDoc readContentDoc(UpdateDecoder decoder) {
        return new ContentDoc(createDocFromOpts(decoder.readString(), (Map<String, Object>) decoder.readAny()));
    }
}