package ofedorova.enity.sync;

/**
 * EntityLockInfo.
 *
 * @author Olga_Fedorova
 */
public class EntityLockInfo<T> {
    private final T entityId;
    private final EntityLock lock;

    public EntityLockInfo(T entityId, EntityLock lock) {
        this.entityId = entityId;
        this.lock = lock;
    }

    public EntityLock getLock() {
        return lock;
    }

    @Override
    public String toString() {
        return String.valueOf(entityId);
    }
}
