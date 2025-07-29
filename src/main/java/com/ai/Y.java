package com.ai;

import com.ai.myutils.Maps;
import com.ai.myutils.decoder.Decoder;
import com.ai.structs.AbstractStruct;
import com.ai.structs.item.Item;
import com.ai.types.*;
import com.ai.types.arraytype.AbstractType;
import com.ai.types.vo.AbsolutePosition;
import com.ai.types.vo.DecodedUpdate;
import com.ai.types.vo.ObfuscatorOptions;
import com.ai.types.vo.UpdateMeta;
import com.ai.types.ytext.YText;
import com.ai.types.ytext.YTextUtils;
import com.ai.utils.*;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.encoder.UpdateEncoder;
import com.ai.utils.structstore.StructStore;
import com.alibaba.fastjson.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public class Y {

    // Singleton check
    private static final String IMPORT_IDENTIFIER = "__ $YJS$ __";
    static {
        if (System.getProperty(IMPORT_IDENTIFIER) != null) {
            System.err.println("Yjs was already imported. This breaks constructor checks and will lead to issues! - " +
                    "https://github.com/yjs/yjs/issues/438");
        }
        System.setProperty(IMPORT_IDENTIFIER, "true");
    }

    // Type methods
    public static List<Item> getTypeChildren(AbstractType type) {
        return AbstractType.getTypeChildren(type);
    }

    // Position methods
    public static RelativePosition createRelativePositionFromTypeIndex(AbstractType type, int index) {
        return RelativePosition.createRelativePositionFromTypeIndex(type, index, 0);
    }
    public static RelativePosition createRelativePositionFromTypeIndex(AbstractType type, int index, int assoc) {
        return RelativePosition.createRelativePositionFromTypeIndex(type, index, assoc);
    }

    public static RelativePosition createRelativePositionFromJSON(JSONObject json) {
        return RelativePosition.createRelativePositionFromJSON(json);
    }

    public static AbsolutePosition createAbsolutePositionFromRelativePosition(RelativePosition relPos, Doc doc, boolean followUndoneDeletions) {
        return RelativePosition.createAbsolutePositionFromRelativePosition(relPos, doc, followUndoneDeletions);
    }
    public static AbsolutePosition createAbsolutePositionFromRelativePosition(RelativePosition relPos, Doc doc) {
        return RelativePosition.createAbsolutePositionFromRelativePosition(relPos, doc, null);
    }

    public static boolean compareRelativePositions(RelativePosition a, RelativePosition b) {
        return RelativePosition.compareRelativePositions(a, b);
    }

    // ID methods
    public static ID createID(int client, int clock) {
        return new ID(client, clock);
    }

    public static boolean compareIDs(ID a, ID b) {
        return ID.compareIDs(a, b);
    }

    // Snapshot methods
    public static int getState(StructStore store, int client) {
        return StructStore.getState(store, client);
    }

    public static Snapshot createSnapshot(DeleteSet deleteSet, Map<Integer, Integer> stateMap) {
        return new Snapshot(deleteSet, stateMap);
    }

    public static DeleteSet createDeleteSet() {
        return new DeleteSet();
    }

    public static DeleteSet createDeleteSetFromStructStore(StructStore store) {
        return DeleteSet.createDeleteSetFromStructStore(store);
    }

    public static void cleanupYTextFormatting(YText text) {
        YTextUtils.cleanupYTextFormatting(text);
    }

    public static Snapshot snapshot(Doc doc) {
        return Snapshot.snapshot(doc);
    }

    public static Snapshot emptySnapshot() {
        return new Snapshot(new DeleteSet(), Maps.of());
    }

    // Struct methods
    public static String findRootTypeKey(AbstractType type) {
        return ID.findRootTypeKey(type);
    }

    public static int findIndexSS(List<AbstractStruct> structs, long clock) {
        return StructStore.findIndexSS(structs, clock);
    }

    public static AbstractStruct getItem(StructStore store, ID id) {
        return StructStore.getItem(store, id);
    }

    public static AbstractStruct getItemCleanStart(Transaction transaction, ID id) {
        return StructStore.getItemCleanStart(transaction, id);
    }

    public static AbstractStruct getItemCleanEnd(Transaction transaction, StructStore store, ID id) {
        return StructStore.getItemCleanEnd(transaction, store, id);
    }

    // Snapshot conversion methods
    public static List<Object> typeListToArraySnapshot(AbstractType<?> type, Snapshot snapshot) {
        return AbstractType.typeListToArraySnapshot(type, snapshot);
    }

    public static Object typeMapGetSnapshot(AbstractType<?> parent, String key, Snapshot snapshot) {
        return AbstractType.typeMapGetSnapshot(parent, key, snapshot);
    }

    public static Map<String, Object> typeMapGetAllSnapshot(AbstractType<?> parent, Snapshot snapshot) {
        return AbstractType.typeMapGetAllSnapshot(parent, snapshot);
    }

    public static Doc createDocFromSnapshot(Doc originDoc, Snapshot snapshot) {
        return Snapshot.createDocFromSnapshot(originDoc, snapshot, new Doc());
    }
    public static Doc createDocFromSnapshot(Doc originDoc, Snapshot snapshot, Doc newDoc) {
        return Snapshot.createDocFromSnapshot(originDoc, snapshot, newDoc);
    }

    // Update methods
    public static void applyUpdate(Doc ydoc, int[] update) {
        Encoding.applyUpdate(ydoc, update, null);
    }
    public static void applyUpdate(Doc ydoc, int[] update, Object transactionOrigin) {
        Encoding.applyUpdate(ydoc, update, transactionOrigin);
    }

    public static void applyUpdateV2(Doc ydoc, int[] update) {
        Encoding.applyUpdateV2(ydoc, update);
    }

    public static void readUpdate(Decoder decoder, Doc ydoc, Object transactionOrigin) {
        Encoding.readUpdate(decoder, ydoc, transactionOrigin);
    }

    public static void readUpdateV2(Decoder decoder, Doc ydoc, Object transactionOrigin) {
        Encoding.readUpdateV2(decoder, ydoc, transactionOrigin);
    }

    public static int[] encodeStateAsUpdate(Doc doc) {
        return Encoding.encodeStateAsUpdate(doc, new int[]{0});

    }
    public static int[] encodeStateAsUpdate(Doc doc, int[] encodedTargetStateVector) {
        return Encoding.encodeStateAsUpdate(doc, encodedTargetStateVector);
    }

    public static int[] encodeStateAsUpdateV2(Doc doc) {
        return Encoding.encodeStateAsUpdateV2(doc, null, null);
    }
    public static int[] encodeStateAsUpdateV2(Doc doc, int[] encodedTargetStateVector) {
        return Encoding.encodeStateAsUpdateV2(doc, encodedTargetStateVector, null);
    }
    public static int[] encodeStateAsUpdateV2(Doc doc, int[] encodedTargetStateVector, UpdateEncoder encoder) {
        return Encoding.encodeStateAsUpdateV2(doc, encodedTargetStateVector, encoder);

    }

    public static int[] encodeStateVector(Doc doc) {
        return Encoding.encodeStateVector(doc);
    }

    // Snapshot encoding/decoding
    public static Snapshot decodeSnapshot(int[] data) {
        return Snapshot.decodeSnapshot(data);
    }

    public static int[] encodeSnapshot(Snapshot snapshot) {
        return Snapshot.encodeSnapshot(snapshot);
    }

    public static Snapshot decodeSnapshotV2(int[] buf, UpdateDecoder decoder) {
        return Snapshot.decodeSnapshotV2(buf, decoder);
    }

    public static int[] encodeSnapshotV2(Snapshot snapshot, UpdateEncoder encoder) {
        return Snapshot.encodeSnapshotV2(snapshot, encoder);
    }

    public static Map<Integer, Integer> decodeStateVector(int[] data) {
        return Encoding.decodeStateVector(data);
    }

    // Logging methods
    public static void logUpdate(int[] update) {
        Updates.logUpdate(update);
    }

    public static void logUpdateV2(int[] update) {
        Updates.logUpdateV2(update, null);
    }
    public static void logUpdateV2(int[] update, Class<? extends UpdateDecoder> decoderClass) {
        Updates.logUpdateV2(update, decoderClass);
    }

    public static DecodedUpdate decodeUpdate(int[] update) {
        return Updates.decodeUpdate(update);
    }

    public static DecodedUpdate decodeUpdateV2(int[] update, Class<? extends UpdateDecoder> decoderClass) {
        return Updates.decodeUpdateV2(update, decoderClass);
    }

    // Position conversion
    public static JSONObject relativePositionToJSON(RelativePosition pos) {
        return RelativePosition.relativePositionToJSON(pos);
    }

    // Utility methods
    public static boolean isDeleted(DeleteSet ds, ID id) {
        return DeleteSet.isDeleted(ds, id);
    }

    public static boolean isParentOf(AbstractType<?> parent, Item child) {
        return IsParentOf.isParentOf(parent, child);
    }

    public static boolean equalSnapshots(Snapshot a, Snapshot b) {
        return Snapshot.equalSnapshots(a, b);
    }

    // GC methods
    public static void tryGc(DeleteSet ds, StructStore store, Predicate<Item> gcFilter) {
        Transaction.tryGc(ds, store, gcFilter);
    }

    // Transaction methods
    public static <T> void transact(Doc doc, Function<Transaction, T> f) {
        Transaction.transact(doc, f);
    }
    public static <T> void transact(Doc doc, Function<Transaction, T> f, Object origin) {
        Transaction.transact(doc, f, origin);
    }

    // Logging
    public static void logType(AbstractType<?> type) {
        Logging.logType(type);
    }

    // Update merging
    public static int[] mergeUpdates(List<int[]> updates) {
        return Updates.mergeUpdates(updates);
    }

    public static int[] mergeUpdatesV2(List<int[]> updates) {
        return Updates.mergeUpdatesV2(updates);
    }

    // Update metadata
    public static UpdateMeta parseUpdateMeta(int[] update) {
        return Updates.parseUpdateMeta(update);
    }

    public static UpdateMeta parseUpdateMetaV2(int[] update, Class<? extends UpdateDecoder> decoderClass) {
        return Updates.parseUpdateMetaV2(update, decoderClass);
    }

    // State vector from update
    public static int[] encodeStateVectorFromUpdate(int[] update) {
        return Updates.encodeStateVectorFromUpdate(update);
    }

    public static int[] encodeStateVectorFromUpdateV2(int[] update) {
        return Updates.encodeStateVectorFromUpdateV2(update);
    }

    // Relative position encoding
    public static int[] encodeRelativePosition(RelativePosition pos) {
        return RelativePosition.encodeRelativePosition(pos);
    }

    public static RelativePosition decodeRelativePosition(int[] data) {
        return RelativePosition.decodeRelativePosition(data);
    }

    // Update diffing
    public static int[] diffUpdate(int[] update, int[] stateVector) {
        return Updates.diffUpdate(update, stateVector);
    }

    public static int[] diffUpdateV2(int[] update, int[] stateVector) {
        return Updates.diffUpdateV2(update, stateVector);
    }

    // Update format conversion
    public static int[] convertUpdateFormatV1ToV2(int[] update) {
        return Updates.convertUpdateFormatV1ToV2(update);
    }

    public static int[] convertUpdateFormatV2ToV1(int[] update) {
        return Updates.convertUpdateFormatV2ToV1(update);
    }

    // Update obfuscation
    public static int[] obfuscateUpdate(int[] update) {
        return Updates.obfuscateUpdate(update, null);
    }
    public static int[] obfuscateUpdate(int[] update, ObfuscatorOptions opts) {
        return Updates.obfuscateUpdate(update, opts);
    }

    public static int[] obfuscateUpdateV2(int[] update, ObfuscatorOptions opts) {
        return Updates.obfuscateUpdateV2(update, opts);
    }

    // DeleteSet utilities
    public static boolean equalDeleteSets(DeleteSet a, DeleteSet b) {
        return DeleteSet.equalDeleteSets(a, b);
    }

    public static DeleteSet mergeDeleteSets(List<DeleteSet> dss) {
        return DeleteSet.mergeDeleteSets(dss);
    }

    // Snapshot utilities
    public static boolean snapshotContainsUpdate(Snapshot snapshot, int[] update) {
        return Snapshot.snapshotContainsUpdate(snapshot, update);
    }

    private Y() {
        throw new AssertionError("No Y instances for you!");
    }
}