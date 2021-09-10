package ofedorova.enity.sync.impl.lockers;

import ofedorova.enity.sync.EntityLockInfo;
import ofedorova.enity.sync.EntityLocker;
import ofedorova.enity.sync.exception.DeadlockException;
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

    @Override
    public void lock(T entityId) {
        lock(entityId, false);
    }

    @Override
    public void lock(T entityId, boolean preventDeadlock) throws DeadlockException {
        EntityLockInfo lockInfo = getLock(entityId);
        deadlockChecker.beforeLock(lockInfo, preventDeadlock);
        globalLock.readLock().lock();
        lockInfo.getLock().lock();
    }

    @Override
    public boolean tryLock(T entityId, long timeout, TimeUnit timeUnit) throws InterruptedException {
        EntityLockInfo lockInfo = getLock(entityId);
        deadlockChecker.beforeLock(lockInfo, false);
        globalLock.readLock().lock();
        return lockInfo.getLock().tryLock(timeout, timeUnit);
    }

    @Override
    public void unlock(T entityId) {
        EntityLockInfo lockInfo = getLock(entityId);
        lockInfo.getLock().unlock();
        globalLock.readLock().unlock();
        deadlockChecker.afterUnlock(lockInfo);
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

    protected Map<T, EntityLockInfo> getLockStorage() {
        return lockStorage;
    }

    protected abstract EntityLockInfo getLock(T entityId);
}
