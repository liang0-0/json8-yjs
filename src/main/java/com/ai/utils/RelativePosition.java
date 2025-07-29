package com.ai.utils;

import com.ai.myutils.decoder.Decoder;
import com.ai.myutils.decoder.decoding;
import com.ai.myutils.encoder.Encoder;
import com.ai.myutils.encoder.encoding;
import com.ai.structs.AbstractStruct;
import com.ai.structs.ContentType;
import com.ai.structs.item.Item;
import com.ai.types.ID;
import com.ai.types.arraytype.AbstractType;
import com.ai.types.vo.AbsolutePosition;
import com.ai.types.vo.ItemDiffResult;
import com.ai.utils.structstore.StructStore;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

import static com.ai.structs.item.Item.followRedone;
import static com.ai.types.ID.createID;
import static com.ai.types.ID.findRootTypeKey;
import static com.ai.utils.structstore.StructStore.getItem;

/**
 * A relative position is based on the Yjs model and is not affected by document changes.
 * E.g. If you place a relative position before a certain character, it will always point to this character.
 * If you place a relative position at the end of a type, it will always point to the end of the type.
 * <p>
 * A numeric position is often unsuited for user selections, because it does not change when content is inserted
 * before or after.
 * <p>
 * ```Insert(0, 'x')('a|bc') = 'xa|bc'``` Where | is the relative position.
 * <p>
 * One of the properties must be defined.
 *
 * <pre><code>
 *  // Current cursor position is at position 10
 *  const relativePosition = createRelativePositionFromIndex(yText, 10)
 *  // modify yText
 *  yText.insert(0, 'abc')
 *  yText.delete(3, 10)
 *  // Compute the cursor position
 *  const absolutePosition = createAbsolutePositionFromRelativePosition(y, relativePosition)
 *  absolutePosition.type === yText // => true
 *  console.log('cursor location is ' + absolutePosition.index) // => cursor location is 3
 *  </code></pre>
 */
public class RelativePosition {
    public final ID type;
    public final String tname;
    public final ID item;
    public final int assoc;

    public RelativePosition(ID type, String tname, ID item, int assoc) {
        this.type = type;
        this.tname = tname;
        this.item = item;
        this.assoc = assoc;
    }

    public RelativePosition(ID type, String tname, ID item) {
        this(type, tname, item, 0);
    }

    public static JSONObject relativePositionToJSON(RelativePosition rpos) {
        final JSONObject json = new JSONObject();
        if (rpos.type != null) {
            json.put("type", rpos.type);
        }
        if (rpos.tname != null) {
            json.put("tname", rpos.tname);
        }
        if (rpos.item != null) {
            json.put("item", rpos.item);
        }
        if (rpos.assoc != 0) {
            json.put("assoc", rpos.assoc);
        }
        return json;
    }

    /**
     * Creates a RelativePosition from a JSON object
     *
     * @param json The JSON object containing relative position data
     * @return A new RelativePosition instance
     */
    public static RelativePosition createRelativePositionFromJSON(JSONObject json) {
        // Parse type ID from JSON
        ID typeId = null;
        if (json.containsKey("type") && StringUtils.isNotBlank(json.getString("type"))) {
            JSONObject typeJson = json.getJSONObject("type");
            typeId = createID(typeJson.getInteger("client"),
                    typeJson.getInteger("clock"));
        }

        // Parse tname (type name)
        String tname = json.containsKey("tname") && !StringUtils.isBlank(json.getString("tname")) ?
                json.getString("tname") : null;

        // Parse item ID from JSON
        ID itemId = null;
        if (json.containsKey("item") && StringUtils.isNotBlank(json.getString("item"))) {
            JSONObject itemJson = json.getJSONObject("item");
            itemId = createID(itemJson.getInteger("client"),
                    itemJson.getInteger("clock"));
        }

        // Parse association (default to 0 if not specified)
        int assoc = json.containsKey("assoc") && StringUtils.isNotBlank(json.getString("assoc")) ?
                json.getInteger("assoc") : 0;

        return new RelativePosition(typeId, tname, itemId, assoc);
    }
    // export const createRelativePositionFromJSON = json => new RelativePosition(json.type == null ? null : createID(json.type.client, json.type.clock), json.tname ?? null, json
    // .item == null ? null : createID(json.item.client, json.item.clock), json.assoc == null ? 0 : json.assoc)


    public static AbsolutePosition createAbsolutePosition(AbstractType type, int index, int assoc) {
        return new AbsolutePosition(type, index, assoc);
    }

    public static RelativePosition createRelativePosition(AbstractType type, ID item, int assoc) {
        ID typeid = null;
        String tname = null;
        if (type._item == null) {
            tname = findRootTypeKey(type);
        } else {
            typeid = new ID(type._item.id.client, type._item.id.clock);
        }
        return new RelativePosition(typeid, tname, item, assoc);
    }

    public static RelativePosition createRelativePositionFromTypeIndex(AbstractType type, int index, int assoc) {
        Item t = type._start;
        if (assoc < 0) {
            if (index == 0) {
                return createRelativePosition(type, null, assoc);
            }
            index--;
        }
        while (t != null) {
            if (!t.deleted() && t.countable()) {
                if (t.length > index) {
                    return createRelativePosition(type, new ID(t.id.client, t.id.clock + index), assoc);
                }
                index -= t.length;
            }
            if (t.right == null && assoc < 0) {
                return createRelativePosition(type, t.getLastId(), assoc);
            }
            t = t.right;
        }
        return createRelativePosition(type, null, assoc);
    }

