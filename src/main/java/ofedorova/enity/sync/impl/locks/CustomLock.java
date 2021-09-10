package ofedorova.enity.sync.impl.locks;

import ofedorova.enity.sync.EntityLock;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;

/**
 * CustomLock.
 *
 * @author Olga_Fedorova
 */
public class CustomLock implements EntityLock {

    private volatile Thread ownerThread;
    private final AtomicInteger state = new AtomicInteger(0);
    private final ConcurrentLinkedQueue<Thread> waiters = new ConcurrentLinkedQueue<>();

    @Override
    public void lock() {
        if (!tryAcquire() && addWaiterAndAcquireQueued(0, false)) {
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
    public boolean tryLock(long timeout, TimeUnit timeUnit) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        } else {
            return tryAcquire() || addWaiterAndAcquireQueued(timeUnit.toNanos(timeout), true);
        }
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

    boolean addWaiterAndAcquireQueued(long nanosTimeout, boolean checkTimeout) {
        if (checkTimeout && nanosTimeout <= 0L) {
            return false;
        }

        long deadline = System.nanoTime() + nanosTimeout;
        Thread currentThread = Thread.currentThread();
        waiters.add(currentThread);

        while (true) {
            if (currentThread.isInterrupted()) {
                waiters.remove(currentThread);
                return false;
            }

            if (checkTimeout && deadline - System.nanoTime() <= 0L) {
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

    @Override
    public Thread getOwner() {
        return ownerThread;
    }

    @Override
    public Set<Thread> getOwnerAndQueuedThreads() {
        Set<Thread> queued = new HashSet<>();
        queued.addAll(waiters);
        if (ownerThread != null) {
            queued.add(ownerThread);
        }
        return queued;
    }
}
