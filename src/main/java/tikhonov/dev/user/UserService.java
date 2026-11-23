package tikhonov.dev.user;

import tikhonov.dev.account.AccountService;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Пользователи в PostgreSQL. Создание пользователя и его первого счета идёт одной транзакцией.
 */
@Service
public class UserService {

    private final SessionFactory sessionFactory;
    private final AccountService accountService;

    /**
     * @param sessionFactory фабрика сессий
     * @param accountService открытие первого счета
     */
    public UserService(
            SessionFactory sessionFactory,
            AccountService accountService
    ) {
        this.sessionFactory = sessionFactory;
        this.accountService = accountService;
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

        return new User(); // stub заглушка

    }

    /**
     * @param id идентификатор пользователя
     * @return пользователь, если он есть
     */
    public User findUserById(long id) {
        requirePositiveId(id);

        return new User(); // stub заглущка

    }

    /**
     * @return пользователи в порядке id
     */
    public List<User> findAll() {
            return new ArrayList<>(); // stub заглушка

    }

    private static void requirePositiveId(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("user id must be > 0");
        }
    }
}