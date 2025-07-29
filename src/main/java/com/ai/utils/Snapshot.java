package com.ai.utils;

import com.ai.myutils.decoder.Decoder;
import com.ai.myutils.decoder.decoding;
import com.ai.myutils.encoder.encoding;
import com.ai.structs.AbstractStruct;
import com.ai.structs.item.Item;
import com.ai.types.vo.LazyStructReader;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.decoder.UpdateDecoderV1;
import com.ai.utils.codec.decoder.UpdateDecoderV2;
import com.ai.utils.codec.encoder.UpdateEncoder;
import com.ai.utils.codec.encoder.UpdateEncoderV1;
import com.ai.utils.codec.encoder.UpdateEncoderV2;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

import static com.ai.types.ID.createID;
import static com.ai.utils.DeleteSet.*;
import static com.ai.utils.Encoding.applyUpdateV2;
import static com.ai.utils.Encoding.readStateVector;
import static com.ai.utils.structstore.StructStore.*;

public class Snapshot {
    public final DeleteSet ds;
    public final Map<Integer, Integer> sv;

    public Snapshot(DeleteSet ds, Map<Integer, Integer> sv) {
        this.ds = ds;
        this.sv = sv;
    }

    public static boolean equalSnapshots(Snapshot snap1, Snapshot snap2) {
        Map<Integer, List<DeleteItem>> ds1 = snap1.ds.clients;
        Map<Integer, List<DeleteItem>> ds2 = snap2.ds.clients;
        Map<Integer, Integer> sv1 = snap1.sv;
        Map<Integer, Integer> sv2 = snap2.sv;

        if (sv1.size() != sv2.size() || ds1.size() != ds2.size()) {
            return false;
        }

        for (Map.Entry<Integer, Integer> entry : sv1.entrySet()) {
            if (!entry.getValue().equals(sv2.get(entry.getKey()))) {
                return false;
            }
        }

        for (Map.Entry<Integer, List<DeleteItem>> entry : ds1.entrySet()) {
            List<DeleteItem> dsitems1 = entry.getValue();
            List<DeleteItem> dsitems2 = ds2.getOrDefault(entry.getKey(), new ArrayList<>());

            if (dsitems1.size() != dsitems2.size()) {
                return false;
            }

            for (int i = 0; i < dsitems1.size(); i++) {
                DeleteItem dsitem1 = dsitems1.get(i);
                DeleteItem dsitem2 = dsitems2.get(i);
                if (dsitem1.clock != dsitem2.clock || dsitem1.len != dsitem2.len) {
                    return false;
                }
            }
        }
        return true;
    }

    public static int[] encodeSnapshotV2(Snapshot snapshot, UpdateEncoder encoder) {
        DeleteSet.writeDeleteSet(encoder, snapshot.ds);
        Encoding.writeStateVector(encoder, snapshot.sv);
        return encoder.toUint8Array();
    }

    public static int[] encodeSnapshot(Snapshot snapshot) {
        return encodeSnapshotV2(snapshot, new UpdateEncoderV1());
    }

    public static Snapshot decodeSnapshotV2(int[] buf, UpdateDecoder decoder) {
        return new Snapshot(readDeleteSet(decoder), readStateVector(decoder));
    }

    public static Snapshot decodeSnapshot(int[] buf) {
        return decodeSnapshotV2(buf, new UpdateDecoderV1(decoding.createDecoder(buf)));
    }

    public static Snapshot createSnapshot(DeleteSet ds, Map<Integer, Integer> sm) {
        return new Snapshot(ds, sm);
    }

    public static final Snapshot emptySnapshot = createSnapshot(createDeleteSet(), new HashMap<>());

    public static Snapshot snapshot(Doc doc) {
        return createSnapshot(createDeleteSetFromStructStore(doc.store), getStateVector(doc.store));
    }

    public static boolean isVisible(Item item, Snapshot snapshot) {
        return snapshot == null
                ? !item.deleted()
                : snapshot.sv.containsKey(item.id.client)
                && (snapshot.sv.get(item.id.client) > item.id.clock
                && !isDeleted(snapshot.ds, item.id));
    }

    public static void splitSnapshotAffectedStructs(Transaction transaction, Snapshot snapshot) {
        Set<Snapshot> meta = (Set<Snapshot>) transaction.meta.computeIfAbsent(
                "splitSnapshotAffectedStructs",
                k -> new LinkedHashSet<>());

        if (!meta.contains(snapshot)) {
            snapshot.sv.forEach((client, clock) -> {
                if (clock < getState(transaction.doc.store, client)) {
                    getItemCleanStart(transaction, createID(client, clock));
                }
            });
            iterateDeletedStructs(transaction, snapshot.ds, item -> {
            });
            meta.add(snapshot);
        }
    }

    public static Doc createDocFromSnapshot(Doc originDoc, Snapshot snapshot, Doc newDoc) {
        if (originDoc.gc) {
            throw new RuntimeException("Garbage-collection must be disabled in originDoc!");
        }

        UpdateEncoder encoder = new UpdateEncoderV2<>();
        originDoc.transact(transaction -> {
            int size = (int) snapshot.sv.values().stream().filter(clock -> clock > 0).count();
            encoding.writeVarUint(encoder.restEncoder, size);

            for (Map.Entry<Integer, Integer> entry : snapshot.sv.entrySet()) {
                int client = entry.getKey();
                int clock = entry.getValue();
                if (clock == 0) continue;

                if (clock < getState(originDoc.store, client)) {
                    getItemCleanStart(transaction, createID(client, clock));
                }

                List<AbstractStruct> structs = originDoc.store.clients.getOrDefault(client, new ArrayList<>());
                int lastStructIndex = findIndexSS(structs, clock - 1);

                encoding.writeVarUint(encoder.restEncoder, lastStructIndex + 1);
                encoder.writeClient(client);
                encoding.writeVarUint(encoder.restEncoder, 0);

                for (int i = 0; i <= lastStructIndex; i++) {
                    structs.get(i).write(encoder, 0, null);
                }
            }
            DeleteSet.writeDeleteSet(encoder, snapshot.ds);
            return null;
        });

        applyUpdateV2(newDoc, encoder.toUint8Array(), "snapshot");
        return newDoc;
    }

    public static boolean snapshotContainsUpdateV2(Snapshot snapshot, int[] update) {
        return snapshotContainsUpdateV2(snapshot, update, UpdateDecoderV2.class);
    }

    public static boolean snapshotContainsUpdateV2(Snapshot snapshot, int[] update, Class<?> YDecoder) {
        List<Item> structs = new ArrayList<>();
        UpdateDecoder updateDecoder = null;
        try {
            updateDecoder = (UpdateDecoder) YDecoder.getConstructor(Decoder.class).newInstance(decoding.createDecoder(update));
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        LazyStructReader lazyDecoder = new LazyStructReader(updateDecoder, false);

        for (Item curr = (Item) lazyDecoder.curr; curr != null; curr = (Item) lazyDecoder.next()) {
            structs.add(curr);
            if ((snapshot.sv.getOrDefault(curr.id.client, 0) < curr.id.clock + curr.length)) {
                return false;
            }
        }

        DeleteSet mergedDS = mergeDeleteSets(Arrays.asList(snapshot.ds, readDeleteSet(updateDecoder)));
        return equalDeleteSets(snapshot.ds, mergedDS);
    }

    public static boolean snapshotContainsUpdate(Snapshot snapshot, int[] update) {
        return snapshotContainsUpdateV2(snapshot, update, UpdateDecoderV1.class);
    }
}