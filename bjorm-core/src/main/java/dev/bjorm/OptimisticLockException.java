package dev.bjorm;
/** Raised when version-controlled update or delete affects no row. */
public final class OptimisticLockException extends RuntimeException {
    public OptimisticLockException(String message) { super(message); }
}
