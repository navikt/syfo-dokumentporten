package no.nav.syfo.document.db

import no.nav.syfo.document.api.v1.dto.DocumentResponse

data class Page<out T>(
    val page: Int,
    val totalPages: Int,
    val totalElements: Long,
    val pageSize: Int,
    val items: List<T>,
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 50
        const val MAX_PAGE_SIZE = 200
        const val FIRST_PAGE = 0
    }

    enum class OrderBy(val columnName: String) {
        CREATED("created"),
        UPDATED("updated"),
    }

    enum class OrderDirection {
        ASC,
        DESC,
    }
}

fun Page<PersistedDocumentEntity>.toDocumentResponsePage(): Page<DocumentResponse> {
    return Page(
        items = this.items.map { it.toDocumentResponse() },
        pageSize = this.pageSize,
        page = this.page,
        totalElements = this.totalElements,
        totalPages = this.totalPages,
    )
}
