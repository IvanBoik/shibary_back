package org.boiko.shibary_admin

import de.codecentric.boot.admin.server.config.EnableAdminServer
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Standalone Spring Boot Admin monitoring server.
 *
 * Runs as a separate process/container so it stays up and observable even when the main
 * application fails to start: its DOWN/OFFLINE state and last known health/logs remain visible.
 *
 * This module deliberately has no database and no business logic. Monitored applications (the main
 * service) register themselves here as SBA clients.
 */
@SpringBootApplication
@EnableAdminServer
class ShibaryAdminApplication

fun main(args: Array<String>) {
  runApplication<ShibaryAdminApplication>(*args)
}
