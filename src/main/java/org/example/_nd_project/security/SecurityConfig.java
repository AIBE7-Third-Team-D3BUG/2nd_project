package org.example._nd_project.security;

import org.example._nd_project.member.MemberService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    @Profile("db")
    public DaoAuthenticationProvider daoAuthenticationProvider(MemberUserDetailsService userDetailsService,
                                                                PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    @Profile("db")
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   DaoAuthenticationProvider daoAuthenticationProvider,
                                                   LoginAttemptService attempts,
                                                   LoginFailureHandler loginFailureHandler,
                                                   MemberService memberService,
                                                   ObjectProvider<KakaoOAuth2UserService> kakaoOAuth2UserServiceProvider,
                                                   ObjectProvider<GoogleOAuth2UserService> googleOAuth2UserServiceProvider,
                                                   SessionRegistry sessionRegistry) throws Exception {
        AuthenticationSuccessHandler loginSuccessHandler = (request, response, authentication) -> {
            attempts.clear(authentication.getName(), request.getRemoteAddr());
            if (authentication.getPrincipal() instanceof MemberPrincipal principal) {
                memberService.recordSuccessfulLogin(principal.memberId());
            } else {
                memberService.recordSuccessfulLogin(authentication.getName());
            }
            boolean admin = authentication.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
            response.sendRedirect(request.getContextPath() + (admin ? "/admin" : "/profile"));
        };
        http
                .authenticationProvider(daoAuthenticationProvider)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/signup", "/login", "/error", "/css/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/profile/**", "/tasks/**", "/chat", "/chat/**", "/ws/**").authenticated()
                        .anyRequest().permitAll()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .failureHandler(loginFailureHandler)
                        .successHandler(loginSuccessHandler)
                )
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler((request, response, exception) ->
                                response.sendRedirect(request.getContextPath() + "/?accessDenied"))
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                )
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.migrateSession())
                        .maximumSessions(1)
                        .sessionRegistry(sessionRegistry)
                )
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                )
                .addFilterBefore(new LoginRateLimitFilter(attempts), UsernamePasswordAuthenticationFilter.class);

        KakaoOAuth2UserService kakaoOAuth2UserService = kakaoOAuth2UserServiceProvider.getIfAvailable();
        GoogleOAuth2UserService googleOAuth2UserService = googleOAuth2UserServiceProvider.getIfAvailable();
        if (kakaoOAuth2UserService != null || googleOAuth2UserService != null) {
            OAuth2UserService<OAuth2UserRequest, OAuth2User> socialOAuth2UserService = userRequest -> {
                String registrationId = userRequest.getClientRegistration().getRegistrationId();
                if ("kakao".equals(registrationId) && kakaoOAuth2UserService != null) {
                    return kakaoOAuth2UserService.loadUser(userRequest);
                }
                if ("google".equals(registrationId) && googleOAuth2UserService != null) {
                    return googleOAuth2UserService.loadUser(userRequest);
                }
                return new DefaultOAuth2UserService().loadUser(userRequest);
            };
            http.oauth2Login(oauth2 -> oauth2
                    .loginPage("/login")
                    .userInfoEndpoint(userInfo -> userInfo.userService(socialOAuth2UserService))
                    .successHandler(loginSuccessHandler)
                    .failureHandler((request, response, exception) -> {
                        log.warn("Social OAuth login failed: type={}, message={}",
                                exception.getClass().getSimpleName(), exception.getMessage());
                        response.sendRedirect(request.getContextPath() + "/login?socialError");
                    })
            );
        }

        return http.build();
    }
}
