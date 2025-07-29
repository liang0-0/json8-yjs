package com.ai.test;

import com.ai.types.YMap;
import com.ai.utils.Doc;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;

public class MapTest {
    
    @Test
    public void testMapBasicOperations() {
        Doc doc = new Doc();
        YMap ymap = doc.getMap("test");
        
        ymap.set("key1", "value1");
        assertEquals("value1", ymap.get("key1"));
        
        ymap.set("key2", 123);
        assertEquals(123, ymap.get("key2"));
    }
    
    @Test
    public void testNestedMaps() {
        Doc doc = new Doc();
        YMap parent = doc.getMap("parent");
        YMap child = new YMap();
        
        parent.set("child", child);
        child.set("name", "test");
        
        YMap retrieved = (YMap) parent.get("child");
        assertEquals("test", retrieved.get("name"));
    }
    
    // 更多测试方法...
}