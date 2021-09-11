package ofedorova.enity.sync.impl.lockers;

import ofedorova.enity.sync.EntityLockInfo;
import ofedorova.enity.sync.EntityLocker;
import ofedorova.enity.sync.DeadlockChecker;
import ofedorova.enity.sync.impl.utils.DeadlockCheckerImpl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * EntityLockerImpl.
 *
 * @author Olga_Fedorova
 */
public abstract class AbstractEntityLockerImpl<T> implements EntityLocker<T> {

    private final Map<T, EntityLockInfo> lockStorage = new ConcurrentHashMap<>();
    private final DeadlockChecker deadlockChecker = new DeadlockCheckerImpl();
    private final ReentrantReadWriteLock globalLock = new ReentrantReadWriteLock();
    private final Map<Thread, Integer> locksCountByThread = new ConcurrentHashMap<>();
    private final int countGlobalLockEscalation;

    protected AbstractEntityLockerImpl() {
        this.countGlobalLockEscalation = 4;
    }

    protected AbstractEntityLockerImpl(int countGlobalLockEscalation) {
        this.countGlobalLockEscalation = countGlobalLockEscalation;
    }

    @Override
    public void lock(T entityId) {
        EntityLockInfo lockInfo = getLock(entityId);
        deadlockChecker.beforeLock(lockInfo, true);
        lockInfo.getLock().lock();
        incrementLocksAndGlobalEscalation(0, false);
    }

    @Override
    public boolean tryLock(T entityId, long timeout, TimeUnit timeUnit) throws InterruptedException {
        EntityLockInfo lockInfo = getLock(entityId);
        deadlockChecker.beforeLock(lockInfo, false);
        long deadline = System.nanoTime() + timeUnit.toNanos(timeout);
        boolean isLocked = lockInfo.getLock().tryLock(timeout, timeUnit);
        if (isLocked) {
            incrementLocksAndGlobalEscalation(deadline, true);
        }
        return isLocked;
    }

    @Override
    public void unlock(T entityId) {
        EntityLockInfo lockInfo = getLock(entityId);
        lockInfo.getLock().unlock();
        deadlockChecker.afterUnlock(lockInfo);
        decrementLocksAndGlobalEscalation();
    }

    @Override
    public void globalLock() {
        globalLock.writeLock().lock();
    }

    @Override
    public boolean tryGlobalLock(long timeout, TimeUnit timeUnit) throws InterruptedException {
        return globalLock.writeLock().tryLock(timeout, timeUnit);
    }

    @Override
    public void globalUnlock() {
        globalLock.writeLock().unlock();
    }

    private void incrementLocksAndGlobalEscalation(long deadline, boolean checkTimeout) {
        Integer countLocks = locksCountByThread.compute(Thread.currentThread(), (thread, count) -> count == null ? 1 : count + 1);
        if (countLocks != null && countLocks >= countGlobalLockEscalation) {
            for (int i = 0; i < countLocks-1; i++) {
                globalLock.readLock().unlock();
            }

            boolean isGlobalLocked = false;
            if (checkTimeout) {
                try {
                    isGlobalLocked = tryGlobalLock(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    isGlobalLocked = false;
                }
            } else {
                globalLock();
                isGlobalLocked = true;
            }

            if (!isGlobalLocked) {
                for (int i = 0; i < countLocks-1; i++) {
                    globalLock.readLock().lock();
                }
            }

        } else {
            globalLock.readLock().lock();
        }
    }

    private void decrementLocksAndGlobalEscalation() {
        Integer countLocks = locksCountByThread.compute(Thread.currentThread(), (thread, count) -> count == null ? 1 : count - 1);
        if (countLocks != null && globalLock.writeLock().isHeldByCurrentThread() &&
                countLocks < countGlobalLockEscalation) {
            globalUnlock();
            for (int i = 0; i < countLocks; i++) {
                globalLock.readLock().lock();
            }
        } else {
            globalLock.readLock().unlock();
        }
    }

    protected Map<T, EntityLockInfo> getLockStorage() {
        return lockStorage;
    }

    protected abstract EntityLockInfo getLock(T entityId);
}
