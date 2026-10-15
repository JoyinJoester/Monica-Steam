use jni::{
    objects::{JByteArray, JClass},
    sys::jbyteArray,
    JNIEnv,
};
use monica_steam_core::family::{parse_shared_library_apps, FamilySharedGame};
use std::ptr;

const FAMILY_SHARED_APPS_BRIDGE_MAGIC: &[u8; 4] = b"MSF2";

fn write_required_string(out: &mut Vec<u8>, value: &str) -> Option<()> {
    let bytes = value.as_bytes();
    let length = u32::try_from(bytes.len()).ok()?;
    out.extend_from_slice(&length.to_le_bytes());
    out.extend_from_slice(bytes);
    Some(())
}

fn serialize_family_shared_apps(games: &[FamilySharedGame]) -> Option<Vec<u8>> {
    let count = u32::try_from(games.len()).ok()?;
    let mut out = Vec::new();
    out.extend_from_slice(FAMILY_SHARED_APPS_BRIDGE_MAGIC);
    out.extend_from_slice(&count.to_le_bytes());
    for game in games {
        out.extend_from_slice(&game.app_id.to_le_bytes());
        out.extend_from_slice(&game.playtime_forever_minutes.to_le_bytes());
        out.extend_from_slice(&game.last_played_at.to_le_bytes());
        write_required_string(&mut out, &game.name)?;
        write_required_string(&mut out, &game.icon_hash)?;
        let owner_count = u32::try_from(game.owner_steam_ids.len()).ok()?;
        out.extend_from_slice(&owner_count.to_le_bytes());
        for owner in &game.owner_steam_ids {
            write_required_string(&mut out, owner)?;
        }
    }
    Some(out)
}

#[no_mangle]
pub extern "system" fn Java_takagi_ru_monica_steam_core_RustSteamCoreNative_nativeParseFamilySharedApps(
    env: JNIEnv<'_>,
    _class: JClass<'_>,
    response: JByteArray<'_>,
) -> jbyteArray {
    let Ok(response) = env.convert_byte_array(&response) else {
        return ptr::null_mut();
    };
    let Ok(games) = parse_shared_library_apps(&response) else {
        return ptr::null_mut();
    };
    let Some(encoded) = serialize_family_shared_apps(&games) else {
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
    fn family_bridge_layout_is_stable() {
        let encoded = serialize_family_shared_apps(&[FamilySharedGame {
            app_id: 20,
            name: "Shared game".to_string(),
            playtime_forever_minutes: 7_200,
            last_played_at: 4_000_000_000,
            icon_hash: "icon-20".to_string(),
            owner_steam_ids: vec!["76561198000000002".to_string()],
        }])
        .unwrap();

        assert_eq!(&encoded[0..4], b"MSF2");
        assert_eq!(u32::from_le_bytes(encoded[4..8].try_into().unwrap()), 1);
        assert_eq!(i32::from_le_bytes(encoded[8..12].try_into().unwrap()), 20);
        assert_eq!(i32::from_le_bytes(encoded[12..16].try_into().unwrap()), 7_200);
        assert_eq!(i64::from_le_bytes(encoded[16..24].try_into().unwrap()), 4_000_000_000);
    }
}
