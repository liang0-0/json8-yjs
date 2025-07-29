package com.ai.structs;

import com.ai.structs.item.AbstractContent;
import com.ai.structs.item.Item;
import com.ai.utils.structstore.StructStore;
import com.ai.utils.Transaction;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.encoder.UpdateEncoder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ContentAny extends AbstractContent {
    public List<Object> arr;

    public ContentAny(List<Object> arr) {
        this.arr = arr;
    }

    public int getLength() {
        return arr.size();
    }

    public List<Object> getContent() {
        return arr;
    }

    public boolean isCountable() {
        return true;
    }

    public ContentAny copy() {
        return new ContentAny(arr);
    }

    public ContentAny splice(int offset) {
        ContentAny right = new ContentAny(arr.subList(offset, arr.size()));
        arr = arr.subList(0, offset);
        return right;
    }

    @Override
    public boolean mergeWith(AbstractContent right) {
        arr.addAll(((ContentAny) right).arr);
        return true;
    }


    public void integrate(Transaction transaction, Item item) {}
    public void delete(Transaction transaction) {}
    public void gc(StructStore store) {}

    public void write(UpdateEncoder encoder, int offset) {
        int len = arr.size();
        encoder.writeLen(len - offset);
        for (int i = offset; i < len; i++) {
            Object c = arr.get(i);
            encoder.writeAny(c);
        }
    }

    public int getRef() {
        return 8;
    }

    public static ContentAny readContentAny(UpdateDecoder decoder) {
        int len = decoder.readLen();
        List<Object> cs = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            cs.add(i, decoder.readAny());
        }
        return new ContentAny(cs);
    }
}