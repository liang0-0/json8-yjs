package com.ai.utils;

import com.ai.myutils.observable.BinaryConsumer;
import com.ai.myutils.observable.ObservableV2;
import com.ai.structs.ContentDoc;
import com.ai.structs.item.Item;
import com.ai.types.YArray;
import com.ai.types.YMap;
import com.ai.types.YXmlElement;
import com.ai.types.YXmlFragment;
import com.ai.types.arraytype.AbstractType;
import com.ai.types.ytext.YText;
import com.ai.utils.structstore.StructStore;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A Yjs instance handles the state of shared data.
 */
public class Doc extends ObservableV2 {
    public boolean gc;
    Predicate<Item> gcFilter;
    public int clientID;
    public String guid;
    String collectionid;
    public Map<String, AbstractType<?>> share;
    public StructStore store;
    Transaction _transaction;
    List<Transaction> _transactionCleanups;
    @Getter
    private Set<Doc> subdocs;
    public Item _item;
    public boolean shouldLoad;
    public boolean autoLoad;
    public Object meta;
    public boolean isLoaded;
    public boolean isSynced;
    private boolean isDestroyed;
    public CompletableFuture<Doc> whenLoaded;
    public CompletableFuture<Void> whenSynced;


    public Doc() {
        this(new DocOptions());
    }

    public Doc(DocOptions opts) {
        super();
        this.gc = opts.gc;
        this.gcFilter = opts.gcFilter;
        this.clientID = generateNewClientId();
        this.guid = opts.guid != null ? opts.guid : UUID.randomUUID().toString();
        this.collectionid = opts.collectionid;
        this.share = new HashMap<>();
        this.store = new StructStore();
        this._transactionCleanups = new ArrayList<>();
        this.subdocs = new LinkedHashSet<>();
        this.shouldLoad = opts.shouldLoad;
        this.autoLoad = opts.autoLoad;
        this.meta = opts.meta;
        this.isLoaded = false;
        this.isSynced = false;
        this.isDestroyed = false;

        this.whenLoaded = new CompletableFuture<>();
        this.<Doc>on("load", doc -> {
            Doc.this.isLoaded = true;
            Doc.this.whenLoaded.complete(doc);
        });

        this.whenSynced = provideSyncedPromise();
        this.<Boolean, Doc>on("sync", (isSynced, doc) -> {
            boolean synced = isSynced == null || isSynced;
            if (!synced && Doc.this.isSynced) {
                Doc.this.whenSynced = provideSyncedPromise();
            }
            Doc.this.isSynced = synced;
            if (Doc.this.isSynced && !Doc.this.isLoaded) {
                Doc.this.emit("load", Doc.this);
            }
        });
    }

    static int generateNewClientId() {
        return new Random().nextInt(Integer.MAX_VALUE);
    }

    private CompletableFuture<Void> provideSyncedPromise() {
        // 使用AtomicReference来保存eventHandler引用
        AtomicReference<BinaryConsumer<Boolean, Doc>> eventHandlerRef = new AtomicReference<>();

        // 创建future
        CompletableFuture<Void> future = new CompletableFuture<>();

        // 定义eventHandler
        BinaryConsumer<Boolean, Doc> eventHandler = (isSynced, doc) -> {
            if (isSynced == null || isSynced) {
                this.off("sync", eventHandlerRef.get());
                future.complete(null);
            }
        };

        // 将eventHandler保存到AtomicReference中
        eventHandlerRef.set(eventHandler);

        // 添加监听器
        this.on("sync", eventHandler);

        return future;
    }

    public void load() {
        Item item = this._item;
        if (item != null && !this.shouldLoad) {
            Transaction.transact(((AbstractType<?>) item.parent).doc, transaction -> {
                transaction.subdocsLoaded.add(this);
                return null;
            }, null, true);
        }
        this.shouldLoad = true;
    }

    public Set<String> getSubdocGuids() {
        Set<String> guids = new LinkedHashSet<>();
        for (Doc doc : this.subdocs) {
            guids.add(doc.guid);
        }
        return guids;
    }

