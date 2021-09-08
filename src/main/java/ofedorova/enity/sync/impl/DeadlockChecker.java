package ofedorova.enity.sync.impl;

import ofedorova.enity.sync.EntityLockInfo;

/**
 * DeadlockChecker.
 *
 * @author Olga_Fedorova
 */
public interface DeadlockChecker {

    void beforeLock(EntityLockInfo lockInfo, boolean preventDeadlock);

    void afterUnlock(EntityLockInfo lockInfo);
}
