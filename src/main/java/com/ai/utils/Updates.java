package com.ai.utils;

import com.ai.myutils.Maps;
import com.ai.myutils.decoder.Decoder;
import com.ai.myutils.decoder.decoding;
import com.ai.myutils.encoder.Encoder;
import com.ai.myutils.encoder.encoding;
import com.ai.structs.*;
import com.ai.structs.item.AbstractContent;
import com.ai.structs.item.Item;
import com.ai.types.ID;
import com.ai.types.YXmlElement;
import com.ai.types.YXmlHook;
import com.ai.types.arraytype.AbstractType;
import com.ai.types.vo.*;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.decoder.UpdateDecoderV1;
import com.ai.utils.codec.decoder.UpdateDecoderV2;
import com.ai.utils.codec.encoder.*;
import org.apache.commons.lang3.ObjectUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.ai.utils.DeleteSet.*;

/**
 * Yjs更新处理工具类，提供更新日志记录、差异比较、格式转换等功能
 */
public class Updates {

    /**
     * 记录更新日志（V1格式）
     *
     * @param update 更新数据
     */
    public static void logUpdate(int[] update) {
        logUpdateV2(update, UpdateDecoderV1.class);
    }

    /**
     * 记录更新日志（指定解码器格式）
     *
     * @param update       更新数据
     * @param decoderClass 解码器类
     */
    public static void logUpdateV2(int[] update, Class<? extends UpdateDecoder> decoderClass) {
        decoderClass = ObjectUtils.getIfNull(decoderClass, UpdateDecoderV2.class);
        try {
            UpdateDecoder decoder = decoderClass.getConstructor(Decoder.class)
                    .newInstance(new Decoder(update));

            LazyStructReader lazyDecoder = new LazyStructReader(decoder, false);
            List<AbstractStruct> structs = new ArrayList<>();

            for (AbstractStruct curr = lazyDecoder.curr; curr != null; curr = lazyDecoder.next()) {
                structs.add(curr);
            }

            System.out.println("Structs: " + structs);
            DeleteSet ds = readDeleteSet(decoder);
            System.out.println("DeleteSet: " + ds);
        } catch (Exception e) {
            throw new RuntimeException("Failed to log update", e);
        }
    }

    /**
     * 解码更新数据（V1格式）
     *
     * @param update 更新数据
     * @return 解码结果（结构体和删除集）
     */
    public static DecodedUpdate decodeUpdate(int[] update) {
        return decodeUpdateV2(update, UpdateDecoderV1.class);
    }

    /**
     * 解码更新数据（指定解码器格式）
     *
     * @param update       更新数据
     * @param decoderClass 解码器类
     * @return 解码结果（结构体和删除集）
     */
    public static DecodedUpdate decodeUpdateV2(int[] update, Class<? extends UpdateDecoder> decoderClass) {
        try {
            UpdateDecoder decoder = decoderClass.getConstructor(Decoder.class)
                    .newInstance(new Decoder(update));

            LazyStructReader lazyDecoder = new LazyStructReader(decoder, false);
            List<AbstractStruct> structs = new ArrayList<>();

            for (AbstractStruct curr = lazyDecoder.curr; curr != null; curr = lazyDecoder.next()) {
                structs.add(curr);
            }

            return new DecodedUpdate(structs, readDeleteSet(decoder));
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode update", e);
        }
    }

    /**
     * 合并多个更新（V1格式）
     *
     * @param updates 更新列表
     * @return 合并后的更新数据
     */
    public static int[] mergeUpdates(List<int[]> updates) {
        return mergeUpdatesV2(updates, UpdateDecoderV1.class, UpdateEncoderV1.class);
    }

    public static int[] mergeUpdatesV2(List<int[]> updates) {
        return mergeUpdatesV2(updates, UpdateDecoderV2.class, UpdateEncoderV2.class);
    }

