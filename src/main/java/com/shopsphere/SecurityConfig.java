package com.shopsphere;

import com.shopsphere.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> {
            String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
            com.shopsphere.model.User customer = users
                .findByEmail(normalizedEmail)
                .orElseThrow(() ->
                    new UsernameNotFoundException("Invalid credentials")
                );
            return User.withUsername(customer.email)
                .password(customer.password)
                .roles(customer.role.name())
                .build();
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http)
        throws Exception {
        // Spring Security's default CSRF protection remains enabled for all write requests.
        http.authorizeHttpRequests(authorize ->
            authorize
                .requestMatchers("/api/admin/**")
                .hasRole("ADMIN")
                .requestMatchers(
                    "/api/auth/me",
                    "/api/cart/**",
                    "/api/orders/**"
                )
                .authenticated()
                .anyRequest()
                .permitAll()
        );

        http.formLogin(login ->
            login
                .loginProcessingUrl("/api/auth/login")
                .successHandler((request, response, authentication) ->
                    writeMessage(response, 200, "Signed in")
                )
                .failureHandler((request, response, exception) ->
                    writeMessage(response, 401, "Incorrect email or password")
                )
                .permitAll()
        );

        http.logout(logout ->
            logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((request, response, authentication) ->
                    writeMessage(response, 200, "Signed out")
                )
                .deleteCookies("JSESSIONID")
        );

        http.exceptionHandling(errors ->
            errors
                .authenticationEntryPoint((request, response, exception) ->
                    writeMessage(response, 401, "Please sign in to continue")
                )
                .accessDeniedHandler((request, response, exception) ->
                    writeMessage(
                        response,
                        403,
                        "Access denied or session expired. Refresh and try again."
                    )
                )
        );
        return http.build();
    }

    // Messages are fixed server-owned strings, not unescaped user input.
    private static void writeMessage(
        HttpServletResponse response,
        int status,
        String message
    ) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
