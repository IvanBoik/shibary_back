package org.boiko.shibary_back.config

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Wires the database-backed business layer (controllers, services, repositories, clients).
 *
 * Disabled in the `admin` profile: the standalone Spring Boot Admin server has no business logic
 * and, crucially, must not require a DataSource so it can start (and stay observable) even while
 * the database or the main application is down.
 */
@Configuration
@Profile("!admin")
@ComponentScan(
  basePackages = [
    "org.boiko.shibary_back.controller",
    "org.boiko.shibary_back.service",
    "org.boiko.shibary_back.repository",
    "org.boiko.shibary_back.client",
  ],
)
class BusinessConfig
