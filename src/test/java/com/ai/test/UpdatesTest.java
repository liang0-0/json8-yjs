package com.ai.test;

import com.ai.Y;
import com.ai.myutils.Maps;
import com.ai.myutils.Uint8Array;
import com.ai.myutils.decoder.Decoder;
import com.ai.myutils.decoder.decoding;
import com.ai.myutils.encoder.encoding;
import com.ai.structs.AbstractStruct;
import com.ai.structs.item.Item;
import com.ai.test.helper.TestHelper;
import com.ai.types.YArray;
import com.ai.types.YMap;
import com.ai.types.YXmlElement;
import com.ai.types.vo.UpdateMeta;
import com.ai.types.ytext.YText;
import com.ai.utils.DeleteSet;
import com.ai.utils.Doc;
import com.ai.utils.DocOptions;
import com.ai.utils.Transaction;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.decoder.UpdateDecoderV2;
import com.ai.utils.codec.encoder.UpdateEncoderV2;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.ai.utils.DeleteSet.readDeleteSet;
import static com.ai.utils.DeleteSet.writeDeleteSet;
import static com.ai.utils.Encoding.readClientsStructRefs;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;

public class UpdatesTest {

    private static class EncV1 {
        public Function<List<int[]>, int[]> mergeUpdates = Y::mergeUpdates;
        public Function<Doc, int[]> encodeStateAsUpdate = Y::encodeStateAsUpdate;
        public BiConsumer<Doc, int[]> docBiConsumer = Y::applyUpdate;
        public Consumer<int[]> logUpdate = Y::logUpdate;
        public Function<int[], UpdateMeta> parseUpdateMeta = Y::parseUpdateMeta;
        public Function<int[], int[]> encodeStateVectorFromUpdate = Y::encodeStateVectorFromUpdate;
        public Function<Doc, int[]> encodeStateVector = Y::encodeStateVector;
        public String updateEventName = "update";
        public String description = "V1";
        public BiFunction<int[], int[], int[]> diffUpdate = Y::diffUpdate;
    }

    private static class EncV2 extends EncV1 {
        public Function<List<int[]>, int[]> mergeUpdates = Y::mergeUpdatesV2;
        public BiFunction<Doc, int[], int[]> encodeStateAsUpdate = Y::encodeStateAsUpdateV2;
        public BiConsumer<Doc, int[]> docBiConsumer = Y::applyUpdateV2;
        public Consumer<int[]> logUpdate = Y::logUpdateV2;
        public BiFunction<int[], Class<? extends UpdateDecoder>, UpdateMeta> parseUpdateMeta = Y::parseUpdateMetaV2;
        public Function<int[], int[]> encodeStateVectorFromUpdate = Y::encodeStateVectorFromUpdateV2;
        public Function<Doc, int[]> encodeStateVector = Y::encodeStateVector;
        public String updateEventName = "updateV2";
        public String description = "V2";
        public BiFunction<int[], int[], int[]> diffUpdate = Y::diffUpdateV2;
    }

    private static class EncDoc extends EncV1 {
        public Function<List<int[]>, int[]> mergeUpdates = (updates) -> {
            final Doc ydoc = new Doc();
            updates.forEach(update -> Y.applyUpdateV2(ydoc, update));
            return Y.encodeStateAsUpdateV2(ydoc);
        };

        public BiFunction<Doc, int[], int[]> encodeStateAsUpdate = Y::encodeStateAsUpdateV2;
        public BiConsumer<Doc, int[]> docBiConsumer = Y::applyUpdateV2;
        public Consumer<int[]> logUpdate = Y::logUpdateV2;
        public BiFunction<int[], Class<? extends UpdateDecoder>, UpdateMeta> parseUpdateMeta = Y::parseUpdateMetaV2;
        public Function<int[], int[]> encodeStateVectorFromUpdate = Y::encodeStateVectorFromUpdateV2;
        public Function<Doc, int[]> encodeStateVector = Y::encodeStateVector;
        public String updateEventName = "updateV2";
        public String description = "Merge via Y.Doc";
        public BiFunction<int[], int[], int[]> diffUpdate = (update, sv) -> {
            final Doc ydoc = new Doc();
            Y.applyUpdateV2(ydoc, update);
            return Y.encodeStateAsUpdateV2(ydoc, sv);
        };
    }

