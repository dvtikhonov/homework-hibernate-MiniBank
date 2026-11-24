package tikhonov.dev.config;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.resource.transaction.spi.TransactionStatus;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Общая транзакция для вложенных вызовов сервисов.
 * <p>
 * Внешний вызов открывает транзакцию и отвечает за commit, rollback и закрытие
 * {@link Session}. Вложенный вызов получает ту же current session и не завершает её.
 * Отдельный savepoint не создаётся.
 */
@Component
public class TransactionRunner {

    private final SessionFactory sessionFactory;

    /**
     * @param sessionFactory фабрика с контекстом сессии {@code thread}
     */
    public TransactionRunner(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    /**
     * Выполняет действие в текущей сессии потока.
     *
     * @param action чтение или запись через current session
     * @param <T>    тип результата
     * @return результат действия
     */
    public <T> T execute(Supplier<T> action) {
        Session session = sessionFactory.getCurrentSession();
        Transaction transaction = session.getTransaction();
        boolean owner = transaction.getStatus() == TransactionStatus.NOT_ACTIVE;
        if (owner) {
            transaction = session.beginTransaction();
        }
        try {
            T result = action.get();
            if (owner) {
                transaction.commit();
            }
            return result;
        } catch (RuntimeException ex) {
            if (owner) {
                rollback(transaction, ex);
            }
            throw ex;
        } finally {
            if (owner && session.isOpen()) {
                session.close();
            }
        }
    }

    private static void rollback(Transaction transaction, RuntimeException original) {
        try {
            if (transaction.getStatus().canRollback()) {
                transaction.rollback();
            }
        } catch (RuntimeException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }
}