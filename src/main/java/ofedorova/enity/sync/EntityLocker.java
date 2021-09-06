package ofedorova.enity.sync;

/**
 * EntityLocker.
 *
 * @author Olga_Fedorova
 */
public interface EntityLocker<T> {

    void lock(T entityId);

    void unlock(T entityId);

}
