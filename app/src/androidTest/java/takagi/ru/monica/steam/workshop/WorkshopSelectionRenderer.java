package takagi.ru.monica.steam.workshop;

import androidx.compose.runtime.Composer;
import androidx.compose.ui.Modifier;
import java.util.List;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import takagi.ru.monica.steam.data.SteamAccount;
import takagi.ru.monica.steam.data.SteamStorageSource;
import takagi.ru.monica.steam.navigation.ui.SteamEssentialsFloatingToolbarKt;
import takagi.ru.monica.steam.navigation.ui.SteamToolbarItem;

/**
 * Calls the production layouts without generating test-side references to Compose $stable
 * fields that R8 has folded out of the application. Zero change masks let the layouts
 * inspect their inputs normally; no production keep rules or test-only UI are needed.
 */
final class WorkshopSelectionRenderer {
    static void render(SteamAccount account, SteamStorageSource source, Modifier dockModifier,
                       List<SteamToolbarItem> dockItems, Composer composer) {
        Function0<Unit> noOp = () -> Unit.INSTANCE;
        SteamWorkshopScreenKt.SteamWorkshopScreen(550, "Left 4 Dead 2", account, source,
                noOp, Modifier.Companion, null, noOp, null, null, composer, 0, 0);
        SteamEssentialsFloatingToolbarKt.SteamEssentialsFloatingToolbar(dockModifier,
                dockItems, 0, null, null, true, composer, 0, 0);
    }

    private WorkshopSelectionRenderer() { }
}
