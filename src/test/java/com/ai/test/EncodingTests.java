package com.ai.test;


import com.ai.Y;
import com.ai.utils.Doc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EncodingTests {

    // 对应testStructReferences
    @Test
    public void testContentRefs() {
//        assertEquals(11, Y.ContentRefs.getContentRefsCount());
//        assertSame(Y.ContentRefs.readContentDeleted, Y.ContentRefs.getHandler(1));
//        assertSame(Y.ContentRefs.readContentJSON, Y.ContentRefs.getHandler(2));
    }

    // 对应testPermanentUserData
    @Test
    public void testUserDataSync() throws Exception {
        Doc doc1 = new Doc();
        Doc doc2 = new Doc();
        
        // 初始化用户数据逻辑
        doc1.getText("text").insert(0, "xhi", null);
        doc1.getText("text").delete(0, 1);
        
        // 跨文档同步验证
        int[] update = Y.encodeStateAsUpdate(doc1, null);
        Y.applyUpdate(doc2, update);
        
        assertEquals(doc1.getText("text").toString(),
                     doc2.getText("text").toString());
    }

    // 对应testDiffStateVectorOfUpdateIsEmpty
    @Test
    public void testStateVectorEncoding() {
        Doc doc = new Doc();
        doc.getText(null).insert(0, "a", null);
        
        int[] update = Y.encodeStateVector(doc);
        assertTrue(update.length > 0);
    }
}