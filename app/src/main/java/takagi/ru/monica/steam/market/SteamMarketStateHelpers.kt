package takagi.ru.monica.steam.market

import takagi.ru.monica.steam.network.SteamConfirmation

fun mergeSteamInventoryStacks(
    existing: List<SteamInventoryItemStack>,
    incoming: List<SteamInventoryItem>
): List<SteamInventoryItemStack> {
    val stacks = existing.associateBy { it.item.stackKey }.toMutableMap()
    val order = existing.map { it.item.stackKey }.toMutableList()
    incoming.forEach { item ->
        val amount = item.amount.coerceAtLeast(1)
        val current = stacks[item.stackKey]
        if (current == null) {
            stacks[item.stackKey] = SteamInventoryItemStack(
                item = item,
                assetIds = listOf(item.assetId),
                assetAmounts = mapOf(item.assetId to amount)
            )
            order += item.stackKey
        } else {
            val assetIds = if (item.assetId in current.assetIds) {
                current.assetIds
            } else {
                current.assetIds + item.assetId
            }
            val previousAmount = current.assetAmounts[item.assetId] ?: 0
            stacks[item.stackKey] = current.copy(
                assetIds = assetIds,
                assetAmounts = current.assetAmounts + (
                    item.assetId to safeSteamInventoryAmountSum(previousAmount, amount)
                )
            )
        }
    }
    return order.mapNotNull(stacks::get)
}

data class SteamInventorySaleAllocation(
    val assetId: String,
    val amount: Int
)

fun allocateSteamInventorySale(
    stack: SteamInventoryItemStack,
    requestedQuantity: Int
): List<SteamInventorySaleAllocation> {
    var remaining = requestedQuantity.coerceIn(0, stack.count)
    if (remaining == 0) return emptyList()
    return buildList {
        stack.assetIds.forEach { assetId ->
            if (remaining == 0) return@forEach
            val available = (stack.assetAmounts[assetId] ?: 1).coerceAtLeast(0)
            val amount = minOf(available, remaining)
            if (amount > 0) {
                add(SteamInventorySaleAllocation(assetId = assetId, amount = amount))
                remaining -= amount
            }
        }
    }
}

fun removeSteamInventoryAmount(
    stack: SteamInventoryItemStack,
    removedQuantity: Int
): SteamInventoryItemStack? {
    var remainingToRemove = removedQuantity.coerceAtLeast(0)
    if (remainingToRemove == 0) return stack
    val remainingIds = mutableListOf<String>()
    val remainingAmounts = linkedMapOf<String, Int>()
    stack.assetIds.forEach { assetId ->
        val available = (stack.assetAmounts[assetId] ?: 1).coerceAtLeast(0)
        val removed = minOf(available, remainingToRemove)
        val left = available - removed
        remainingToRemove -= removed
        if (left > 0) {
            remainingIds += assetId
            remainingAmounts[assetId] = left
        }
    }
    return if (remainingIds.isEmpty()) {
        null
    } else {
        stack.copy(assetIds = remainingIds, assetAmounts = remainingAmounts)
    }
}

private fun safeSteamInventoryAmountSum(left: Int, right: Int): Int =
    (left.toLong() + right.toLong())
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()

fun findNewSteamMarketConfirmations(
    preExistingIds: Set<String>,
    latest: List<SteamConfirmation>
): List<SteamConfirmation> {
    return latest.filter { confirmation ->
        confirmation.isMarketListingConfirmation() && confirmation.id !in preExistingIds
    }
}

fun removeCancelledSteamMarketListings(
    existing: List<SteamMarketListing>,
    cancelledListingIds: Set<String>
): List<SteamMarketListing> {
    if (cancelledListingIds.isEmpty()) return existing
    return existing.filterNot { it.listingId in cancelledListingIds }
}

fun SteamConfirmation.isMarketListingConfirmation(): Boolean {
    val normalized = type.trim().lowercase()
    return normalized == "3" || normalized.contains("market") || normalized.contains("sell")
}
