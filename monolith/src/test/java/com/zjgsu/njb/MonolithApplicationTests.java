package com.zjgsu.njb;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 启动测试：确认 Spring 应用上下文可以正常加载。
 *
 * <p>使用 test profile（H2 内存库），因此不依赖本机 MySQL。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class MonolithApplicationTests {

    @Test
    void contextLoads() {
        // 上下文能成功加载即通过
    }

}
