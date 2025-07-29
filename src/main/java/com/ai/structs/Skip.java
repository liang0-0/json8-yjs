package com.ai.structs;

import com.ai.myutils.encoder.encoding;
import com.ai.types.ID;
import com.ai.utils.structstore.StructStore;
import com.ai.utils.Transaction;
import com.ai.utils.codec.encoder.UpdateEncoder;

public class Skip extends AbstractStruct {
    public static final int structSkipRefNumber = 10;

    public Skip(ID id, int length) {
        super(id, length);
    }

    @Override
    public boolean deleted() {
        return true;
    }

    @Override
    public void delete(Transaction transaction) {
        // Skip structs cannot be deleted
    }

    public boolean mergeWith(Skip right) {
        if (this.getClass() != right.getClass()) {
            return false;
        }
        this.length += right.length;
        return true;
    }

    @Override
    public void integrate(Transaction transaction, int offset) {
        throw new RuntimeException("Skip structs cannot be integrated");
    }

    @Override
    public void write(UpdateEncoder encoder, int offset, Integer encodingRef) {
        encoder.writeInfo(structSkipRefNumber);
        // write as VarUint because Skips can't make use of predictable length-encoding
        encoding.writeVarUint(encoder.restEncoder, this.length - offset);
    }

    public Integer getMissing(Transaction transaction, StructStore store) {
        return null;
    }
}
