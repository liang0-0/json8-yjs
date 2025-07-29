package com.ai.structs;

import com.ai.structs.item.AbstractContent;
import com.ai.structs.item.Item;
import com.ai.utils.DeleteSet;
import com.ai.utils.structstore.StructStore;
import com.ai.utils.Transaction;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.encoder.UpdateEncoder;

import java.util.ArrayList;
import java.util.List;

public class ContentDeleted extends AbstractContent {
    private int len;

    public ContentDeleted(int len) {
        this.len = len;
    }

    public int getLength() {
        return len;
    }

    public List<Object> getContent() {
        return new ArrayList<>();
    }

    public boolean isCountable() {
        return false;
    }

    public ContentDeleted copy() {
        return new ContentDeleted(len);
    }

    public ContentDeleted splice(int offset) {
        ContentDeleted right = new ContentDeleted(len - offset);
        len = offset;
        return right;
    }

    public boolean mergeWith(AbstractContent right) {
        len += right.getLength();
        return true;
    }

    public void integrate(Transaction transaction, Item item) {
        DeleteSet.addToDeleteSet(transaction.deleteSet, item.id.client, item.id.clock, len);
        item.markDeleted();
    }

    public void delete(Transaction transaction) {}
    public void gc(StructStore store) {}

    public void write(UpdateEncoder encoder, int offset) {
        encoder.writeLen(len - offset);
    }

    public int getRef() {
        return 1;
    }

    public static ContentDeleted readContentDeleted(UpdateDecoder decoder) {
        return new ContentDeleted(decoder.readLen());
    }
}