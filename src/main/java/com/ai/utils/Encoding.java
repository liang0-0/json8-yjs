package com.ai.utils;

import com.ai.myutils.Uint8Array;
import com.ai.myutils.binary;
import com.ai.myutils.decoder.Decoder;
import com.ai.myutils.decoder.decoding;
import com.ai.myutils.encoder.encoding;
import com.ai.structs.AbstractStruct;
import com.ai.structs.GC;
import com.ai.structs.Skip;
import com.ai.structs.item.Item;
import com.ai.types.ID;
import com.ai.types.vo.StructRefs;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.decoder.UpdateDecoderV1;
import com.ai.utils.codec.decoder.UpdateDecoderV2;
import com.ai.utils.codec.encoder.*;
import com.ai.utils.structstore.Structs;
import com.ai.utils.structstore.StructStore;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.ObjectUtils;

import java.util.*;
import java.util.stream.Collectors;

import static com.ai.myutils.Printer.print;
import static com.ai.utils.Updates.*;
import static com.ai.utils.structstore.StructStore.findIndexSS;
import static com.ai.utils.structstore.StructStore.getState;

public class Encoding {

    // Constants for info flags
    public static final int GC_FLAG = 0;
    public static final int SKIP_STRUCT = 10;

    /**
     * Writes structs to the encoder starting from the specified clock
     */
    public static void writeStructs(UpdateEncoder encoder, List<AbstractStruct> structs, int client, int clock) {
        // write first id
        clock = Math.max(clock, structs.get(0).id.clock); // make sure the first id exists
        int startNewStructs = findIndexSS(structs, clock);
        // write # encoded structs
        encoding.writeVarUint(encoder.restEncoder, structs.size() - startNewStructs);
        encoder.writeClient(client);
        encoding.writeVarUint(encoder.restEncoder, clock);
        AbstractStruct firstStruct = structs.get(startNewStructs);
        // write first struct with an offset
        firstStruct.write(encoder, clock - firstStruct.id.clock, null);
        for (int i = startNewStructs + 1; i < structs.size(); i++) {
            structs.get(i).write(encoder, 0, null);
        }
    }

    /**
     * Writes client structs to the encoder
     */
    public static void writeClientsStructs(UpdateEncoder encoder, StructStore store, Map<Integer, Integer> _sm) {
        // we filter all valid sm entries into filteredSm
        Map<Integer, Integer> sm = new HashMap<>();
        _sm.forEach((client, clock) -> {
            // only write if new structs are available
            if (StructStore.getState(store, client) > clock) {
                sm.put(client, clock);
            }
        });

        StructStore.getStateVector(store).forEach((client, clock) -> {
            if (!_sm.containsKey(client)) {
                sm.put(client, 0);
            }
        });

        // write # states that were updated
        encoding.writeVarUint(encoder.restEncoder, sm.size());

        // Write items with higher client ids first
        // This heavily improves the conflict algorithm.
        List<Map.Entry<Integer, Integer>> toSort = new ArrayList<>(sm.entrySet());
        toSort.sort((a, b) -> b.getKey() - a.getKey());
        for (int i = 0; i < toSort.size(); i++) {
            Map.Entry<Integer, Integer> entry = toSort.get(i);
            int client = entry.getKey();
            int clock = entry.getValue();
            writeStructs(encoder, store.clients.get(client), client, clock);
        }
    }

