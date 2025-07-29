package com.ai.structs;

import com.ai.types.ID;
import com.ai.utils.structstore.StructStore;
import com.ai.utils.Transaction;
import com.ai.utils.codec.encoder.UpdateEncoder;

public abstract class AbstractStruct {
    public ID id;
    public int length;

    public AbstractStruct(ID id, int length) {
        this.id = id;
        this.length = length;
    }

    public abstract boolean deleted();

    public abstract void delete(Transaction transaction);

    /**
   * Merge this struct with the item to the right.
   * This method is already assuming that `this.id.clock + this.length === this.id.clock`.
   * Also this method does *not* remove right from StructStore!
   * @param {AbstractStruct} right
   * @return {boolean} whether this merged with right
   */
    public boolean mergeWith(AbstractStruct right) {
        return false;
    }

    public abstract void write(UpdateEncoder encoder, int offset, Integer encodingRef);

    public abstract void integrate(Transaction transaction, int offset);

    public abstract Integer getMissing(Transaction transaction, StructStore store);
}