    /**
     * 合并多个更新（指定编解码器格式）
     *
     * @param updates      更新列表
     * @param decoderClass 解码器类
     * @param encoderClass 编码器类
     * @return 合并后的更新数据
     */
    public static int[] mergeUpdatesV2(List<int[]> updates,
                                       Class<? extends UpdateDecoder> decoderClass,
                                       Class<? extends UpdateEncoder> encoderClass) {
        if (updates.size() == 1) {
            return updates.get(0);
        }

        // Initialize decoders
        List<UpdateDecoder> updateDecoders = new ArrayList<>();
        for (int[] update : updates) {
            try {
                UpdateDecoder decoder = decoderClass.getConstructor(Decoder.class).newInstance(new Decoder(update));
                updateDecoders.add(decoder);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create decoder", e);
            }
        }

        List<LazyStructReader> lazyStructDecoders = updateDecoders.stream()
                .map(decoder -> new LazyStructReader(decoder, true))
                .collect(Collectors.toList());

        CurrentWrite currWrite = null;

        // Initialize encoder
        UpdateEncoder updateEncoder;
        try {
            updateEncoder = encoderClass.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create encoder", e);
        }
        LazyStructWriter lazyStructEncoder = new LazyStructWriter(updateEncoder);

        while (true) {
            // Filter and sort decoders
            lazyStructDecoders = lazyStructDecoders.stream()
                    .filter(dec -> dec.curr != null)
                    .sorted((dec1, dec2) -> {
                        ID id1 = dec1.curr.id;
                        ID id2 = dec2.curr.id;

                        if (id1.client == id2.client) {
                            int clockDiff = id1.clock - id2.clock;
                            if (clockDiff == 0) {
                                return dec1.curr.getClass() == dec2.curr.getClass()
                                        ? 0
                                        : dec1.curr instanceof Skip ? 1 : -1;
                            }
                            return clockDiff;
                        }
                        return id2.client - id1.client;
                    })
                    .collect(Collectors.toList());

            if (lazyStructDecoders.isEmpty()) {
                break;
            }

            LazyStructReader currDecoder = lazyStructDecoders.get(0);
            int firstClient = currDecoder.curr.id.client;

            if (currWrite != null) {
                AbstractStruct curr = currDecoder.curr;
                boolean iterated = false;

                while (curr != null &&
                        curr.id.clock + curr.length <= currWrite.struct.id.clock + currWrite.struct.length &&
                        curr.id.client >= currWrite.struct.id.client) {
                    curr = currDecoder.next();
                    iterated = true;
                }

                if (curr == null ||
                        curr.id.client != firstClient ||
                        (iterated && curr.id.clock > currWrite.struct.id.clock + currWrite.struct.length)) {
                    continue;
                }

                if (firstClient != currWrite.struct.id.client) {
                    writeStructToLazyStructWriter(lazyStructEncoder, currWrite.struct, currWrite.offset);
                    currWrite = new CurrentWrite(curr, 0);
                    currDecoder.next();
                } else {
                    if (currWrite.struct.id.clock + currWrite.struct.length < curr.id.clock) {
                        if (currWrite.struct instanceof Skip) {
                            currWrite.struct.length = curr.id.clock + curr.length - currWrite.struct.id.clock;
                        } else {
                            writeStructToLazyStructWriter(lazyStructEncoder, currWrite.struct, currWrite.offset);
                            int diff = curr.id.clock - currWrite.struct.id.clock - currWrite.struct.length;
                            Skip skip = new Skip(ID.createID(firstClient, currWrite.struct.id.clock + currWrite.struct.length), diff);
                            currWrite = new CurrentWrite(skip, 0);
                        }
                    } else {
                        int diff = currWrite.struct.id.clock + currWrite.struct.length - curr.id.clock;
                        if (diff > 0) {
                            if (currWrite.struct instanceof Skip) {
                                currWrite.struct.length -= diff;
                            } else {
                                curr = sliceStruct(curr, diff);
                            }
                        }
                        if (!currWrite.struct.mergeWith(curr)) {
                            writeStructToLazyStructWriter(lazyStructEncoder, currWrite.struct, currWrite.offset);
                            currWrite = new CurrentWrite(curr, 0);
                            currDecoder.next();
                        }
                    }
                }
            } else {
                currWrite = new CurrentWrite(currDecoder.curr, 0);
                currDecoder.next();
            }

            AbstractStruct next = currDecoder.curr;
            while (next != null &&
                    next.id.client == firstClient &&
                    next.id.clock == currWrite.struct.id.clock + currWrite.struct.length &&
                    !(next instanceof Skip)) {
                writeStructToLazyStructWriter(lazyStructEncoder, currWrite.struct, currWrite.offset);
                currWrite = new CurrentWrite(next, 0);
                next = currDecoder.next();
            }
        }

        if (currWrite != null) {
            writeStructToLazyStructWriter(lazyStructEncoder, currWrite.struct, currWrite.offset);
        }
        finishLazyStructWriting(lazyStructEncoder);

        // Process delete sets
        List<DeleteSet> deleteSets = updateDecoders.stream()
                .map(DeleteSet::readDeleteSet)
                .collect(Collectors.toList());
        DeleteSet mergedDeleteSet = mergeDeleteSets(deleteSets);
        writeDeleteSet(updateEncoder, mergedDeleteSet);

        return updateEncoder.toUint8Array();
    }

