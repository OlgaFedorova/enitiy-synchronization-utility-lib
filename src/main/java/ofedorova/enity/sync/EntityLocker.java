package ofedorova.enity.sync;

import ofedorova.enity.sync.exception.DeadlockException;

import java.util.concurrent.TimeUnit;

/**
 * EntityLocker.
 *
 * @author Olga_Fedorova
 */
public interface EntityLocker<T> {

    void lock(T entityId);

    void lock(T entityId, boolean preventDeadlock) throws DeadlockException;

    boolean tryLock(T entityId, long timeout , TimeUnit timeUnit) throws InterruptedException;

    void unlock(T entityId);

    void globalLock();

    boolean tryGlobalLock(long timeout , TimeUnit timeUnit) throws InterruptedException;

    void globalUnlock();

}
