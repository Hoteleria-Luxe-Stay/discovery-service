package com.hotel.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke + integration test del Discovery Service (Eureka Server).
 *
 * Levanta el ApplicationContext con eureka.client desactivado (esta config ya
 * esta en application.yml: register-with-eureka=false, fetch-registry=false).
 * Verifica que la SecurityFilterChain se construye correctamente, lo que
 * garantiza que los lambdas de la DSL HttpSecurity (csrf, authorizeHttpRequests,
 * httpBasic) se ejecutan y JaCoCo los registra como cubiertos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = {
        "EUREKA_USER=test-user",
        "EUREKA_PASSWORD=test-pass"
})
class DiscoveryServiceApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Test
    void contextLoadsSuccessfully() {
        assertThat(applicationContext).isNotNull();
        // El bean se llama 'filterChain' (nombre del metodo @Bean en SecurityConfig)
        assertThat(applicationContext.containsBean("filterChain")).isTrue();
    }

    @Test
    void securityFilterChainBeanIsBuilt() {
        // Cubre los lambdas de la DSL HttpSecurity (csrf, authorizeHttpRequests, httpBasic)
        assertThat(securityFilterChain).isNotNull();
    }

    @Test
    void discoveryServiceApplicationBeanIsLoaded() {
        DiscoveryServiceApplication app = applicationContext.getBean(DiscoveryServiceApplication.class);
        assertThat(app).isNotNull();
    }
}