    /**
     * Reads client struct references from the decoder
     */
    public static Map<Integer, StructRefs> readClientsStructRefs(UpdateDecoder decoder, Doc doc) {
        Map<Integer, StructRefs> clientRefs = new HashMap<>();
        int numOfStateUpdates = decoding.readVarUint(decoder.restDecoder);

        for (int i = 0; i < numOfStateUpdates; i++) {
            int numberOfStructs = decoding.readVarUint(decoder.restDecoder);
            AbstractStruct[] refs = new AbstractStruct[numberOfStructs];
            int client = decoder.readClient();
            int clock = decoding.readVarUint(decoder.restDecoder);

            clientRefs.put(client, new StructRefs(0, refs));

            for (int j = 0; j < numberOfStructs; j++) {
                int info = decoder.readInfo();
                switch (binary.BITS5 & info) {
                    case GC_FLAG: {
                        int len = decoder.readLen();
                        refs[j] = new GC(ID.createID(client, clock), len);
                        clock += len;
                        break;
                    }
                    case SKIP_STRUCT: {  // Skip Struct (nothing to apply)
                        int len = decoding.readVarUint(decoder.restDecoder);
                        refs[j] = new Skip(ID.createID(client, clock), len);
                        clock += len;
                        break;
                    }
                    default: { // Item with content
                        boolean cantCopyParentInfo = (info & (binary.BIT7 | binary.BIT8)) == 0;
                        Item item = new Item(
                                ID.createID(client, clock),
                                null, // left
                                (info & binary.BIT8) == binary.BIT8 ? decoder.readLeftID() : null, // origin
                                null, // right
                                (info & binary.BIT7) == binary.BIT7 ? decoder.readRightID() : null, // right origin
                                cantCopyParentInfo ?
                                        (decoder.readParentInfo() ? doc.get(decoder.readString()) : decoder.readLeftID()) :
                                        null, // parent
                                cantCopyParentInfo && (info & binary.BIT6) == binary.BIT6 ? decoder.readString() : null, // parentSub
                                Item.readItemContent(decoder, info) // item content
                        );
                        refs[j] = item;
                        clock += item.length;
                    }
                }
            }
        }
        return clientRefs;
    }

    private static final Map<Integer, Integer> missingSV = new HashMap<>();

    private static void updateMissingSv(int client, int clock) {
        Integer mclock = missingSV.get(client);
        if (mclock == null || mclock > clock) {
            missingSV.put(client, clock);
        }
    }

    private static void addStackToRestSS(List<AbstractStruct> stack, Map<Integer, StructRefs> clientsStructRefs, StructStore restStructs, List<Integer> clientsStructRefsIds) {
        for (AbstractStruct item : stack) {
            int client = item.id.client;
            StructRefs inapplicableItems = clientsStructRefs.get(client);

            if (inapplicableItems != null) {
                // decrement because we weren't able to apply previous operation
                inapplicableItems.i--;
                AbstractStruct[] subarray = ArrayUtils.subarray(inapplicableItems.refs, inapplicableItems.i, inapplicableItems.refs.length);
                restStructs.clients.put(client, Arrays.asList(subarray));
                clientsStructRefs.remove(client);
                inapplicableItems.i = 0;
                inapplicableItems.refs = new AbstractStruct[]{};
            } else {
                // item was the last item on clientsStructRefs
                restStructs.clients.put(client, Collections.singletonList(item));
            }
            // remove client from clientsStructRefsIds
            clientsStructRefsIds.removeIf(c -> c == client);
        }
        stack.clear();
    }

    private static StructRefs getNextStructTarget(List<Integer> clientsStructRefsIds, Map<Integer, StructRefs> clientsStructRefs) {
        if (clientsStructRefsIds.isEmpty()) {
            return null;
        }

        StructRefs nextStructsTarget = clientsStructRefs.get(
                clientsStructRefsIds.get(clientsStructRefsIds.size() - 1));

        while (nextStructsTarget.refs.length == nextStructsTarget.i) {
            clientsStructRefsIds.remove(clientsStructRefsIds.size() - 1);
            if (!clientsStructRefsIds.isEmpty()) {
                nextStructsTarget = clientsStructRefs.get(
                        clientsStructRefsIds.get(clientsStructRefsIds.size() - 1));
            } else {
                return null;
            }
        }
        return nextStructsTarget;
    }

