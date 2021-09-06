package ofedorova.enity.sync.impl;

import java.util.concurrent.locks.Lock;
/**
 * EntityCustomLockImpl.
 *
 * @author Olga_Fedorova
 */
public class EntityCustomLockImpl<T> extends AbstractEntityLockerImpl<T> {

    @Override
    protected Lock getLock(T entityId) {
        if (entityId == null) {
            throw new IllegalArgumentException("Entity ID cannot be null");
        }
        return getLockStorage().computeIfAbsent(entityId, lock -> new CustomLock());
    }
}
