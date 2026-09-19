package com.islamicwebsite.quran

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching

@SpringBootApplication
@EnableCaching
class QuranBackendApplication

fun main(args: Array<String>) {
    runApplication<QuranBackendApplication>(*args)
}
