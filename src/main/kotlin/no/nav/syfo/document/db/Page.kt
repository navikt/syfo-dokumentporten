package no.nav.syfo.document.db

import no.nav.syfo.document.api.v1.dto.DocumentResponse

data class Page<out T>(
    val items: List<T>,
    val meta: Meta,
) {
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
        meta = this.meta,
    )
}
