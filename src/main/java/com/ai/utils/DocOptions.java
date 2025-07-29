package com.ai.utils;

import com.ai.structs.item.Item;
import lombok.*;
import lombok.experimental.Accessors;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

@Data
@Accessors(chain = true)
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class DocOptions {
    @Builder.Default
    public boolean gc = true;

    @ToString.Exclude
    @Builder.Default
    public Predicate<Item> gcFilter = item -> true;

    @Builder.Default
    public String guid = UUID.randomUUID().toString();

    public String collectionid;
    public Object meta;

    @Builder.Default
    public boolean autoLoad = false;

    @Builder.Default
    public boolean shouldLoad = true;

    // 保留这个构造函数以便兼容旧代码
    public DocOptions(String guid, boolean shouldLoad, Map<String, Object> opts) {
        this.guid = guid;
        this.shouldLoad = shouldLoad;

        if (opts.containsKey("meta")) {
            this.meta = opts.get("meta");
        }
        if (opts.containsKey("autoLoad")) {
            this.autoLoad = Boolean.parseBoolean(String.valueOf(opts.get("autoLoad")));
        }
        if (opts.containsKey("gc")) {
            this.gc = Boolean.parseBoolean(String.valueOf(opts.get("gc")));
        }
        if (opts.containsKey("collectionid")) {
            this.collectionid = (String) opts.get("collectionid");
        }
    }

    public DocOptions(String guid) {
        this.guid = guid;
    }
}