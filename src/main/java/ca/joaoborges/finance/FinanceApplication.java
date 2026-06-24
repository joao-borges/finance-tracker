package ca.joaoborges.finance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FinanceApplication {

    public static void main(final String[] args) {
        SpringApplication.run(FinanceApplication.class, args);
    }

}
