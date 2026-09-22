package com.islamicwebsite.quran.api

import com.islamicwebsite.quran.domain.HadithBookEntity
import com.islamicwebsite.quran.domain.HadithEntity
import com.islamicwebsite.quran.repo.HadithBookRepository
import com.islamicwebsite.quran.repo.HadithRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST API for the six canonical Sunni hadith collections (Kutub as-Sittah).
 *
 *   GET /api/hadith/books
 *   GET /api/hadith/{book}?page=0&size=50
 *   GET /api/hadith/{book}/{number}
 *
 * Payload shape (Hadith):  { book, number, section, arabic, translation: {en,id}, grade }
 * Payload shape (Book):    mirrors the frontend HadithBook manifest.
 */
@RestController
@RequestMapping("/api/hadith")
class HadithApi(
    private val booksRepo: HadithBookRepository,
    private val hadithRepo: HadithRepository,
) {
    companion object {
        const val MAX_PAGE_SIZE = 200
        const val DEFAULT_PAGE_SIZE = 50
    }

    @GetMapping("/books")
    @Cacheable("hadith-books")
    fun listBooks(): List<Map<String, Any?>> =
        booksRepo.findAllByOrderByDisplayOrderAsc().map(::toBookDto)

    @GetMapping("/{book}")
    @Cacheable("hadith-page", key = "#book + ':' + #page + ':' + #size")
    fun getBookPage(
        @PathVariable book: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "$DEFAULT_PAGE_SIZE") size: Int,
    ): ResponseEntity<Map<String, Any?>> {
        if (!booksRepo.existsById(book)) return ResponseEntity.notFound().build()
        val safeSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val safePage = page.coerceAtLeast(0)
        val slice = hadithRepo.findByBookSlugOrderByOrdinalAsc(book, PageRequest.of(safePage, safeSize))
        return ResponseEntity.ok(
            mapOf(
                "book" to book,
                "total" to slice.totalElements,
                "page" to safePage,
                "size" to safeSize,
                "totalPages" to slice.totalPages,
                "hadiths" to slice.content.map(::toHadithDto),
            )
        )
    }

    @GetMapping("/{book}/{number}")
    @Cacheable("hadith-one", key = "#book + ':' + #number")
    fun getHadith(@PathVariable book: String, @PathVariable number: String): ResponseEntity<Map<String, Any?>> {
        val h = hadithRepo.findByBookSlugAndNumber(book, number) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(toHadithDto(h))
    }

    private fun toBookDto(b: HadithBookEntity): Map<String, Any?> = mapOf(
        "slug" to b.slug,
        "apiSlug" to b.apiSlug,
        "name" to mapOf("en" to b.nameEn, "id" to b.nameId),
        "arabicName" to b.arabicName,
        "compiler" to mapOf("en" to b.compilerEn, "id" to b.compilerId),
        "compilerArabic" to b.compilerArabic,
        "eraCE" to b.eraCe,
        "totalHadith" to b.totalHadith,
        "description" to mapOf("en" to b.descriptionEn, "id" to b.descriptionId),
    )

    private fun toHadithDto(h: HadithEntity): Map<String, Any?> = mapOf(
        "book" to h.bookSlug,
        "number" to h.number,
        "section" to h.section,
        "arabic" to h.arabic,
        "translation" to mapOf("en" to h.textEn, "id" to h.textId),
        "grade" to h.grade,
    )
}
