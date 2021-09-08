package ofedorova.enity.sync;

import java.util.Set;
import java.util.concurrent.locks.Lock;

/**
 * EntityLock.
 *
 * @author Olga_Fedorova
 */
public interface EntityLock extends Lock {

    Thread getOwner();

    Set<Thread> getOwnerAndQueuedThreads();

}
