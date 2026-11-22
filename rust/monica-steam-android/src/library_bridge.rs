use jni::{
    objects::{JByteArray, JClass},
    sys::jbyteArray,
    JNIEnv,
};
use monica_steam_core::{
    achievement::{parse_achievement_details, AchievementDetail},
    library::{
        parse_achievement_progress, parse_owned_games, parse_store_items, AchievementProgress,
        OwnedGame, StoreMetadata,
    },
};
use std::ptr;

const OWNED_GAMES_BRIDGE_MAGIC: &[u8; 4] = b"MSL1";
const ACHIEVEMENT_PROGRESS_BRIDGE_MAGIC: &[u8; 4] = b"MSP1";
const STORE_ITEMS_BRIDGE_MAGIC: &[u8; 4] = b"MST1";
const ACHIEVEMENT_DETAILS_BRIDGE_MAGIC: &[u8; 4] = b"MSA2";

fn write_optional_string(out: &mut Vec<u8>, value: Option<&str>) -> Option<()> {
    match value {
        Some(value) => {
            let bytes = value.as_bytes();
            let length = i32::try_from(bytes.len()).ok()?;
            out.extend_from_slice(&length.to_le_bytes());
            out.extend_from_slice(bytes);
        }
        None => out.extend_from_slice(&(-1i32).to_le_bytes()),
    }
    Some(())
}

fn write_required_string(out: &mut Vec<u8>, value: &str) -> Option<()> {
    let bytes = value.as_bytes();
    let length = u32::try_from(bytes.len()).ok()?;
    out.extend_from_slice(&length.to_le_bytes());
    out.extend_from_slice(bytes);
    Some(())
}

fn serialize_owned_games(games: &[OwnedGame]) -> Option<Vec<u8>> {
    let count = u32::try_from(games.len()).ok()?;
    let mut out = Vec::new();
    out.extend_from_slice(OWNED_GAMES_BRIDGE_MAGIC);
    out.extend_from_slice(&count.to_le_bytes());
    for game in games {
        out.extend_from_slice(&game.app_id.to_le_bytes());
        out.extend_from_slice(&game.playtime_recent_minutes.to_le_bytes());
        out.extend_from_slice(&game.playtime_forever_minutes.to_le_bytes());
        out.extend_from_slice(&game.last_played_at.to_le_bytes());
        write_required_string(&mut out, &game.name)?;
        write_required_string(&mut out, &game.icon_hash)?;
    }
    Some(out)
}

fn serialize_achievement_progress(progress: &[AchievementProgress]) -> Option<Vec<u8>> {
    let count = u32::try_from(progress.len()).ok()?;
    let mut out = Vec::with_capacity(8 + progress.len().saturating_mul(16));
    out.extend_from_slice(ACHIEVEMENT_PROGRESS_BRIDGE_MAGIC);
    out.extend_from_slice(&count.to_le_bytes());
    for item in progress {
        out.extend_from_slice(&item.app_id.to_le_bytes());
        out.extend_from_slice(&item.unlocked.to_le_bytes());
        out.extend_from_slice(&item.total.to_le_bytes());
        out.extend_from_slice(&(if item.all_unlocked { 1i32 } else { 0i32 }).to_le_bytes());
    }
    Some(out)
}

fn serialize_store_items(items: &[StoreMetadata]) -> Option<Vec<u8>> {
    let count = u32::try_from(items.len()).ok()?;
    let mut out = Vec::new();
    out.extend_from_slice(STORE_ITEMS_BRIDGE_MAGIC);
    out.extend_from_slice(&count.to_le_bytes());
    for item in items {
        out.extend_from_slice(&item.app_id.to_le_bytes());
        let cloud_state = match item.supports_steam_cloud {
            Some(true) => 1i32,
            Some(false) => 0i32,
            None => -1i32,
        };
        out.extend_from_slice(&cloud_state.to_le_bytes());
        let has_price = item.final_price_minor.is_some() && item.original_price_minor.is_some();
        out.extend_from_slice(&(if has_price { 1i32 } else { 0i32 }).to_le_bytes());
        out.extend_from_slice(&item.final_price_minor.unwrap_or(0).to_le_bytes());
        out.extend_from_slice(&item.original_price_minor.unwrap_or(0).to_le_bytes());
        write_required_string(&mut out, &item.header_image_url)?;
    }
    Some(out)
}

