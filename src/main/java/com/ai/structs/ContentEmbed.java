package com.ai.structs;

import com.ai.structs.item.AbstractContent;
import com.ai.structs.item.Item;
import com.ai.utils.structstore.StructStore;
import com.ai.utils.Transaction;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.encoder.UpdateEncoder;

import java.util.Collections;
import java.util.List;

public class ContentEmbed extends AbstractContent {
    public Object embed;

    public ContentEmbed(Object embed) {
        this.embed = embed;
    }

    public int getLength() {
        return 1;
    }

    public List<Object> getContent() {
        return Collections.singletonList(embed);
    }

    public boolean isCountable() {
        return true;
    }

    public ContentEmbed copy() {
        return new ContentEmbed(embed);
    }

    public ContentEmbed splice(int offset) {
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
        encoder.writeJSON(embed);
    }

    public int getRef() {
        return 5;
    }

    public static ContentEmbed readContentEmbed(UpdateDecoder decoder) {
        return new ContentEmbed(decoder.readJSON());
    }
}