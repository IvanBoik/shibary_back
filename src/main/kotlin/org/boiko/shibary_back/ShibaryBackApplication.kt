package org.boiko.shibary_back

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
class ShibaryBackApplication

fun main(args: Array<String>) {
    runApplication<ShibaryBackApplication>(*args)
}
