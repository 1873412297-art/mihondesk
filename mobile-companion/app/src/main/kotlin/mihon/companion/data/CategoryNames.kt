package mihon.companion.data

import mihon.sync.core.model.AndroidBackupCategory
import mihon.sync.core.model.CategoryKey

/** Wire format links mangas to categories by the sender's category `order`; we store names. */
internal object CategoryNames {
    fun fromOrders(orders: List<Long>, categories: List<AndroidBackupCategory>): List<String> {
        val byOrder = categories.associateBy { it.order }
        return orders.mapNotNull { byOrder[it]?.name }.distinctBy(CategoryKey::normalize)
    }

    fun toOrders(names: List<String>, categories: List<AndroidBackupCategory>): List<Long> {
        val byName = categories.associateBy { CategoryKey.normalize(it.name) }
        return names.mapNotNull { byName[CategoryKey.normalize(it)]?.order }.distinct().sorted()
    }
}
