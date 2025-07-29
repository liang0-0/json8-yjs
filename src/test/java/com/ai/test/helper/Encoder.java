package com.ai.test.helper;

import com.ai.utils.Doc;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 编码器功能封装
 */
public class Encoder {
    final Function<Doc, int[]> encodeStateAsUpdate;
    final Function<List<int[]>, int[]> mergeUpdates;
    final BiConsumer<Doc, int[]> applyUpdate;
    final Consumer<int[]> logUpdate;
    final String updateEventName;
    final BiFunction<int[], int[], int[]> diffUpdate;

    public Encoder(Function<Doc, int[]> encodeStateAsUpdate,
                   Function<List<int[]>, int[]> mergeUpdates,
                   BiConsumer<Doc, int[]> applyUpdate,
                   Consumer<int[]> logUpdate,
                   String updateEventName,
                   BiFunction<int[], int[], int[]> diffUpdate) {
        this.encodeStateAsUpdate = encodeStateAsUpdate;
        this.mergeUpdates = mergeUpdates;
        this.applyUpdate = applyUpdate;
        this.logUpdate = logUpdate;
        this.updateEventName = updateEventName;
        this.diffUpdate = diffUpdate;
    }
}
