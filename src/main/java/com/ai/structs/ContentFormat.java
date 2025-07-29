package com.ai.structs;

import com.ai.structs.item.AbstractContent;
import com.ai.structs.item.Item;
import com.ai.types.arraytype.AbstractType;
import com.ai.types.ytext.YText;
import com.ai.utils.structstore.StructStore;
import com.ai.utils.Transaction;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.encoder.UpdateEncoder;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@ToString
public class ContentFormat extends AbstractContent {
    public String key;
    public Object value;

    public ContentFormat(String key, Object value) {
        this.key = key;
        this.value = value;
    }

    public int getLength() {
        return 1;
    }

    @Override
    public List<Object> getContent() {
        return new ArrayList<>();
    }

    public boolean isCountable() {
        return false;
    }

    public ContentFormat copy() {
        return new ContentFormat(key, value);
    }

    public ContentFormat splice(int offset) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean mergeWith(AbstractContent right) {
        return false;
    }

    public void integrate(Transaction transaction, Item item) {
        AbstractType<?> parent = (AbstractType<?>) item.parent;
        if (null != parent._searchMarker) {
            parent._searchMarker.clear();
        }
        if (parent instanceof YText) {
            ((YText) parent)._hasFormatting = true;
        }
    }

    public void delete(Transaction transaction) {}
    public void gc(StructStore store) {}

    public void write(UpdateEncoder encoder, int offset) {
        encoder.writeKey(key);
        encoder.writeJSON(value);
    }

    public int getRef() {
        return 6;
    }

    public static ContentFormat readContentFormat(UpdateDecoder decoder) {
        return new ContentFormat(decoder.readKey(), decoder.readJSON());
    }
}