    /**
     * Integrates structs into the document
     */
    public static Structs integrateStructs(Transaction transaction, StructStore store, Map<Integer, StructRefs> clientsStructRefs) {
        List<AbstractStruct> stack = new ArrayList<>();
        List<Integer> clientsStructRefsIds = new ArrayList<>(clientsStructRefs.keySet());
        clientsStructRefsIds.sort(Integer::compare);

        if (clientsStructRefsIds.isEmpty()) {
            return null;
        }


        StructRefs curStructsTarget = getNextStructTarget(clientsStructRefsIds, clientsStructRefs);
        if (curStructsTarget == null) {
            return null;
        }

        StructStore restStructs = new StructStore();
        Map<Integer, Integer> missingSV = new HashMap<>();


        AbstractStruct stackHead = curStructsTarget.refs[curStructsTarget.i++];
        Map<Integer, Integer> state = new HashMap<>();


        while (true) {
            if (!(stackHead instanceof Skip)) {
                AbstractStruct finalStackHead = stackHead;
                int localClock = state.computeIfAbsent(stackHead.id.client,
                        k -> StructStore.getState(store, finalStackHead.id.client));
                int offset = localClock - stackHead.id.clock;

                if (offset < 0) {
                    // update from the same client is missing
                    stack.add(stackHead);
                    updateMissingSv(stackHead.id.client, stackHead.id.clock - 1);
                    addStackToRestSS(stack, clientsStructRefs, restStructs, clientsStructRefsIds);
                } else {
                    Integer missing = stackHead.getMissing(transaction, store);
                    if (missing != null) {
                        stack.add(stackHead);
                        StructRefs structRefs = clientsStructRefs.getOrDefault(missing,
                                new StructRefs(0, new AbstractStruct[]{}));

                        if (structRefs.refs.length == structRefs.i) {
                            // This update message causally depends on another update message
                            updateMissingSv(missing, StructStore.getState(store, missing));
                            addStackToRestSS(stack, clientsStructRefs, restStructs, clientsStructRefsIds);
                        } else {
                            stackHead = structRefs.refs[structRefs.i++];
                            continue;
                        }
                    } else if (offset == 0 || offset < stackHead.length) {
                        // all fine, apply the stackhead
                        stackHead.integrate(transaction, offset);
                        state.put(stackHead.id.client, stackHead.id.clock + stackHead.length);
                    }
                }
            }

            // iterate to next stackHead
            if (!stack.isEmpty()) {
                stackHead = stack.remove(stack.size() - 1);
            } else if (curStructsTarget != null && curStructsTarget.i < curStructsTarget.refs.length) {
                stackHead = curStructsTarget.refs[curStructsTarget.i++];
            } else {
                curStructsTarget = getNextStructTarget(clientsStructRefsIds, clientsStructRefs);
                if (curStructsTarget == null) {
                    // we are done!
                    break;
                } else {
                    stackHead = curStructsTarget.refs[curStructsTarget.i++];
                }
            }
        }

        if (!restStructs.clients.isEmpty()) {
            UpdateEncoderV2 encoder = new UpdateEncoderV2();
            writeClientsStructs(encoder, restStructs, new HashMap<>());
            // write empty deleteset
            encoding.writeVarUint(encoder.restEncoder, 0);
            return new Structs(missingSV, encoder.toUint8Array());
        }
        return null;
    }


    /**
     * Writes structs from transaction
     */
    public static void writeStructsFromTransaction(UpdateEncoder encoder, Transaction transaction) {
        writeClientsStructs(encoder, transaction.doc.store, transaction.beforeState);
    }
    // export const readUpdate = (decoder, ydoc, transactionOrigin) => readUpdateV2(decoder, ydoc, transactionOrigin, new UpdateDecoderV1(decoder))

    public static void readUpdateV2(Decoder decoder, Doc ydoc, Object transactionOrigin) {
        readUpdateV2(decoder, ydoc, transactionOrigin, new UpdateDecoderV1(decoder));
    }


    /**
     * Reads and applies an update (V2 format)
     */
    public static void readUpdateV2(Decoder decoder, Doc ydoc, Object transactionOrigin, UpdateDecoder structDecoder) {
        Transaction.transact(ydoc, transaction -> {
            transaction.local = false;
            boolean retry = false;
            Doc doc = transaction.doc;
            StructStore store = doc.store;

            Map<Integer, StructRefs> ss = readClientsStructRefs(structDecoder, doc);
            // 在integrateStructs方法开始处添加
            Structs restStructs = integrateStructs(transaction, store, ss);

            Structs pending = store.pendingStructs;
            if (pending != null) {
                // check if we can apply something
                for (Map.Entry<Integer, Integer> entry : pending.missing.entrySet()) {
                    Integer client = entry.getKey();
                    Integer clock = entry.getValue();
                    if (clock < StructStore.getState(store, client)) {
                        retry = true;
                        break;
                    }
                }

                if (restStructs != null) {
                    // merge restStructs into store.pending
                    for (Map.Entry<Integer, Integer> entry : restStructs.missing.entrySet()) {
                        Integer client = entry.getKey();
                        Integer mclock = pending.missing.get(client);
                        Integer clock = entry.getValue();
                        if (mclock == null || mclock > clock) {
                            pending.missing.put(client, clock);
                        }
                    }
                    pending.update = mergeUpdatesV2(Arrays.asList(pending.update, restStructs.update));
                }
            } else {
                store.pendingStructs = restStructs;
            }

            int[] dsRest = DeleteSet.readAndApplyDeleteSet(structDecoder, transaction, store);
            if (store.pendingDs != null) {
                UpdateDecoderV2 pendingDSUpdate = new UpdateDecoderV2(decoding.createDecoder(store.pendingDs));
                decoding.readVarUint(pendingDSUpdate.restDecoder); // read 0 structs
                int[] dsRest2 = DeleteSet.readAndApplyDeleteSet(pendingDSUpdate, transaction, store);

                if (dsRest != null && dsRest2 != null) {
                    store.pendingDs = mergeUpdatesV2(Arrays.asList(dsRest, dsRest2));
                } else {
                    store.pendingDs = dsRest != null ? dsRest : dsRest2;
                }
            } else {
                store.pendingDs = dsRest;
            }

            if (retry) {
                int[] update = store.pendingStructs.update;
                store.pendingStructs = null;
                applyUpdateV2(transaction.doc, update);
            }
            return null;
        }, transactionOrigin, false);
    }


