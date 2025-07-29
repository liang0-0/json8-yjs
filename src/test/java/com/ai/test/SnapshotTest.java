package com.ai.test;

import com.ai.Y;
import com.ai.myutils.Maps;
import com.ai.test.helper.TestConnector;
import com.ai.test.helper.TestHelper;
import com.ai.types.YArray;
import com.ai.types.YMap;
import com.ai.types.YXmlElement;
import com.ai.utils.Doc;
import com.ai.utils.DocOptions;
import com.ai.utils.Snapshot;
import com.ai.utils.Transaction;
import org.junit.jupiter.api.Test;

import static java.util.Collections.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

public class SnapshotTest {

    @Test
    public void testBasic() {
        Doc ydoc = new Doc(new DocOptions().setGc(false));
        ydoc.getText().insert(0, "world!");
        Snapshot snapshot = Y.snapshot(ydoc);
        ydoc.getText().insert(0, "hello ");
        Doc restored = Y.createDocFromSnapshot(ydoc, snapshot);
        assertEquals("world!", restored.getText().toString());
    }

    @Test
    public void testBasicXmlAttributes() {
        Doc ydoc = new Doc(new DocOptions().setGc(false));
        YXmlElement yxml = (YXmlElement) ydoc.getMap().set("el", new YXmlElement("div"));
        Snapshot snapshot1 = Y.snapshot(ydoc);
        yxml.setAttribute("a", "1");
        Snapshot snapshot2 = Y.snapshot(ydoc);
        yxml.setAttribute("a", "2");

        assertEquals(Maps.of("a", "2"), yxml.getAttributes());
        assertEquals(Maps.of("a", "1"), yxml.getAttributes(snapshot2));
        assertEquals(Maps.of(), yxml.getAttributes(snapshot1));
    }

    @Test
    public void testBasicRestoreSnapshot() {
        Doc doc = new Doc(new DocOptions().setGc(false));
        doc.getArray("array").insert(0, singletonList("hello"));
        Snapshot snap = Y.snapshot(doc);
        doc.getArray("array").insert(1, singletonList("world"));

        Doc docRestored = Y.createDocFromSnapshot(doc, snap);

        assertEquals(singletonList("hello"), docRestored.getArray("array").toArray());
        assertEquals(Arrays.asList("hello", "world"), doc.getArray("array").toArray());
    }

    @Test
    public void testEmptyRestoreSnapshot() {
        Doc doc = new Doc(new DocOptions().setGc(false));
        Snapshot snap = Y.snapshot(doc);
        snap.sv.put(9999, 0);
        doc.getArray().insert(0, singletonList("world"));

        Doc docRestored = Y.createDocFromSnapshot(doc, snap);

        assertEquals(emptyList(), docRestored.getArray().toArray());
        assertEquals(singletonList("world"), doc.getArray().toArray());

        // Test with latest state snapshot
        Snapshot snap2 = Y.snapshot(doc);
        Doc docRestored2 = Y.createDocFromSnapshot(doc, snap2);
        assertEquals(singletonList("world"), docRestored2.getArray().toArray());
    }

    @Test
    public void testRestoreSnapshotWithSubType() {
        Doc doc = new Doc(new DocOptions().setGc(false));
        doc.getArray("array").insert(0, singletonList(new YMap()));
        YMap subMap = (YMap) doc.getArray("array").get(0);
        subMap.set("key1", "value1");

        Snapshot snap = Y.snapshot(doc);
        subMap.set("key2", "value2");

        Doc docRestored = Y.createDocFromSnapshot(doc, snap);

        Map<String, Object> expected1 = new HashMap<>();
        expected1.put("key1", "value1");
        assertEquals(singletonList(expected1), docRestored.getArray("array").toJSON());

        Map<String, Object> expected2 = new HashMap<>();
        expected2.put("key1", "value1");
        expected2.put("key2", "value2");
        assertEquals(singletonList(expected2), doc.getArray("array").toJSON());
    }

    @Test
    public void testRestoreDeletedItem1() {
        Doc doc = new Doc(new DocOptions().setGc(false));
        doc.getArray("array").insert(0, Arrays.asList("item1", "item2"));

        Snapshot snap = Y.snapshot(doc);
        doc.getArray("array").delete(0);

        Doc docRestored = Y.createDocFromSnapshot(doc, snap);

        assertEquals(Arrays.asList("item1", "item2"), docRestored.getArray("array").toArray());
        assertEquals(singletonList("item2"), doc.getArray("array").toArray());
    }