fn serialize_achievement_details(items: &[AchievementDetail]) -> Option<Vec<u8>> {
    let count = u32::try_from(items.len()).ok()?;
    let mut out = Vec::new();
    out.extend_from_slice(ACHIEVEMENT_DETAILS_BRIDGE_MAGIC);
    out.extend_from_slice(&count.to_le_bytes());
    for item in items {
        out.extend_from_slice(&(if item.achieved { 1i32 } else { 0i32 }).to_le_bytes());
        let has_unlock_time = item.unlock_time_seconds.is_some();
        out.extend_from_slice(&(if has_unlock_time { 1i32 } else { 0i32 }).to_le_bytes());
        out.extend_from_slice(&item.unlock_time_seconds.unwrap_or(0).to_le_bytes());
        out.extend_from_slice(&item.group_id.to_le_bytes());
        out.extend_from_slice(&(u32::from(item.archived) | (u32::from(item.hidden) << 1)).to_le_bytes());
        write_required_string(&mut out, &item.api_name)?;
        write_required_string(&mut out, &item.display_name)?;
        write_required_string(&mut out, &item.description)?;
        write_optional_string(&mut out, item.icon_url.as_deref())?;
        write_optional_string(&mut out, item.locked_icon_url.as_deref())?;
    }
    Some(out)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParseOwnedGames(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    response: JByteArray<'_>,
) -> jbyteArray {
    let Ok(response) = env.convert_byte_array(&response) else {
        return ptr::null_mut();
    };
    let Ok(games) = parse_owned_games(&response) else {
        return ptr::null_mut();
    };
    let Some(encoded) = serialize_owned_games(&games) else {
        return ptr::null_mut();
    };
    env.byte_array_from_slice(&encoded)
        .map(|result| result.into_raw())
        .unwrap_or(ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParseAchievementProgress(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    response: JByteArray<'_>,
) -> jbyteArray {
    let Ok(response) = env.convert_byte_array(&response) else {
        return ptr::null_mut();
    };
    let Ok(progress) = parse_achievement_progress(&response) else {
        return ptr::null_mut();
    };
    let Some(encoded) = serialize_achievement_progress(&progress) else {
        return ptr::null_mut();
    };
    env.byte_array_from_slice(&encoded)
        .map(|result| result.into_raw())
        .unwrap_or(ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParseStoreItems(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    response: JByteArray<'_>,
) -> jbyteArray {
    let Ok(response) = env.convert_byte_array(&response) else {
        return ptr::null_mut();
    };
    let Ok(items) = parse_store_items(&response) else {
        return ptr::null_mut();
    };
    let Some(encoded) = serialize_store_items(&items) else {
        return ptr::null_mut();
    };
    env.byte_array_from_slice(&encoded)
        .map(|result| result.into_raw())
        .unwrap_or(ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParseAchievementDetails(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    definitions_response: JByteArray<'_>,
    user_response: JByteArray<'_>,
) -> jbyteArray {
    let Ok(definitions_response) = env.convert_byte_array(&definitions_response) else {
        return ptr::null_mut();
    };
    let Ok(user_response) = env.convert_byte_array(&user_response) else {
        return ptr::null_mut();
    };
    let Ok(items) = parse_achievement_details(&definitions_response, &user_response) else {
        return ptr::null_mut();
    };
    let Some(encoded) = serialize_achievement_details(&items) else {
        return ptr::null_mut();
    };
    env.byte_array_from_slice(&encoded)
        .map(|result| result.into_raw())
        .unwrap_or(ptr::null_mut())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn store_bridge_layout_is_stable() {
        let encoded = serialize_store_items(&[
            StoreMetadata {
                app_id: 730,
                header_image_url: "https://cdn.example/header.jpg".to_string(),
                final_price_minor: Some(1290),
                original_price_minor: Some(8600),
                supports_steam_cloud: Some(true),
            },
            StoreMetadata {
                app_id: 570,
                header_image_url: String::new(),
                final_price_minor: None,
                original_price_minor: None,
                supports_steam_cloud: None,
            },
        ])
        .unwrap();

        assert_eq!(&encoded[0..4], b"MST1");
        assert_eq!(u32::from_le_bytes(encoded[4..8].try_into().unwrap()), 2);
        assert_eq!(i32::from_le_bytes(encoded[8..12].try_into().unwrap()), 730);
        assert_eq!(i32::from_le_bytes(encoded[12..16].try_into().unwrap()), 1);
        assert_eq!(i32::from_le_bytes(encoded[16..20].try_into().unwrap()), 1);
        assert_eq!(i64::from_le_bytes(encoded[20..28].try_into().unwrap()), 1290);
        assert_eq!(i64::from_le_bytes(encoded[28..36].try_into().unwrap()), 8600);
        let header_len = u32::from_le_bytes(encoded[36..40].try_into().unwrap()) as usize;
        assert_eq!(&encoded[40..40 + header_len], b"https://cdn.example/header.jpg");
    }

    #[test]
    fn achievement_details_bridge_layout_is_stable() {
        let encoded = serialize_achievement_details(&[AchievementDetail {
            api_name: "ACH_WIN".to_string(),
            display_name: "Winner".to_string(),
            description: "Win once".to_string(),
            achieved: true,
            unlock_time_seconds: Some(1_700_000_000),
            icon_url: Some("https://cdn.example/icon.jpg".to_string()),
            locked_icon_url: None,
            group_id: 42,
            archived: true,
            hidden: false,
        }])
        .unwrap();

        assert_eq!(&encoded[0..4], b"MSA2");
        assert_eq!(u32::from_le_bytes(encoded[4..8].try_into().unwrap()), 1);
        assert_eq!(i32::from_le_bytes(encoded[8..12].try_into().unwrap()), 1);
        assert_eq!(i32::from_le_bytes(encoded[12..16].try_into().unwrap()), 1);
        assert_eq!(
            i64::from_le_bytes(encoded[16..24].try_into().unwrap()),
            1_700_000_000
        );
        assert_eq!(u32::from_le_bytes(encoded[24..28].try_into().unwrap()), 42);
        assert_eq!(u32::from_le_bytes(encoded[28..32].try_into().unwrap()), 1);
    }
}