    public AbstractType transact(Function<Transaction, AbstractType> f) {
        return transact(f, null);
    }

    public AbstractType transact(Function<Transaction, AbstractType> f, Object origin) {
        return Transaction.transact(this, f, origin);
    }

    public AbstractType get(String name) {
        return this.get(name, AbstractType::new);
    }

    /**
     * Gets or creates a type with the specified name and constructor
     *
     * @param name            The name of the type to get/create
     * @param typeConstructor Supplier that creates a new instance of the type
     * @param <T>             The type of AbstractType to return
     * @return The existing or newly created type
     * @throws IllegalStateException if type exists with different constructor
     */
    @SuppressWarnings("unchecked")
    public AbstractType get(String name, Supplier<AbstractType> typeConstructor) {
        // Compute if absent
        AbstractType type = share.computeIfAbsent(name, k -> {
            AbstractType t = typeConstructor.get();
            t._integrate(this, null);
            return t;
        });

        // Check type compatibility
        Class<?> existingTypeClass = type.getClass();
        Class<?> expectedTypeClass = typeConstructor.get().getClass();

        if (!expectedTypeClass.isAssignableFrom(existingTypeClass)) {
            if (existingTypeClass == AbstractType.class) {
                // Upgrade existing abstract type to concrete type
                AbstractType newType = typeConstructor.get();
                transferTypeProperties(type, newType);
                share.put(name, newType);
                newType._integrate(this, null);
                return newType;
            } else {
                throw new IllegalStateException(
                        "Type with the name " + name + " has already been defined with a different constructor");
            }
        }

        return type;
    }

    /**
     * Transfers properties from old type to new type
     */
    private void transferTypeProperties(AbstractType type, AbstractType t) {
        // Transfer map items
        t._map = type._map;
        type._map.forEach((key, item) -> {
            Item n = (Item) item;
            for (; n != null; n = n.left) {
                n.parent = t;
            }
        });

        // Transfer start items
        t._start = type._start;
        for (Item n = t._start; n != null; n = n.right) {
            n.parent = t;
        }

        // Transfer length
        t._length = type._length;
    }

    public YArray getArray() {
        return getArray("");
    }
    public YArray getArray(String name) {
        return (YArray) this.get(name, YArray::new);
    }

    public YText getText() {
        return (YText) this.get("", YText::new);
    }

    public YText getText(String name) {
        return (YText) this.get(name, YText::new);
    }

    public YMap getMap() {
        return (YMap) this.get("", YMap::new);
    }
    public YMap getMap(String name) {
        return (YMap) this.get(name, YMap::new);
    }

    public YXmlElement getXmlElement(String name) {
        return (YXmlElement) this.get(name, YXmlElement::new);
    }

    public YXmlFragment getXmlFragment() {
        return (YXmlFragment) this.get("", YXmlFragment::new);
    }
    public YXmlFragment getXmlFragment(String name) {
        return (YXmlFragment) this.get(name, YXmlFragment::new);
    }

    public Map<String, Object> toJSON() {
        Map<String, Object> doc = new HashMap<>();
        this.share.forEach((key, value) -> {
            doc.put(key, value.toJSON());
        });
        return doc;
    }

    public void destroy() {
        this.isDestroyed = true;
        this.subdocs.forEach(Doc::destroy);
        Item item = this._item;
        if (item != null) {
            this._item = null;
            ContentDoc content = (ContentDoc) item.content;
            content.doc = new Doc(new DocOptions(this.guid, false, content.opts));
            content.doc._item = item;

            Transaction.transact(((AbstractType<?>) item.parent).doc, transaction -> {
                Doc doc = content.doc;
                if (!item.deleted()) {
                    transaction.subdocsAdded.add(doc);
                }
                transaction.subdocsRemoved.add(this);
                return null;
            }, null, true);
        }
        this.emit("destroyed", true);
        this.emit("destroy", this);
        super.destroy();
    }

}