    /**
     * 从更新数据生成状态向量（V1格式）
     *
     * @param update 更新数据
     * @return 状态向量数据
     */
    public static int[] encodeStateVectorFromUpdate(int[] update) {
        return encodeStateVectorFromUpdateV2(update, UpdateEncoderV1.class, UpdateDecoderV1.class);
    }

    public static int[] encodeStateVectorFromUpdateV2(int[] update) {
        return encodeStateVectorFromUpdateV2(update, UpdateEncoderV2.class, UpdateDecoderV2.class);
    }

    /**
     * 从更新数据生成状态向量（指定编解码器格式）
     *
     * @param update   更新数据
     * @param YEncoder 编码器类
     * @param YDecoder 解码器类
     * @return 状态向量数据
     */
    public static int[] encodeStateVectorFromUpdateV2(int[] update,
                                                      Class<? extends UpdateEncoder> YEncoder,
                                                      Class<? extends UpdateDecoder> YDecoder) {
        try {
            UpdateEncoder encoder = YEncoder.newInstance();
            UpdateDecoder decoder = YDecoder.getConstructor(Decoder.class).newInstance(decoding.createDecoder(update));

            LazyStructReader updateDecoder = new LazyStructReader(decoder, false);
            AbstractStruct curr = updateDecoder.curr;

            if (curr != null) {
                int size = 0;
                int currClient = curr.id.client;
                boolean stopCounting = curr.id.clock != 0;
                int currClock = stopCounting ? 0 : curr.id.clock + curr.length;

                for (; curr != null; curr = updateDecoder.next()) {
                    if (currClient != curr.id.client) {
                        if (currClock != 0) {
                            size++;
                            encoding.writeVarUint(encoder.restEncoder, currClient);
                            encoding.writeVarUint(encoder.restEncoder, currClock);
                        }
                        currClient = curr.id.client;
                        currClock = 0;
                        stopCounting = curr.id.clock != 0;
                    }

                    if (curr instanceof Skip) {
                        stopCounting = true;
                    }

                    if (!stopCounting) {
                        currClock = curr.id.clock + curr.length;
                    }
                }

                if (currClock != 0) {
                    size++;
                    encoding.writeVarUint(encoder.restEncoder, currClient);
                    encoding.writeVarUint(encoder.restEncoder, currClock);
                }

                // 写入状态向量大小
                Encoder enc = encoding.createEncoder();
                encoding.writeVarUint(enc, size);
                encoding.writeBinaryEncoder(enc, encoder.restEncoder);
                encoder.restEncoder = enc;
                return encoder.toUint8Array();
            } else {
                encoding.writeVarUint(encoder.restEncoder, 0);
                return encoder.toUint8Array();
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to encode state vector", e);
        }
    }

    /**
     * 解析更新元数据（V1格式）
     *
     * @param update 更新数据
     * @return 包含from和to映射的元数据
     */
    public static UpdateMeta parseUpdateMeta(int[] update) {
        return parseUpdateMetaV2(update, UpdateDecoderV1.class);
    }

    /**
     * 解析更新元数据（指定解码器格式）
     *
     * @param update       更新数据
     * @param decoderClass 解码器类
     * @return 包含from和to映射的元数据
     */
    public static UpdateMeta parseUpdateMetaV2(int[] update, Class<? extends UpdateDecoder> decoderClass) {
        try {
            UpdateDecoder decoder = decoderClass.getConstructor(Decoder.class)
                    .newInstance(new Decoder(update));

            LazyStructReader reader = new LazyStructReader(decoder, false);
            AbstractStruct curr = reader.curr;

            Map<Integer, Integer> from = new HashMap<>();
            Map<Integer, Integer> to = new HashMap<>();

            if (curr != null) {
                int currClient = curr.id.client;
                int currClock = curr.id.clock;
                from.put(currClient, currClock);

                for (; curr != null; curr = reader.next()) {
                    if (currClient != curr.id.client) {
                        to.put(currClient, currClock);
                        from.put(curr.id.client, curr.id.clock);
                        currClient = curr.id.client;
                    }
                    currClock = curr.id.clock + curr.length;
                }
                to.put(currClient, currClock);
            }

            return new UpdateMeta(from, to);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse update meta", e);
        }
    }

    public static int[] diffUpdateV2(int[] update, int[] sv) {
        return diffUpdateV2(update, sv, UpdateDecoderV2.class, UpdateEncoderV2.class);
    }

    /**
     * 计算更新差异（指定编解码器格式）
     *
     * @param update       更新数据
     * @param sv           状态向量
     * @param decoderClass 解码器类
     * @param encoderClass 编码器类
     * @return 差异更新数据
     */
    public static int[] diffUpdateV2(int[] update, int[] sv,
                                     Class<? extends UpdateDecoder> decoderClass,
                                     Class<? extends UpdateEncoder> encoderClass) {
        try {
            Map<Integer, Integer> state = Encoding.decodeStateVector(sv);
            UpdateEncoder encoder = encoderClass.newInstance();
            LazyStructWriter writer = new LazyStructWriter(encoder);

            UpdateDecoder decoder = decoderClass.getConstructor(Decoder.class)
                    .newInstance(new Decoder(update));
            LazyStructReader reader = new LazyStructReader(decoder, false);

            while (reader.curr != null) {
                AbstractStruct curr = reader.curr;
                int currClient = curr.id.client;
                int svClock = state.getOrDefault(currClient, 0);

                if (curr instanceof Skip) {
                    reader.next();
                    continue;
                }

                if (curr.id.clock + curr.length > svClock) {
                    int offset = Math.max(svClock - curr.id.clock, 0);
                    writeStructToLazyStructWriter(writer, curr, offset);
                    reader.next();

                    while (reader.curr != null && reader.curr.id.client == currClient) {
                        writeStructToLazyStructWriter(writer, reader.curr, 0);
                        reader.next();
                    }
                } else {
                    while (reader.curr != null &&
                            reader.curr.id.client == currClient &&
                            reader.curr.id.clock + reader.curr.length <= svClock) {
                        reader.next();
                    }
                }
            }

            finishLazyStructWriting(writer);
            DeleteSet ds = readDeleteSet(decoder);
            writeDeleteSet(encoder, ds);
            return encoder.toUint8Array();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute update diff", e);
        }
    }

    /**
     * 计算更新差异（V1格式）
     *
     * @param update 更新数据
     * @param sv     状态向量
     * @return 差异更新数据
     */
    public static int[] diffUpdate(int[] update, int[] sv) {
        return diffUpdateV2(update, sv, UpdateDecoderV1.class, UpdateEncoderV1.class);
    }

    /**
     * 该方法用于切片任何类型的结构体并获取右侧部分
     * 它不处理副作用，因此应仅由延迟编码器使用
     *
     * @param left 要切分的结构体(Item/GC/Skip)
     * @param diff 切分偏移量
     * @return 切分后的右侧结构体(Item / GC)
     */
    public static AbstractStruct sliceStruct(AbstractStruct left, int diff) {
        if (left instanceof GC) {
            ID id = left.id;
            return new GC(new ID(id.client, id.clock + diff), left.length - diff);
        } else if (left instanceof Skip) {
            ID id = left.id;
            return new Skip(new ID(id.client, id.clock + diff), left.length - diff);
        } else {
            Item leftItem = (Item) left;
            ID id = leftItem.id;
            AbstractContent content = leftItem.content.splice(diff);
            return new Item(
                    new ID(id.client, id.clock + diff),
                    null,
                    new ID(id.client, id.clock + diff - 1),
                    null,
                    leftItem.rightOrigin,
                    leftItem.parent,
                    leftItem.parentSub,
                    content
            );
        }
    }

    /**
     * 刷新延迟结构体写入器，将缓存数据写入输出
     *
     * @param lazyWriter 延迟结构体写入器
     */
    public static void flushLazyStructWriter(LazyStructWriter lazyWriter) {
        if (lazyWriter.written > 0) {
            lazyWriter.clientStructs.add(new ClientStruct(
                    lazyWriter.written,
                    encoding.toUint8Array(lazyWriter.encoder.restEncoder)
            ));
            lazyWriter.encoder.restEncoder = encoding.createEncoder();
            lazyWriter.written = 0;
        }
    }

    /**
     * 将结构体写入延迟结构体写入器
     *
     * @param lazyWriter 延迟结构体写入器
     * @param struct     要写入的结构体(Item/GC)
     * @param offset     写入偏移量
     */
    public static void writeStructToLazyStructWriter(LazyStructWriter lazyWriter,
                                                     AbstractStruct struct,
                                                     int offset) {
        // 如果开始处理另一个客户端，先刷新当前写入器
        if (lazyWriter.written > 0 && lazyWriter.currClient != struct.id.client) {
            flushLazyStructWriter(lazyWriter);
        }
        if (lazyWriter.written == 0) {
            lazyWriter.currClient = struct.id.client;
            // 写入客户端ID
            lazyWriter.encoder.writeClient(struct.id.client);
            // 写入起始时钟
            encoding.writeVarUint(lazyWriter.encoder.restEncoder, struct.id.clock + offset);
        }
        struct.write(lazyWriter.encoder, offset, null);
        lazyWriter.written++;
    }

    /**
     * 完成延迟结构体写入，将所有部分合并到一起
     * 调用此方法后，可以继续使用UpdateEncoder
     *
     * @param lazyWriter 延迟结构体写入器
     */
    public static void finishLazyStructWriting(LazyStructWriter lazyWriter) {
        flushLazyStructWriter(lazyWriter);

        Encoder restEncoder = lazyWriter.encoder.restEncoder;

        // 写入更新的状态数量(即客户端数量)
        encoding.writeVarUint(restEncoder, lazyWriter.clientStructs.size());

        for (ClientStruct partStructs : lazyWriter.clientStructs) {
            // 写入编码的结构体数量
            encoding.writeVarUint(restEncoder, partStructs.written);
            // 写入片段的剩余部分
            encoding.writeUint8Array(restEncoder, partStructs.restEncoder);
        }
    }

    /**
     * 转换更新格式
     *
     * @param update           原始更新数据
     * @param blockTransformer 块转换函数
     * @param YDecoder         解码器类
     * @param YEncoder         编码器类
     * @return 转换后的更新数据
     */
    public static int[] convertUpdateFormat(int[] update,
                                            Function<AbstractStruct, AbstractStruct> blockTransformer,
                                            Class<? extends UpdateDecoder> YDecoder,
                                            Class<? extends UpdateEncoder> YEncoder) {
        UpdateDecoder updateDecoder;
        try {
            updateDecoder = YDecoder.getConstructor(Decoder.class)
                    .newInstance(new Decoder(update));
        } catch (Exception e) {
            throw new RuntimeException("Failed to create decoder", e);
        }

        LazyStructReader lazyDecoder = new LazyStructReader(updateDecoder, false);
        UpdateEncoder updateEncoder;
        try {
            updateEncoder = YEncoder.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create encoder", e);
        }
        LazyStructWriter lazyWriter = new LazyStructWriter(updateEncoder);

        for (AbstractStruct curr = lazyDecoder.curr; curr != null; curr = lazyDecoder.next()) {
            writeStructToLazyStructWriter(lazyWriter, blockTransformer.apply(curr), 0);
        }

        finishLazyStructWriting(lazyWriter);
        DeleteSet ds = readDeleteSet(updateDecoder);
        writeDeleteSet(updateEncoder, ds);
        return updateEncoder.toUint8Array();
    }


    /**
     * 创建混淆器
     *
     * @param obfuscator 混淆器选项
     * @return 混淆函数
     */
    public static Function<AbstractStruct, AbstractStruct> createObfuscator(ObfuscatorOptions obfuscator) {
        // { formatting = true, subdocs = true, yxml = true
        obfuscator = ObjectUtils.getIfNull(obfuscator, new ObfuscatorOptions());
        obfuscator.formatting = ObjectUtils.getIfNull(obfuscator.formatting, true);
        obfuscator.subdocs = ObjectUtils.getIfNull(obfuscator.subdocs, true);
        obfuscator.yxml = ObjectUtils.getIfNull(obfuscator.yxml, true);

        final int[] i = {0};
        Map<String, String> mapKeyCache = new HashMap<>();
        Map<String, String> nodeNameCache = new HashMap<>();
        Map<String, String> formattingKeyCache = new HashMap<>();
        Map<Object, Object> formattingValueCache = new HashMap<>();
        formattingValueCache.put(null, null); // 格式化范围的结束应始终是格式化范围的结束

        ObfuscatorOptions finalObfuscator = obfuscator;
        return block -> {
            if (block instanceof GC || block instanceof Skip) {
                return block;
            } else if (block instanceof Item) {
                Item item = (Item) block;
                AbstractContent content = item.content;

                if (content instanceof ContentDeleted) {
                    // 无操作
                } else if (content instanceof ContentType) {
                    AbstractType type = ((ContentType) content).type;
                    if (finalObfuscator.yxml) {
                        if (type instanceof YXmlElement) {
                            ((YXmlElement) type).nodeName = nodeNameCache.computeIfAbsent(
                                    ((YXmlElement) type).nodeName, k -> "node-" + i[0]);
                        }
                        if (type instanceof YXmlHook) {
                            ((YXmlHook) type).hookName = nodeNameCache.computeIfAbsent(
                                    ((YXmlHook) type).hookName, k -> "hook-" + i[0]);
                        }
                    }
                } else if (content instanceof ContentAny) {
                    ((ContentAny) content).arr = Collections.nCopies(
                            ((ContentAny) content).arr.size(), i[0]);
                } else if (content instanceof ContentBinary) {
                    ((ContentBinary) content).content = new int[]{i[0]};
                } else if (content instanceof ContentDoc) {
                    if (finalObfuscator.subdocs) {
                        ((ContentDoc) content).opts = new HashMap<>();
                        ((ContentDoc) content).doc.guid = String.valueOf(i[0]);
                    }
                } else if (content instanceof ContentEmbed) {
                    ((ContentEmbed) content).embed = new HashMap<>();
                } else if (content instanceof ContentFormat) {
                    if (finalObfuscator.formatting) {
                        ContentFormat c = (ContentFormat) content;
                        c.key = formattingKeyCache.computeIfAbsent(c.key, k -> String.valueOf(i[0]));
                        c.value = formattingValueCache.computeIfAbsent(c.value, k -> Maps.of("i", i[0]));
                    }
                } else if (content instanceof ContentJSON) {
                    ((ContentJSON) content).arr = Collections.nCopies(
                            ((ContentJSON) content).arr.size(), i[0]);
                } else if (content instanceof ContentString) {
                    int repeat = ((ContentString) content).str.length();
                    ((ContentString) content).str = String.join("",
                            Collections.nCopies(repeat, String.valueOf(i[0] % 10)));
                } else {
                    throw new RuntimeException("未知的内容类型");
                }

                if (item.parentSub != null) {
                    item.parentSub = mapKeyCache.computeIfAbsent(
                            item.parentSub, k -> String.valueOf(i[0]));
                }
                i[0]++;
                return block;
            }
            throw new RuntimeException("未知的块类型");
        };
    }

    /**
     * 混淆更新数据
     *
     * @param update 原始更新数据
     * @param opts   混淆选项
     * @return 混淆后的更新数据
     */
    public static int[] obfuscateUpdate(int[] update, ObfuscatorOptions opts) {
        return convertUpdateFormat(update, createObfuscator(opts), UpdateDecoderV1.class, UpdateEncoderV1.class);
    }

    /**
     * 混淆V2格式更新数据
     *
     * @param update 原始更新数据
     * @param opts   混淆选项
     * @return 混淆后的更新数据
     */
    public static int[] obfuscateUpdateV2(int[] update, ObfuscatorOptions opts) {
        return convertUpdateFormat(update, createObfuscator(opts), UpdateDecoderV2.class, UpdateEncoderV2.class);
    }

    /**
     * 将V1格式更新转换为V2格式
     *
     * @param update V1格式更新数据
     * @return V2格式更新数据
     */
    public static int[] convertUpdateFormatV1ToV2(int[] update) {
        return convertUpdateFormat(update, Function.identity(), UpdateDecoderV1.class, UpdateEncoderV2.class);
    }

    /**
     * 将V2格式更新转换为V1格式
     *
     * @param update V2格式更新数据
     * @return V1格式更新数据
     */
    public static int[] convertUpdateFormatV2ToV1(int[] update) {
        return convertUpdateFormat(update, Function.identity(), UpdateDecoderV2.class, UpdateEncoderV1.class);
    }


}