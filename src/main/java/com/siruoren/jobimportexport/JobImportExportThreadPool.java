package com.siruoren.jobimportexport;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 共享线程池管理器，用于统一管理导入/导出/更新操作的并发执行。
 * <ul>
 *   <li>核心线程数 4，最大线程数 8，避免无限制创建线程</li>
 *   <li>有界队列（容量 64），防止内存溢出</li>
 *   <li>空闲线程 60 秒自动回收</li>
 *   <li>守护线程，JVM 退出时自动终止</li>
 * </ul>
 */
public final class JobImportExportThreadPool {

    private static final int CORE_POOL_SIZE = 4;
    private static final int MAX_POOL_SIZE = 8;
    private static final long KEEP_ALIVE_SECONDS = 60L;
    private static final int QUEUE_CAPACITY = 64;

    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAX_POOL_SIZE,
            KEEP_ALIVE_SECONDS,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(QUEUE_CAPACITY),
            new JobImportExportThreadFactory(),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private JobImportExportThreadPool() {
    }

    public static ExecutorService getExecutor() {
        return EXECUTOR;
    }

    /**
     * 提交任务并自动传播当前线程的安全上下文到工作线程，
     * 确保 Jenkins 权限检查（如 Item.READ、Item.CONFIGURE）在工作线程中正常工作。
     */
    public static Future<?> submitWithAuth(Runnable task) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return EXECUTOR.submit(() -> {
            SecurityContextHolder.getContext().setAuthentication(auth);
            try {
                task.run();
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
    }

    private static class JobImportExportThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "job-import-export-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
