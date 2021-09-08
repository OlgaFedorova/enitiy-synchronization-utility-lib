package ofedorova.enity.sync.impl.lockers;

import ofedorova.enity.sync.EntityLockInfo;
import ofedorova.enity.sync.impl.locks.ExtendedReentrantLock;

/**
 * EntityExtendedReentrantLockImpl.
 *
 * @author Olga_Fedorova
 */
public class EntityExtendedReentrantLockImpl<T> extends AbstractEntityLockerImpl<T> {

    @Override
    protected EntityLockInfo getLock(T entityId) {
        if (entityId == null) {
            throw new IllegalArgumentException("Entity ID cannot be null");
        }
        return getLockStorage().computeIfAbsent(entityId, lock -> new EntityLockInfo(entityId, new ExtendedReentrantLock()));
    }
}
