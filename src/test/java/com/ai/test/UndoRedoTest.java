package com.ai.test;

import com.ai.Y;
import com.ai.myutils.Maps;
import com.ai.structs.ContentType;
import com.ai.structs.item.Item;
import com.ai.test.helper.TestConnector;
import com.ai.test.helper.TestHelper;
import com.ai.test.helper.TestYInstance;
import com.ai.types.*;
import com.ai.types.arraytype.AbstractType;
import com.ai.types.ytext.YText;
import com.ai.utils.Doc;
import com.ai.utils.DocOptions;
import com.ai.utils.Transaction;
import com.ai.utils.YEvent;
import com.ai.utils.undo.StackItem;
import com.ai.utils.undo.StackItemEvent;
import com.ai.utils.undo.UndoManager;
import com.ai.utils.undo.UndoManagerOptions;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public class UndoRedoTest {
    private void testYjsMerge(Doc ydoc) {
        YXmlText content = (YXmlText) ydoc.get("text", YXmlText::new);

        content.format(0, 6, Maps.of("bold", null));
        content.format(6, 4, Maps.of("type", "text"));

        JSONArray expected = new JSONArray();
        JSONObject item = new JSONObject();
        item.put("attributes", Maps.of("type", "text"));
        item.put("insert", "Merge Test");
        expected.add(item);
        item = new JSONObject();
        item.put("attributes", Maps.of("type", "text", "italic", true));
        item.put("insert", " After");
        expected.add(item);

        assertEquals(expected, content.toDelta());
    }

    private Doc initializeYDoc() {
        Doc ydoc = new Doc(new DocOptions().setGc(false));
        YXmlText content = (YXmlText) ydoc.get("text", YXmlText::new);
        content.insert(0, " After", Maps.of("type", "text", "italic", true));
        content.insert(0, "Test", Maps.of("type", "text"));
        content.insert(0, "Merge ", Maps.of("type", "text", "bold", true));
        return ydoc;
    }

    @Test
    public void testInconsistentFormat() {
        {
            Doc ydoc = initializeYDoc();
            testYjsMerge(ydoc);
        }
        {
            Doc initialYDoc = initializeYDoc();
            Doc ydoc = new Doc(new DocOptions().setGc(false));
            Y.applyUpdate(ydoc, Y.encodeStateAsUpdate(initialYDoc));
            testYjsMerge(ydoc);
        }
    }

// export const testInfiniteCaptureTimeout = tc => {
//   const { array0 } = init(tc, { users: 3 })
//   const undoManager = new UndoManager(array0, { captureTimeout: Number.MAX_VALUE })
//   array0.push([1, 2, 3])
//   undoManager.stopCapturing()
//   array0.push([4, 5, 6])
//   undoManager.undo()
//   t.compare(array0.toArray(), [1, 2, 3])
// }

    @Test
    public void testInfiniteCaptureTimeout() {
        Map<String, Object> initMap = TestHelper.init(new TestHelper.TestCase(new Random()), 2, null);
        YArray array0 = (YArray) initMap.get("array0");
        final UndoManager undoManager = new UndoManager(array0, new UndoManagerOptions().setCaptureTimeout(Integer.MAX_VALUE));

        array0.push(Arrays.asList(1, 2, 3));
        undoManager.stopCapturing();
        array0.push(Arrays.asList(4, 5, 6));
        undoManager.undo();
        List<Integer> expected = Arrays.asList(1, 2, 3);
        assertEquals(expected, array0.toArray());
    }

    //    @Test
    public void testUndoText() {
        Map<String, Object> initMap = TestHelper.init(new TestHelper.TestCase(new Random()), 3, null);
        TestConnector testConnector = (TestConnector) initMap.get("testConnector");
        YText text0 = (YText) initMap.get("text0");
        YText text1 = (YText) initMap.get("text1");
        final UndoManager undoManager = new UndoManager(text0);

        // Items that are added & deleted in the same transaction won't be undo
        text0.insert(0, "test");
        text0.delete(0, 4);
        undoManager.undo();
        assertEquals("", text0.toString());

        // Follow redone items
        text0.insert(0, "a");
        undoManager.stopCapturing();
        text0.delete(0, 1);
        undoManager.stopCapturing();
        undoManager.undo();
        assertEquals("a", text0.toString());
        undoManager.undo();
        assertEquals("", text0.toString());

        text0.insert(0, "abc");
        text1.insert(0, "xyz");
        testConnector.syncAll();
        undoManager.undo();
        assertEquals("xyz", text1.toString());
        undoManager.redo();
        assertEquals("abcxyz", text0.toString() + text1.toString());
        testConnector.syncAll();
        text1.delete(0, 1);
        testConnector.syncAll();
        undoManager.undo();
        assertEquals("xyz", text0.toString());
        undoManager.redo();
        assertEquals("bcxyz", text0.toString());

        // Test marks
        Map<String, Object> format = new HashMap<>();
        format.put("bold", true);
        text0.format(1, 3, format);
        assertEquals(
                Arrays.asList(
                        Collections.singletonMap("insert", "b"),
                        new HashMap<String, Object>() {{
                            put("insert", "cxy");
                            put("attributes", Collections.singletonMap("bold", true));
                        }},
                        Collections.singletonMap("insert", "z")
                ),
                text0.toDelta()
        );
        undoManager.undo();
        assertEquals(
                Collections.singletonList(Collections.singletonMap("insert", "bcxyz")),
                text0.toDelta()
        );
        undoManager.redo();
        assertEquals(
                Arrays.asList(
                        Collections.singletonMap("insert", "b"),
                        new HashMap<String, Object>() {{
                            put("insert", "cxy");
                            put("attributes", Collections.singletonMap("bold", true));
                        }},
                        Collections.singletonMap("insert", "z")
                ),
                text0.toDelta()
        );
    }

    @Test
    public void testEmptyTypeScope() {
        Doc ydoc = new Doc();
        UndoManager um = new UndoManager(Collections.emptyList(), new UndoManagerOptions().setDoc(ydoc));
        YArray yarray = ydoc.getArray();
        um.addToScope(yarray);
        yarray.insert(0, Collections.singletonList(1));
        um.undo();
        assertEquals(0, yarray.length());
    }

    @Test
    public void testRejectUpdateExample() {
        // Setup initial documents and updates
        Doc tmpydoc1 = new Doc();
        tmpydoc1.getArray("restricted").insert(0, Collections.singletonList(1));
        tmpydoc1.getArray("public").insert(0, Collections.singletonList(1));
        int[] update1 = Y.encodeStateAsUpdate(tmpydoc1);

        Doc tmpydoc2 = new Doc();
        tmpydoc2.getArray("public").insert(0, Collections.singletonList(2));
        int[] update2 = Y.encodeStateAsUpdate(tmpydoc2);

        // Create target document
        Doc ydoc = new Doc();
        YArray restrictedType = ydoc.getArray("restricted");

        // Update handler implementation
        Consumer<int[]> updateHandler = (update) -> {
            UndoManager um = new UndoManager(restrictedType,
                    new UndoManagerOptions().setTrackedOrigins(Collections.singleton("remote change")));

            int[] beforePendingDs = ydoc.store.pendingDs;
            int[] beforePendingStructs = ydoc.store.pendingStructs != null ?
                    ydoc.store.pendingStructs.update : null;

            try {
                Y.applyUpdate(ydoc, update, "remote change");
            } finally {
                // Undo all changes
                while (!um.undoStack.isEmpty()) {
                    um.undo();
                }
                um.destroy();

                // Restore pending state
                ydoc.store.pendingDs = (beforePendingDs);
                ydoc.store.pendingStructs = (null);

                if (beforePendingStructs != null) {
                    Y.applyUpdateV2(ydoc, beforePendingStructs);
                }
            }
        };

        // Apply updates
        updateHandler.accept(update1);
        updateHandler.accept(update2);

        // Verify results
        assertEquals(0, restrictedType.length());
        assertEquals(2, ydoc.getArray("public").length());
    }

    @Test
    public void testGlobalScope() {
        Doc ydoc = new Doc();
        UndoManager um = new UndoManager(ydoc);
        YArray yarray = ydoc.getArray();
        yarray.insert(0, Collections.singletonList(1));
        um.undo();
        assertEquals(0, yarray.length());
    }

    @Test
    public void testDoubleUndo() {
        Doc doc = new Doc();
        YText text = doc.getText();
        text.insert(0, "1221");

        UndoManager manager = new UndoManager(text);

        text.insert(2, "3");
        text.insert(3, "3");

        manager.undo();
        manager.undo();

        text.insert(2, "3");

        assertEquals("12321", text.toString());
    }

    //    @Test
    public void testUndoMap() {
        Map<String, Object> initMap = TestHelper.init(new TestHelper.TestCase(new Random()), 2, null);
        TestConnector testConnector = (TestConnector) initMap.get("testConnector");
        YMap map0 = (YMap) initMap.get("map0");
        YMap map1 = (YMap) initMap.get("map1");

        map0.set("a", 0);
        UndoManager undoManager = new UndoManager(map0);

        map0.set("a", 1);
        undoManager.undo();
        assertEquals(0, map0.get("a"));

        undoManager.redo();
        assertEquals(1, map0.get("a"));

        // Test sub-types
        YMap subType = new YMap();
        map0.set("a", subType);
        subType.set("x", 42);
        assertEquals(Maps.of("a", Maps.of("x", 42)), map0.toJSON());

        undoManager.undo();
        assertEquals(1, map0.get("a"));

        undoManager.redo();
        assertEquals(Maps.of("a", Maps.of("x", 42)), map0.toJSON());

        testConnector.syncAll();
        map1.set("a", 44);
        testConnector.syncAll();

        undoManager.undo();
        assertEquals(44, map0.get("a"));

        undoManager.redo();
        assertEquals(44, map0.get("a"));

        // Test setting value multiple times
        map0.set("b", "initial");
        undoManager.stopCapturing();
        map0.set("b", "val1");
        map0.set("b", "val2");
        undoManager.stopCapturing();
        undoManager.undo();
        assertEquals("initial", map0.get("b"));
    }

    //    @Test
    public void testUndoArray() {
        Map<String, Object> initMap = TestHelper.init(new TestHelper.TestCase(new Random()), 3, null);
        TestConnector testConnector = (TestConnector) initMap.get("testConnector");
        YArray array0 = (YArray) initMap.get("array0");
        YArray array1 = (YArray) initMap.get("array1");

        UndoManager undoManager = new UndoManager(array0);
        array0.insert(0, Arrays.asList(1, 2, 3));
        array1.insert(0, Arrays.asList(4, 5, 6));
        testConnector.syncAll();

        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), array0.toArray());
        undoManager.undo();
        assertEquals(Arrays.asList(4, 5, 6), array0.toArray());
        undoManager.redo();
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), array0.toArray());

        testConnector.syncAll();
        array1.delete(0, 1);
        testConnector.syncAll();

        undoManager.undo();
        assertEquals(Arrays.asList(4, 5, 6), array0.toArray());
        undoManager.redo();
        assertEquals(Arrays.asList(2, 3, 4, 5, 6), array0.toArray());

        array0.delete(0, 5);
        YMap ymap = new YMap();
        array0.insert(0, Collections.singletonList(ymap));
        assertEquals(Collections.singletonList(Collections.emptyMap()), array0.toJSON());

        undoManager.stopCapturing();
        ymap.set("a", 1);
        assertEquals(Collections.singletonList(Maps.of("a", 1)), array0.toJSON());

        undoManager.undo();
        assertEquals(Collections.singletonList(Collections.emptyMap()), array0.toJSON());
        undoManager.undo();
        assertEquals(Arrays.asList(2, 3, 4, 5, 6), array0.toJSON());
        undoManager.redo();
        assertEquals(Collections.singletonList(Collections.emptyMap()), array0.toJSON());
        undoManager.redo();
        assertEquals(Collections.singletonList(Maps.of("a", 1)), array0.toJSON());

        testConnector.syncAll();
        assertEquals(Collections.singletonList(Maps.of("a", 1, "b", 2)), array0.toJSON());

        undoManager.undo();
        assertEquals(Collections.singletonList(Maps.of("b", 2)), array0.toJSON());
        undoManager.undo();
        assertEquals(Arrays.asList(2, 3, 4, 5, 6), array0.toJSON());
        undoManager.redo();
        assertEquals(Collections.singletonList(Maps.of("b", 2)), array0.toJSON());
        undoManager.redo();
        assertEquals(Collections.singletonList(Maps.of("a", 1, "b", 2)), array0.toJSON());
    }


    @Test
    public void testUndoXml() {
        Map<String, Object> initMap = TestHelper.init(new TestHelper.TestCase(new Random()), 3, null);
        YXmlElement xml0 = (YXmlElement) initMap.get("xml0");
        UndoManager undoManager = new UndoManager(xml0);

        YXmlElement child = new YXmlElement("p");
        xml0.insert(0, Collections.singletonList(child));
        YXmlText textchild = new YXmlText("content");
        child.insert(0, Collections.singletonList(textchild));
        assertEquals("<undefined><p>content</p></undefined>", xml0.toString());

        undoManager.stopCapturing();
        textchild.format(3, 4, Collections.singletonMap("bold", Collections.emptyMap()));
        assertEquals("<undefined><p>con<bold>tent</bold></p></undefined>", xml0.toString());

        undoManager.undo();
        assertEquals("<undefined><p>content</p></undefined>", xml0.toString());

        undoManager.redo();
        assertEquals("<undefined><p>con<bold>tent</bold></p></undefined>", xml0.toString());

        xml0.delete(0, 1);
        assertEquals("<undefined></undefined>", xml0.toString());

        // 在执行undo前后检查结构
        undoManager.undo();

        assertEquals("<undefined><p>con<bold>tent</bold></p></undefined>", xml0.toString());
    }

    @Test
    public void testUndoEvents() {
        Map<String, Object> initMap = TestHelper.init(new TestHelper.TestCase(new Random()), 3, null);
        YText text0 = (YText) initMap.get("text0");
        UndoManager undoManager = new UndoManager(text0);

        AtomicInteger counter = new AtomicInteger(0);
        AtomicInteger receivedMetadata = new AtomicInteger(-1);

        undoManager.<StackItemEvent, UndoManager>on("stack-item-added", (event, um) -> {
            assertNotNull(event.type);
            assertTrue(event.changedParentTypes.containsKey(text0));
            event.stackItem.meta.put("test", counter.getAndIncrement());
        });

        undoManager.<StackItemEvent, UndoManager>on("stack-item-popped", (event, um) -> {
            assertNotNull(event.type);
            assertTrue(event.changedParentTypes.containsKey(text0));
            receivedMetadata.set((Integer) event.stackItem.meta.get("test"));
        });

        text0.insert(0, "abc");
        undoManager.undo();
        assertEquals(0, receivedMetadata.get());

        undoManager.redo();
        assertEquals(1, receivedMetadata.get());
    }

    @Test
    public void testTrackClass() {
        Map<String, Object> initMap = TestHelper.init(new TestHelper.TestCase(new Random()), 3, null);
        YText text0 = (YText) initMap.get("text0");
        List<TestYInstance> users = (List<TestYInstance>) initMap.get("users");

        UndoManager undoManager = new UndoManager(text0,
                new UndoManagerOptions().setTrackedOrigins(Collections.singleton(Integer.class)));

        users.get(0).transact((transaction) -> {
            text0.insert(0, "abc");
            return null;
        }, 42);

        assertEquals("abc", text0.toString());
        undoManager.undo();
        assertEquals("", text0.toString());
    }

    @Test
    public void testTypeScope() {
        Map<String, Object> initMap = TestHelper.init(new TestHelper.TestCase(new Random()), 3, null);
        YArray array0 = (YArray) initMap.get("array0");

        YText text0 = new YText();
        YText text1 = new YText();
        array0.insert(0, Arrays.asList(text0, text1));

        UndoManager undoManager = new UndoManager(text0);
        UndoManager undoManagerBoth = new UndoManager(Arrays.asList(text0, text1));

        text1.insert(0, "abc");
        assertEquals(0, undoManager.undoStack.size());
        assertEquals(1, undoManagerBoth.undoStack.size());
        assertEquals("abc", text1.toString());

        undoManager.undo();
        assertEquals("abc", text1.toString());

        undoManagerBoth.undo();
        assertEquals("", text1.toString());
    }

    @Test
    public void testUndoInEmbed() {
        Map<String, Object> initMap = TestHelper.init(new TestHelper.TestCase(new Random()), 3, null);
        YText text0 = (YText) initMap.get("text0");
        UndoManager undoManager = new UndoManager(text0);

        YText nestedText = new YText("initial text");
        undoManager.stopCapturing();

        text0.insertEmbed(0, nestedText, Collections.singletonMap("bold", true));
        assertEquals("initial text", nestedText.toString());

        undoManager.stopCapturing();
        nestedText.delete(0, nestedText.length());
        nestedText.insert(0, "other text");
        assertEquals("other text", nestedText.toString());

        undoManager.undo();
        assertEquals("initial text", nestedText.toString());

        undoManager.undo();
        assertEquals(0, text0.length());
    }

    @Test
    public void testUndoDeleteFilter() {
        Map<String, Object> initMap = TestHelper.init(new TestHelper.TestCase(new Random()), 3, null);
        YArray array0 = (YArray) initMap.get("array0");

        UndoManager undoManager = new UndoManager(array0,
                new UndoManagerOptions().setDeleteFilter(item ->
                        !(item instanceof Item) ||
                                (((Item) item).content instanceof ContentType &&
                                        ((ContentType) ((Item) item).content).type._map.isEmpty())));

        YMap map0 = new YMap();
        map0.set("hi", 1);
        YMap map1 = new YMap();
        array0.insert(0, Arrays.asList(map0, map1));

        undoManager.undo();
        assertEquals(1, array0.length());
        assertEquals(1, ((AbstractType) array0.get(0))._map.size());
    }

    @Test
    public void testUndoUntilChangePerformed() {
        Doc doc = new Doc();
        Doc doc2 = new Doc();

        doc.<int[], Object, Doc, Transaction>on("update", (update, o, d, t) -> Y.applyUpdate(doc2, update));
        doc2.<int[], Object, Doc, Transaction>on("update", (update, o, d, t) -> Y.applyUpdate(doc, update));

        YArray yArray = doc.getArray("array");
        YArray yArray2 = doc2.getArray("array");

        YMap yMap = new YMap();
        yMap.set("hello", "world");
        yArray.push(Collections.singletonList(yMap));

        YMap yMap2 = new YMap();
        yMap2.set("key", "value");
        yArray.push(Collections.singletonList(yMap2));

        UndoManager undoManager = new UndoManager(Collections.singletonList(yArray),
                new UndoManagerOptions().setTrackedOrigins(Collections.singleton(doc.clientID)));

        UndoManager undoManager2 = new UndoManager(Collections.singletonList(doc2.get("array")),
                new UndoManagerOptions().setTrackedOrigins(Collections.singleton(doc2.clientID)));

        Y.transact(doc, (transaction) -> yMap2.set("key", "value modified"), doc.clientID);
        undoManager.stopCapturing();

        Y.transact(doc, (transaction) -> yMap.set("hello", "world modified"), doc.clientID);
        Y.transact(doc2, (transaction) -> {
            yArray2.delete(0);
            return null;
        }, doc2.clientID);

        undoManager2.undo();
        undoManager.undo();
        assertEquals("value", yMap2.get("key"));
    }

    @Test
    public void testUndoNestedUndoIssue() {
        Doc doc = new Doc(new DocOptions().setGc(false));
        YMap design = doc.getMap();
        UndoManager undoManager = new UndoManager(design, new UndoManagerOptions().setCaptureTimeout(0));

        YMap text = new YMap();
        YArray blocks1 = new YArray();
        YMap blocks1block = new YMap();

        doc.transact((transaction) -> {
            blocks1block.set("text", "Type Something");
            blocks1.push(Collections.singletonList(blocks1block));
            text.set("blocks", blocks1block);
            design.set("text", text);
            return null;
        });

        YArray blocks2 = new YArray();
        YMap blocks2block = new YMap();
        doc.transact((transaction) -> {
            blocks2block.set("text", "Something");
            blocks2.push(Collections.singletonList(blocks2block));
            text.set("blocks", blocks2block);
            return null;
        });

        YArray blocks3 = new YArray();
        YMap blocks3block = new YMap();
        doc.transact((transaction) -> {
            blocks3block.set("text", "Something Else");
            blocks3.push(Collections.singletonList(blocks3block));
            text.set("blocks", blocks3block);
            return null;
        });

        assertEquals(Maps.of("text", Maps.of("blocks", Maps.of("text", "Something Else"))), design.toJSON());

        undoManager.undo();
        assertEquals(Maps.of("text", Maps.of("blocks", Maps.of("text", "Something"))), design.toJSON());

        undoManager.undo();
        assertEquals(Maps.of("text", Maps.of("blocks", Maps.of("text", "Type Something"))), design.toJSON());

        undoManager.undo();
        assertEquals(Collections.emptyMap(), design.toJSON());

        undoManager.redo();
        assertEquals(Maps.of("text", Maps.of("blocks", Maps.of("text", "Type Something"))), design.toJSON());

        undoManager.redo();
        assertEquals(Maps.of("text", Maps.of("blocks", Maps.of("text", "Something"))), design.toJSON());

        undoManager.redo();
        assertEquals(Maps.of("text", Maps.of("blocks", Maps.of("text", "Something Else"))), design.toJSON());
    }

    @Test
    public void testConsecutiveRedoBug() {
        Doc doc = new Doc();
        YMap yRoot = doc.getMap();
        UndoManager undoMgr = new UndoManager(yRoot);

        YMap yPoint = new YMap();
        yPoint.set("x", 0);
        yPoint.set("y", 0);
        yRoot.set("a", yPoint);
        undoMgr.stopCapturing();

        yPoint.set("x", 100);
        yPoint.set("y", 100);
        undoMgr.stopCapturing();

        yPoint.set("x", 200);
        yPoint.set("y", 200);
        undoMgr.stopCapturing();

        yPoint.set("x", 300);
        yPoint.set("y", 300);
        undoMgr.stopCapturing();

        assertEquals(Maps.of("x", 300, "y", 300), yPoint.toJSON());

        undoMgr.undo();
        assertEquals(Maps.of("x", 200, "y", 200), yPoint.toJSON());

        undoMgr.undo();
        assertEquals(Maps.of("x", 100, "y", 100), yPoint.toJSON());

        undoMgr.undo();
        assertEquals(Maps.of("x", 0, "y", 0), yPoint.toJSON());

        undoMgr.undo();
        assertNull(yRoot.get("a"));

        undoMgr.redo();
        yPoint = (YMap) yRoot.get("a");
        assertEquals(Maps.of("x", 0, "y", 0), yPoint.toJSON());

        undoMgr.redo();
        assertEquals(Maps.of("x", 100, "y", 100), yPoint.toJSON());

        undoMgr.redo();
        assertEquals(Maps.of("x", 200, "y", 200), yPoint.toJSON());

        undoMgr.redo();
        assertEquals(Maps.of("x", 300, "y", 300), yPoint.toJSON());
    }

    @Test
    public void testUndoXmlBug() {
        String origin = "origin";
        Doc doc = new Doc();
        YXmlFragment fragment = doc.getXmlFragment("t");
        UndoManager undoManager = new UndoManager(fragment,
                new UndoManagerOptions()
                        .setCaptureTimeout(0)
                        .setTrackedOrigins(Collections.singleton(origin)));

        doc.transact((transaction) -> {
            YXmlElement e = new YXmlElement("test-node");
            e.setAttribute("a", "100");
            e.setAttribute("b", "0");
            fragment.insert(fragment.length(), Collections.singletonList(e));
            return null;
        }, origin);

        doc.transact((transaction) -> {
            YXmlElement e = (YXmlElement) fragment.get(0);
            e.setAttribute("a", "200");
            return null;
        }, origin);

        doc.transact((transaction) -> {
            YXmlElement e = (YXmlElement) fragment.get(0);
            e.setAttribute("a", "180");
            e.setAttribute("b", "50");
            return null;
        }, origin);

        undoManager.undo();
        undoManager.undo();
        undoManager.undo();

        undoManager.redo();
        undoManager.redo();
        undoManager.redo();
        assertEquals("<test-node a=\"180\" b=\"50\"></test-node>", fragment.toString());
    }

    @Test
    public void testUndoBlockBug() {
        Doc doc = new Doc(new DocOptions().setGc(false));
        YMap design = doc.getMap();
        UndoManager undoManager = new UndoManager(design, new UndoManagerOptions().setCaptureTimeout(0));

        YMap text = new YMap();

        YArray blocks1 = new YArray();
        YMap blocks1block = new YMap();
        doc.transact((transaction) -> {
            blocks1block.set("text", "1");
            blocks1.push(Collections.singletonList(blocks1block));
            text.set("blocks", blocks1block);
            design.set("text", text);
            return null;
        });

        YArray blocks2 = new YArray();
        YMap blocks2block = new YMap();
        doc.transact((transaction) -> {
            blocks2block.set("text", "2");
            blocks2.push(Collections.singletonList(blocks2block));
            text.set("blocks", blocks2block);
            return null;
        });

        YArray blocks3 = new YArray();
        YMap blocks3block = new YMap();
        doc.transact((transaction) -> {
            blocks3block.set("text", "3");
            blocks3.push(Collections.singletonList(blocks3block));
            text.set("blocks", blocks3block);
            return null;
        });

        YArray blocks4 = new YArray();
        YMap blocks4block = new YMap();
        doc.transact((transaction) -> {
            blocks4block.set("text", "4");
            blocks4.push(Collections.singletonList(blocks4block));
            text.set("blocks", blocks4block);
            return null;
        });

        undoManager.undo();
        undoManager.undo();
        undoManager.undo();
        undoManager.undo();

        undoManager.redo();
        undoManager.redo();
        undoManager.redo();
        undoManager.redo();

        assertEquals(Maps.of("text", Maps.of("blocks", Maps.of("text", "4"))), design.toJSON());
    }

    @Test
    public void testUndoDeleteTextFormat() {
        Doc doc = new Doc();
        YText text = doc.getText();
        text.insert(0, "Attack ships on fire off the shoulder of Orion.");

        Doc doc2 = new Doc();
        YText text2 = doc2.getText();
        Y.applyUpdate(doc2, Y.encodeStateAsUpdate(doc));

        UndoManager undoManager = new UndoManager(text);

        text.format(13, 7, Collections.singletonMap("bold", true));
        undoManager.stopCapturing();
        Y.applyUpdate(doc2, Y.encodeStateAsUpdate(doc));

        text.format(16, 4, Collections.singletonMap("bold", null));
        undoManager.stopCapturing();
        Y.applyUpdate(doc2, Y.encodeStateAsUpdate(doc));

        undoManager.undo();
        Y.applyUpdate(doc2, Y.encodeStateAsUpdate(doc));

        List<Map<String, Object>> expect = Arrays.asList(
                Maps.of("insert", "Attack ships "),
                Maps.of("insert", "on fire", "attributes", Maps.of("bold", true)),
                Maps.of("insert", " off the shoulder of Orion.")
        );
        assertEquals(expect, text.toDelta());
        assertEquals(expect, text2.toDelta());
    }

    @Test
    public void testBehaviorOfIgnoreRemoteMapChangesProperty() {
        Doc doc = new Doc();
        Doc doc2 = new Doc();

        doc.<int[], Object, Doc, Transaction>on("update", (update, o, d, t) -> Y.applyUpdate(doc2, update, doc));
        doc2.<int[], Object, Doc, Transaction>on("update", (update, o, d, t) -> Y.applyUpdate(doc, update, doc2));

        YMap map1 = doc.getMap();
        YMap map2 = doc2.getMap();

        UndoManager um1 = new UndoManager(map1, new UndoManagerOptions().setIgnoreRemoteMapChanges(true));

        map1.set("x", 1);
        map2.set("x", 2);
        map1.set("x", 3);
        map2.set("x", 4);

        um1.undo();
        assertEquals(2, map1.get("x"));
        assertEquals(2, map2.get("x"));
    }

    @Test
    public void testSpecialDeletionCase() {
        String origin = "undoable";
        Doc doc = new Doc();
        YXmlFragment fragment = doc.getXmlFragment();
        UndoManager undoManager = new UndoManager(fragment,
                new UndoManagerOptions().setTrackedOrigins(Collections.singleton(origin)));

        doc.transact((transaction) -> {
            YXmlElement e = new YXmlElement("test");
            e.setAttribute("a", "1");
            e.setAttribute("b", "2");
            fragment.insert(0, Collections.singletonList(e));
            return null;
        });

        assertEquals("<test a=\"1\" b=\"2\"></test>", fragment.toString());

        doc.transact((transaction) -> {
            YXmlElement e = (YXmlElement) fragment.get(0);
            e.setAttribute("b", "3");
            fragment.delete(0);
            return null;
        }, origin);

        assertEquals("", fragment.toString());

        undoManager.undo();
        assertEquals("<test a=\"1\" b=\"2\"></test>", fragment.toString());
    }

    @Test
    public void testUndoDeleteInMap() {
        Map<String, Object> initMap = TestHelper.init(new TestHelper.TestCase(new Random()), 3, null);
        YMap map0 = (YMap) initMap.get("map0");

        UndoManager undoManager = new UndoManager(map0, new UndoManagerOptions().setCaptureTimeout(0));

        map0.set("a", "a");
        map0.delete("a");
        map0.set("a", "b");
        map0.delete("a");
        map0.set("a", "c");
        map0.delete("a");
        map0.set("a", "d");

        assertEquals(Maps.of("a", "d"), map0.toJSON());

        undoManager.undo();
        assertEquals(Collections.emptyMap(), map0.toJSON());

        undoManager.undo();
        assertEquals(Maps.of("a", "c"), map0.toJSON());

        undoManager.undo();
        assertEquals(Collections.emptyMap(), map0.toJSON());

        undoManager.undo();
        assertEquals(Maps.of("a", "b"), map0.toJSON());

        undoManager.undo();
        assertEquals(Collections.emptyMap(), map0.toJSON());

        undoManager.undo();
        assertEquals(Maps.of("a", "a"), map0.toJSON());
    }

    @Test
    public void testUndoDoingStackItem() {
        Doc doc = new Doc();
        YText text = doc.getText("text");
        UndoManager undoManager = new UndoManager(Collections.singletonList(text));

        undoManager.<StackItemEvent, UndoManager>on("stack-item-added", (event, um) -> {
            event.stackItem.meta.put("str", "42");
        });

        AtomicReference<String> metaUndo = new AtomicReference<>(null);
        AtomicReference<String> metaRedo = new AtomicReference<>(null);

        text.observe((event, transaction) -> {
            if (event.transaction.origin == undoManager) {
                if (undoManager.undoing) {
                    metaUndo.set((String) undoManager.currStackItem.meta.get("str"));
                } else if (undoManager.redoing) {
                    metaRedo.set((String) undoManager.currStackItem.meta.get("str"));
                }
            }
        });

        text.insert(0, "abc");
        undoManager.undo();
        undoManager.redo();

        assertEquals("42", metaUndo.get());
        assertEquals("42", metaRedo.get());
        assertNull(undoManager.currStackItem);
    }

}
