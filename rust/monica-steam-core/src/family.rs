use crate::proto::{parse_all_ref, ProtoError, ProtoFieldRef, ProtoValueRef};
use std::collections::HashSet;

const MAX_FAMILY_RESPONSE_BYTES: usize = 64 * 1024 * 1024;
const MAX_FAMILY_APPS: usize = 100_000;
const MAX_OWNERS_PER_APP: usize = 1_024;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FamilySharedGame {
    pub app_id: i32,
    pub name: String,
    pub playtime_forever_minutes: i32,
    pub last_played_at: i64,
    pub icon_hash: String,
    pub owner_steam_ids: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum FamilyParseError {
    Proto(ProtoError),
    PayloadTooLarge,
    TooManyApps,
    TooManyOwners,
}

impl From<ProtoError> for FamilyParseError {
    fn from(value: ProtoError) -> Self {
        Self::Proto(value)
    }
}

pub fn parse_shared_library_apps(
    response: &[u8],
) -> Result<Vec<FamilySharedGame>, FamilyParseError> {
    if response.len() > MAX_FAMILY_RESPONSE_BYTES {
        return Err(FamilyParseError::PayloadTooLarge);
    }
    let fields = parse_all_ref(response)?;
    let candidate_count = fields
        .iter()
        .filter(|field| field.number == 1 && has_kotlin_bytes(field))
        .count();
    if candidate_count > MAX_FAMILY_APPS {
        return Err(FamilyParseError::TooManyApps);
    }

    let mut games = Vec::with_capacity(candidate_count);
    let mut seen_apps = HashSet::with_capacity(candidate_count);
    for app_field in fields.iter().filter(|field| field.number == 1) {
        let Some(parsed) = with_kotlin_bytes(app_field, |bytes| {
            // Kotlin wraps each nested app parse in runCatching and skips only
            // that app when malformed.
            let Ok(app) = parse_all_ref(bytes) else {
                return Ok::<_, FamilyParseError>(None);
            };
            let Some(app_id_field) = first_field(&app, 1) else {
                return Ok(None);
            };
            let app_id = kotlin_as_long(app_id_field) as i32;
            if app_id <= 0 {
                return Ok(None);
            }
            let exclude_reason = first_field(&app, 10)
                .map(kotlin_as_long)
                .unwrap_or(0) as i32;
            if exclude_reason != 0 {
                return Ok(None);
            }

            let owner_count = app
                .iter()
                .filter(|field| field.number == 2 && has_kotlin_bytes(field))
                .count();
            if owner_count > MAX_OWNERS_PER_APP {
                return Err(FamilyParseError::TooManyOwners);
            }
            let mut owner_steam_ids = Vec::with_capacity(owner_count);
            let mut seen_owners = HashSet::with_capacity(owner_count);
            for owner in app
                .iter()
                .filter(|field| field.number == 2 && has_kotlin_bytes(field))
            {
                let value = kotlin_fixed64_unsigned_string(owner);
                // Kotlin filters only isNotBlank(); "0" therefore remains valid.
                if !is_kotlin_blank(&value) && seen_owners.insert(value.clone()) {
                    owner_steam_ids.push(value);
                }
            }

            let playtime = first_field(&app, 13)
                .map(kotlin_as_long)
                .unwrap_or(0)
                .max(0)
                .min(i32::MAX as i64) as i32;
            let raw_name = first_kotlin_string(&app, 6);
            let name = if is_kotlin_blank(&raw_name) {
                format!("App {app_id}")
            } else {
                raw_name
            };
            Ok(Some(FamilySharedGame {
                app_id,
                name,
                playtime_forever_minutes: playtime,
                last_played_at: first_field(&app, 12).map(kotlin_as_long).unwrap_or(0).max(0),
                icon_hash: first_kotlin_string(&app, 9),
                owner_steam_ids,
            }))
        }) else {
            continue;
        };
        let Some(game) = parsed? else {
            continue;
        };
        // Kotlin distinctBy(appId) preserves the first surviving occurrence.
        if seen_apps.insert(game.app_id) {
            games.push(game);
        }
    }
    Ok(games)
}

fn first_field<'fields, 'data>(
    fields: &'fields [ProtoFieldRef<'data>],
    number: u32,
) -> Option<&'fields ProtoFieldRef<'data>> {
    fields.iter().find(|field| field.number == number)
}

