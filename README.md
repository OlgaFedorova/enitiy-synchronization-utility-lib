EntityLocker
------------

# Description

Implementation of [EntityLocker](src/main/java/ofedorova/enity/sync/EntityLocker.java) interface is a reusable utility class that provides synchronization mechanism similar to row-level DB locking.

The class is supposed to be used by the components that are responsible for managing storage and caching of different type of entities in 
the application. EntityLocker itself does not deal with the entities, only with the IDs (primary keys) of the entities.

Features:

1. EntityLocker supports different types of entity IDs.

2. EntityLocker’s interface allows the caller to specify which entity does it want to work with (using entity ID), and designate 
the boundaries of the code that should have exclusive access to the entity (called “protected code”) with construction:
 ```java
EntityLocker entityLocker;
T entityId;
.....
 try {
     entityLocker.lock(entityId);
     ...;
 } finally {
     entityLocker.unlock(entityId);
 }
 ```

3. For any given entity, EntityLocker guarantees that at most one thread executes protected code on that entity. If there’s a 
concurrent request to lock the same entity, the other thread should wait until the entity becomes available.

4. EntityLocker allows concurrent execution of protected code on different entities.

5. It allows reentrant locking.

# Implementation

## AbstractEntityLockerImpl
The main implementation of the [EntityLocker](src/main/java/ofedorova/enity/sync/EntityLocker.java) interface is represented by the 
[AbstractEntityLockerImpl](src/main/java/ofedorova/enity/sync/impl/AbstractEntityLockerImpl.java) class.
The class contains one abstract method Lock getLock (T entityId) that allows you to define the implementation of the Lock interface for the lockStorage variable to use.

The class is extended by two implementations:
- [EntityReentrantLockImpl](src/main/java/ofedorova/enity/sync/impl/EntityReentrantLockImpl.java)
- [EntityCustomLockImpl](src/main/java/ofedorova/enity/sync/impl/EntityCustomLockImpl.java)

Test for all implementation are [here](src/test/java/ofedorova/enity/sync/impl/EntityLockerImplTest.java).

### EntityReentrantLockImpl
[EntityReentrantLockImpl](src/main/java/ofedorova/enity/sync/impl/EntityReentrantLockImpl.java) class uses lockStorage with `java.util.concurrent.locks.ReentrantLock` implementation.

### EntityCustomLockImpl
[EntityCustomLockImpl](src/main/java/ofedorova/enity/sync/impl/EntityCustomLockImpl.java) class uses lockStorage with [`CustomLock`](src/main/java/ofedorova/enity/sync/impl/CustomLock.java) implementation.

##№ CustomLock
The class [`CustomLock`](src/main/java/ofedorova/enity/sync/impl/CustomLock.java) implements the `java.util.concurrent.locks.Lock` interface. 
The class implements two main methods:
```java
@Override
public void lock() {
    ...;
}

@Override
public void unlock() {
    ...;
}
```

This custom lock implementation support next features:

1. Allow to designate the boundaries of the code that should have exclusive access with construction:
```java
CustomLock customLock = new CustomLock();

try {
    customLock.lock();
    ...;
} finally {
    customLock.unlock();
}
```

2. Guarantee that at most one thread executes protected code.

3. Allow reentrant locking

Tests for class are implement [here](src/test/java/ofedorova/enity/sync/impl/CustomLockTest.java).