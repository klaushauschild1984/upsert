package de.hauschild.upsert.sql

import io.micrometer.observation.Observation

internal inline fun <T> Observation.timed(block: () -> T): T {
    start()
    return try {
        block().also { stop() }
    } catch (exception: Exception) {
        error(exception)
        stop()
        throw exception
    }
}
