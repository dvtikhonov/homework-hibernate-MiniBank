package tikhonov.dev.config;

import tikhonov.dev.account.Account;
import tikhonov.dev.user.User;
import org.hibernate.SessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

/**
 * Ручная сборка {@link SessionFactory}. Параметры читаются из
 * {@code application.properties}, Spring Boot и автоконфигурации не используются.
 */
@Configuration
@PropertySource("classpath:application.properties")
public class HibernateConfiguration {

    /**
     * @param env настройки подключения и Hibernate
     * @return фабрика сессий; Spring закроет её при остановке контекста
     */
    @Bean(destroyMethod = "close")
    public SessionFactory sessionFactory(Environment env) {
        org.hibernate.cfg.Configuration cfg = new org.hibernate.cfg.Configuration();
        String currentSessionContext = env.getRequiredProperty("hibernate.current_session_context_class");
        cfg.addAnnotatedClass(User.class)
                .addAnnotatedClass(Account.class)
                .setProperty("hibernate.connection.driver_class", env.getRequiredProperty("db.driver"))
                .setProperty("hibernate.connection.url", env.getRequiredProperty("db.url"))
                .setProperty("hibernate.connection.username", env.getRequiredProperty("db.username"))
                .setProperty("hibernate.connection.password", env.getRequiredProperty("db.password"))
                .setProperty("hibernate.dialect", env.getRequiredProperty("db.dialect"))
                .setProperty("hibernate.hbm2ddl.auto", env.getRequiredProperty("hibernate.hbm2ddl.auto"))
                .setProperty("hibernate.show_sql", env.getRequiredProperty("hibernate.show_sql"))
                .setProperty("hibernate.format_sql", env.getRequiredProperty("hibernate.format_sql"))
                .setProperty("hibernate.current_session_context_class", currentSessionContext);
        return cfg.buildSessionFactory();
    }
}