package ofedorova.enity.sync.impl.locks;

import ofedorova.enity.sync.EntityLock;

import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * ExtendedReentrantLock.
 *
 * @author Olga_Fedorova
 */
public class ExtendedReentrantLock extends ReentrantLock implements EntityLock {

    @Override
    public Thread getOwner() {
        return super.getOwner();
    }

    @Override
    public Set<Thread> getOwnerAndQueuedThreads() {
        Set<Thread> queued = super.getQueuedThreads().stream().collect(Collectors.toSet());
        if (getOwner() != null) {
            queued.add(getOwner());
        }
        return queued;
    }
}
