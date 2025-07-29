package com.ai.types;

import com.ai.myutils.decoder.Decoder;
import com.ai.myutils.decoder.decoding;
import com.ai.myutils.encoder.Encoder;
import com.ai.myutils.encoder.encoding;
import com.ai.types.arraytype.AbstractType;
import com.ai.utils.Doc;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.encoder.UpdateEncoder;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Objects;

@ToString
public class ID {
    public final int client;
    public int clock;

    /**
     * @param client client id
     * @param clock unique per client id, continuous number
     */
    public ID(int client, int clock) {
        this.client = client;
        this.clock = clock;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ID other = (ID) obj;
        return client == other.client && clock == other.clock;
    }

    @Override
    public int hashCode() {
        return Objects.hash(client, clock);
    }

    public static boolean compareIDs(ID a, ID b) {
        return Objects.equals(a, b);
    }

    public static ID createID(int client, int clock) {
        return new ID(client, clock);
    }

    public static void writeID(Encoder encoder, ID id) {
        encoding.writeVarUint(encoder, id.client);
        encoding.writeVarUint(encoder, id.clock);
    }

    public static ID readID(Decoder decoder) {
        return createID(decoding.readVarUint(decoder), decoding.readVarUint(decoder));
    }

    public static String findRootTypeKey(AbstractType<?> type) {
        Doc doc = type.doc;
        if (doc == null) {
            throw new IllegalStateException("Unexpected case: type has no associated document");
        }
        
        for (Map.Entry<String, AbstractType<?>> entry : doc.share.entrySet()) {
            if (entry.getValue() == type) {
                return StringUtils.defaultString(entry.getKey());
            }
        }
        throw new IllegalStateException("Unexpected case: type not found in document share");
    }
}