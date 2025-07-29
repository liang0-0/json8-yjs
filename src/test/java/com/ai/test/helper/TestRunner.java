package com.ai.test.helper;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

public class TestRunner {
    public static void main(String[] args) {
        // 1. 创建Launcher
        Launcher launcher = LauncherFactory.create();

        // 2. 创建监听器收集结果
        SummaryGeneratingListener listener = new SummaryGeneratingListener();

        // 3. 构建测试发现请求（指定要运行的测试类）
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(TestSuite.class))
                .build();

        // 4. 执行测试
        launcher.execute(request, listener);

        // 5. 处理结果
        TestExecutionSummary summary = listener.getSummary();
        if (summary.getTotalFailureCount() > 0) {
            summary.getFailures().forEach(failure ->
                    System.err.println("Failure: " + failure.getTestIdentifier() + " - " + failure.getException()));
        }
    }
}