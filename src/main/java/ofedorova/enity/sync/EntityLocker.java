package ofedorova.enity.sync;

import java.util.concurrent.TimeUnit;

/**
 * EntityLocker.
 *
 * @author Olga_Fedorova
 */
public interface EntityLocker<T> {

    void lock(T entityId);

    boolean tryLock(T entityId, long timeout , TimeUnit timeUnit) throws InterruptedException;

    void unlock(T entityId);

    void globalLock();

    boolean tryGlobalLock(long timeout , TimeUnit timeUnit) throws InterruptedException;

    void globalUnlock();

}
