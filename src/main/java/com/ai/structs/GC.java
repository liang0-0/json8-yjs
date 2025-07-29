package com.ai.structs;


import com.ai.types.ID;
import com.ai.utils.structstore.StructStore;
import com.ai.utils.Transaction;
import com.ai.utils.codec.encoder.UpdateEncoder;

public class GC extends AbstractStruct {
    public static final int structGCRefNumber = 0;

    public GC(ID id, int length) {
        super(id, length);
    }

    @Override
    public boolean deleted() {
        return true;
    }

    @Override
    public void delete(Transaction transaction) {}

    @Override
    public boolean mergeWith(AbstractStruct right) {
        if (this.getClass() != right.getClass()) {
            return false;
        }
        this.length += right.length;
        return true;
    }

    @Override
    public void integrate(Transaction transaction, int offset) {
        if (offset > 0) {
            this.id.clock += offset;
            this.length -= offset;
        }
        StructStore.addStruct(transaction.doc.store, this);
    }

    @Override
    public void write(UpdateEncoder encoder, int offset, Integer encodingRef) {
        encoder.writeInfo(structGCRefNumber);
        encoder.writeLen(this.length - offset);
    }

    public Integer getMissing(Transaction transaction, StructStore store) {
        return null;
    }
}