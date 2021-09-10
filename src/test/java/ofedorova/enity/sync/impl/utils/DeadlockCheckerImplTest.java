package ofedorova.enity.sync.impl.utils;

import com.google.common.collect.Sets;
import ofedorova.enity.sync.EntityLock;
import ofedorova.enity.sync.EntityLockInfo;
import ofedorova.enity.sync.exception.DeadlockException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

/**
 * DeadlockCheckerImplTest.
 *
 * @author Olga_Fedorova
 */
class DeadlockCheckerImplTest {

    /*
    T1 holds R1, R4, wait R2
    T2 holds R2, R5
    T3 holds R3

    T2 tries to block R3 and queued to waiting R3
     */
    @Test
    public void test_notDeadlockPossibleCase1() throws Exception {
        DeadlockCheckerImpl deadlockChecker = new DeadlockCheckerImpl();
        Field locksByThreadsField = DeadlockCheckerImpl.class.getDeclaredField("locksByThreads");
        locksByThreadsField.setAccessible(true);
        Map<Thread, Set<EntityLockInfo>> locksByThreads = (Map<Thread, Set<EntityLockInfo>>) locksByThreadsField.get(deadlockChecker);

        Thread t1 = new Thread();
        Thread t2 = Thread.currentThread();
        Thread t3 = new Thread();


        EntityLockInfo r1 = new EntityLockInfo("r1", new EntityTestLock(t1, Sets.newHashSet()));
        EntityLockInfo r2 = new EntityLockInfo("r2", new EntityTestLock(t2, Sets.newHashSet(t1)));
        EntityLockInfo r3 = new EntityLockInfo("r3", new EntityTestLock(t3, Sets.newHashSet()));
        EntityLockInfo r4 = new EntityLockInfo("r4", new EntityTestLock(t1, Sets.newHashSet()));
        EntityLockInfo r5 = new EntityLockInfo("r5", new EntityTestLock(t2, Sets.newHashSet()));

        locksByThreads.put(t1, Sets.newHashSet(r1, r4, r2));
        locksByThreads.put(t2, Sets.newHashSet(r2, r5));
        locksByThreads.put(t3, Sets.newHashSet(r3));

        deadlockChecker.beforeLock(r3, true);

    }

    /*
    T1 holds R1, R4
    T2 holds R2, R5, wait R3
    T3 holds R3

    T1 tries to block R2 and queued to waiting R2
     */
    @Test
    public void test_notDeadlockPossibleCase2() throws Exception {
        DeadlockCheckerImpl deadlockChecker = new DeadlockCheckerImpl();
        Field locksByThreadsField = DeadlockCheckerImpl.class.getDeclaredField("locksByThreads");
        locksByThreadsField.setAccessible(true);
        Map<Thread, Set<EntityLockInfo>> locksByThreads = (Map<Thread, Set<EntityLockInfo>>) locksByThreadsField.get(deadlockChecker);

        Thread t1 = Thread.currentThread();
        Thread t2 = new Thread();
        Thread t3 = new Thread();

        EntityLockInfo r1 = new EntityLockInfo("r1", new EntityTestLock(t1, Sets.newHashSet()));
        EntityLockInfo r2 = new EntityLockInfo("r2", new EntityTestLock(t2, Sets.newHashSet()));
        EntityLockInfo r3 = new EntityLockInfo("r3", new EntityTestLock(t3, Sets.newHashSet(t2)));
        EntityLockInfo r4 = new EntityLockInfo("r4", new EntityTestLock(t1, Sets.newHashSet()));
        EntityLockInfo r5 = new EntityLockInfo("r5", new EntityTestLock(t2, Sets.newHashSet()));

        locksByThreads.put(t1, Sets.newHashSet(r1, r4));
        locksByThreads.put(t2, Sets.newHashSet(r2, r5, r3));
        locksByThreads.put(t3, Sets.newHashSet(r3));

        deadlockChecker.beforeLock(r2, true);

    }

    /*
    T1 holds R1, wait R2
    T2 holds R2, wait R3
    T3 holds R3
    T4 holds R4

    T3 tries to block R4 and queued to waiting R4
     */
    @Test
    public void test_notDeadlockPossibleCase3() throws Exception {
        DeadlockCheckerImpl deadlockChecker = new DeadlockCheckerImpl();
        Field locksByThreadsField = DeadlockCheckerImpl.class.getDeclaredField("locksByThreads");
        locksByThreadsField.setAccessible(true);
        Map<Thread, Set<EntityLockInfo>> locksByThreads = (Map<Thread, Set<EntityLockInfo>>) locksByThreadsField.get(deadlockChecker);

        Thread t1 = new Thread();
        Thread t2 = new Thread();
        Thread t3 = Thread.currentThread();
        Thread t4 = new Thread();

        EntityLockInfo r1 = new EntityLockInfo("r1", new EntityTestLock(t1, Sets.newHashSet()));
        EntityLockInfo r2 = new EntityLockInfo("r2", new EntityTestLock(t2, Sets.newHashSet(t1)));
        EntityLockInfo r3 = new EntityLockInfo("r3", new EntityTestLock(t3, Sets.newHashSet(t2)));
        EntityLockInfo r4 = new EntityLockInfo("r4", new EntityTestLock(t4, Sets.newHashSet()));

        locksByThreads.put(t1, Sets.newHashSet(r1, r2));
        locksByThreads.put(t2, Sets.newHashSet(r2, r3));
        locksByThreads.put(t3, Sets.newHashSet(r3));
        locksByThreads.put(t4, Sets.newHashSet(r3));

        deadlockChecker.beforeLock(r4, true);

    }

