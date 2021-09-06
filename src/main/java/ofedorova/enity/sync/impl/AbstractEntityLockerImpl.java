package ofedorova.enity.sync.impl;

import ofedorova.enity.sync.EntityLocker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

/**
 * EntityLockerImpl.
 *
 * @author Olga_Fedorova
 */
public abstract class AbstractEntityLockerImpl<T>  implements EntityLocker<T> {

    private final Map<T, Lock> lockStorage = new ConcurrentHashMap<>();


    @Override
    public void lock(T entityId) {
        getLock(entityId).lock();
    }

    @Override
    public void unlock(T entityId) {
        getLock(entityId).unlock();
    }

    protected Map<T, Lock> getLockStorage() {
        return lockStorage;
    }

    protected abstract Lock getLock(T entityId);
}
