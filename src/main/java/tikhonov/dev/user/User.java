package tikhonov.dev.user;

import tikhonov.dev.account.Account;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * Пользователь банка. Таблица {@code users}.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login", nullable = false, unique = true, length = 255)
    private String login;

    @OneToMany(mappedBy = "user", fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<Account> accountList = new ArrayList<>();

    /**
     * Конструктор для Hibernate.
     */
    public User() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    /**
     * Живая коллекция счетов. Её подменяет Hibernate, поэтому наружу отдаётся само поле.
     *
     * @return счета пользователя в порядке id
     */
    public List<Account> getAccountList() {
        return accountList;
    }

    public void setAccountList(List<Account> accountList) {
        this.accountList = accountList == null ? new ArrayList<>() : accountList;
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", login='" + login + "', accounts=" + accountList + "}";
    }
}