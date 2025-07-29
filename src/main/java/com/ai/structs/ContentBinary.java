package com.ai.structs;

import com.ai.structs.item.AbstractContent;
import com.ai.structs.item.Item;
import com.ai.utils.structstore.StructStore;
import com.ai.utils.Transaction;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.encoder.UpdateEncoder;

import java.util.Collections;
import java.util.List;

public class ContentBinary extends AbstractContent {
    public int[] content;

    public ContentBinary(int[] content) {
        this.content = content;
    }

    public int getLength() {
        return 1;
    }

    public List<Object> getContent() {
        return Collections.singletonList(content);
    }

    public boolean isCountable() {
        return true;
    }

    public ContentBinary copy() {
        return new ContentBinary(content.clone());
    }

    public ContentBinary splice(int offset) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean mergeWith(AbstractContent right) {
        return false;
    }

    public void integrate(Transaction transaction, Item item) {}
    public void delete(Transaction transaction) {}
    public void gc(StructStore store) {}

    public void write(UpdateEncoder encoder, int offset) {
        encoder.writeBuf(content);
    }

    public int getRef() {
        return 3;
    }

    public static ContentBinary readContentBinary(UpdateDecoder decoder) {
        return new ContentBinary(decoder.readBuf());
    }
}