    /*
    T1 holds R1,wait R2
    T2 holds R2

    T1 tries to block R1 and get deadlock
     */
    @Test
    public void test_deadlockPossibleCase1() throws Exception {
        Assertions.assertThrows(DeadlockException.class, () -> {

            DeadlockCheckerImpl deadlockChecker = new DeadlockCheckerImpl();
            Field locksByThreadsField = DeadlockCheckerImpl.class.getDeclaredField("locksByThreads");
            locksByThreadsField.setAccessible(true);
            Map<Thread, Set<EntityLockInfo>> locksByThreads = (Map<Thread, Set<EntityLockInfo>>) locksByThreadsField.get(deadlockChecker);

            Thread t1 = new Thread();
            Thread t2 = Thread.currentThread();

            EntityLockInfo r1 = new EntityLockInfo("r1", new EntityTestLock(t1, Sets.newHashSet()));
            EntityLockInfo r2 = new EntityLockInfo("r2", new EntityTestLock(t2, Sets.newHashSet(t1)));

            locksByThreads.put(t1, Sets.newHashSet(r1, r2));
            locksByThreads.put(t2, Sets.newHashSet(r2));

            deadlockChecker.beforeLock(r1, true);

        });
    }

    /*
    T1 holds R1, R4, wait R2
    T2 holds R2, R5
    T3 holds R3, wait R4

    T2 tries to block R3 and get deadlock
     */
    @Test
    public void test_deadlockPossibleCase2() throws Exception {
        Assertions.assertThrows(DeadlockException.class, () -> {

            DeadlockCheckerImpl deadlockChecker = new DeadlockCheckerImpl();
            Field locksByThreadsField = DeadlockCheckerImpl.class.getDeclaredField("locksByThreads");
            locksByThreadsField.setAccessible(true);
            Map<Thread, Set<EntityLockInfo>> locksByThreads = (Map<Thread, Set<EntityLockInfo>>) locksByThreadsField.get(deadlockChecker);

            Thread t1 = new Thread();
            Thread t2 = Thread.currentThread();
            Thread t3 = new Thread();

            EntityLockInfo r1 = new EntityLockInfo("r1", new EntityTestLock(t1, Sets.newHashSet()));
            EntityLockInfo r2 = new EntityLockInfo("r2", new EntityTestLock(t2, Sets.newHashSet(t1)));
            EntityLockInfo r3 = new EntityLockInfo("r3", new EntityTestLock(t3, Sets.newHashSet()));
            EntityLockInfo r4 = new EntityLockInfo("r4", new EntityTestLock(t1, Sets.newHashSet(t3)));
            EntityLockInfo r5 = new EntityLockInfo("r5", new EntityTestLock(t2, Sets.newHashSet()));

            locksByThreads.put(t1, Sets.newHashSet(r1, r4, r2));
            locksByThreads.put(t2, Sets.newHashSet(r2, r5));
            locksByThreads.put(t3, Sets.newHashSet(r3, r4));

            deadlockChecker.beforeLock(r3, true);

        });
    }

    /*
    T1 holds R1, wait R2
    T2 holds R2, wait R3
    T3 holds R3, wait R4
    T4 holds R4

    T4 tries to block R1 and get deadlock
     */
    @Test
    public void test_deadlockPossibleCase3() throws Exception {
        Assertions.assertThrows(DeadlockException.class, () -> {

            DeadlockCheckerImpl deadlockChecker = new DeadlockCheckerImpl();
            Field locksByThreadsField = DeadlockCheckerImpl.class.getDeclaredField("locksByThreads");
            locksByThreadsField.setAccessible(true);
            Map<Thread, Set<EntityLockInfo>> locksByThreads = (Map<Thread, Set<EntityLockInfo>>) locksByThreadsField.get(deadlockChecker);

            Thread t1 = new Thread();
            Thread t2 = new Thread();
            Thread t3 = new Thread();
            Thread t4 = Thread.currentThread();

            EntityLockInfo r1 = new EntityLockInfo("r1", new EntityTestLock(t1, Sets.newHashSet()));
            EntityLockInfo r2 = new EntityLockInfo("r2", new EntityTestLock(t2, Sets.newHashSet(t1)));
            EntityLockInfo r3 = new EntityLockInfo("r3", new EntityTestLock(t3, Sets.newHashSet(t2)));
            EntityLockInfo r4 = new EntityLockInfo("r4", new EntityTestLock(t4, Sets.newHashSet(t3)));

            locksByThreads.put(t1, Sets.newHashSet(r1, r2));
            locksByThreads.put(t2, Sets.newHashSet(r2, r3));
            locksByThreads.put(t3, Sets.newHashSet(r3, r4));
            locksByThreads.put(t4, Sets.newHashSet(r4));

            deadlockChecker.beforeLock(r1, true);

        });
    }

    private static class EntityTestLock implements EntityLock {

        private final Thread ownerThread;
        private final Set<Thread> waiters;

        private EntityTestLock(Thread ownerThread, Set<Thread> waiters) {
            this.ownerThread = ownerThread;
            this.waiters = waiters;
        }

        @Override
        public Thread getOwner() {
            return ownerThread;
        }

        @Override
        public Set<Thread> getOwnerAndQueuedThreads() {
            Set<Thread> result = new HashSet<>();
            result.add(ownerThread);
            result.addAll(waiters);
            return result;
        }

        @Override
        public void lock() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void lockInterruptibly() throws InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean tryLock() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean tryLock(long l, TimeUnit timeUnit) throws InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void unlock() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

}