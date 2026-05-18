package org.boiko.shibary_back

import de.codecentric.boot.admin.server.config.EnableAdminServer
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAdminServer
class ShibaryBackApplication

fun main(args: Array<String>) {
  runApplication<ShibaryBackApplication>(*args)
}
