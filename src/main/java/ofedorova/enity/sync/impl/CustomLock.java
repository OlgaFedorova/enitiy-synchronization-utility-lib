package ofedorova.enity.sync.impl;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * CustomLock.
 *
 * @author Olga_Fedorova
 */
class CustomLock implements Lock {

    private volatile Thread ownerThread;
    private final AtomicInteger state = new AtomicInteger(0);
    private final ConcurrentLinkedQueue<Thread> waiters = new ConcurrentLinkedQueue<>();

    @Override
    public void lock() {
        if (!tryAcquire() && addWaiterAndAcquireQueued()) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean tryLock() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean tryLock(long l, TimeUnit timeUnit) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void unlock() {
        release();
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }

    boolean tryAcquire() {
        Thread currentThread = Thread.currentThread();
        if (state.get() == 0) {
            if (state.compareAndSet(0, 1)) {
                ownerThread = currentThread;
                return true;
            }
        } else if (currentThread == ownerThread) {
            state.incrementAndGet();
            return true;
        }
        return false;
    }

    boolean addWaiterAndAcquireQueued() {
        Thread currentThread = Thread.currentThread();
        waiters.add(currentThread);

        while (true) {
            if (currentThread.isInterrupted()) {
                waiters.remove(currentThread);
                return false;
            }

            Thread head = waiters.peek();
            if (head == null) {
                break;
            }

            if (head == currentThread) {
                if (tryAcquire()) {
                    waiters.remove(head);
                    return true;
                }
            }
        }
        return false;
    }

    boolean release() {
        int newState = state.get() - 1;
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalMonitorStateException();
        } else {
            boolean free = false;
            if (newState == 0) {
                ownerThread = null;
                free = true;
            }
            state.decrementAndGet();
            return free;
        }
    }
}
