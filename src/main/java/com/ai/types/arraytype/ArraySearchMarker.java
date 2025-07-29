package com.ai.types.arraytype;

import com.ai.structs.item.Item;

import java.util.Comparator;
import java.util.List;

public class ArraySearchMarker {
    private static final int MAX_SEARCH_MARKER = 80;
    private static long globalSearchMarkerTimestamp = 0;

    public Item p;
    public int index;
    private long timestamp;

    public ArraySearchMarker(int index, Item p) {
        this.p = p;
        if (null != this.p) {
            this.p.setMarker(true);
        }
        this.index = index;
        this.timestamp = globalSearchMarkerTimestamp++;
    }

    public static void refreshMarkerTimestamp(ArraySearchMarker marker) {
        marker.timestamp = globalSearchMarkerTimestamp++;
    }

    public static void overwriteMarker(ArraySearchMarker marker, Item p, int index) {
        marker.p.setMarker(false);
        marker.p = p;
        p.setMarker(true);
        marker.index = index;
        marker.timestamp = globalSearchMarkerTimestamp++;
    }

    public static ArraySearchMarker markPosition(List<ArraySearchMarker> searchMarkers, Item p, int index) {
        if (searchMarkers.size() >= MAX_SEARCH_MARKER) {
            ArraySearchMarker oldest = searchMarkers.stream()
                    .min(Comparator.comparingLong(m -> m.timestamp))
                    .orElseThrow(RuntimeException::new);
            overwriteMarker(oldest, p, index);
            return oldest;
        } else {
            ArraySearchMarker marker = new ArraySearchMarker(index, p);
            searchMarkers.add(marker);
            return marker;
        }
    }

    public static ArraySearchMarker findMarker(AbstractType<?> yarray, int index) {
        if (yarray._start == null || index == 0 || yarray._searchMarker == null) {
            return null;
        }

        ArraySearchMarker marker = yarray._searchMarker.stream()
                .min(Comparator.comparingInt(m -> Math.abs(index - m.index)))
                .orElse(null);

        Item p = yarray._start;
        int pindex = 0;

        if (marker != null) {
            p = marker.p;
            pindex = marker.index;
            refreshMarkerTimestamp(marker);
        }

        // Iterate to right if possible
        while (p.right != null && pindex < index) {
            if (!p.deleted() && p.countable()) {
                if (index < pindex + p.length) {
                    break;
                }
                pindex += p.length;
            }
            p = p.right;
        }

        // Iterate to left if necessary
        while (p.left != null && pindex > index) {
            p = p.left;
            if (!p.deleted() && p.countable()) {
                pindex -= p.length;
            }
        }

        // Make sure p can't be merged with left
        while (p.left != null &&
                p.left.id.client == p.id.client &&
                p.left.id.clock + p.left.length == p.id.clock) {
            p = p.left;
            if (!p.deleted() && p.countable()) {
                pindex -= p.length;
            }
        }

        if (marker != null && Math.abs(marker.index - pindex) < p.length / MAX_SEARCH_MARKER) {
            overwriteMarker(marker, p, pindex);
            return marker;
        } else {
            return markPosition(yarray._searchMarker, p, pindex);
        }
    }

    public static void updateMarkerChanges(List<ArraySearchMarker> searchMarker, int index, int len) {
        // 从后往前遍历标记列表
        for (int i = searchMarker.size() - 1; i >= 0; i--) {
            ArraySearchMarker m = searchMarker.get(i);

            if (len > 0) { // 处理插入情况
                Item p = m.p;
                p.setMarker(false);

                // 向前查找第一个可计数的未删除项
                while (p != null && (p.deleted() || !p.countable())) {
                    p = p.left;
                    if (p != null && !p.deleted() && p.countable()) {
                        // 调整位置索引
                        m.index -= p.length;
                    }
                }

                // 如果找不到有效位置或位置已被标记，则移除该标记
                if (p == null || p.marker()) {
                    searchMarker.remove(i);
                    continue;
                }

                m.p = p;
                p.setMarker(true);
            }

            // 更新标记索引
            if (index < m.index || (len > 0 && index == m.index)) {
                m.index = Math.max(index, m.index + len);
            }
        }
    }
}