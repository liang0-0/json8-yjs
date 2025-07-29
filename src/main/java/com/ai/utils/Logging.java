package com.ai.utils;

import com.ai.structs.item.Item;
import com.ai.types.arraytype.AbstractType;

/**
 * 日志记录辅助工具类
 * 注意：不要在正式系统中使用，因为输出可能非常庞大！
 */
public class Logging {

    /**
     * 记录类型信息
     * @param type 要记录的类型
     */
    public static void logType(AbstractType<?> type) {
        StringBuilder res = new StringBuilder();
        StringBuilder contentRes = new StringBuilder();
        
        Item n = type._start;
        while (n != null) {
            res.append(n).append("\n");
            if (!n.deleted()) {
                contentRes.append(n.content).append("\n");
            }
            n = n.right;
        }

        System.out.println("Children: \n" + res.toString());
        System.out.println("Children content: \n" + contentRes.toString());
    }
}