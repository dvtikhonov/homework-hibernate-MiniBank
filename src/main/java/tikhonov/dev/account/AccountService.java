package tikhonov.dev.account;

import tikhonov.dev.config.TransactionRunner;
import tikhonov.dev.user.User;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * Операции со счетами через current session. Вложенный вызов присоединяется к уже открытой транзакции.
 */
@Service
public class AccountService {

    private final SessionFactory sessionFactory;
    private final TransactionRunner transactions;
    private final AccountProperties accountProperties;

    /**
     * @param sessionFactory    фабрика сессий
     * @param accountProperties начальный баланс и комиссия
     */
    public AccountService(
            SessionFactory sessionFactory,
            TransactionRunner transactions,
            AccountProperties accountProperties
    ) {
        this.sessionFactory = sessionFactory;
        this.transactions = transactions;
        this.accountProperties = accountProperties;
    }

    /**
     * Открывает счет с балансом из настроек и добавляет его пользователю.
     * Если транзакцию уже открыл {@code UserService.createUser}, присоединяется к ней.
     *
     * @param userId идентификатор владельца
     * @return новый счет
     */
    public Account createAccount(long userId) {
        requirePositiveId(userId, "user id");

        return transactions.execute(() -> {
            Session session = sessionFactory.getCurrentSession();
            User user = session.find(User.class, userId);
            if (user == null) {
                throw new IllegalArgumentException("No such user: id=%d".formatted(userId));
            }
            Account account = new Account();
            account.setUser(user);
            account.setMoneyAmount(accountProperties.getDefaultAmount());
            session.persist(account);
            user.getAccountList().add(account);
            session.flush();
            return account;
        });


    }

    /**
     * @param accountId счет
     * @param amount    положительная сумма пополнения
     * @return баланс после пополнения
     */
    public int deposit(long accountId, int amount) {
        requirePositiveId(accountId, "account id");
        requirePositiveAmount(amount);
        return transactions.execute(() -> {
            Account account = requireAccount(accountId);
            int updated = add(account.getMoneyAmount(), amount, account.getId());
            account.setMoneyAmount(updated);
            return updated;
        });
    }

    /**
     * @param accountId счет
     * @param amount    положительная сумма снятия
     * @return баланс после снятия
     */
    public int withdraw(long accountId, int amount) {
        requirePositiveId(accountId, "account id");
        requirePositiveAmount(amount);
        return transactions.execute(() -> {
            Account account = requireAccount(accountId);
            if (amount > account.getMoneyAmount()) {
                throw new IllegalArgumentException(
                        "insufficient funds on account id=%d, moneyAmount=%d, attempted withdraw=%d"
                                .formatted(account.getId(), account.getMoneyAmount(), amount));
            }
            int updated = subtract(account.getMoneyAmount(), amount, account.getId());
            account.setMoneyAmount(updated);
            return updated;
        });
    }

    /**
     * Переводит сумму. Между разными пользователями получатель получает сумму за вычетом комиссии.
     * Оба баланса вычисляются до изменения счетов. Владельцы сравниваются по id.
     *
     * @param fromAccountId счет отправителя
     * @param toAccountId   счет получателя
     * @param amount        списываемая сумма
     * @return результат перевода
     */
    public TransferResult transfer(long fromAccountId, long toAccountId, int amount) {
        requirePositiveId(fromAccountId, "account id");
        requirePositiveId(toAccountId, "account id");
        requirePositiveAmount(amount);
        if (fromAccountId == toAccountId) {
            throw new IllegalArgumentException("source and target account id must be different");
        }

        return transactions.execute(() -> {
            Account source = requireAccount(fromAccountId);
            Account target = requireAccount(toAccountId);
            if (amount > source.getMoneyAmount()) {
                throw new IllegalArgumentException(
                        "Not enough money to transfer from account: id=%d, moneyAmount=%d, attemptedTransfer=%d"
                                .formatted(source.getId(), source.getMoneyAmount(), amount));
            }
            boolean sameUser = source.getUser().getId().equals(target.getUser().getId());
            int received = sameUser ? amount : amountAfterCommission(amount);
            int sourceAfter = subtract(source.getMoneyAmount(), amount, source.getId());
            int targetAfter = add(target.getMoneyAmount(), received, target.getId());
            source.setMoneyAmount(sourceAfter);
            target.setMoneyAmount(targetAfter);
            return new TransferResult(
                    source.getId(),
                    target.getId(),
                    amount,
                    amount - received,
                    received,
                    sameUser
            );
        });
    }

    /**
     * Закрывает счет и переносит остаток без комиссии на счет этого пользователя с наименьшим id.
     * Единственный счет пользователя закрыть нельзя.
     *
     * @param accountId закрываемый счет
     * @return куда и сколько перенесено
     */
    public CloseAccountResult close(long accountId) {
        requirePositiveId(accountId, "account id");

        return transactions.execute(() -> {
            Session session = sessionFactory.getCurrentSession();
            Account account = requireAccount(accountId);
            User user = account.getUser();
            List<Account> accounts = user.getAccountList();
            if (accounts.size() <= 1) {
                throw new IllegalArgumentException(
                        "cannot close the only account of user id=%d".formatted(user.getId()));
            }
            Account target = null;
            for (Account candidate : accounts) {
                if (candidate.getId().equals(account.getId())) {
                    continue;
                }
                if (target == null || candidate.getId().compareTo(target.getId()) < 0) {
                    target = candidate;
                }
            }
            if (target == null) {
                throw new IllegalArgumentException(
                        "cannot close the only account of user id=%d".formatted(user.getId()));
            }
            int remainder = account.getMoneyAmount();
            target.setMoneyAmount(add(target.getMoneyAmount(), remainder, target.getId()));
            accounts.removeIf(item -> item.getId().equals(account.getId()));
            session.remove(account);
            return new CloseAccountResult(
                    account.getId().intValue(),
                    target.getId().intValue(),
                    remainder);
        });
    }

    private Account requireAccount(long accountId) {
        Account account = sessionFactory.getCurrentSession().find(Account.class, accountId);
        if (account == null) {
            throw new IllegalArgumentException("No such account: id=%d".formatted(accountId));
        }
        return account;
    }

    private static void requirePositiveId(long id, String name) {
        if (id <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
    }

    private static void requirePositiveAmount(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
    }

    private int amountAfterCommission(int amount) {
        double received = amount * (1.0d - accountProperties.getTransferCommission());
        long rounded = Math.round(received);
        if (rounded < 0 || rounded > amount) {
            throw new IllegalArgumentException("calculated transfer amount is out of range");
        }
        return (int) rounded;
    }

    private static int add(int balance, int amount, long accountId) {
        try {
            return Math.addExact(balance, amount);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("balance overflow on account id=%d".formatted(accountId));
        }
    }

    private static int subtract(int balance, int amount, long accountId) {
        try {
            return Math.subtractExact(balance, amount);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("balance overflow on account id=%d".formatted(accountId));
        }
    }
}