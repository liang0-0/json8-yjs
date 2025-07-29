package com.ai.test.helper;

import com.ai.test.MapTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@SelectClasses({
    MapTest.class,
//    ArrayTest.class,
//    TextTest.class,
//    XmlTest.class,
//    EncodingTest.class,
//    com.ai.test.UndoRedoTest.class,
//    CompatibilityTest.class,
//    com.ai.test.DocTest.class,
//    com.ai.test.SnapshotTest.class,
//    UpdatesTest.class,
//    RelativePositionsTest.class
    // NodeTest.class - 如果需要Node环境测试
})
public class TestSuite {
    // 这个类不需要包含任何代码
    // 它只是作为所有测试的容器
}