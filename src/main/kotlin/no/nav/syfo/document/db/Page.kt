package no.nav.syfo.document.db

class Page<out T>(
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