    /**
     * 读取并应用文档更新
     * 此方法与applyUpdate效果相同，但接受解码器作为参数
     *
     * @param decoder           解码器
     * @param ydoc              Y文档
     * @param transactionOrigin 事务来源，将存储在transaction.origin和更新事件中
     */
    public static void readUpdate(Decoder decoder, Doc ydoc, Object transactionOrigin) {
        readUpdateV2(decoder, ydoc, transactionOrigin, new UpdateDecoderV1(decoder));
    }

    public static void applyUpdateV2(Doc ydoc, int[] update) {
        applyUpdateV2(ydoc, update, null, UpdateDecoderV2.class);
    }

    public static void applyUpdateV2(Doc ydoc, int[] update, Object transactionOrigin) {
        Decoder decoder = decoding.createDecoder(update);
        readUpdateV2(decoder, ydoc, transactionOrigin, new UpdateDecoderV2(decoder));
    }

    /**
     * 应用文档更新（例如通过y.on('update', update => ..)或encodeStateAsUpdate()创建的更新）
     * 此方法与readUpdate效果相同，但接受Uint8Array而不是解码器
     *
     * @param ydoc              Y文档
     * @param update            更新数据
     * @param transactionOrigin 事务来源
     * @param YDecoder          解码器类(默认为UpdateDecoderV2)
     */
    public static void applyUpdateV2(Doc ydoc, int[] update, Object transactionOrigin, Class<? extends UpdateDecoder> YDecoder) {
        YDecoder = ObjectUtils.getIfNull(YDecoder, UpdateDecoderV2.class);
        Decoder decoder = decoding.createDecoder(update);
        UpdateDecoder updateDecoder;
        try {
            updateDecoder = YDecoder.getConstructor(Decoder.class).newInstance(decoder);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create decoder", e);
        }

        // 保存原始状态
        String before = ydoc.getText("textBlock").toString();

        readUpdateV2(decoder, ydoc, transactionOrigin, updateDecoder);

        // 打印应用后的状态
        String after = ydoc.getText("textBlock").toString();
    }

    /**
     * 应用文档更新（简化版本，使用默认的UpdateDecoderV1）
     */
    public static void applyUpdate(Doc ydoc, int[] update, Object transactionOrigin) {
        applyUpdateV2(ydoc, update, transactionOrigin, UpdateDecoderV1.class);
    }

    /**
     * 将整个文档作为单个更新消息写入编码器
     * 如果指定了远程客户端状态(targetStateVector)，则只写入缺失的操作
     *
     * @param encoder           编码器
     * @param doc               Y文档
     * @param targetStateVector 目标客户端状态，留空则写入所有已知结构
     */
    public static void writeStateAsUpdate(UpdateEncoder encoder, Doc doc, Map<Integer, Integer> targetStateVector) {
        targetStateVector = ObjectUtils.getIfNull(targetStateVector, new HashMap<>());
        writeClientsStructs(encoder, doc.store, targetStateVector);
        Uint8Array.printUint8Array(encoder.toUint8Array());
        DeleteSet.writeDeleteSet(encoder, DeleteSet.createDeleteSetFromStructStore(doc.store));
    }

