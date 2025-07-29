package com.ai.test;

import com.ai.Y;
import com.ai.myutils.observable.BinaryConsumer;
import com.ai.types.YArray;
import com.ai.types.YMap;
import com.ai.types.YXmlFragment;
import com.ai.types.YXmlText;
import com.ai.types.vo.SubdocsEvent;
import com.ai.types.ytext.YText;
import com.ai.utils.Doc;
import com.ai.utils.DocOptions;
import com.ai.utils.Transaction;
import com.ai.utils.undo.UndoManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class DocTest {

    @Test
    public void testAfterTransactionRecursion() {
        Doc ydoc = new Doc();
        YXmlFragment yxml = ydoc.getXmlFragment("");
        ydoc.<Transaction>on("afterTransaction", transaction -> {
            if ("test".equals(transaction.origin)) {
                yxml.toJSON();
            }
        });

        ydoc.transact(tr -> {
            for (int i = 0; i < 15000; i++) {
                yxml.push(Collections.singletonList(new YXmlText("a")));
            }
            return null;
        }, "test");
    }

    @Test
    public void testOriginInTransaction() {
        Doc doc = new Doc();
        YText ytext = doc.getText(null);
        List<String> origins = new ArrayList<>();

        doc.<Transaction>on("afterTransaction", transaction -> {
            origins.add((String) transaction.origin);
            if (origins.size() <= 1) {
                ytext.toDelta(Y.snapshot(doc), null, null);
                doc.transact((tr) -> {
                    ytext.insert(0, "a", null);
                    return null;
                }, "nested");
            }
        });

        doc.transact((transaction) -> {
            ytext.insert(0, "0", null);
            return null;
        }, "first");

        assertArrayEquals(new String[]{"first", "cleanup", "nested"}, origins.toArray());
    }

    @Test
    public void testClientIdDuplicateChange() {
        Doc doc1 = new Doc();
        doc1.clientID = 0;
        Doc doc2 = new Doc();
        doc2.clientID = 0;
        assertEquals(doc2.clientID, doc1.clientID);

        doc1.getArray("a").insert(0, Arrays.asList(1, 2));
        Y.applyUpdate(doc2, Y.encodeStateAsUpdate(doc1, null));
        assertNotEquals(doc2.clientID, doc1.clientID);
    }

    @Test
    public void testGetTypeEmptyId() {
        Doc doc1 = new Doc();
        doc1.getText("").insert(0, "h", null);
        doc1.getText("").insert(1, "i", null);

        Doc doc2 = new Doc();
        Y.applyUpdate(doc2, Y.encodeStateAsUpdate(doc1, null));
        assertEquals("hi", doc2.getText("").toString());
        assertEquals("hi", doc2.getText("").toString());
    }

    @Test
    public void testToJSON() {
        Doc doc = new Doc();
        assertEquals(Collections.emptyMap(), doc.toJSON());

        YArray arr = doc.getArray("array");
        arr.push(Collections.singletonList("test1"));

        YMap map = doc.getMap("map");
        map.set("k1", "v1");
        YMap map2 = new YMap();
        map.set("k2", map2);
        map2.set("m2k1", "m2v1");

        Map<String, Object> expected = new HashMap<>();
        expected.put("array", Collections.singletonList("test1"));
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("k1", "v1");
        Map<String, Object> innerMap = new HashMap<>();
        innerMap.put("m2k1", "m2v1");
        nestedMap.put("k2", innerMap);
        expected.put("map", nestedMap);

        assertEquals(expected, doc.toJSON());
    }

    @Test
    public void testSubdoc() {
        Doc doc = new Doc();
        doc.load();

        List<Object> event = new ArrayList<>();
        doc.<SubdocsEvent, Doc, Transaction>on("subdocs", (subdocs1, doc1, transaction1) -> {
            event.clear();
            event.add(Arrays.asList(subdocs1.added.stream().map(x -> x.guid).toArray()));
            event.add(Arrays.asList(subdocs1.removed.stream().map(x -> x.guid).toArray()));
            event.add(Arrays.asList(subdocs1.loaded.stream().map(x -> x.guid).toArray()));
        });

        YMap subdocs = doc.getMap("mysubdocs");
        Doc docA = new Doc(new DocOptions("a"));
        docA.load();
        subdocs.set("a", docA);
        assertEquals(Arrays.asList(Arrays.asList("a"), Collections.emptyList(), Arrays.asList("a")), event);

        event.clear();
        ((Doc) subdocs.get("a")).load();
        assertTrue(event.isEmpty());

        event.clear();
        ((Doc) subdocs.get("a")).destroy();
        assertEquals(Arrays.asList(Arrays.asList("a"), Arrays.asList("a"), Collections.emptyList()), event);
        ((Doc) subdocs.get("a")).load();
        assertEquals(Arrays.asList(Collections.emptyList(), Collections.emptyList(), Arrays.asList("a")), event);

        DocOptions docOptions = new DocOptions("a");
        docOptions.shouldLoad = false;
        subdocs.set("b", new Doc(docOptions));
        assertEquals(Arrays.asList(Arrays.asList("a"), Collections.emptyList(), Collections.emptyList()), event);
        ((Doc) subdocs.get("b")).load();
        assertEquals(Arrays.asList(Collections.emptyList(), Collections.emptyList(), Arrays.asList("a")), event);

        Doc docC = new Doc(new DocOptions("c"));
        docC.load();
        subdocs.set("c", docC);
        assertEquals(Arrays.asList(Arrays.asList("c"), Collections.emptyList(), Arrays.asList("c")), event);

        assertEquals(new LinkedHashSet<>(Arrays.asList("a", "c")), doc.getSubdocGuids());
    }

    @Test
    public void testSubdocLoadEdgeCases() {
        Doc ydoc = new Doc();
        YArray yarray = ydoc.getArray(null);
        Doc subdoc1 = new Doc();

        AtomicReference<SubdocsEvent> lastEvent = new AtomicReference<>();
        ydoc.<SubdocsEvent, Doc, Transaction>on("subdocs", (subdocs1, doc, transaction) -> {
            lastEvent.set(subdocs1);
        });

        yarray.insert(0, Arrays.asList(subdoc1));
        assertTrue(subdoc1.shouldLoad);
        assertFalse(subdoc1.autoLoad);
        assertTrue(lastEvent.get() != null && lastEvent.get().loaded.contains(subdoc1));
        //   t.assert(lastEvent !== null && lastEvent.added.has(subdoc1))
        assertTrue(lastEvent.get() != null && lastEvent.get().added.contains(subdoc1));

        subdoc1.destroy();
        Doc subdoc2 = (Doc) yarray.get(0);
        assertNotSame(subdoc1, subdoc2);

        assertTrue(lastEvent.get() != null && lastEvent.get().added.contains(subdoc2));
        assertFalse(lastEvent.get() != null && lastEvent.get().loaded.contains(subdoc2));

        subdoc2.load();
        assertTrue(lastEvent.get() != null && !lastEvent.get().added.contains(subdoc2));
        assertTrue(lastEvent.get() != null && lastEvent.get().loaded.contains(subdoc2));

        // apply from remote
        Doc ydoc2 = new Doc();
        ydoc2.<SubdocsEvent, Doc, Transaction>on("subdocs", (event, doc, transaction) -> {
            lastEvent.set(event);
        });

        Y.applyUpdate(ydoc2, Y.encodeStateAsUpdate(ydoc, null));
        Doc subdoc3 = (Doc) ydoc2.getArray("").get(0);
        assertFalse(subdoc3.shouldLoad);
        assertFalse(subdoc3.autoLoad);
        assertTrue(lastEvent.get() != null && lastEvent.get().added.contains(subdoc3));
        assertTrue(lastEvent.get() != null && !lastEvent.get().loaded.contains(subdoc3));
        // load
        subdoc3.load();
        assertTrue(subdoc3.shouldLoad);
        assertFalse(lastEvent.get() != null && lastEvent.get().added.contains(subdoc3));
        assertTrue(lastEvent.get() != null && lastEvent.get().loaded.contains(subdoc3));
    }

    @Test
    public void testSubdocsUndo() {
        Doc ydoc = new Doc();
        YXmlFragment elems = ydoc.getXmlFragment(null);
        UndoManager undoManager = new UndoManager(elems, null);
        Doc subdoc = new Doc();

        elems.insert(0, Arrays.asList(subdoc));
        undoManager.undo();
        undoManager.redo();
        assertEquals(1, elems.length());
    }

    @Test
    public void testLoadDocsEvent() throws Exception {
        Doc ydoc = new Doc();
        assertFalse(ydoc.isLoaded);

        boolean[] loadedEvent = {false};
        ydoc.on("load", (o) -> {
            loadedEvent[0] = true;
        });

        ydoc.emit("load", ydoc);
        ydoc.whenLoaded.thenAccept(doc -> {
            System.out.println("doc = " + doc);
        });
        assertTrue(loadedEvent[0]);
        assertTrue(ydoc.isLoaded);
    }

    @Test
    public void testSyncDocsEvent() throws Exception {
        Doc ydoc = new Doc();
        assertFalse(ydoc.isLoaded);
        assertFalse(ydoc.isSynced);

        boolean[] loadedEvent = {false};
        ydoc.once("load", (o) -> {
            loadedEvent[0] = true;
        });

        boolean[] syncedEvent = {false};
        ydoc.once("sync", (BinaryConsumer<Boolean, Doc>) (isSynced, doc) -> {
            syncedEvent[0] = true;
            assertTrue(isSynced);
        });

        ydoc.emit("sync", true, ydoc);
        ydoc.whenLoaded.get();
        Object oldWhenSynced = ydoc.whenSynced;
        ydoc.whenSynced.get();

        assertTrue(loadedEvent[0]);
        assertTrue(syncedEvent[0]);
        assertTrue(ydoc.isLoaded);
        assertTrue(ydoc.isSynced);

        boolean[] loadedEvent2 = {false};
        ydoc.on("load", (o) -> {
            loadedEvent2[0] = true;
        });

        boolean[] syncedEvent2 = {false};
        ydoc.<Boolean,Doc>on("sync", (isSynced, doc) -> {
            syncedEvent2[0] = true;
            assertFalse(isSynced);
        });

        ydoc.emit("sync", false, ydoc);
        assertFalse(loadedEvent2[0]);
        assertTrue(syncedEvent2[0]);
        assertTrue(ydoc.isLoaded);
        assertFalse(ydoc.isSynced);
        assertNotSame(oldWhenSynced, ydoc.whenSynced);
    }
}