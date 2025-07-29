package com.ai.structs;

import com.ai.structs.item.AbstractContent;
import com.ai.structs.item.Item;
import com.ai.utils.structstore.StructStore;
import com.ai.utils.Transaction;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.encoder.UpdateEncoder;
import lombok.ToString;

import java.util.Arrays;
import java.util.List;

@ToString
public class ContentString extends AbstractContent {
    public String str;

    public ContentString(String str) {
        this.str = str;
    }

    public int getLength() {
        return str.length();
    }

    @Override
    public List<Object> getContent() {
        return Arrays.asList(str.split(""));
    }

    public boolean isCountable() {
        return true;
    }

    public ContentString copy() {
        return new ContentString(str);
    }

    public ContentString splice(int offset) {
        ContentString right = new ContentString(str.substring(offset));
        str = str.substring(0, offset);

        // Handle surrogate pairs
        if (offset > 0) {
            char firstChar = str.charAt(offset - 1);
            if (Character.isHighSurrogate(firstChar)) {
                // Replace invalid split surrogate pairs with replacement character
                str = str.substring(0, offset - 1) + '\uFFFD';
                right.str = '\uFFFD' + right.str.substring(1);
            }
        }
        return right;
    }

    @Override
    public boolean mergeWith(AbstractContent right) {
        str += ((ContentString) right).str;
        return true;
    }

    public void integrate(Transaction transaction, Item item) {}
    public void delete(Transaction transaction) {}
    public void gc(StructStore store) {}

    public void write(UpdateEncoder encoder, int offset) {
        encoder.writeString(offset == 0 ? str : str.substring(offset));
    }

    public int getRef() {
        return 4;
    }

    public static ContentString readContentString(UpdateDecoder decoder) {
        return new ContentString(decoder.readString());
    }
}