package com.ai.utils;

import com.ai.myutils.decoder.decoding;
import com.ai.myutils.encoder.encoding;
import com.ai.structs.AbstractStruct;
import com.ai.structs.item.Item;
import com.ai.types.ID;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.encoder.UpdateEncoder;
import com.ai.utils.codec.encoder.UpdateEncoderV2;
import com.ai.utils.structstore.StructStore;

import java.util.*;
import java.util.function.Consumer;

import static com.ai.utils.structstore.StructStore.iterateStructs;

/**
 * 删除集，包含多个客户端及其删除项
 */
public class DeleteSet {
    public final Map<Integer, List<DeleteItem>> clients;

    public DeleteSet() {
        this.clients = new HashMap<>();
    }

    /**
     * 遍历删除集中的所有结构
     * @param transaction 事务对象
     * @param ds 删除集
     * @param f 回调函数
     */
    public static void iterateDeletedStructs(Transaction transaction, DeleteSet ds, Consumer<AbstractStruct> f) {
        ds.clients.forEach((clientid, deletes) -> {
            List<AbstractStruct> structs = transaction.doc.store.clients.get(clientid);
            if (structs != null && !structs.isEmpty()) {
                AbstractStruct lastStruct = structs.get(structs.size() - 1);
                ID lastId = lastStruct.id;
                int clockState = lastId.clock + lastStruct.length;

                for (DeleteItem del : deletes) {
                    if (del.clock >= clockState) break;
                    iterateStructs(transaction, structs, del.clock, del.len, f);
                }
            }
        });
    }

    /**
     * 在删除项列表中查找指定时钟的索引
     * @param dis 删除项列表
     * @param clock 时钟值
     * @return 索引或null
     */
    private static Integer findIndexDS(List<DeleteItem> dis, int clock) {
        int left = 0;
        int right = dis.size() - 1;
        while (left <= right) {
            int midindex = (left + right) / 2;
            DeleteItem mid = dis.get(midindex);
            int midclock = mid.clock;
            if (midclock <= clock) {
                if (clock < midclock + mid.len) {
                    return midindex;
                }
                left = midindex + 1;
            } else {
                right = midindex - 1;
            }
        }
        return null;
    }

    /**
     * 检查ID是否被删除
     * @param ds 删除集
     * @param id ID对象
     * @return 是否被删除
     */
    public static boolean isDeleted(DeleteSet ds, ID id) {
        List<DeleteItem> dis = ds.clients.get(id.client);
        return dis != null && findIndexDS(dis, id.clock) != null;
    }

    /**
     * 对删除集进行排序和合并
     * @param ds 删除集
     */
    public static void sortAndMergeDeleteSet(DeleteSet ds) {
        ds.clients.forEach((client, dels) -> {
            dels.sort(Comparator.comparingInt(a -> a.clock));
            
            int j = 1;
            for (int i = 1; i < dels.size(); i++) {
                DeleteItem left = dels.get(j - 1);
                DeleteItem right = dels.get(i);
                
                if (left.clock + left.len >= right.clock) {
                    left.len = Math.max(left.len, right.clock + right.len - left.clock);
                } else {
                    if (j < i) {
                        dels.set(j, right);
                    }
                    j++;
                }
            }
            
            if (j < dels.size()) {
                dels.subList(j, dels.size()).clear();
            }
        });
    }

    /**
     * 合并多个删除集
     * @param dss 删除集列表
     * @return 合并后的删除集
     */
    public static DeleteSet mergeDeleteSets(List<DeleteSet> dss) {
        DeleteSet merged = new DeleteSet();
        
        for (int dssI = 0; dssI < dss.size(); dssI++) {
            int finalDssI = dssI;
            dss.get(dssI).clients.forEach((client, delsLeft) -> {
                if (!merged.clients.containsKey(client)) {
                    List<DeleteItem> dels = new ArrayList<>(delsLeft);
                    
                    for (int i = finalDssI + 1; i < dss.size(); i++) {
                        List<DeleteItem> additionalDels = dss.get(i).clients.getOrDefault(client, Collections.emptyList());
                        dels.addAll(additionalDels);
                    }
                    
                    merged.clients.put(client, dels);
                }
            });
        }
        
        sortAndMergeDeleteSet(merged);
        return merged;
    }

    /**
     * 向删除集添加删除项
     * @param ds 删除集
     * @param client 客户端ID
     * @param clock 时钟值
     * @param length 长度
     */
    public static void addToDeleteSet(DeleteSet ds, int client, int clock, int length) {
        ds.clients.computeIfAbsent(client, k -> new ArrayList<>()).add(new DeleteItem(clock, length));
    }

    /**
     * 创建新的删除集
     * @return 新删除集
     */
    public static DeleteSet createDeleteSet() {
        return new DeleteSet();
    }

