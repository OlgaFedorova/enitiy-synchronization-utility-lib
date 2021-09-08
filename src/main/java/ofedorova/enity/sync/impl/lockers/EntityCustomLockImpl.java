package ofedorova.enity.sync.impl.lockers;

import ofedorova.enity.sync.EntityLockInfo;
import ofedorova.enity.sync.impl.locks.CustomLock;

/**
 * EntityCustomLockImpl.
 *
 * @author Olga_Fedorova
 */
public class EntityCustomLockImpl<T> extends AbstractEntityLockerImpl<T> {

    @Override
    protected EntityLockInfo getLock(T entityId) {
        if (entityId == null) {
            throw new IllegalArgumentException("Entity ID cannot be null");
        }
        return getLockStorage().computeIfAbsent(entityId, lock -> new EntityLockInfo(entityId, new CustomLock()));
    }
}