    /**
     * Writes a RelativePosition to an encoder
     *
     * @param encoder The encoder to write to
     * @param rpos    The relative position to encode
     * @return The encoder for method chaining
     * @throws IllegalStateException if the position is invalid
     */
    public static Encoder writeRelativePosition(Encoder encoder, RelativePosition rpos) {
        if (rpos.item != null) {
            // Case 1: Position somewhere in the linked list
            encoding.writeVarUint(encoder, 0);
            ID.writeID(encoder, rpos.item);
        } else if (rpos.tname != null) {
            // Case 2: Position at end of list, type stored in y.share
            encoding.writeUint8(encoder, 1);
            encoding.writeVarString(encoder, rpos.tname);
        } else if (rpos.type != null) {
            // Case 3: Position at end of list, type attached to item
            encoding.writeUint8(encoder, 2);
            ID.writeID(encoder, rpos.type);
        } else {
            throw new IllegalStateException("Unexpected relative position case");
        }
        encoding.writeVarInt(encoder, rpos.assoc, rpos.assoc < 0);
        return encoder;
    }

    /**
     * Encodes a RelativePosition to a byte array
     *
     * @param rpos The relative position to encode
     * @return The encoded byte array
     */
    public static int[] encodeRelativePosition(RelativePosition rpos) {
        Encoder encoder = encoding.createEncoder();
        writeRelativePosition(encoder, rpos);
        return encoding.toUint8Array(encoder);
    }

    /**
     * Reads a RelativePosition from a decoder
     *
     * @param decoder The decoder to read from
     * @return The decoded RelativePosition
     */
    public static RelativePosition readRelativePosition(Decoder decoder) {
        ID type = null;
        String tname = null;
        ID itemID = null;

        int caseType = decoding.readVarUint(decoder);
        switch (caseType) {
            case 0:
                // Case 1: Position somewhere in linked list
                itemID = ID.readID(decoder);
                break;
            case 1:
                // Case 2: Position at end of list, type in y.share
                tname = decoding.readVarString(decoder);
                break;
            case 2:
                // Case 3: Position at end of list, type attached to item
                type = ID.readID(decoder);
                break;
            default:
                throw new IllegalStateException("Unknown relative position case: " + caseType);
        }

        int assoc = decoding.hasContent(decoder) ? decoding.readVarInt(decoder) : 0;
        return new RelativePosition(type, tname, itemID, assoc);
    }

    /**
     * Decodes a RelativePosition from a byte array
     *
     * @param bytes The encoded byte array
     * @return The decoded RelativePosition
     */
    public static RelativePosition decodeRelativePosition(int[] bytes) {
        return readRelativePosition(decoding.createDecoder(bytes));
    }

    /**
     * 从结构存储中获取指定ID的项及其偏移量
     *
     * @param store 结构存储
     * @param id    要查找的项ID
     * @return 包含找到的项和偏移量的结果对象
     */
    public static ItemDiffResult getItemWithOffset(StructStore store, ID id) {
        // 从存储中获取指定ID的项
        AbstractStruct item = getItem(store, id);
        // 计算请求ID与项ID之间的时钟偏移量
        int diff = id.clock - item.id.clock;

        // 返回包含项和偏移量的结果对象
        return new ItemDiffResult((Item) item, diff);
    }

    public static AbsolutePosition createAbsolutePositionFromRelativePosition(RelativePosition rpos, Doc doc, Boolean followUndoneDeletions) {
        if (followUndoneDeletions == null) {
            followUndoneDeletions = true;
        }
        StructStore store = doc.store;
        ID rightID = rpos.item;
        ID typeID = rpos.type;
        String tname = rpos.tname;
        int assoc = rpos.assoc;
        AbstractType<?> type = null;
        int index = 0;

        if (rightID != null) {
            if (StructStore.getState(store, rightID.client) <= rightID.clock) {
                return null;
            }
            ItemDiffResult res = followUndoneDeletions ? followRedone(store, rightID) : getItemWithOffset(store, rightID);
            Item right = res.item;
            if (right == null) {
                return null;
            }
            type = (AbstractType<?>) right.parent;
            if (type._item == null || !type._item.deleted()) {
                index = (right.deleted() || !right.countable()) ? 0 : (res.diff + (assoc >= 0 ? 0 : 1));
                Item n = right.left;
                while (n != null) {
                    if (!n.deleted() && n.countable()) {
                        index += n.length;
                    }
                    n = n.left;
                }
            }
        } else {
            if (tname != null) {
                type = doc.get(tname);
            } else if (typeID != null) {
                if (StructStore.getState(store, typeID.client) <= typeID.clock) {
                    return null;
                }
                Item item = followUndoneDeletions ? followRedone(store, typeID).item : (Item) getItem(store, typeID);
                if (item != null && item.content instanceof ContentType) {
                    type = ((ContentType) item.content).type;
                } else {
                    // struct is garbage collected
                    return null;
                }
            } else {
                throw new RuntimeException("Unexpected case");
            }
            index = assoc >= 0 ? type._length : 0;
        }
        return createAbsolutePosition(type, index, rpos.assoc);
    }

    public static boolean compareRelativePositions(RelativePosition a, RelativePosition b) {
        return Objects.equals(a, b) ||
                (a != null && b != null &&
                        Objects.equals(a.tname, b.tname) &&
                        ID.compareIDs(a.item, b.item) &&
                        ID.compareIDs(a.type, b.type) &&
                        a.assoc == b.assoc);
    }
}