    /**
     * 将整个文档编码为可应用于远程文档的单个更新消息
     * 如果指定了远程客户端状态(encodedTargetStateVector)，则只写入缺失的操作
     *
     * @param doc                      Y文档
     * @param encodedTargetStateVector 目标客户端状态编码，留空则写入所有已知结构
     * @param encoder                  编码器(默认为UpdateEncoderV2)
     * @return 编码后的更新数据
     */
    public static int[] encodeStateAsUpdateV2(Doc doc, int[] encodedTargetStateVector, UpdateEncoder encoder) {
        encodedTargetStateVector = ObjectUtils.getIfNull(encodedTargetStateVector, new int[]{0});
        encoder = ObjectUtils.getIfNull(encoder, new UpdateEncoderV2<>());

        Map<Integer, Integer> targetStateVector = decodeStateVector(encodedTargetStateVector);
        writeStateAsUpdate(encoder, doc, targetStateVector);

        List<int[]> updates = new ArrayList<>();
        updates.add(encoder.toUint8Array());

        // 同时添加待处理的更新(如果有的话)
        if (doc.store.pendingDs != null) {
            updates.add(doc.store.pendingDs);
        }
        if (doc.store.pendingStructs != null) {
            updates.add(Updates.diffUpdateV2(doc.store.pendingStructs.update, encodedTargetStateVector));
        }

        if (updates.size() > 1) {
            if (encoder instanceof UpdateEncoderV1) {
                return mergeUpdates(updates.stream()
                        .map(update -> update == updates.get(0) ? update : convertUpdateFormatV2ToV1(update))
                        .collect(Collectors.toList()));
            } else if (encoder instanceof UpdateEncoderV2) {
                return mergeUpdatesV2(updates);
            }
        }
        return updates.get(0);
    }

    /**
     * 将整个文档编码为更新消息(简化版本，使用默认的UpdateEncoderV1)
     */
    public static int[] encodeStateAsUpdate(Doc doc, int[] encodedTargetStateVector) {
        return encodeStateAsUpdateV2(doc, encodedTargetStateVector, new UpdateEncoderV1());
    }

    /**
     * 从解码器读取状态向量并返回为Map
     *
     * @param decoder 解码器
     * @return 映射客户端ID到该客户端下一个期望的时钟值
     */
    public static Map<Integer, Integer> readStateVector(UpdateDecoder decoder) {
        Map<Integer, Integer> ss = new HashMap<>();
        int ssLength = decoding.readVarUint(decoder.restDecoder);
        for (int i = 0; i < ssLength; i++) {
            int client = decoding.readVarUint(decoder.restDecoder);
            int clock = decoding.readVarUint(decoder.restDecoder);
            ss.put(client, clock);
        }
        return ss;
    }

    /**
     * 解码状态向量并返回状态Map
     *
     * @param decodedState 已解码的状态数据
     * @return 映射客户端ID到该客户端下一个期望的时钟值
     */
    public static Map<Integer, Integer> decodeStateVector(int[] decodedState) {
        return readStateVector(new UpdateDecoderV1(decoding.createDecoder(decodedState)));
    }

    /**
     * 将状态向量写入编码器
     *
     * @param encoder 编码器
     * @param sv      状态向量Map
     */
    public static void writeStateVector(UpdateEncoder encoder, Map<Integer, Integer> sv) {
        encoding.writeVarUint(encoder.restEncoder, sv.size());
        sv.entrySet().stream()
                .sorted((a, b) -> b.getKey() - a.getKey())
                .forEach(entry -> {
                    encoding.writeVarUint(encoder.restEncoder, entry.getKey());
                    encoding.writeVarUint(encoder.restEncoder, entry.getValue());
                });
    }

    /**
     * 将文档状态向量写入编码器
     *
     * @param encoder 编码器
     * @param doc     Y文档
     */
    public static void writeDocumentStateVector(UpdateEncoder encoder, Doc doc) {
        writeStateVector(encoder, StructStore.getStateVector(doc.store));
    }

    /**
     * 将状态编码为Uint8Array
     *
     * @param doc     Y文档或状态Map
     * @param encoder 编码器(默认为DSEncoderV2)
     * @return 编码后的状态数据
     */
    public static int[] encodeStateVectorV2(Object doc, UpdateEncoder encoder) {
        if (encoder == null) {
            encoder = new UpdateEncoderV2<>();
        }

        if (doc instanceof Map) {
            writeStateVector(encoder, (Map<Integer, Integer>) doc);
        } else if (doc instanceof Doc) {
            writeDocumentStateVector(encoder, (Doc) doc);
        }
        return encoder.toUint8Array();
    }

    /**
     * 将状态编码为Uint8Array(简化版本，使用默认的DSEncoderV1)
     */
    public static int[] encodeStateVector(Object doc) {
        return encodeStateVectorV2(doc, new UpdateEncoderV1());
    }

}