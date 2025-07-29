package com.ai.utils;

import com.ai.myutils.decoder.decoding;
import com.ai.structs.item.Item;
import com.ai.types.ID;
import com.ai.types.YArray;
import com.ai.types.YMap;
import com.ai.types.vo.YArrayEvent;
import com.ai.types.vo.YMapEvent;
import com.ai.utils.DeleteSet;
import com.ai.utils.Doc;
import com.ai.utils.Transaction;
import com.ai.utils.codec.decoder.DSDecoderV1;
import com.ai.utils.codec.decoder.UpdateDecoderV1;
import com.ai.utils.codec.encoder.DSEncoderV1;
import com.ai.utils.codec.encoder.UpdateEncoderV1;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

import static com.ai.utils.DeleteSet.*;

/**
 * 永久用户数据管理类，用于跟踪用户及其删除操作
 */
public class PermanentUserData {
    private final YMap yusers;
    private final Doc doc;
    private final Map<Integer, String> clients = new HashMap<>();
    private final Map<String, DeleteSet> dss = new HashMap<>();

    /**
     * 构造函数
     * @param doc 关联的文档
     * @param storeType 用户存储映射(默认为doc.getMap("users"))
     */
    public PermanentUserData(Doc doc, YMap storeType) {
        this.doc = doc;
        this.yusers = storeType != null ? storeType : (YMap) doc.getMap("users");

        // 初始化用户观察逻辑
        this.yusers.observe((event, transaction) -> {
            if (!(event instanceof YMapEvent)) return;
            YMapEvent<?> yMapEvent = (YMapEvent<?>) event;
            yMapEvent.keysChanged.forEach(userDescription ->
                initUser((YMap) this.yusers.get(userDescription), userDescription)
            );
        });

        // 初始化已有用户数据
        this.yusers.forEach(this::initUser);
    }

    private void initUser(Object o, String userDescription) {
        this.initUser(((YMap) o), userDescription);
    }

    /**
     * 初始化用户数据
     * @param user 用户映射
     * @param userDescription 用户描述
     */
    private void initUser(YMap user, String userDescription) {
        YArray ds = (YArray) user.get("ds");
        YArray ids = (YArray) user.get("ids");

        // 添加客户端ID的回调
        Consumer<Integer> addClientId = clientid -> this.clients.put(clientid, userDescription);

        // 观察删除集变化
        ds.observe((event, transaction) -> {
            if (!(event instanceof YArrayEvent)) return;
            YArrayEvent<?> yArrayEvent = (YArrayEvent<?>) event;
            ((Set<Item>) yArrayEvent.getChanges().get("added")).forEach(item -> {
                item.content.getContent().forEach(content -> {
                    if (content instanceof byte[]) {
                        DeleteSet currentDs = this.dss.getOrDefault(userDescription, createDeleteSet());
                        DeleteSet newDs = readDeleteSet(new UpdateDecoderV1(decoding.createDecoder((int[])content)));
                        this.dss.put(userDescription, mergeDeleteSets(Arrays.asList(currentDs, newDs)));
                    }
                });
            });
        });

        // 初始化删除集
        List<DeleteSet> deleteSets = ds.map(encodedDs ->
            readDeleteSet(new UpdateDecoderV1(decoding.createDecoder((int[])encodedDs)))
        );
        this.dss.put(userDescription, mergeDeleteSets(deleteSets));

        // 观察ID数组变化
        ids.observe((event, transaction) -> {
            if (!(event instanceof YArrayEvent)) return;
            YArrayEvent<?> yArrayEvent = (YArrayEvent<?>) event;
            ((Set<Item>) yArrayEvent.getChanges().get("added")).forEach(item ->
                    item.content.getContent().stream().map(o -> ((Integer) o)).collect(Collectors.toList()).forEach(addClientId)
            );
        });

        // 初始化客户端ID
        ids.forEach(id -> addClientId.accept((Integer)id));
    }

    /**
     * 设置用户映射
     * @param doc 文档
     * @param clientid 客户端ID
     * @param userDescription 用户描述
     * @param conf 配置选项
     */
    public void setUserMapping(Doc doc, int clientid, String userDescription,
                             Map<String, BiFunction<Transaction, DeleteSet, Boolean>> conf) {
        BiFunction<Transaction, DeleteSet, Boolean> filter = conf.getOrDefault("filter", (BiFunction<Transaction, DeleteSet, Boolean>) (t, ds) -> true);

        final YMap[] user = {(YMap) this.yusers.get(userDescription)};
        if (user[0] == null) {
            user[0] = new YMap();
            user[0].set("ids", new YArray());
            user[0].set("ds", new YArray());
            this.yusers.set(userDescription, user[0]);
        }

        ((YArray) user[0].get("ids")).push(Collections.singletonList(clientid));

        // 观察用户映射变化
        this.yusers.observe((event, transaction) -> {
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    YMap userOverwrite = (YMap) yusers.get(userDescription);
                    if (userOverwrite != user[0]) {
                        // 用户被覆盖，将所有数据迁移到新用户对象
                        user[0] = userOverwrite;

                        // 迁移所有客户端ID
                        clients.forEach((cid, desc) -> {
                            if (userDescription.equals(desc)) {
                                ((YArray) user[0].get("ids")).push(Collections.singletonList(cid));
                            }
                        });

                        // 迁移删除集
                        DSEncoderV1 encoder = new UpdateEncoderV1();
                        DeleteSet ds = dss.get(userDescription);
                        if (ds != null) {
                            writeDeleteSet(encoder, ds);
                            ((YArray) user[0].get("ds")).push(Collections.singletonList(encoder.toUint8Array()));
                        }
                    }
                }
            }, 0);
        });

        // 监听事务完成事件
        doc.<Transaction>on("afterTransaction", transaction -> {
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    YArray yds = (YArray) user[0].get("ds");
                    DeleteSet ds = transaction.deleteSet;
                    if (transaction.local && !ds.clients.isEmpty() && filter.apply(transaction, ds)) {
                        DSEncoderV1 encoder = new UpdateEncoderV1();
                        writeDeleteSet(encoder, ds);
                        yds.push(Collections.singletonList(encoder.toUint8Array()));
                    }
                }
            }, 0);
        });
    }

    /**
     * 根据客户端ID获取用户描述
     * @param clientid 客户端ID
     * @return 用户描述或null
     */
    public String getUserByClientId(int clientid) {
        return clients.getOrDefault(clientid, null);
    }

    /**
     * 根据删除的ID获取用户描述
     * @param id 删除的ID
     * @return 用户描述或null
     */
    public String getUserByDeletedId(ID id) {
        for (Map.Entry<String, DeleteSet> entry : dss.entrySet()) {
            if (isDeleted(entry.getValue(), id)) {
                return entry.getKey();
            }
        }
        return null;
    }
}