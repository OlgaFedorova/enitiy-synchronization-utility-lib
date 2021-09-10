package ofedorova.enity.sync;

/**
 * DeadlockChecker.
 *
 * @author Olga_Fedorova
 */
public interface DeadlockChecker {

    void beforeLock(EntityLockInfo lockInfo, boolean preventDeadlock);

    void afterUnlock(EntityLockInfo lockInfo);
}
