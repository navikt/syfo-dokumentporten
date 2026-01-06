package no.nav.syfo.document.db

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.syfo.document.api.v1.dto.DocumentResponse

data class Page<out T>(
    @JsonIgnore
    val page: Int,
    @JsonIgnore
    val totalPages: Int,
    @JsonIgnore
    val totalElements: Long,
    @JsonIgnore
    val limit: Int,
    val items: List<T>,
) {
    val meta = Meta(
        size = items.size,
        pageSize = limit,
        hasMore = page < totalPages,
        resultSize = totalElements,
    )

    enum class OrderBy(val columnName: String) {
        CREATED("created"),
        UPDATED("updated"),
    }

    enum class OrderDirection {
        ASC,
        DESC,
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 50
        const val MAX_PAGE_SIZE = 200
        const val FIRST_PAGE = 0
    }

    data class Meta(
        val size: Int,
        val pageSize: Int,
        val hasMore: Boolean,
        val resultSize: Long,
    )
}

fun Page<PersistedDocumentEntity>.toDocumentResponsePage(): Page<DocumentResponse> {
    return Page(
        items = this.items.map { it.toDocumentResponse() },
        limit = this.limit,
        page = this.page,
        totalElements = this.totalElements,
        totalPages = this.totalPages,
    )
}
