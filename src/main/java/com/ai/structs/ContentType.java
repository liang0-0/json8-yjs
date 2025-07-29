package com.ai.structs;

import com.ai.structs.item.AbstractContent;
import com.ai.structs.item.Item;
import com.ai.types.*;
import com.ai.types.arraytype.AbstractType;
import com.ai.types.ytext.YText;
import com.ai.utils.structstore.StructStore;
import com.ai.utils.Transaction;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.encoder.UpdateEncoder;

import java.util.*;
import java.util.function.Function;

public class ContentType extends AbstractContent {
    public static final int YArrayRefID = 0;
    public static final int YMapRefID = 1;
    public static final int YTextRefID = 2;
    public static final int YXmlElementRefID = 3;
    public static final int YXmlFragmentRefID = 4;
    public static final int YXmlHookRefID = 5;
    public static final int YXmlTextRefID = 6;

    // 定义类型读取方法的引用列表
    private static final List<Function<UpdateDecoder, AbstractType<?>>> typeRefs = Arrays.asList(
            YArray::readYArray,
            YMap::readYMap,
            YText::readYText,
            YXmlElement::readYXmlElement,
            YXmlFragment::readYXmlFragment,
            YXmlHook::readYXmlHook,
            YXmlText::readYXmlText
    );

    public final AbstractType<?> type;

    public ContentType(AbstractType<?> type) {
        this.type = type;
    }

    public int getLength() {
        return 1;
    }

    @Override
    public List<Object> getContent() {
        return Collections.singletonList(type);
    }

    public boolean isCountable() {
        return true;
    }

    public ContentType copy() {
        return new ContentType(type._copy());
    }

    public ContentType splice(int offset) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean mergeWith(AbstractContent right) {
        return false;
    }

    public void integrate(Transaction transaction, Item item) {
        type._integrate(transaction.doc, item);
    }

    public void delete(Transaction transaction) {
        Item item = type._start;
        while (item != null) {
            if (!item.deleted()) {
                item.delete(transaction);
            } else if (item.id.clock < transaction.beforeState.getOrDefault(item.id.client, 0)) {
                transaction.mergeStructs.add(item);
            }
            item = item.right;
        }

        for (Map.Entry<String, Item> entry : type._map.entrySet()) {
            Item mapItem = entry.getValue();
            if (!mapItem.deleted()) {
                mapItem.delete(transaction);
            } else if (mapItem.id.clock < transaction.beforeState.getOrDefault(mapItem.id.client, 0)) {
                transaction.mergeStructs.add(mapItem);
            }
        }
        transaction.changed.remove(type);
    }

    public void gc(StructStore store) {
        Item item = type._start;
        while (item != null) {
            item.gc(store, true);
            item = item.right;
        }
        type._start = null;

        for (Map.Entry<String, Item> entry : type._map.entrySet()) {
            Item mapItem = entry.getValue();
            while (mapItem != null) {
                mapItem.gc(store, true);
                mapItem = mapItem.left;
            }
        }
        type._map = new HashMap<>();
    }

    public void write(UpdateEncoder encoder, int offset) {
        type._write(encoder);
    }

    public int getRef() {
        return 7;
    }

    public static ContentType readContentType(UpdateDecoder decoder) {
        int index = decoder.readTypeRef();
        Function<UpdateDecoder, AbstractType<?>> typeFunction = typeRefs.get(index);
        return new ContentType(typeFunction.apply(decoder));
    }

    @FunctionalInterface
    private interface TypeReader {
        AbstractType<?> read(UpdateDecoder decoder);
    }
}