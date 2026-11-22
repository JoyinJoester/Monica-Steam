package takagi.ru.monica.steam.store.gift

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamStoreGiftUiGuardTest {
    @Test
    fun purchaseActionsUseAnExpressiveSplitButtonAndNativeFriendPicker() {
        val splitButton = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/gift/ui/" +
                "SteamStoreGiftPurchaseSplitButton.kt"
        ).readText()
        val picker = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/gift/ui/" +
                "SteamStoreGiftRecipientSheet.kt"
        ).readText()
        val store = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamStoreScreen.kt"
        ).readText()

        assertTrue(splitButton.contains("SplitButtonLayout("))
        assertTrue(splitButton.contains("SplitButtonDefaults.TonalLeadingButton("))
        assertTrue(splitButton.contains("SplitButtonDefaults.TonalTrailingButton("))
        assertTrue(splitButton.contains("height(PurchaseActionHeight)"))
        assertTrue(splitButton.contains("size(PurchaseActionHeight)"))
        assertTrue(splitButton.contains("onAddAsGift()"))
        assertTrue(picker.contains("MonicaModalBottomSheet("))
        assertTrue(picker.contains("SteamSheetSearchField(query, { query = it },"))
        assertTrue(picker.contains("FriendAvatar(friend = friend, size = 48)"))
        assertTrue(picker.contains("heightIn(min = 80.dp)"))
        assertTrue(store.contains("SteamStoreGiftPurchaseSplitButton("))
        assertTrue(store.contains("SteamStoreGiftRecipientSheet("))
    }

    @Test
    fun purchaseSplitButtonsUseTheSameActionHeight() {
        val splitButton = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/gift/ui/" +
                "SteamStoreGiftPurchaseSplitButton.kt"
        ).readText()

        assertTrue(splitButton.contains("private val PurchaseActionHeight = 52.dp"))
        assertEquals(1, Regex("height\\(PurchaseActionHeight\\)").findAll(splitButton).count())
        assertEquals(1, Regex("size\\(PurchaseActionHeight\\)").findAll(splitButton).count())
    }

    @Test
    fun cartShowsGiftRecipientsAndAllowsChangingThem() {
        val cart = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/ui/SteamNativeCartScreen.kt"
        ).readText()
        val viewModel = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/store/presentation/SteamStoreViewModel.kt"
        ).readText()

        assertTrue(cart.contains("SteamCartPurchaseTypeRow("))
        assertTrue(cart.contains("item.giftRecipient"))
        assertTrue(cart.contains("onEditGiftRecipient"))
        assertTrue(viewModel.contains("fun beginGiftPurchase("))
        assertTrue(viewModel.contains("fun selectGiftRecipient("))
        assertTrue(viewModel.contains("pending.copy(giftRecipient = recipient)"))
    }

    private fun projectFile(path: String): File {
        var directory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            directory.parentFile != null &&
            !File(directory, "settings.gradle").exists() &&
            !File(directory, "settings.gradle.kts").exists()
        ) {
            directory = directory.parentFile!!.canonicalFile
        }
        return File(directory, path)
    }
}
