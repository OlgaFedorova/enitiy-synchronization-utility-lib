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
                locker.lock(entityId);
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
                locker.lock(entityId);
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
                locker.lock(entityId);
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
            locker.lock(entityId);
            resultForFirsrEntity.set(entityId);
            latch.countDown();
        }).start();

        new Thread(() -> {
            EntityLocker locker = testData.locker;
            Object entityId = testData.entityIds[1];
            locker.lock(entityId);
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
                locker.lock(entityId);
                try {
                    locker.lock(entityId);
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
                    locker.lock(testData.entityIds[0]);
                    locker.lock(testData.entityIds[3]);
                    latchForEntity1.countDown();
                    latchForEntity2.await();
                    locker.lock(testData.entityIds[1]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            executorService.submit(() -> {
                try {
                    locker.lock(testData.entityIds[1]);
                    locker.lock(testData.entityIds[4]);
                    latchForEntity2.countDown();
                    latchForEntity3.await();
                    locker.lock(testData.entityIds[2]);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            locker.lock(testData.entityIds[2]);
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
                    locker.lock(testData.entityIds[0]);
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

    @ParameterizedTest
    @MethodSource("testDataProvider")
    public void test_globalLockWaitAllNonGlobalUnlock(TestData testData) throws Exception {

        AtomicBoolean tryAcquireForThread3 = new AtomicBoolean();
        AbstractEntityLockerImpl locker = testData.locker;

        CountDownLatch latch = new CountDownLatch(2);

        Thread thread1 = new Thread(() -> {
            locker.lock(testData.entityIds[0]);
            latch.countDown();
        });
        thread1.start();

        Thread thread2 = new Thread(() -> {
            locker.lock(testData.entityIds[1]);
            latch.countDown();
        });
        thread2.start();

        Thread thread3 = new Thread(() -> {
            try {
                latch.await();
                locker.globalLock();
                tryAcquireForThread3.set(true);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        });
        thread3.start();
        latch.await();

        Assertions.assertFalse(tryAcquireForThread3.get());
    }

    @ParameterizedTest
    @MethodSource("testDataProvider")
    public void test_globalLockAndAllNonGlobalWait(TestData testData) throws Exception {

        AtomicBoolean tryAcquireForNonGlobal = new AtomicBoolean();
        AtomicBoolean tryAcquireForGlobal = new AtomicBoolean();
        AbstractEntityLockerImpl locker = testData.locker;

        CountDownLatch latch = new CountDownLatch(1);

        Thread thread1 = new Thread(() -> {
            locker.globalLock();
            tryAcquireForGlobal.set(true);
            latch.countDown();
        });
        thread1.start();

        Thread thread2 = new Thread(() -> {
            try {
                latch.await();
                locker.lock(testData.entityIds[1]);
                tryAcquireForNonGlobal.set(true);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread2.start();

        Thread thread3 = new Thread(() -> {
            try {
                latch.await();
                locker.lock(testData.entityIds[1]);
                tryAcquireForNonGlobal.set(true);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread3.start();

        latch.await();

        Assertions.assertFalse(tryAcquireForNonGlobal.get());
        Assertions.assertTrue(tryAcquireForGlobal.get());
    }

    @ParameterizedTest
    @MethodSource("testDataProvider")
    public void test_globalUnLock(TestData testData) throws Exception {

        AtomicBoolean tryAcquireForNonGlobal = new AtomicBoolean();
        AtomicBoolean tryAcquireForGlobal = new AtomicBoolean();
        AbstractEntityLockerImpl locker = testData.locker;

        CountDownLatch latch = new CountDownLatch(2);

        Thread thread1 = new Thread(() -> {
            try {
                locker.globalLock();
                tryAcquireForGlobal.set(true);
                latch.countDown();
            } finally {
                locker.globalUnlock();
            }

        });
        thread1.start();

        Thread thread2 = new Thread(() -> {
            locker.lock(testData.entityIds[1]);
            tryAcquireForNonGlobal.set(true);
            latch.countDown();
        });
        thread2.start();

        latch.await();

        Assertions.assertTrue(tryAcquireForNonGlobal.get());
        Assertions.assertTrue(tryAcquireForGlobal.get());
    }

    @ParameterizedTest
    @MethodSource("testDataProvider")
    public void test_reentrantGlobalUnLock(TestData testData) throws Exception {

        AtomicBoolean tryAcquireForGlobal = new AtomicBoolean();
        AbstractEntityLockerImpl locker = testData.locker;

        CountDownLatch latch = new CountDownLatch(1);

        Thread thread1 = new Thread(() -> {
            try {
                locker.globalLock();
                try {
                    locker.globalLock();
                    tryAcquireForGlobal.set(true);
                } finally {
                    locker.globalUnlock();
                }
                latch.countDown();
            } finally {
                locker.globalUnlock();
            }

        });
        thread1.start();

        latch.await();

        Assertions.assertTrue(tryAcquireForGlobal.get());
    }

    @ParameterizedTest
    @MethodSource("testDataProvider")
    public void test_tryGlobakLockWithTimeout(TestData testData) throws Exception {

        AtomicBoolean tryAcquireForThread2 = new AtomicBoolean();
        AbstractEntityLockerImpl locker = testData.locker;
        CountDownLatch latch = new CountDownLatch(1);

        Thread thread1 = new Thread(() -> {
            locker.globalLock();
            latch.countDown();
        });
        thread1.start();

        AtomicLong timePassed = new AtomicLong(0);
        Thread thread2 = new Thread(() -> {
            try {
                latch.await();
                long start = System.nanoTime();
                tryAcquireForThread2.set(locker.tryGlobalLock(10, TimeUnit.SECONDS));
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

    @ParameterizedTest
    @MethodSource("testDataProvider")
    public void test_globalLockEscalation(TestData testData) throws Exception {

        AtomicBoolean tryAcquireForThread2 = new AtomicBoolean();
        AbstractEntityLockerImpl locker = testData.locker;
        CountDownLatch latch = new CountDownLatch(1);

        Thread thread1 = new Thread(() -> {
            locker.lock(testData.entityIds[0]);
            locker.lock(testData.entityIds[1]);
            locker.lock(testData.entityIds[2]);
            locker.lock(testData.entityIds[3]);
            latch.countDown();
        });
        thread1.start();

        Thread thread2 = new Thread(() -> {
            try {
                latch.await();
                locker.lock(testData.entityIds[4]);
                tryAcquireForThread2.set(true);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread2.start();

        Assertions.assertFalse(tryAcquireForThread2.get());
    }

    @ParameterizedTest
    @MethodSource("testDataProvider")
    public void test_globalLockEscalationFree(TestData testData) throws Exception {

        AtomicBoolean tryAcquireForThread2 = new AtomicBoolean();
        AbstractEntityLockerImpl locker = testData.locker;
        CountDownLatch latch = new CountDownLatch(1);

        Thread thread1 = new Thread(() -> {
            locker.lock(testData.entityIds[0]);
            locker.lock(testData.entityIds[1]);
            locker.lock(testData.entityIds[2]);
            locker.lock(testData.entityIds[3]);
            locker.unlock(testData.entityIds[3]);
            latch.countDown();
        });
        thread1.start();

        Thread thread2 = new Thread(() -> {
            try {
                latch.await();
                locker.lock(testData.entityIds[4]);
                tryAcquireForThread2.set(true);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread2.start();
        thread2.join();

        Assertions.assertTrue(tryAcquireForThread2.get());
    }

    @ParameterizedTest
    @MethodSource("testDataProvider")
    public void test_globalLoclWithTryLockEscalationFree(TestData testData) throws Exception {

        AtomicBoolean tryAcquireForThread2 = new AtomicBoolean();
        AbstractEntityLockerImpl locker = testData.locker;
        CountDownLatch latch = new CountDownLatch(1);

        Thread thread1 = new Thread(() -> {
            try {
                locker.lock(testData.entityIds[0]);
                locker.lock(testData.entityIds[1]);
                locker.lock(testData.entityIds[2]);
                locker.tryLock(testData.entityIds[3], 10, TimeUnit.MICROSECONDS);
                locker.unlock(testData.entityIds[3]);
                latch.countDown();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread1.start();

        Thread thread2 = new Thread(() -> {
            try {
                latch.await();
                locker.lock(testData.entityIds[4]);
                tryAcquireForThread2.set(true);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread2.start();
        thread2.join();

        Assertions.assertTrue(tryAcquireForThread2.get());
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