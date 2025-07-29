package com.ai.structs.item;

import com.ai.utils.structstore.StructStore;
import com.ai.utils.Transaction;
import com.ai.utils.codec.encoder.UpdateEncoder;

import java.util.List;

/**
 * Do not implement this class!
 */
public abstract class AbstractContent {
    /**
     * @return {number}
     */
    public abstract int getLength();

    /**
     * @return {Array<any>}
     */
    public abstract List<Object> getContent();

    /**
     * Should return false if this Item is some kind of meta information
     * (e.g. format information).
     *
     * * Whether this Item should be addressable via `yarray.get(i)`
     * * Whether this Item should be counted when computing yarray.length
     *
     * @return {boolean}
     */
    public abstract boolean isCountable();

    /**
     * @return {AbstractContent}
     */
    public abstract AbstractContent copy();

    /**
     * @param {number} offset
     * @return {AbstractContent}
     */
    public abstract AbstractContent splice(int offset);

    /**
     * @param {AbstractContent} right
     * @return {boolean}
     */
    public abstract boolean mergeWith(AbstractContent right);

    /**
     * @param {Transaction} transaction
     * @param {Item} item
     */
    public abstract void integrate(Transaction transaction, Item item);

    /**
     * @param {Transaction} transaction
     */
    public abstract void delete(Transaction transaction);

    /**
     * @param {StructStore} store
     */
    public abstract void gc(StructStore store);

    /**
     * @param {UpdateEncoder} encoder
     * @param {number} offset
     */
    public abstract void write(UpdateEncoder encoder, int offset);

    /**
     * @return {number}
     */
    public abstract int getRef();
}
