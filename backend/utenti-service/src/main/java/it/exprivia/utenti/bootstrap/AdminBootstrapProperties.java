package it.exprivia.utenti.bootstrap;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "admin-bootstrap")
public class AdminBootstrapProperties {

    private boolean enabled;
    private String fullName;
    private String email;
    private String password;
}
