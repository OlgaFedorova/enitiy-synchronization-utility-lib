package ofedorova.enity.sync.impl;

import ofedorova.enity.sync.EntityLocker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.concurrent.CountDownLatch;
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
            Object entityId = testData.entityId;
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

        Assertions.assertEquals(testData.entityId, result.get());
    }

    @ParameterizedTest
    @MethodSource("testDataProvider")
    public void test_guaranteeOneThreadExecute(TestData testData) throws Exception {

        AtomicReference result = new AtomicReference();

        CountDownLatch latch = new CountDownLatch(2);

        Runnable runnable = () -> {
            EntityLocker locker = testData.locker;
            Object entityId = testData.entityId;
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
            Object entityId = testData.entityId;
            try {
                locker.lock(entityId);
                new Thread(runnable).start();
            } finally {
                locker.unlock(entityId);
                latch.countDown();
            }
        }).start();

        latch.await();

        Assertions.assertEquals(testData.entityId, result.get());
    }

    @ParameterizedTest
    @MethodSource("testDataProvider")
    public void test_concurrentExecutionOnDifferentEntities(TestData testData) throws Exception {

        AtomicReference resultForFirsrEntity = new AtomicReference();
        AtomicReference resultForSecondEntity = new AtomicReference();

        CountDownLatch latch = new CountDownLatch(2);

        new Thread(() -> {
            EntityLocker locker = testData.locker;
            Object entityId = testData.entityId;
            locker.lock(entityId);
            resultForFirsrEntity.set(entityId);
            latch.countDown();
        }).start();

        new Thread(() -> {
            EntityLocker locker = testData.locker;
            Object entityId = testData.differentEntityId;
            locker.lock(entityId);
            resultForSecondEntity.set(entityId);
            latch.countDown();
        }).start();

        latch.await();

        Assertions.assertEquals(testData.entityId, resultForFirsrEntity.get());
        Assertions.assertEquals(testData.differentEntityId, resultForSecondEntity.get());
    }

    @ParameterizedTest
    @MethodSource("testDataProvider")
    public void test_reentrantLocking(TestData testData) throws Exception {

        AtomicReference result = new AtomicReference();

        CountDownLatch latch = new CountDownLatch(1);

        new Thread(() -> {
            EntityLocker locker = testData.locker;
            Object entityId = testData.entityId;
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

        Assertions.assertEquals(testData.entityId, result.get());
    }


    static List<TestData> testDataProvider() {
        return List.of(
                new TestData(new EntityReentrantLockImpl<String>(), "stringId", "differentStringId"),
                new TestData(new EntityCustomLockImpl<String>(), "stringId", "differentStringId"),
                new TestData(new EntityReentrantLockImpl<Long>(), 1L, 2L),
                new TestData(new EntityCustomLockImpl<Long>(), 1L, 2L)
        );
    }

    private static class TestData<T> {
        private final EntityLocker<T> locker;
        private final T entityId;
        private final T differentEntityId;

        TestData(EntityLocker<T> locker, T entityId, T differentEntityId) {
            this.locker = locker;
            this.entityId = entityId;
            this.differentEntityId = differentEntityId;
        }
    }

}