    private static EncV1 encV1 = new EncV1();
    private static EncV2 encV2 = new EncV2();
    private static EncDoc encDoc = new EncDoc();

    private static EncV1[] encoders = new EncV1[]{encV1, encV2, encDoc};

    private static Doc fromUpdates(List<Doc> users, EncV1 enc) {
        final List<int[]> updates = users.stream().map(enc.encodeStateAsUpdate).collect(Collectors.toList());
        final Doc ydoc = new Doc();
        enc.docBiConsumer.accept(ydoc, enc.mergeUpdates.apply(updates));
        return ydoc;
    }

    @Test
    public void testMergeUpdates() {
        // const { users, array0, array1 } = init(tc, { users: 3 })
        Map<String, Object> initMap = TestHelper.init(new TestHelper.TestCase(new Random()), 3, null);
        List<Doc> users = (List<Doc>) initMap.get("users");
        YArray array0 = (YArray) initMap.get("array0");
        YArray array1 = (YArray) initMap.get("array1");

        array0.insert(0, singletonList(1));
        array1.insert(0, singletonList(2));

        TestHelper.compare(users);

        Arrays.stream(encoders).forEach(enc -> {
            Doc merged = fromUpdates(users, enc);
            assertArrayEquals(array0.toArray().toArray(), merged.getArray("array").toArray().toArray());
        });
    }

    @Test
    public void testKeyEncoding() {
        //  const { users, text0, text1 } = init(tc, { users: 2 })
        Map<String, Object> initMap = TestHelper.init(new TestHelper.TestCase(new Random()), 2, null);
        List<Doc> users = (List<Doc>) initMap.get("users");
        YText text0 = (YText) initMap.get("text0");
        YText text1 = (YText) initMap.get("text1");


        // Insert text with formatting
        text0.insert(0, "a", Maps.of("italic", true));
        text0.insert(0, "b");
        text0.insert(0, "c", Maps.of("italic", true));

        // Apply update to second document
        Doc doc = users.get(0);
        Y.applyUpdateV2(users.get(1), Y.encodeStateAsUpdateV2(doc));

        // Verify delta format
        List<Map<String, Object>> expectedDelta = Arrays.asList(
                Maps.of("insert", "c", "attributes", Maps.of("italic", true)),
                Maps.of("insert", "b"),
                Maps.of("insert", "a", "attributes", Maps.of("italic", true))
        );
        assertEquals(expectedDelta, text1.toDelta());

        TestHelper.compare(users);
    }