    /**
     * 从结构存储创建删除集
     * @param ss 结构存储
     * @return 删除集
     */
    public static DeleteSet createDeleteSetFromStructStore(StructStore ss) {
        DeleteSet ds = createDeleteSet();
        
        ss.clients.forEach((client, structs) -> {
            List<DeleteItem> dsitems = new ArrayList<>();
            
            for (int i = 0; i < structs.size(); i++) {
                AbstractStruct struct = structs.get(i);
                if (struct.deleted()) {
                    int clock = struct.id.clock;
                    int len = struct.length;
                    
                    while (i + 1 < structs.size()) {
                        AbstractStruct next = structs.get(i + 1);
                        if (!next.deleted()) break;
                        len += next.length;
                        i++;
                    }
                    
                    dsitems.add(new DeleteItem(clock, len));
                }
            }
            
            if (!dsitems.isEmpty()) {
                ds.clients.put(client, dsitems);
            }
        });
        
        return ds;
    }
 /**
     * 将删除集写入编码器
     * @param encoder 编码器(DSEncoderV1或V2)
     * @param ds 要写入的删除集
     */
    public static void writeDeleteSet(UpdateEncoder encoder, DeleteSet ds) {
        // 写入客户端数量
        encoding.writeVarUint(encoder.restEncoder, ds.clients.size());

        // 按客户端ID降序排序以确保确定性顺序
        ds.clients.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getKey(), a.getKey()))
            .forEach(entry -> {
                encoder.resetDsCurVal();
                int client = entry.getKey();
                List<DeleteItem> dsItems = entry.getValue();

                // 写入客户端ID和删除项数量
                encoding.writeVarUint(encoder.restEncoder, client);
                encoding.writeVarUint(encoder.restEncoder, dsItems.size());

                // 写入每个删除项
                for (DeleteItem item : dsItems) {
                    encoder.writeDsClock(item.clock);
                    encoder.writeDsLen(item.len);
                }
            });
    }

    /**
     * 从解码器读取删除集
     * @param decoder 解码器(DSDecoderV1或V2)
     * @return 读取的删除集
     */
    public static DeleteSet readDeleteSet(UpdateDecoder decoder) {
        DeleteSet ds = new DeleteSet();
        int numClients = decoding.readVarUint(decoder.restDecoder);

        for (int i = 0; i < numClients; i++) {
            decoder.resetDsCurVal();
            int client = decoding.readVarUint(decoder.restDecoder);
            int numberOfDeletes = decoding.readVarUint(decoder.restDecoder);

            if (numberOfDeletes > 0) {
                List<DeleteItem> dsItems = ds.clients.computeIfAbsent(client, k -> new ArrayList<>());
                for (int j = 0; j < numberOfDeletes; j++) {
                    int clock = decoder.readDsClock();
                    int len = decoder.readDsLen();
                    dsItems.add(new DeleteItem(clock, len));
                }
            }
        }
        return ds;
    }

    /**
     * 读取并应用删除集
     * @param decoder 解码器
     * @param transaction 当前事务
     * @param store 结构存储
     * @return 未能应用的删除集的更新数据，如果全部应用成功则返回null
     */
    public static int[] readAndApplyDeleteSet(UpdateDecoder decoder, Transaction transaction, StructStore store) {
        DeleteSet unappliedDS = new DeleteSet();
        int numClients = decoding.readVarUint(decoder.restDecoder);

        for (int i = 0; i < numClients; i++) {
            decoder.resetDsCurVal();
            int client = decoding.readVarUint(decoder.restDecoder);
            int numberOfDeletes = decoding.readVarUint(decoder.restDecoder);

            List<AbstractStruct> structs = store.clients.getOrDefault(client, Collections.emptyList());
            int state = StructStore.getState(store, client);

            for (int j = 0; j < numberOfDeletes; j++) {
                int clock = decoder.readDsClock();
                int clockEnd = clock + decoder.readDsLen();

                if (clock < state) {
                    if (state < clockEnd) {
                        addToDeleteSet(unappliedDS, client, state, clockEnd - state);
                    }

                    int index = StructStore.findIndexSS(structs, clock);
                    AbstractStruct struct = structs.get(index);

                    // 如果需要，分割第一个项
                    if (!struct.deleted() && struct.id.clock < clock) {
                        AbstractStruct split = Item.splitItem(transaction, (Item) struct, clock - struct.id.clock);
                        structs.add(index + 1, split);
                        index++; // 移动到下一个结构
                    }

                    // 处理范围内的结构
                    while (index < structs.size()) {
                        struct = structs.get(index++);
                        if (struct.id.clock < clockEnd) {
                            if (!struct.deleted()) {
                                if (clockEnd < struct.id.clock + struct.length) {
                                    AbstractStruct split = Item.splitItem(transaction, (Item) struct, clockEnd - struct.id.clock);
                                    structs.add(index, split);
                                }
                                struct.delete(transaction);
                            }
                        } else {
                            break;
                        }
                    }
                } else {
                    addToDeleteSet(unappliedDS, client, clock, clockEnd - clock);
                }
            }
        }

        // 如果有未应用的删除集，编码为更新
        if (!unappliedDS.clients.isEmpty()) {
            UpdateEncoderV2<?> encoder = new UpdateEncoderV2<>();
            encoding.writeVarUint(encoder.restEncoder, 0); // 编码0个结构
            writeDeleteSet(encoder, unappliedDS);
            return encoder.toUint8Array();
        }
        return null;
    }

    /**
     * 比较两个删除集是否相等
     * @param ds1 删除集1
     * @param ds2 删除集2
     * @return 是否相等
     */
    public static boolean equalDeleteSets(DeleteSet ds1, DeleteSet ds2) {
        if (ds1.clients.size() != ds2.clients.size()) return false;
        
        for (Map.Entry<Integer, List<DeleteItem>> entry : ds1.clients.entrySet()) {
            List<DeleteItem> deleteItems1 = entry.getValue();
            List<DeleteItem> deleteItems2 = ds2.clients.get(entry.getKey());
            
            if (deleteItems2 == null || deleteItems1.size() != deleteItems2.size()) {
                return false;
            }
            
            for (int i = 0; i < deleteItems1.size(); i++) {
                DeleteItem di1 = deleteItems1.get(i);
                DeleteItem di2 = deleteItems2.get(i);
                
                if (di1.clock != di2.clock || di1.len != di2.len) {
                    return false;
                }
            }
        }
        
        return true;
    }
}
