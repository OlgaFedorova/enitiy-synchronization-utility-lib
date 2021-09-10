package ofedorova.enity.sync.impl.locks;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CustomLockTest.
 *
 * @author Olga_Fedorova
 */
public class CustomLockTest {

    @Test
    public void test_tryAcquireForSingleThread() {
        CustomLock customLock = new CustomLock();
        Assertions.assertTrue(customLock.tryAcquire());
    }

    @Test
    public void test_tryAcquireForTwoThreads() throws Exception {
        CustomLock customLock = new CustomLock();

        AtomicBoolean tryAcquireForThread1 = new AtomicBoolean();
        AtomicBoolean tryAcquireForThread2 = new AtomicBoolean();

        Thread thread1 = new Thread(() -> {
            tryAcquireForThread1.set(customLock.tryAcquire());
        });
        thread1.start();
        thread1.join();


        Thread thread2 = new Thread(() -> {
            tryAcquireForThread2.set(customLock.tryAcquire());
        });
        thread2.start();
        thread2.join();

        Assertions.assertTrue(tryAcquireForThread1.get());
        Assertions.assertFalse(tryAcquireForThread2.get());
    }

    @Test
    public void test_oneLockAndTwoWaiters() throws Exception {
        CustomLock customLock = new CustomLock();

        Thread thread1 = new Thread(() -> {
            customLock.lock();
        });
        thread1.start();
        thread1.join();

        Thread thread2 = new Thread(() -> {
            customLock.lock();
        });
        thread2.start();
        thread2.join(2000);

        Thread thread3 = new Thread(() -> {
            customLock.lock();
        });
        thread3.start();
        thread3.join(2000);

        Field ownerThreadField = CustomLock.class.getDeclaredField("ownerThread");
        ownerThreadField.setAccessible(true);
        Thread ownerThread = (Thread) ownerThreadField.get(customLock);

        Field waitersField = CustomLock.class.getDeclaredField("waiters");
        waitersField.setAccessible(true);
        ConcurrentLinkedQueue<Thread> waiters = (ConcurrentLinkedQueue<Thread>) waitersField.get(customLock);

        Assertions.assertEquals(thread1, ownerThread);
        Assertions.assertEquals(2, waiters.size());
        Assertions.assertEquals(thread2, waiters.poll());
        Assertions.assertEquals(thread3, waiters.poll());
    }

    @Test
    public void test_oneLockAndSecondWaiterIsInterrupted() throws Exception {
        CustomLock customLock = new CustomLock();

        AtomicBoolean tryAcquireForThread2 = new AtomicBoolean();

        Thread thread1 = new Thread(() -> {
            customLock.lock();
        });
        thread1.start();
        thread1.join();

        Thread thread2 = new Thread(() -> {
            tryAcquireForThread2.set(customLock.addWaiterAndAcquireQueued(0, false));
        });
        thread2.start();
        thread2.interrupt();
        thread2.join();

        Field ownerThreadField = CustomLock.class.getDeclaredField("ownerThread");
        ownerThreadField.setAccessible(true);
        Thread ownerThread = (Thread) ownerThreadField.get(customLock);

        Assertions.assertEquals(thread1, ownerThread);
        Assertions.assertFalse(tryAcquireForThread2.get());
    }

    @Test
    public void test_firstLockSecondWaitAndThenLockToo() throws Exception {
        CustomLock customLock = new CustomLock();
        CountDownLatch latch = new CountDownLatch(2);

        Thread thread1 = new Thread(() -> {
            customLock.lock();
            latch.countDown();
        });

        try {
            latch.countDown();
            customLock.lock();
            thread1.start();
        } finally {
            customLock.unlock();
            thread1.join();
        }

        latch.await();

        Field ownerThreadField = CustomLock.class.getDeclaredField("ownerThread");
        ownerThreadField.setAccessible(true);
        Thread ownerThread = (Thread) ownerThreadField.get(customLock);

        Field waitersField = CustomLock.class.getDeclaredField("waiters");
        waitersField.setAccessible(true);
        ConcurrentLinkedQueue<Thread> waiters = (ConcurrentLinkedQueue<Thread>) waitersField.get(customLock);

        Assertions.assertEquals(thread1, ownerThread);
        Assertions.assertEquals(0, waiters.size());
    }