    private void checkUpdateCases(Doc ydoc, List<int[]> updates, EncV1 enc, boolean hasDeletes) {
        List<int[]> cases = new ArrayList<>();
        // Case 1: Simple case, simply merge everything
        cases.add(enc.mergeUpdates.apply(updates));

        // Case 2: Overlapping updates
        cases.add(enc.mergeUpdates.apply(Arrays.asList(
                enc.mergeUpdates.apply(updates.subList(2, updates.size())),
                enc.mergeUpdates.apply(updates.subList(0, 2))
        )));

        // Case 3: Overlapping updates
        cases.add(enc.mergeUpdates.apply(Arrays.asList(
                enc.mergeUpdates.apply(updates.subList(2, updates.size())),
                enc.mergeUpdates.apply(updates.subList(1, 3)),
                updates.get(0)
        )));

        // Case 4: Separated updates (containing skips)
        cases.add(enc.mergeUpdates.apply(Arrays.asList(
                enc.mergeUpdates.apply(Arrays.asList(updates.get(0), updates.get(2))),
                enc.mergeUpdates.apply(Arrays.asList(updates.get(1), updates.get(3))),
                enc.mergeUpdates.apply(updates.subList(4, updates.size()))
        )));

        // Case 5: overlapping with many duplicates
        cases.add(enc.mergeUpdates.apply(cases));

        // const targetState = enc.encodeStateAsUpdate(ydoc)
        // t.info('Target State: ')
        // enc.logUpdate(targetState)

        cases.forEach((mergedUpdates) -> {
            // t.info('State Case $' + i + ':')
//            updates.forEach(update -> enc.logUpdate.accept(update));
            final Doc merged = new Doc(new DocOptions().setGc(false));
            enc.docBiConsumer.accept(merged, mergedUpdates);
            assertArrayEquals(merged.getArray().toArray().toArray(), ydoc.getArray().toArray().toArray());
            assertArrayEquals(enc.encodeStateVector.apply(merged), enc.encodeStateVectorFromUpdate.apply(mergedUpdates));

            if (!enc.updateEventName.equals("update")) { // TODO should this also work on legacy updates?
                for (int j = 1; j < updates.size(); j++) {
                    final int[] partMerged = enc.mergeUpdates.apply(updates.subList(j, updates.size()));
                    final UpdateMeta partMeta = enc.parseUpdateMeta.apply(partMerged);
                    final int[] targetSV = Y.encodeStateVectorFromUpdateV2(Y.mergeUpdatesV2(updates.subList(0, j)));
                    final int[] diffed = enc.diffUpdate.apply(mergedUpdates, targetSV);
                    final UpdateMeta diffedMeta = enc.parseUpdateMeta.apply(diffed);
                    assertEquals(partMeta, diffedMeta);
                    {
                        // We can'd do the following
                        //  - t.compare(diffed, mergedDeletes)
                        // because diffed contains the set of all deletes.
                        // So we add all deletes from `diffed` to `partDeletes` and compare then
                        final Decoder decoder = decoding.createDecoder(diffed);
                        final UpdateDecoderV2 updateDecoder = new UpdateDecoderV2(decoder);
                        readClientsStructRefs(updateDecoder, new Doc());
                        final DeleteSet ds = readDeleteSet(updateDecoder);
                        final UpdateEncoderV2<?> updateEncoder = new UpdateEncoderV2<>();
                        encoding.writeVarUint(updateEncoder.restEncoder, 0); // 0 structs
                        writeDeleteSet(updateEncoder, ds);
                        final int[] deletesUpdate = updateEncoder.toUint8Array();
                        final int[] mergedDeletes = Y.mergeUpdatesV2(Arrays.asList(deletesUpdate, partMerged));
                        if (!hasDeletes || enc.equals(encDoc)) {
                            // deletes will almost definitely lead to different encoders because of the mergeStruct feature that is present in encDoc
                            assertArrayEquals(diffed, mergedDeletes);
                        }
                    }
                }
            }

            UpdateMeta meta = enc.parseUpdateMeta.apply(mergedUpdates);
            meta.from.forEach((client, clock) -> assertEquals(0, (int) clock));
            meta.to.forEach((client, clock) -> {
                List<AbstractStruct> structs = (merged.store.clients.get(client));
                final Item lastStruct = (Item) structs.get(structs.size() - 1);
                assertEquals(lastStruct.id.clock + lastStruct.length, (int) clock);
            });
        });
    }


    @Test
    public void testMergeUpdates1() {
        Arrays.stream(encoders).forEach((enc) -> {
            System.out.println("Using encoder: " + enc.description);
            final Doc ydoc = new Doc(new DocOptions().setGc(false));
            final List<int[]> updates = new ArrayList<>();
            ydoc.<int[], Object, Doc, Transaction>on("update", (update, origin, doc, transaction) -> updates.add(update));

            YArray array = ydoc.getArray();
            array.insert(0, singletonList(1));
            array.insert(0, singletonList(2));
            array.insert(0, singletonList(3));
            array.insert(0, singletonList(4));

            checkUpdateCases(ydoc, updates, enc, false);
        });
    }

    @Test
    public void testMergeUpdates2() {
        Arrays.stream(encoders).forEach((enc) -> {
            System.out.println("Using encoder: " + enc.description);
            final Doc ydoc = new Doc(new DocOptions().setGc(false));
            final List<int[]> updates = new ArrayList<>();
            ydoc.<int[], Object, Doc, Transaction>on("update", (update, origin, doc, transaction) -> updates.add(update));

            YArray array = ydoc.getArray();
            array.insert(0, Arrays.asList(1, 2));
            array.delete(1, 1);
            array.insert(0, Arrays.asList(3, 4));
            array.delete(1, 2);

            checkUpdateCases(ydoc, updates, enc, true);
        });
    }