fn kotlin_as_long(field: &ProtoFieldRef<'_>) -> i64 {
    match field.value {
        ProtoValueRef::Varint(value) => value as i64,
        _ => 0,
    }
}

fn first_kotlin_string(fields: &[ProtoFieldRef<'_>], number: u32) -> String {
    first_field(fields, number)
        .and_then(|field| {
            with_kotlin_bytes(field, |bytes| String::from_utf8_lossy(bytes).into_owned())
        })
        .unwrap_or_default()
}

fn kotlin_fixed64_unsigned_string(field: &ProtoFieldRef<'_>) -> String {
    let value = match field.value {
        ProtoValueRef::Varint(_) | ProtoValueRef::Fixed32(_) => 0,
        ProtoValueRef::Fixed64(value) => value,
        ProtoValueRef::Bytes(bytes) => {
            if bytes.len() < 8 {
                0
            } else {
                let mut raw = [0u8; 8];
                raw.copy_from_slice(&bytes[..8]);
                u64::from_le_bytes(raw)
            }
        }
    };
    value.to_string()
}

fn has_kotlin_bytes(field: &ProtoFieldRef<'_>) -> bool {
    !matches!(field.value, ProtoValueRef::Varint(_))
}

fn with_kotlin_bytes<T>(field: &ProtoFieldRef<'_>, f: impl FnOnce(&[u8]) -> T) -> Option<T> {
    match field.value {
        ProtoValueRef::Varint(_) => None,
        ProtoValueRef::Bytes(bytes) => Some(f(bytes)),
        ProtoValueRef::Fixed64(value) => {
            let bytes = value.to_le_bytes();
            Some(f(&bytes))
        }
        ProtoValueRef::Fixed32(value) => {
            let bytes = value.to_le_bytes();
            Some(f(&bytes))
        }
    }
}

fn is_kotlin_blank(value: &str) -> bool {
    value.chars().all(char::is_whitespace)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::proto::ProtoWriter;

    const OWNER: i64 = 76_561_198_000_000_002;

    fn shared_app(
        app_id: i64,
        name: &str,
        playtime: i64,
        exclude_reason: i64,
    ) -> ProtoWriter {
        let mut app = ProtoWriter::new();
        app.write_varint(1, app_id).unwrap();
        app.write_fixed64(2, OWNER).unwrap();
        app.write_string(6, name).unwrap();
        app.write_string(9, &format!("icon-{app_id}")).unwrap();
        app.write_varint(10, exclude_reason).unwrap();
        app.write_varint(13, playtime).unwrap();
        app
    }

    #[test]
    fn shared_apps_match_kotlin_fields_and_exclusions() {
        let mut response = ProtoWriter::new();
        response
            .write_message(1, &shared_app(20, "Shared game", 7_200, 0))
            .unwrap();
        response
            .write_message(1, &shared_app(30, "Excluded", 3_600, 2))
            .unwrap();

        let games = parse_shared_library_apps(response.as_bytes()).unwrap();
        assert_eq!(games.len(), 1);
        assert_eq!(games[0].app_id, 20);
        assert_eq!(games[0].name, "Shared game");
        assert_eq!(games[0].playtime_forever_minutes, 7_200);
        assert_eq!(games[0].last_played_at, 0);
        assert_eq!(games[0].icon_hash, "icon-20");
        assert_eq!(games[0].owner_steam_ids, vec![OWNER.to_string()]);
    }

    #[test]
    fn last_played_preserves_unsigned_seconds_and_clamps_negative_values() {
        for (raw, expected) in [(4_000_000_000, 4_000_000_000), (-1, 0), (0, 0)] {
            let mut app = shared_app(20, "Shared", 60, 0);
            app.write_varint(12, raw).unwrap();
            app.write_varint(12, 123).unwrap();
            let mut response = ProtoWriter::new();
            response.write_message(1, &app).unwrap();
            let game = &parse_shared_library_apps(response.as_bytes()).unwrap()[0];
            assert_eq!(game.last_played_at, expected);
            assert_eq!(game.playtime_forever_minutes, 60);
        }
    }

    #[test]
    fn first_duplicate_fields_and_first_duplicate_app_win() {
        let mut first = ProtoWriter::new();
        first.write_varint(1, 20).unwrap();
        first.write_varint(1, 99).unwrap();
        first.write_string(6, "first-name").unwrap();
        first.write_string(6, "second-name").unwrap();
        first.write_varint(13, 15).unwrap();
        first.write_varint(13, 999).unwrap();
        let second = shared_app(20, "duplicate-app", 1_000, 0);
        let mut response = ProtoWriter::new();
        response.write_message(1, &first).unwrap();
        response.write_message(1, &second).unwrap();

        let games = parse_shared_library_apps(response.as_bytes()).unwrap();
        assert_eq!(games.len(), 1);
        assert_eq!(games[0].app_id, 20);
        assert_eq!(games[0].name, "first-name");
        assert_eq!(games[0].playtime_forever_minutes, 15);
    }

    #[test]
    fn duplicate_owners_preserve_first_order_and_deduplicate() {
        let mut app = ProtoWriter::new();
        app.write_varint(1, 20).unwrap();
        app.write_fixed64(2, OWNER).unwrap();
        app.write_fixed64(2, OWNER).unwrap();
        app.write_fixed64(2, OWNER + 1).unwrap();
        let mut response = ProtoWriter::new();
        response.write_message(1, &app).unwrap();

        let games = parse_shared_library_apps(response.as_bytes()).unwrap();
        assert_eq!(
            games[0].owner_steam_ids,
            vec![OWNER.to_string(), (OWNER + 1).to_string()]
        );
    }

    #[test]
    fn short_bytes_owner_becomes_zero_like_kotlin_fixed64_reader() {
        let mut app = ProtoWriter::new();
        app.write_varint(1, 20).unwrap();
        app.write_bytes(2, &[1, 2, 3, 4]).unwrap();
        let mut response = ProtoWriter::new();
        response.write_message(1, &app).unwrap();

        let games = parse_shared_library_apps(response.as_bytes()).unwrap();
        assert_eq!(games[0].owner_steam_ids, vec!["0".to_string()]);
    }

    #[test]
    fn malformed_nested_app_is_skipped_and_top_level_error_propagates() {
        let valid = shared_app(20, "Shared", 10, 0);
        let mut response = ProtoWriter::new();
        response.write_bytes(1, &[0x08, 0x80]).unwrap();
        response.write_message(1, &valid).unwrap();
        let games = parse_shared_library_apps(response.as_bytes()).unwrap();
        assert_eq!(games.len(), 1);

        assert!(matches!(
            parse_shared_library_apps(&[0x08, 0x80]),
            Err(FamilyParseError::Proto(_))
        ));
    }

    #[test]
    fn negative_playtime_clamps_and_blank_name_falls_back() {
        let mut app = shared_app(20, "   ", -1, 0);
        app.write_varint(13, 99).unwrap();
        let mut response = ProtoWriter::new();
        response.write_message(1, &app).unwrap();
        let game = &parse_shared_library_apps(response.as_bytes()).unwrap()[0];
        assert_eq!(game.name, "App 20");
        assert_eq!(game.playtime_forever_minutes, 0);
    }
}
