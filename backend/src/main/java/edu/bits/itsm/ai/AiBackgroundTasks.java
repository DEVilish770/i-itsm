package edu.bits.itsm.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Two worker threads for AI work that no HTTP request waits for. The queue is bounded: when it is full the task is
 * dropped with a log line, which is safe because every such task only improves text that is already saved.
 */
@Component
public class AiBackgroundTasks implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(AiBackgroundTasks.class);
    private static final AtomicInteger THREADS = new AtomicInteger();

    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100),
            r -> { Thread t = new Thread(r, "ai-worker-" + THREADS.incrementAndGet()); t.setDaemon(true); return t; },
            (task, executor) -> log.warn("AI background queue is full; the template text is kept"));

    public void submit(Runnable task) {
        pool.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException ex) {
                log.warn("AI background task failed: {}", ex.getClass().getSimpleName());
            }
        });
    }

    @Override
    public void destroy() { pool.shutdownNow(); }
}