    @Test
    public void testRestoreLeftItem() {
        Doc doc = new Doc(new DocOptions().setGc(false));
        doc.getArray("array").insert(0, singletonList("item1"));
        doc.getMap("map").set("test", 1);
        doc.getArray("array").insert(0, singletonList("item0"));

        Snapshot snap = Y.snapshot(doc);
        doc.getArray("array").delete(1);

        Doc docRestored = Y.createDocFromSnapshot(doc, snap);

        assertEquals(Arrays.asList("item0", "item1"), docRestored.getArray("array").toArray());
        assertEquals(singletonList("item0"), doc.getArray("array").toArray());
    }

    @Test
    public void testDeletedItemsBase() {
        Doc doc = new Doc(new DocOptions().setGc(false));
        doc.getArray("array").insert(0, singletonList("item1"));
        doc.getArray("array").delete(0);
        Snapshot snap = Y.snapshot(doc);
        doc.getArray("array").insert(0, singletonList("item0"));

        Doc docRestored = Y.createDocFromSnapshot(doc, snap);

        assertEquals(emptyList(), docRestored.getArray("array").toArray());
        assertEquals(singletonList("item0"), doc.getArray("array").toArray());
    }

    @Test
    public void testDeletedItems2() {
        Doc doc = new Doc(new DocOptions().setGc(false));
        doc.getArray("array").insert(0, Arrays.asList("item1", "item2", "item3"));
        doc.getArray("array").delete(1);
        Snapshot snap = Y.snapshot(doc);
        doc.getArray("array").insert(0, singletonList("item0"));

        Doc docRestored = Y.createDocFromSnapshot(doc, snap);

        assertEquals(Arrays.asList("item1", "item3"), docRestored.getArray("array").toArray());
        assertEquals(Arrays.asList("item0", "item1", "item3"), doc.getArray("array").toArray());
    }

    @Test
    public void testDependentChanges() {
        // array0, array1, testConnector
        Map<String, Object> initMap = TestHelper.init(new TestHelper.TestCase(new Random()), 2, null);
        TestConnector testConnector = (TestConnector) initMap.get("testConnector");

        YArray array0 = (YArray) initMap.get("array0");
        YArray array1 = (YArray) initMap.get("array1");
        Doc doc0 = array0.doc;
        Doc doc1 = array1.doc;

        doc0.gc = false;
        doc1.gc = false;

        array0.insert(0, singletonList("user1item1"));
        testConnector.syncAll();
        array1.insert(1, singletonList("user2item1"));
        testConnector.syncAll();

        Snapshot snap = Y.snapshot(doc0);

        array0.insert(2, singletonList("user1item2"));
        testConnector.syncAll();
        array1.insert(3, singletonList("user2item2"));
        testConnector.syncAll();

        Doc docRestored0 = Y.createDocFromSnapshot(doc0, snap);
        assertEquals(Arrays.asList("user1item1", "user2item1"),
                docRestored0.getArray("array").toArray());

        Doc docRestored1 = Y.createDocFromSnapshot(doc1, snap);
        assertEquals(Arrays.asList("user1item1", "user2item1"),
                docRestored1.getArray("array").toArray());
    }

    @Test
    public void testContainsUpdate() {
        Doc ydoc = new Doc();
        List<int[]> updates = new ArrayList<>();
        ydoc.<int[], Object, Doc, Transaction>on("update", (update, origin, doc, transaction) -> {
            updates.add(update);
        });
        YArray yarr = ydoc.getArray();
        Snapshot snapshot1 = Y.snapshot(ydoc);
        yarr.insert(0, singletonList(1));
        Snapshot snapshot2 = Y.snapshot(ydoc);
        yarr.delete(0, 1);
        Snapshot snapshotFinal = Y.snapshot(ydoc);

        assertFalse(Y.snapshotContainsUpdate(snapshot1, updates.get(0)));
        assertFalse(Y.snapshotContainsUpdate(snapshot2, updates.get(1)));
        assertTrue(Y.snapshotContainsUpdate(snapshot2, updates.get(0)));
        assertTrue(Y.snapshotContainsUpdate(snapshotFinal, updates.get(0)));
        assertTrue(Y.snapshotContainsUpdate(snapshotFinal, updates.get(1)));
    }
}