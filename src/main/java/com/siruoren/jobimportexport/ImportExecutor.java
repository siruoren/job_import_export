package com.siruoren.jobimportexport;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ImportExecutor {
    private static final Logger LOGGER = Logger.getLogger(ImportExecutor.class.getName());
    
    // 核心线程数：CPU核心数
    private static final int CORE_POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors());
    // 最大线程数：CPU核心数 * 2
    private static final int MAX_POOL_SIZE = CORE_POOL_SIZE * 2;
    // 队列容量
    private static final int QUEUE_CAPACITY = 100;
    // 空闲线程存活时间
    private static final long KEEP_ALIVE_TIME = 60L;
    // 线程名称前缀
    private static final String THREAD_NAME_PREFIX = "import-executor-";

    private static ImportExecutor instance;
    private final ThreadPoolExecutor executor;
    private final AtomicInteger activeTasks = new AtomicInteger(0);

    private ImportExecutor() {
        this.executor = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_TIME,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(0);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r, THREAD_NAME_PREFIX + counter.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    }
                },
                new RejectedExecutionHandler() {
                    @Override
                    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                        LOGGER.log(Level.WARNING, "Import task rejected - thread pool is full. " +
                                "Active: {0}, Queue: {1}", new Object[]{e.getActiveCount(), e.getQueue().size()});
                    }
                }
        );
        // 核心线程也允许超时回收
        executor.allowCoreThreadTimeOut(true);
    }

    public static synchronized ImportExecutor getInstance() {
        if (instance == null) {
            instance = new ImportExecutor();
        }
        return instance;
    }

    /**
     * 提交导入任务
     * @return true 如果任务被接受，false 如果线程池已满
     */
    public boolean submitTask(Runnable task) {
        try {
            executor.execute(task);
            activeTasks.incrementAndGet();
            return true;
        } catch (RejectedExecutionException e) {
            LOGGER.log(Level.WARNING, "Import task rejected: thread pool is full");
            return false;
        }
    }

    /**
     * 获取当前活跃的导入任务数
     */
    public int getActiveTaskCount() {
        return activeTasks.get();
    }

    /**
     * 获取线程池队列大小
     */
    public int getQueueSize() {
        return executor.getQueue().size();
    }

    /**
     * 获取线程池状态信息
     */
    public ExecutorStatus getStatus() {
        return new ExecutorStatus(
                executor.getPoolSize(),
                executor.getActiveCount(),
                executor.getQueue().size(),
                executor.getCompletedTaskCount()
        );
    }

    /**
     * 任务完成后调用此方法减少计数
     */
    public void taskCompleted() {
        activeTasks.decrementAndGet();
    }

    /**
     * 关闭执行器
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 执行器状态信息
     */
    public static class ExecutorStatus {
        public final int poolSize;
        public final int activeCount;
        public final int queueSize;
        public final long completedTasks;

        public ExecutorStatus(int poolSize, int activeCount, int queueSize, long completedTasks) {
            this.poolSize = poolSize;
            this.activeCount = activeCount;
            this.queueSize = queueSize;
            this.completedTasks = completedTasks;
        }
    }
}