    @Test
    public void test_reentrantLockWithoutUnlock() throws Exception {
        CustomLock customLock = new CustomLock();

        customLock.lock();
        customLock.lock();

        Field ownerThreadField = CustomLock.class.getDeclaredField("ownerThread");
        ownerThreadField.setAccessible(true);
        Thread ownerThread = (Thread) ownerThreadField.get(customLock);

        Field state = CustomLock.class.getDeclaredField("state");
        state.setAccessible(true);
        AtomicInteger stateThread = (AtomicInteger) state.get(customLock);

        Field waitersField = CustomLock.class.getDeclaredField("waiters");
        waitersField.setAccessible(true);
        ConcurrentLinkedQueue<Thread> waiters = (ConcurrentLinkedQueue<Thread>) waitersField.get(customLock);

        Assertions.assertEquals(Thread.currentThread(), ownerThread);
        Assertions.assertEquals(2, stateThread.get());
        Assertions.assertEquals(0, waiters.size());
    }

    @Test
    public void test_reentrantLockWithOneUnlock() throws Exception {
        CustomLock customLock = new CustomLock();

        try {
            customLock.lock();
            customLock.lock();
        } finally {
            customLock.unlock();
        }

        Field ownerThreadField = CustomLock.class.getDeclaredField("ownerThread");
        ownerThreadField.setAccessible(true);
        Thread ownerThread = (Thread) ownerThreadField.get(customLock);

        Field state = CustomLock.class.getDeclaredField("state");
        state.setAccessible(true);
        AtomicInteger stateThread = (AtomicInteger) state.get(customLock);

        Field waitersField = CustomLock.class.getDeclaredField("waiters");
        waitersField.setAccessible(true);
        ConcurrentLinkedQueue<Thread> waiters = (ConcurrentLinkedQueue<Thread>) waitersField.get(customLock);

        Assertions.assertEquals(Thread.currentThread(), ownerThread);
        Assertions.assertEquals(1, stateThread.get());
        Assertions.assertEquals(0, waiters.size());
    }

    @Test
    public void test_reentrantLockWithCorrectUnlock() throws Exception {
        CustomLock customLock = new CustomLock();

        try {
            customLock.lock();
            try {
                customLock.lock();
            } finally {
                customLock.unlock();
            }
        } finally {
            customLock.unlock();
        }

        Field ownerThreadField = CustomLock.class.getDeclaredField("ownerThread");
        ownerThreadField.setAccessible(true);
        Thread ownerThread = (Thread) ownerThreadField.get(customLock);

        Field state = CustomLock.class.getDeclaredField("state");
        state.setAccessible(true);
        AtomicInteger stateThread = (AtomicInteger) state.get(customLock);

        Field waitersField = CustomLock.class.getDeclaredField("waiters");
        waitersField.setAccessible(true);
        ConcurrentLinkedQueue<Thread> waiters = (ConcurrentLinkedQueue<Thread>) waitersField.get(customLock);

        Assertions.assertEquals(null, ownerThread);
        Assertions.assertEquals(0, stateThread.get());
        Assertions.assertEquals(0, waiters.size());
    }

    @Test
    public void test_tryLockWithTimeout() throws Exception {
        CustomLock customLock = new CustomLock();

        AtomicBoolean tryAcquireForThread1 = new AtomicBoolean();
        AtomicBoolean tryAcquireForThread2 = new AtomicBoolean();

        Thread thread1 = new Thread(() -> {
            tryAcquireForThread1.set(customLock.tryAcquire());
        });
        thread1.start();

        AtomicLong timePassed = new AtomicLong(0);
        Thread thread2 = new Thread(() -> {
            try {
                long start = System.nanoTime();
                tryAcquireForThread2.set(customLock.tryLock(10, TimeUnit.SECONDS));
                long end = System.nanoTime();
                timePassed.set(end - start);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread2.start();
        thread2.join();

        Assertions.assertTrue(tryAcquireForThread1.get());
        Assertions.assertFalse(tryAcquireForThread2.get());
        Assertions.assertTrue(timePassed.get() >= TimeUnit.SECONDS.toNanos(10));
    }

}