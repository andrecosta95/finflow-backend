package br.com.finflow.auth.config

import br.com.finflow.auth.service.OAuth2UserService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val oAuth2UserService: OAuth2UserService,
    private val oAuth2SuccessHandler: OAuth2SuccessHandler,
    @Value("\${app.frontend.url}") private val frontendUrl: String
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // Desabilita CSRF — API REST stateless usa JWT, não session cookie
            .csrf { it.disable() }

            // CORS configurado explicitamente (apenas frontend autorizado)
            .cors { it.configurationSource(corsConfigurationSource()) }

            // Sem sessão no servidor — cada request é autenticado via JWT
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }

            .authorizeHttpRequests { auth ->
                auth
                    // Rotas públicas
                    .requestMatchers(
                        "/api/auth/**",
                        "/oauth2/**",
                        "/login/**",
                        "/dev/**",
                        "/actuator/health",
                        "/v3/api-docs/**",
                        "/swagger-ui/**"
                    ).permitAll()
                    // Todo o resto exige autenticação
                    .anyRequest().authenticated()
            }

            // Configura login OAuth2 (Google, Facebook, Apple)
            .oauth2Login { oauth2 ->
                oauth2
                    .userInfoEndpoint { it.userService(oAuth2UserService) }
                    .successHandler(oAuth2SuccessHandler)
                    .failureUrl("$frontendUrl/login?error=oauth_failed")
            }

            // JWT filter roda antes do filtro de autenticação padrão do Spring
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOrigins = listOf(frontendUrl)
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true  // Necessário para enviar o cookie de refresh token
            maxAge = 3600L
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}
