package ofedorova.enity.sync.impl.lockers;

import ofedorova.enity.sync.EntityLockInfo;
import ofedorova.enity.sync.exception.DeadlockException;
import ofedorova.enity.sync.impl.DeadlockChecker;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DeadlockChecker.
 *
 * @author Olga_Fedorova
 */
public class DeadlockCheckerImpl implements DeadlockChecker {

    private final Map<Thread, Set<EntityLockInfo>> locksByThreads = new ConcurrentHashMap<>();

    @Override
    public void beforeLock(EntityLockInfo lockInfo, boolean preventDeadlock) {
        if (preventDeadlock) {
            checkDeadlock(lockInfo);
        }
        addLockForThread(lockInfo);
    }

    @Override
    public void afterUnlock(EntityLockInfo lockInfo) {
        removeLockForThread(lockInfo);
    }

    private void addLockForThread(EntityLockInfo lock) {
        Set<EntityLockInfo> locks = locksByThreads.computeIfAbsent(Thread.currentThread(), v -> new HashSet<>());
        locks.add(lock);
    }

    private void removeLockForThread(EntityLockInfo lock) {
        Set<EntityLockInfo> locks = locksByThreads.get(Thread.currentThread());
        if (locks != null) {
            locks.remove(lock);
        }
    }

    private void checkDeadlock(EntityLockInfo lock) {

        Set<EntityLockInfo> blockedEntities = new HashSet<>();
        Set<Thread> checkedThreads = new HashSet<>();
        Queue<EntityLockInfo> queue = new LinkedList<>();

        queue.add(lock);

        while (queue.peek() != null) {
            EntityLockInfo pollLock = queue.poll();
            if (!blockedEntities.contains(pollLock)) {
                boolean isBlockedByOtherThreds = false;
                for (Thread checkThread : pollLock.getLock().getOwnerAndQueuedThreads()) {
                    if (!checkedThreads.contains(checkThread)) {
                        Set<EntityLockInfo> lockHoldsByCheckThread = locksByThreads.getOrDefault(checkThread, new HashSet<>());
                        if (lockHoldsByCheckThread.size() > 1 || checkThread == Thread.currentThread()) {
                            queue.addAll(lockHoldsByCheckThread);
                            checkedThreads.add(checkThread);
                            isBlockedByOtherThreds = true;
                        }
                    }
                }
                if (isBlockedByOtherThreds == true) {
                    blockedEntities.add(pollLock);
                }
            }
        }

        Set<EntityLockInfo> deadlockEntities = blockedEntities;

        if (deadlockEntities.size() >= 2) {
            throw new DeadlockException(String.format("Deadlock entities: %s", deadlockEntities));
        }
    }
}
