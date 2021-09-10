package ofedorova.enity.sync.impl.lockers;

import ofedorova.enity.sync.EntityLockInfo;
import ofedorova.enity.sync.EntityLocker;
import ofedorova.enity.sync.exception.DeadlockException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


/**
 * EntityLockerImplTest.
 *
 * @author Olga_Fedorova
 */
class EntityLockerImplTest {


    @ParameterizedTest
    @MethodSource("testDataProvider")
    public void test_differentTypesOfEntityIds(TestData testData) throws Exception {

        AtomicReference result = new AtomicReference();

        Runnable runnable = () -> {
            EntityLocker locker = testData.locker;
            Object entityId = testData.entityIds[0];
            try {
                locker.lock(entityId, true);
                result.set(entityId);
            } finally {
                locker.unlock(entityId);
            }
        };

        Thread thread = new Thread(runnable);
        thread.start();
        thread.join();

        Assertions.assertEquals(testData.entityIds[0], result.get());
    }

    @ParameterizedTest
    @MethodSource("testDataProvider")
    public void test_guaranteeOneThreadExecute(TestData testData) throws Exception {

        AtomicReference result = new AtomicReference();

        CountDownLatch latch = new CountDownLatch(2);

        Runnable runnable = () -> {
            EntityLocker locker = testData.locker;
            Object entityId = testData.entityIds[0];
            try {
                locker.lock(entityId, true);
                result.set(entityId);
            } finally {
                locker.unlock(entityId);
                latch.countDown();
            }
        };

        new Thread(() -> {
            EntityLocker locker = testData.locker;
            Object entityId = testData.entityIds[0];
            try {
                locker.lock(entityId, true);
                new Thread(runnable).start();
            } finally {
                locker.unlock(entityId);
                latch.countDown();
            }
        }).start();

        latch.await();

        Assertions.assertEquals(testData.entityIds[0], result.get());
    }

    @ParameterizedTest
    @MethodSource("testDataProvider")
    public void test_concurrentExecutionOnDifferentEntities(TestData testData) throws Exception {

        AtomicReference resultForFirsrEntity = new AtomicReference();
        AtomicReference resultForSecondEntity = new AtomicReference();

        CountDownLatch latch = new CountDownLatch(2);

        new Thread(() -> {
            EntityLocker locker = testData.locker;
            Object entityId = testData.entityIds[0];
            locker.lock(entityId, true);
            resultForFirsrEntity.set(entityId);
            latch.countDown();
        }).start();

        new Thread(() -> {
            EntityLocker locker = testData.locker;
            Object entityId = testData.entityIds[1];
            locker.lock(entityId, true);
            resultForSecondEntity.set(entityId);
            latch.countDown();
        }).start();

        latch.await();

        Assertions.assertEquals(testData.entityIds[0], resultForFirsrEntity.get());
        Assertions.assertEquals(testData.entityIds[1], resultForSecondEntity.get());
    }

    @ParameterizedTest
    @MethodSource("testDataProvider")
    public void test_reentrantLocking(TestData testData) throws Exception {

        AtomicReference result = new AtomicReference();

        CountDownLatch latch = new CountDownLatch(1);

        new Thread(() -> {
            EntityLocker locker = testData.locker;
            Object entityId = testData.entityIds[0];
            try {
                locker.lock(entityId, true);
                try {
                    locker.lock(entityId, true);
                    result.set(entityId);
                    latch.countDown();
                } finally {
                    locker.unlock(entityId);
                }
            } finally {
                locker.unlock(entityId);
            }
        }).start();

        latch.await();

        Assertions.assertEquals(testData.entityIds[0], result.get());
    }

    @ParameterizedTest
    @MethodSource("testDataProvider")
    public void test_deadlockCaseWithException(TestData testData) throws Exception {

        Assertions.assertThrows(DeadlockException.class, () -> {

            ExecutorService executorService = Executors.newFixedThreadPool(2);
            CountDownLatch latchForEntity1 = new CountDownLatch(1);
            CountDownLatch latchForEntity2 = new CountDownLatch(1);
            CountDownLatch latchForEntity3 = new CountDownLatch(1);

            AbstractEntityLockerImpl locker = testData.locker;


            executorService.submit(() -> {
                try {
                    locker.lock(testData.entityIds[0], true);
                    locker.lock(testData.entityIds[3], true);
                    latchForEntity1.countDown();
                    latchForEntity2.await();
                    locker.lock(testData.entityIds[1], true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            executorService.submit(() -> {
                try {
                    locker.lock(testData.entityIds[1], true);
                    locker.lock(testData.entityIds[4], true);
                    latchForEntity2.countDown();
                    latchForEntity3.await();
                    locker.lock(testData.entityIds[2], true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            locker.lock(testData.entityIds[2], true);
            latchForEntity3.countDown();

            latchForEntity1.await();
            latchForEntity2.await();

            EntityLockInfo lockInfo1 = locker.getLock(testData.entityIds[0]);
            EntityLockInfo lockInfo2 = locker.getLock(testData.entityIds[1]);
            EntityLockInfo lockInfo3 = locker.getLock(testData.entityIds[2]);

            while (true) {
                if (lockInfo1.getLock().getOwner() != null &&
                        lockInfo2.getLock().getOwnerAndQueuedThreads().size() == 2 &&
                        lockInfo3.getLock().getOwnerAndQueuedThreads().size() == 2) {
                    locker.lock(testData.entityIds[0], true);
                }
            }

        });
    }

    @ParameterizedTest
    @MethodSource("testDataProvider")
    public void test_tryLockWithTimeout(TestData testData) throws Exception {

        AtomicBoolean tryAcquireForThread2 = new AtomicBoolean();
        AbstractEntityLockerImpl locker = testData.locker;
        CountDownLatch latch = new CountDownLatch(1);

        Thread thread1 = new Thread(() -> {
            locker.lock(testData.entityIds[0]);
            latch.countDown();
        });
        thread1.start();

        AtomicLong timePassed = new AtomicLong(0);
        Thread thread2 = new Thread(() -> {
            try {
                latch.await();
                long start = System.nanoTime();
                tryAcquireForThread2.set(locker.tryLock(testData.entityIds[0], 10, TimeUnit.SECONDS));
                long end = System.nanoTime();
                timePassed.set(end - start);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread2.start();
        thread2.join();

        Assertions.assertFalse(tryAcquireForThread2.get());
        Assertions.assertTrue(timePassed.get() >= TimeUnit.SECONDS.toNanos(10));
    }


    static List<TestData> testDataProvider() {
        return List.of(
                new TestData(new EntityExtendedReentrantLockImpl<String>(), new String[] {"stringId1", "stringId2", "stringId3", "stringId4", "stringId5"}),
                new TestData(new EntityCustomLockImpl<String>(), new String[] {"stringId1", "stringId2", "stringId3", "stringId4", "stringId5"}),
                new TestData(new EntityExtendedReentrantLockImpl<Long>(), new Long[]{1L, 2L, 3L, 4L, 5L}),
                new TestData(new EntityCustomLockImpl<Long>(), new Long[]{1L, 2L, 3L, 4L, 5L})
        );
    }

    private static class TestData<T> {
        private final AbstractEntityLockerImpl<T> locker;
        private final T[] entityIds;

        TestData(AbstractEntityLockerImpl<T> locker, T[] entityIds) {
            this.locker = locker;
            this.entityIds = entityIds;
        }
    }

}