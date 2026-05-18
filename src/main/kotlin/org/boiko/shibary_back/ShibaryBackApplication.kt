package org.boiko.shibary_back

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class ShibaryBackApplication

fun main(args: Array<String>) {
  runApplication<ShibaryBackApplication>(*args)
}
