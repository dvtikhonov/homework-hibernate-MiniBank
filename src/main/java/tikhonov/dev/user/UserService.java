package tikhonov.dev.user;

import tikhonov.dev.account.AccountService;
import org.hibernate.SessionFactory;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import tikhonov.dev.config.TransactionRunner;

import java.util.List;

/**
 * Пользователи в PostgreSQL. Создание пользователя и его первого счета идёт одной транзакцией.
 */
@Service
public class UserService {

    private final SessionFactory sessionFactory;
    private final AccountService accountService;
    private final TransactionRunner transactions;

    /**
     * @param sessionFactory фабрика сессий
     * @param accountService открытие первого счета
     */
    public UserService(
            SessionFactory sessionFactory,
            AccountService accountService,
            TransactionRunner transactions
    ) {
        this.sessionFactory = sessionFactory;
        this.accountService = accountService;
        this.transactions = transactions;
    }

    /**
     * Сохраняет пользователя и в той же транзакции открывает первый счет
     * с {@code account.default-amount}.
     *
     * @param login логин, уникальный с учётом регистра
     * @return созданный пользователь вместе с первым счетом
     */
    public User createUser(String login) {
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("login must not be empty");
        }
        String normalizedLogin = login.trim();
        if (normalizedLogin.isEmpty()) {
            throw new IllegalArgumentException("login must not be empty");
        }

        return transactions.execute(() -> {
            Session session = sessionFactory.getCurrentSession();
            Long existing = session.createQuery(
                            "select count(u) from User u where u.login = :login", Long.class)
                    .setParameter("login", normalizedLogin)
                    .getSingleResult();
            if (existing != null && existing > 0) {
                throw new IllegalArgumentException(
                        "user with login '%s' already exists".formatted(normalizedLogin));
            }
            User user = new User();
            user.setLogin(normalizedLogin);
            session.persist(user);
            session.flush();
            accountService.createAccount(user.getId());
            return user;
        });
    }

    /**
     * @param id идентификатор пользователя
     * @return пользователь, если он есть
     */
    public User findUserById(long id) {
        requirePositiveId(id);
        return transactions.execute(() -> {
            User user = sessionFactory.getCurrentSession().find(User.class, id);
            if (user == null) {
                throw new IllegalArgumentException("No such user: id=%d".formatted(id));
            }
            return user;
        });
    }

    /**
     * @return пользователи в порядке id
     */
    public List<User> findAll() {
        return transactions.execute(() -> sessionFactory.getCurrentSession()
                .createQuery("select distinct u from User u order by u.id", User.class)
                .getResultList());

    }

    private static void requirePositiveId(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("user id must be > 0");
        }
    }
}