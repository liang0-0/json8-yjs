package com.ai.test.helper;

import com.ai.Y;
import com.ai.myutils.decoder.Decoder;
import com.ai.myutils.decoder.decoding;
import com.ai.myutils.encoder.Encoder;
import com.ai.myutils.encoder.encoding;
import com.ai.utils.Doc;

/**
 * 同步协议工具类
 */
public class syncProtocol {
    public static final int messageYjsSyncStep1 = 0;
    public static final int messageYjsSyncStep2 = 1;
    public static final int messageYjsUpdate = 2;

    public static void writeSyncStep1(Encoder encoder, Doc doc) {
        encoding.writeVarUint(encoder, messageYjsSyncStep1);
        int[] sv = Y.encodeStateVector(doc);
        encoding.writeVarUint8Array(encoder, sv);
    }

    public static void writeSyncStep2(Encoder encoder, Doc doc, int[] encodedStateVector) {
        encoding.writeVarUint(encoder, messageYjsSyncStep2);
        encoding.writeVarUint8Array(encoder, Y.encodeStateAsUpdate(doc, encodedStateVector));
    }

    /**
     * Read SyncStep1 message and reply with SyncStep2.
     *
     * @param decoder {decoding.Decoder}  The reply to the received message
     * @param encoder {encoding.Encoder} The received message
     * @param doc     {Y.Doc}
     */
    public static void readSyncStep1(Decoder decoder, Encoder encoder, Doc doc) {
        writeSyncStep2(encoder, doc, decoding.readVarUint8Array(decoder));
    }

    /**
     * Read and apply Structs and then DeleteStore to a y instance.
     *
     * @param decoder           {decoding.Decoder} The received message
     * @param doc               {Y.Doc}
     * @param transactionOrigin {any}
     */
    public static void readSyncStep2(Decoder decoder, Doc doc, TestConnector transactionOrigin) {
        try {
            Y.applyUpdate(doc, decoding.readVarUint8Array(decoder), transactionOrigin);
        } catch (Exception e) {
            // This catches errors that are thrown by event handlers
            System.err.println("Caught error while handling a Yjs update");
            e.printStackTrace();
        }
    }

    public static void writeUpdate(Encoder encoder, int[] update) {
        encoding.writeVarUint(encoder, messageYjsUpdate);
        encoding.writeVarUint8Array(encoder, update);
    }

    public static void readUpdate(Decoder decoder, Doc doc, TestConnector transactionOrigin) {
        readSyncStep2(decoder, doc, transactionOrigin);
    }

    public static void readSyncMessage(Decoder decoder, Encoder encoder, TestYInstance doc, TestConnector transactionOrigin) {
        int messageType = decoding.readVarUint(decoder);
        switch (messageType) {
            case messageYjsSyncStep1:
                readSyncStep1(decoder, encoder, doc);
                break;
            case messageYjsSyncStep2:
                readSyncStep2(decoder, doc, transactionOrigin);
                break;
            case messageYjsUpdate:
                readUpdate(decoder, doc, transactionOrigin);
                break;
            default:
                throw new Error("Unknown message type");
        }
    }
}
