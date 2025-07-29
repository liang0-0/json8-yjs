package com.ai.types;

import java.util.*;
import java.util.stream.Collectors;

import com.ai.structs.ContentType;
import com.ai.structs.item.Item;
import com.ai.types.arraytype.AbstractType;
import com.ai.types.ytext.YText;
import com.ai.utils.codec.decoder.UpdateDecoder;
import com.ai.utils.codec.decoder.UpdateDecoderV1;
import com.ai.utils.codec.decoder.UpdateDecoderV2;
import com.ai.utils.codec.encoder.UpdateEncoderV1;
import com.ai.utils.codec.encoder.UpdateEncoderV2;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import static com.ai.structs.ContentType.YXmlTextRefID;

/**
 * XML文本节点类型实现
 * Represents text in a Dom Element. In the future this type will also handle
 * simple formatting information like bold and italic.
 */
public class YXmlText extends YText {

    public YXmlText() {
    }

    public YXmlText(String initialText) {
        super(initialText);
    }

    /**
     * 获取下一个兄弟节点
     *
     * @return 下一个兄弟节点，可能为YXmlElement或YXmlText
     */
    public Object getNextSibling() {
        Item n = this._item != null ? this._item.next() : null;
        return n != null ? ((ContentType) n.content).type : null;
    }

    /**
     * 获取上一个兄弟节点
     *
     * @return 上一个兄弟节点，可能为YXmlElement或YXmlText
     */
    public Object getPrevSibling() {
        Item n = this._item != null ? this._item.prev() : null;
        return n != null ? ((ContentType) n.content).type : null;
    }

    @Override
    public YXmlText _copy() {
        return new YXmlText();
    }

    /**
     * Makes a copy of this data type that can be included somewhere else.
     * Note that the content is only readable _after_ it has been included somewhere in the Ydoc.
     *
     * @return Cloned YXmlText
     */
    @Override
    public YXmlText clone() {
        YXmlText text = new YXmlText();
        text.applyDelta(this.toDelta());
        return text;
    }

    /**
     * Creates a Dom Element that mirrors this YXmlText.
     *
     * @param _document The document object (defaults to document)
     * @param hooks     Optional property to customize how hooks are presented
     * @param binding   Used by DomBinding to create associations
     * @return DOM Text node
     */
    public Text toDOM(Document _document, Map<String, Object> hooks, Object binding) {
        Text dom = _document.createTextNode(this.toString());
        if (binding instanceof DomBinding) {
            ((DomBinding) binding)._createAssociation(dom, this);
        }
        return dom;
    }

    /**
     * Overloaded toDOM with default parameters
     *
     * @param _document The document object
     * @return DOM Text node
     */
    public Text toDOM(Document _document) {
        return toDOM(_document, null, null);
    }

    @Override
    public String toString() {
        return this.toDelta().stream()
                .map(delta -> {
                    List<Map<String, Object>> nestedNodes = new ArrayList<>();

                    // Process attributes
                    Map<String, Object> attributes = (Map<String, Object>) delta.get("attributes");
                    if (null != attributes) {
                        attributes.forEach((nodeName, attrs) -> {
                            List<Map<String, String>> attrList = new ArrayList<>();
                            if (attrs instanceof Map) {
                                ((Map<String, ?>) attrs).forEach((key, value) -> {
                                    attrList.add(new HashMap<String, String>() {{
                                        put("key", key);
                                        put("value", value.toString());
                                    }});
                                });
//                            } else {
//                                System.out.println("attrs = " + attrs);
                            }

                            // Sort attributes for consistent order
                            attrList.sort(Comparator.comparing(a -> a.get("key")));
                            nestedNodes.add(new HashMap<String, Object>() {{
                                put("nodeName", nodeName);
                                put("attrs", attrList);
                            }});
                        });
                    }

                    // Sort nodes for consistent order
                    nestedNodes.sort(Comparator.comparing(a -> ((String) a.get("nodeName"))));

                    // Build DOM string
                    StringBuilder str = new StringBuilder();
                    for (Map<String, Object> node : nestedNodes) {
                        str.append("<").append(node.get("nodeName"));

                        @SuppressWarnings("unchecked")
                        List<Map<String, String>> attrs = (List<Map<String, String>>) node.get("attrs");
                        for (Map<String, String> attr : attrs) {
                            str.append(" ")
                                    .append(attr.get("key"))
                                    .append("=\"")
                                    .append(attr.get("value"))
                                    .append("\"");
                        }
                        str.append(">");
                    }

                    str.append(delta.get("insert"));

                    // Close tags in reverse order
                    for (int i = nestedNodes.size() - 1; i >= 0; i--) {
                        str.append("</")
                                .append(nestedNodes.get(i).get("nodeName"))
                                .append(">");
                    }

                    return str.toString();
                })
                .collect(Collectors.joining());
    }

    /**
     * @return JSON string representation
     */
    public String toJSON() {
        return this.toString();
    }

    /**
     * Transform the properties to binary and write to encoder V1
     *
     * @param encoder The encoder to write data to
     */
    public void _write(UpdateEncoderV1 encoder) {
        encoder.writeTypeRef(YXmlTextRefID);
    }

    /**
     * Transform the properties to binary and write to encoder V2
     *
     * @param encoder The encoder to write data to
     */
    public void _write(UpdateEncoderV2 encoder) {
        encoder.writeTypeRef(YXmlTextRefID);
    }

    /**
     * Read YXmlText using decoder V1
     *
     * @param decoder The decoder to read from
     * @return New YXmlText instance
     */
    public static YXmlText readYXmlText(UpdateDecoderV1 decoder) {
        return new YXmlText();
    }

    /**
     * Read YXmlText using decoder V2
     *
     * @param decoder The decoder to read from
     * @return New YXmlText instance
     */
    public static YXmlText readYXmlText(UpdateDecoderV2 decoder) {
        return new YXmlText();
    }

    public static AbstractType<?> readYXmlText(UpdateDecoder updateDecoder) {
        return new YXmlText();
    }

    /**
     * Interface for DOM binding functionality
     */
    public interface DomBinding {
        void _createAssociation(Node dom, YXmlText text);
    }
}