    @Test
    public void testMergePendingUpdates() {
        Doc yDoc = new Doc();
        List<int[]> serverUpdates = new ArrayList<>();

        yDoc.<int[], Object, Doc, Transaction>on("update", (update, origin, doc, transaction) -> {
            serverUpdates.add(update);
        });

        YText yText = yDoc.getText("textBlock");
        yText.applyDelta(singletonList(new JSONObject(Maps.of("insert", "r"))));
        yText.applyDelta(singletonList(new JSONObject(Maps.of("insert", "o"))));
        yText.applyDelta(singletonList(new JSONObject(Maps.of("insert", "n"))));
        yText.applyDelta(singletonList(new JSONObject(Maps.of("insert", "e"))));
        yText.applyDelta(singletonList(new JSONObject(Maps.of("insert", "n"))));

        Doc yDoc1 = new Doc();
        Y.applyUpdate(yDoc1, serverUpdates.get(0));
        int[] update1 = Y.encodeStateAsUpdate(yDoc1);
//        Uint8Array.printUint8Array(update1);
        System.out.println(yDoc1.getText("textBlock"));

        Doc yDoc2 = new Doc();
        Y.applyUpdate(yDoc2, update1);
        Y.applyUpdate(yDoc2, serverUpdates.get(1));
        int[] update2 = Y.encodeStateAsUpdate(yDoc2);
        System.out.println(yDoc2.getText("textBlock"));

        Doc yDoc3 = new Doc();
        Y.applyUpdate(yDoc3, update2);
        Y.applyUpdate(yDoc3, serverUpdates.get(3));
        int[] update3 = Y.encodeStateAsUpdate(yDoc3);
        System.out.println(yDoc3.getText("textBlock"));

        Doc yDoc4 = new Doc();
        Y.applyUpdate(yDoc4, update3);
        Y.applyUpdate(yDoc4, serverUpdates.get(2));
        int[] update4 = Y.encodeStateAsUpdate(yDoc4);
        System.out.println(yDoc4.getText("textBlock"));

        Doc yDoc5 = new Doc();
        Y.applyUpdate(yDoc5, update4);
        Y.applyUpdate(yDoc5, serverUpdates.get(4));
        int[] update5 = Y.encodeStateAsUpdate(yDoc5); // eslint-disable-line
        System.out.println(yDoc5.getText("textBlock"));

        YText yText5 = yDoc5.getText("textBlock");
        assertEquals("nenor", yText5.toString());
    }

    @Test
    public void testObfuscateUpdates() {
        Doc ydoc = new Doc();
        YText ytext = ydoc.getText("text");
        YMap ymap = ydoc.getMap("map");
        YArray yarray = ydoc.getArray("array");

        // Test ytext
        ytext.applyDelta(Arrays.asList(
                new JSONObject(Maps.of("insert", "text", "attributes", Maps.of("bold", true))),
                new JSONObject(Maps.of("insert", Maps.of("href", "supersecreturl")))
        ));

        // Test ymap
        ymap.set("key", "secret1");
        ymap.set("key", "secret2");

        // Test yarray with subtype & subdoc
        YXmlElement subtype = new YXmlElement("secretnodename");
        Doc subdoc = new Doc(new DocOptions("secret"));
        subtype.setAttribute("attr", "val");
        yarray.insert(0, Arrays.asList("teststring", 42, subtype, subdoc));

        // Obfuscate and verify
        int[] obfuscatedUpdate = Y.obfuscateUpdate(Y.encodeStateAsUpdate(ydoc));
        Doc odoc = new Doc();
        Y.applyUpdate(odoc, obfuscatedUpdate);

        YText otext = odoc.getText("text");
        YMap omap = odoc.getMap("map");
        YArray oarray = odoc.getArray("array");

        // Verify text
        List<JSONObject> delta = otext.toDelta();
        assertEquals(2, delta.size());
        assertNotEquals("text", delta.get(0).get("insert"));
        assertEquals(4, ((String) delta.get(0).get("insert")).length());
        assertEquals(1, ((Map<?, ?>) delta.get(0).get("attributes")).size());
        assertFalse(((Map<?, ?>) delta.get(0).get("attributes")).containsKey("bold"));

        // Verify map
        assertEquals(1, omap.size());
        assertFalse(omap.has("key"));

        // Verify array
        List<Object> result = oarray.toArray();
        assertEquals(4, result.size());
        assertNotEquals("teststring", result.get(0));
        assertNotEquals(42, result.get(1));

        YXmlElement osubtype = (YXmlElement) result.get(2);
        assertNotEquals(subtype.nodeName, osubtype.nodeName);
        assertEquals(1, osubtype.getAttributes().size());
        assertNull(osubtype.getAttribute("attr"));

        Doc osubdoc = (Doc) result.get(3);
        assertNotEquals(subdoc.guid, osubdoc.guid);
    }
}