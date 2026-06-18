package com.example.productcrud.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Kept separate from the @SpringBootApplication class: that class doubles as
 * the @SpringBootConfiguration picked up by slice tests like @WebMvcTest, which
 * have no EntityManagerFactory and would fail to build the auditing handler bean.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
