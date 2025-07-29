package com.ai.structs;

import java.util.ArrayList;
import java.util.List;

import com.ai.structs.item.AbstractContent;
import com.ai.structs.item.Item;
import com.ai.utils.structstore.StructStore;
import com.ai.utils.Transaction;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.encoder.UpdateEncoder;
import com.alibaba.fastjson.JSON;

public class ContentJSON extends AbstractContent {
    public List<Object> arr;

    public ContentJSON(List<Object> arr) {
        this.arr = new ArrayList<>(arr);
    }

    public int getLength() {
        return arr.size();
    }

    public List<Object> getContent() {
        return new ArrayList<>(arr);
    }

    public boolean isCountable() {
        return true;
    }

    public ContentJSON copy() {
        return new ContentJSON(arr);
    }

    public ContentJSON splice(int offset) {
        ContentJSON right = new ContentJSON(arr.subList(offset, arr.size()));
        arr = new ArrayList<>(arr.subList(0, offset));
        return right;
    }

    public boolean mergeWith(AbstractContent right) {
        if (right instanceof ContentJSON) {
            ContentJSON contentJSON = (ContentJSON) right;
            arr.addAll(contentJSON.arr);
            return true;
        }
        return false;
    }

    public void integrate(Transaction transaction, Item item) {}
    public void delete(Transaction transaction) {}
    public void gc(StructStore store) {}

    public void write(UpdateEncoder encoder, int offset) {
        int len = arr.size();
        encoder.writeLen(len - offset);
        for (int i = offset; i < len; i++) {
            Object c = arr.get(i);
            if (c == null) {
                encoder.writeString("null");
            } else {
                encoder.writeString(JSON.toJSONString(c));
            }
        }
    }

    public int getRef() {
        return 2;
    }

    public static ContentJSON readContentJSON(UpdateDecoder decoder) {
        int len = decoder.readLen();
        List<Object> cs = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            String c = decoder.readString();
            if ("undefined".equals(c)) {
                cs.add(null); // Java doesn't have undefined, using null instead
            } else {
                cs.add(JSON.parse(c));
            }
        }
        return new ContentJSON(cs);
    }
}