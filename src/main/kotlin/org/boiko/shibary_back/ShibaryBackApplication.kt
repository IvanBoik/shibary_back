package org.boiko.shibary_back

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

/**
 * Only the [config] package is component-scanned here. The business layer
 * (controllers/services/repositories/clients) is wired in via [config.BusinessConfig], which is
 * disabled in the `admin` profile so the standalone admin server starts without a DataSource.
 */
@SpringBootApplication(scanBasePackages = ["org.boiko.shibary_back.config"])
@ConfigurationPropertiesScan
class ShibaryBackApplication

fun main(args: Array<String>) {
  runApplication<ShibaryBackApplication>(*args)
}
