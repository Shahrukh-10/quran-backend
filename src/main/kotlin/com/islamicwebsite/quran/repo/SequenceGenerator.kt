package com.islamicwebsite.quran.repo

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component

/**
 * Simple Mongo-backed sequence generator to fill the gap left by JPA's
 * `@GeneratedValue(IDENTITY)`. Each domain that needs a monotonically-
 * increasing Long id (AppUser, Masjid, …) asks this helper for the next
 * value under a named sequence key.
 *
 * Uses findAndModify with $inc so concurrent callers each get a distinct id.
 */
@Document(collection = "app_counters")
class DbCounter(
    @Id var key: String = "",
    var seq: Long = 0,
)

@Component
class SequenceGenerator(private val mongo: MongoTemplate) {
    fun nextValue(key: String): Long {
        val opts = FindAndModifyOptions.options().returnNew(true).upsert(true)
        val counter = mongo.findAndModify(
            Query(Criteria.where("_id").`is`(key)),
            Update().inc("seq", 1L),
            opts,
            DbCounter::class.java,
        )
        return counter?.seq ?: 1L
    }
}
