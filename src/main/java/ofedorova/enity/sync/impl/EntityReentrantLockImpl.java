package ofedorova.enity.sync.impl;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * EntityReentrantLockImpl.
 *
 * @author Olga_Fedorova
 */
public class EntityReentrantLockImpl<T> extends AbstractEntityLockerImpl<T> {

    @Override
    protected Lock getLock(T entityId) {
        if (entityId == null) {
            throw new IllegalArgumentException("Entity ID cannot be null");
        }
        return getLockStorage().computeIfAbsent(entityId, lock -> new ReentrantLock());
    }
}
