EntityLocker
------------
- [Interface EntityLocker](#Interface-EntityLocker)
- [Implementation](#Implementation)
    - [AbstractEntityLockerImpl](#AbstractEntityLockerImpl)
        - [EntityExtendedReentrantLockImpl](#EntityExtendedReentrantLockImpl)
        - [EntityCustomLockImpl](#EntityCustomLockImpl)
        - [EntityLock](#EntityLock)
        - [ExtendedReentrantLock](#ExtendedReentrantLock)
        - [CustomLock](#CustomLock)
        - [DeadlockChecker](#DeadlockChecker)

# Interface EntityLocker

Implementation of [EntityLocker](src/main/java/ofedorova/enity/sync/EntityLocker.java) interface is a reusable utility class 
that provides synchronization mechanism similar to row-level DB locking.

The class is supposed to be used by the components that are responsible for managing storage and caching of different type of entities in 
the application. `EntityLocker` itself does not deal with the entities, only with the IDs (primary keys) of the entities.

**Features:**

1. `EntityLocker` supports different types of entity IDs.

2. `EntityLocker` interface allows the caller to specify which entity does it want to work with (using entity ID), and designate 
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

3. For any given entity, `EntityLocker` guarantees that at most one thread executes protected code on that entity. If there’s a 
concurrent request to lock the same entity, the other thread should wait until the entity becomes available.

4. `EntityLocker` allows concurrent execution of protected code on different entities.

5. It allows reentrant locking.

6. It allows the caller to specify timeout for locking an entity with construction:
  ```java
 EntityLocker entityLocker;
 T entityId;
 .....
  try {
      entityLocker.tryLock(entityId, 10, TimeUnit.SECONDS);
      ...;
  } finally {
      entityLocker.unlock(entityId);
  }
  ```

7. It implements protection from deadlocks (but not taking into account possible locks outside EntityLocker).

8. It implements global lock. Protected code that executes under a global lock must not execute concurrently 
with any other protected code with construction:
```java
EntityLocker entityLocker;
.....
try {
   entityLocker.globalLock();
   ...;
} finally {
   entityLocker.globalUnlock();
}
```

or with construction:
```java
EntityLocker entityLocker;
.....
try {
   entityLocker.tryGlobalLock(10, TimeUnit.SECONDS);
   ...;
} finally {
   entityLocker.globalUnlock();
}
```

# Implementation

## AbstractEntityLockerImpl
The main implementation of the [EntityLocker](src/main/java/ofedorova/enity/sync/EntityLocker.java) interface is represented by the 
[AbstractEntityLockerImpl](src/main/java/ofedorova/enity/sync/impl/lockers/AbstractEntityLockerImpl.java) class.
The class contains one abstract method `EntityLockInfo getLock(T entityId)` that allows you to define the implementation 
of the [`EntityLock`](src/main/java/ofedorova/enity/sync/EntityLock.java) interface for the field `lock` in object of type
[`EntityLockInfo`](src/main/java/ofedorova/enity/sync/EntityLockInfo.java).
 
[`EntityLockInfo`](src/main/java/ofedorova/enity/sync/EntityLockInfo.java) class is used for `lockStorage` variable that holds lock 
for entity in [AbstractEntityLockerImpl](src/main/java/ofedorova/enity/sync/impl/lockers/AbstractEntityLockerImpl.java) class.

The class is extended by two implementations:
- [EntityExtendedReentrantLockImpl](src/main/java/ofedorova/enity/sync/impl/lockers/EntityExtendedReentrantLockImpl.java)
- [EntityCustomLockImpl](src/main/java/ofedorova/enity/sync/impl/lockers/EntityCustomLockImpl.java)

Test for all implementation are [here](src/test/java/ofedorova/enity/sync/impl/lockers/EntityLockerImplTest.java).

### EntityExtendedReentrantLockImpl
[EntityExtendedReentrantLockImpl](src/main/java/ofedorova/enity/sync/impl/lockers/EntityExtendedReentrantLockImpl.java) class 
uses `lockStorage` with [`ExtendedReentrantLock`](src/main/java/ofedorova/enity/sync/impl/locks/ExtendedReentrantLock.java) implementation.

### EntityCustomLockImpl
[EntityCustomLockImpl](src/main/java/ofedorova/enity/sync/impl/lockers/EntityCustomLockImpl.java) class uses `lockStorage` 
with [`CustomLock`](src/main/java/ofedorova/enity/sync/impl/locks/CustomLock.java) implementation.

### EntityLock
The interface [EntityLock](src/main/java/ofedorova/enity/sync/EntityLock.java) extends the `java.util.concurrent.locks.Lock` interface, 
and exposes new methods:
```java

    Thread getOwner();

    Set<Thread> getOwnerAndQueuedThreads();
```
These methods are used in the algorithm for determining a possible deadlock.

### ExtendedReentrantLock
The class [`ExtendedReentrantLock`](src/main/java/ofedorova/enity/sync/impl/locks/ExtendedReentrantLock.java) implements 
the [`EntityLock`](src/main/java/ofedorova/enity/sync/EntityLock.java) interface and extends `java.util.concurrent.locks.ReentrantLock`. 

### CustomLock
The class [`CustomLock`](src/main/java/ofedorova/enity/sync/impl/locks/CustomLock.java) implements the 
[`EntityLock`](src/main/java/ofedorova/enity/sync/EntityLock.java) interface. 
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

4. It allows the caller to specify timeout for locking an entity with construction:
 ```java
 CustomLock customLock = new CustomLock();
 
 try {
     customLock.tryLock(10, TimeUnit.SECONDS);
     ...;
 } finally {
     customLock.unlock();
 }
 ```

Tests for class are implement [here](src/test/java/ofedorova/enity/sync/impl/locks/CustomLockTest.java).

### DeadlockChecker
The [`DeadlockChecker`](src/main/java/ofedorova/enity/sync/DeadlockChecker.java) interface is used to determine a possible deadlock in case of an attempt to block the entity and 
throws a [`DeadlockException`](src/main/java/ofedorova/enity/sync/exception/DeadlockException.java) error.
The interface implements the [`DeadlockCheckerImpl`](src/main/java/ofedorova/enity/sync/impl/utils/DeadlockCheckerImpl.java) class.
For determine a possible deadlock, we are  try to make a list of deadlock resources. If the size of the list is 
greater than or equal to 2, then this is a deadlock case.
The list is built by traversing resources and threads that hold them or are in the queue to wait for a lock.

Example:
- Thread1 holds Resource1, Resource4, wait Resource2
- Thread2 holds Resource2, Resource5
- Thread3 holds Resource3, wait Resource4

When Thread2 tries to block Resource3 gets deadlock with Resource3, Resource2, Resource4.


Tests for class are implement [here](src/test/java/ofedorova/enity/sync/impl/utils/DeadlockCheckerImplTest.java).