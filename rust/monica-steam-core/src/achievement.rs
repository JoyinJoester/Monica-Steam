use crate::proto::{parse_all_ref, ProtoError, ProtoFieldRef, ProtoValueRef};
use std::collections::HashMap;

const MAX_ACHIEVEMENT_RESPONSE_BYTES: usize = 32 * 1024 * 1024;
const MAX_ACHIEVEMENTS: usize = 100_000;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AchievementDetail {
    pub api_name: String,
    pub display_name: String,
    pub description: String,
    pub achieved: bool,
    pub unlock_time_seconds: Option<i64>,
    pub icon_url: Option<String>,
    pub locked_icon_url: Option<String>,
    pub group_id: u32,
    pub archived: bool,
    pub hidden: bool,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum AchievementParseError {
    Proto(ProtoError),
    PayloadTooLarge,
    TooManyAchievements,
}

impl From<ProtoError> for AchievementParseError {
    fn from(value: ProtoError) -> Self {
        Self::Proto(value)
    }
}

#[derive(Debug, Clone, Copy)]
struct AchievementStatus {
    achieved: bool,
    unlock_time_seconds: Option<i64>,
}

pub fn parse_achievement_details(
    definitions_response: &[u8],
    user_response: &[u8],
) -> Result<Vec<AchievementDetail>, AchievementParseError> {
    ensure_payload_size(definitions_response)?;
    ensure_payload_size(user_response)?;

    let statuses = parse_statuses(user_response)?;
    let definitions = parse_all_ref(definitions_response)?;
    let definition_count = definitions
        .iter()
        .filter(|field| field.number == 1 && has_kotlin_bytes(field))
        .count();
    if definition_count > MAX_ACHIEVEMENTS {
        return Err(AchievementParseError::TooManyAchievements);
    }

    let mut achievements = Vec::with_capacity(definition_count);
    for field in definitions.iter().filter(|field| field.number == 1) {
        let Some(parsed) = with_kotlin_bytes(field, |bytes| {
            let Ok(definition) = parse_all_ref(bytes) else {
                // Mirrors the Kotlin runCatching around each nested definition.
                return None;
            };
            let key_field = last_field(&definition, 8)?;
            let key = kotlin_as_long(key_field);
            let api_name = last_kotlin_string(&definition, 1);
            let display_name_raw = last_kotlin_string(&definition, 2);
            let display_name = if is_kotlin_blank(&display_name_raw) {
                if is_kotlin_blank(&api_name) {
                    "Achievement".to_string()
                } else {
                    api_name.clone()
                }
            } else {
                display_name_raw
            };
            let status = statuses.get(&key).copied();
            Some(AchievementDetail {
                api_name: if is_kotlin_blank(&api_name) {
                    format!("achievement_{key}")
                } else {
                    api_name
                },
                display_name,
                description: last_kotlin_string(&definition, 3),
                achieved: status.is_some_and(|value| value.achieved),
                unlock_time_seconds: status.and_then(|value| value.unlock_time_seconds),
                icon_url: last_optional_non_blank_string(&definition, 4),
                locked_icon_url: last_optional_non_blank_string(&definition, 5),
                group_id: last_field(&definition, 11).map(kotlin_as_long).unwrap_or(0) as u32,
                archived: last_field(&definition, 12).is_some_and(|f| kotlin_as_long(f) != 0),
                hidden: last_field(&definition, 6).is_some_and(|f| kotlin_as_long(f) != 0),
            })
        }) else {
            continue;
        };
        if let Some(achievement) = parsed {
            achievements.push(achievement);
        }
    }
    Ok(achievements)
}

fn parse_statuses(
    user_response: &[u8],
) -> Result<HashMap<i64, AchievementStatus>, AchievementParseError> {
    let fields = parse_all_ref(user_response)?;
    let status_count = fields
        .iter()
        .filter(|field| field.number == 1 && has_kotlin_bytes(field))
        .count();
    if status_count > MAX_ACHIEVEMENTS {
        return Err(AchievementParseError::TooManyAchievements);
    }
    let mut statuses = HashMap::with_capacity(status_count);
    for field in fields.iter().filter(|field| field.number == 1) {
        let Some(parsed) = with_kotlin_bytes(field, |bytes| {
            let Ok(status) = parse_all_ref(bytes) else {
                return None;
            };
            let key_field = last_field(&status, 1)?;
            let key = kotlin_as_long(key_field);
            let achieved = last_field(&status, 2)
                .map(kotlin_as_long)
                .is_some_and(|value| value != 0);
            let unlock_time_seconds = last_field(&status, 3).and_then(kotlin_unlock_time);
            Some((
                key,
                AchievementStatus {
                    achieved,
                    unlock_time_seconds,
                },
            ))
        }) else {
            continue;
        };
        if let Some((key, status)) = parsed {
            // Kotlin Sequence<Pair>.toMap() replaces duplicate values with the
            // last occurrence. Order is irrelevant because this map is lookup-only.
            statuses.insert(key, status);
        }
    }
    Ok(statuses)
}

fn kotlin_unlock_time(field: &ProtoFieldRef<'_>) -> Option<i64> {
    let value = match field.value {
        // Kotlin special-cases wire type 5 with asFixed32UnsignedLong.
        ProtoValueRef::Fixed32(value) => value as i64,
        // Every other wire type uses asLong, which only reads varint.
        ProtoValueRef::Varint(value) => value as i64,
        _ => 0,
    };
    (value > 0).then_some(value)
}

fn ensure_payload_size(response: &[u8]) -> Result<(), AchievementParseError> {
    if response.len() > MAX_ACHIEVEMENT_RESPONSE_BYTES {
        Err(AchievementParseError::PayloadTooLarge)
    } else {
        Ok(())
    }
}

fn last_field<'fields, 'data>(
    fields: &'fields [ProtoFieldRef<'data>],
    number: u32,
) -> Option<&'fields ProtoFieldRef<'data>> {
    fields.iter().rev().find(|field| field.number == number)
}

fn kotlin_as_long(field: &ProtoFieldRef<'_>) -> i64 {
    match field.value {
        ProtoValueRef::Varint(value) => value as i64,
        _ => 0,
    }
}

fn last_kotlin_string(fields: &[ProtoFieldRef<'_>], number: u32) -> String {
    last_field(fields, number)
        .and_then(|field| {
            with_kotlin_bytes(field, |bytes| String::from_utf8_lossy(bytes).into_owned())
        })
        .unwrap_or_default()
}

fn last_optional_non_blank_string(
    fields: &[ProtoFieldRef<'_>],
    number: u32,
) -> Option<String> {
    let field = last_field(fields, number)?;
    let value = with_kotlin_bytes(field, |bytes| {
        String::from_utf8_lossy(bytes).into_owned()
    })
    .unwrap_or_default();
    (!is_kotlin_blank(&value)).then_some(value)
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

    fn definition(
        key: i64,
        api_name: &str,
        display_name: &str,
        description: &str,
    ) -> ProtoWriter {
        let mut writer = ProtoWriter::new();
        writer.write_string(1, api_name).unwrap();
        writer.write_string(2, display_name).unwrap();
        writer.write_string(3, description).unwrap();
        writer.write_string(4, "https://cdn.example/icon.jpg").unwrap();
        writer
            .write_string(5, "https://cdn.example/icon_gray.jpg")
            .unwrap();
        writer.write_varint(8, key).unwrap();
        writer
    }

    fn status(key: i64, achieved: bool, unlock_time: Option<i64>) -> ProtoWriter {
        let mut writer = ProtoWriter::new();
        writer.write_varint(1, key).unwrap();
        writer.write_bool(2, achieved).unwrap();
        if let Some(unlock_time) = unlock_time {
            writer.write_varint(3, unlock_time).unwrap();
        }
        writer
    }

    #[test]
    fn latest_schema_preserves_unsigned_group_archive_and_hidden_flags() {
        let mut item = definition(42, "ARCHIVED", "Old", "Retained");
        item.write_varint(11, 0xffff_ffff).unwrap();
        item.write_bool(12, true).unwrap();
        item.write_bool(6, true).unwrap();
        let mut response = ProtoWriter::new();
        response.write_message(1, &item).unwrap();
        let parsed = parse_achievement_details(response.as_bytes(), &[]).unwrap();
        assert_eq!(parsed[0].group_id, u32::MAX);
        assert!(parsed[0].archived);
        assert!(parsed[0].hidden);
    }

    #[test]
    fn merges_definitions_with_user_status_in_definition_order() {
        let mut definitions = ProtoWriter::new();
        definitions
            .write_message(1, &definition(42, "ACH_WIN", "Winner", "Win once"))
            .unwrap();
        definitions
            .write_message(1, &definition(43, "ACH_SECRET", "Secret", "Hidden"))
            .unwrap();
        let mut user = ProtoWriter::new();
        user.write_message(1, &status(42, true, Some(1_700_000_000)))
            .unwrap();

        let parsed = parse_achievement_details(definitions.as_bytes(), user.as_bytes()).unwrap();
        assert_eq!(parsed.len(), 2);
        assert_eq!(parsed[0].api_name, "ACH_WIN");
        assert!(parsed[0].achieved);
        assert_eq!(parsed[0].unlock_time_seconds, Some(1_700_000_000));
        assert!(!parsed[1].achieved);
        assert_eq!(parsed[1].unlock_time_seconds, None);
    }

    #[test]
    fn fixed32_unlock_time_matches_kotlin_special_case() {
        let mut definitions = ProtoWriter::new();
        definitions
            .write_message(1, &definition(42, "ACH_WIN", "Winner", ""))
            .unwrap();
        let mut nested = ProtoWriter::new();
        nested.write_varint(1, 42).unwrap();
        nested.write_bool(2, true).unwrap();
        nested.write_fixed32(3, 1_700_000_000u32).unwrap();
        let mut user = ProtoWriter::new();
        user.write_message(1, &nested).unwrap();

        let parsed = parse_achievement_details(definitions.as_bytes(), user.as_bytes()).unwrap();
        assert_eq!(parsed[0].unlock_time_seconds, Some(1_700_000_000));
    }

    #[test]
    fn fixed64_unlock_time_is_zero_like_kotlin_as_long() {
        let mut definitions = ProtoWriter::new();
        definitions
            .write_message(1, &definition(42, "ACH_WIN", "Winner", ""))
            .unwrap();
        let mut nested = ProtoWriter::new();
        nested.write_varint(1, 42).unwrap();
        nested.write_bool(2, true).unwrap();
        nested.write_fixed64(3, 1_700_000_000).unwrap();
        let mut user = ProtoWriter::new();
        user.write_message(1, &nested).unwrap();

        let parsed = parse_achievement_details(definitions.as_bytes(), user.as_bytes()).unwrap();
        assert_eq!(parsed[0].unlock_time_seconds, None);
    }

    #[test]
    fn duplicate_status_uses_last_value() {
        let mut definitions = ProtoWriter::new();
        definitions
            .write_message(1, &definition(42, "ACH_WIN", "Winner", ""))
            .unwrap();
        let mut user = ProtoWriter::new();
        user.write_message(1, &status(42, false, None)).unwrap();
        user.write_message(1, &status(42, true, Some(99))).unwrap();

        let parsed = parse_achievement_details(definitions.as_bytes(), user.as_bytes()).unwrap();
        assert!(parsed[0].achieved);
        assert_eq!(parsed[0].unlock_time_seconds, Some(99));
    }

    #[test]
    fn blank_names_and_optional_icons_match_kotlin_fallback() {
        let mut raw = ProtoWriter::new();
        raw.write_string(1, "   ").unwrap();
        raw.write_string(2, "\t").unwrap();
        raw.write_string(3, "Description").unwrap();
        raw.write_string(4, "   ").unwrap();
        raw.write_varint(8, 7).unwrap();
        let mut definitions = ProtoWriter::new();
        definitions.write_message(1, &raw).unwrap();

        let parsed = parse_achievement_details(definitions.as_bytes(), &[]).unwrap();
        assert_eq!(parsed[0].api_name, "achievement_7");
        assert_eq!(parsed[0].display_name, "Achievement");
        assert_eq!(parsed[0].icon_url, None);
        assert_eq!(parsed[0].locked_icon_url, None);
    }

    #[test]
    fn malformed_nested_entries_are_skipped() {
        let valid_definition = definition(42, "ACH_WIN", "Winner", "");
        let valid_status = status(42, true, Some(10));
        let mut definitions = ProtoWriter::new();
        definitions.write_bytes(1, &[0x08, 0x80]).unwrap();
        definitions.write_message(1, &valid_definition).unwrap();
        let mut user = ProtoWriter::new();
        user.write_bytes(1, &[0x08, 0x80]).unwrap();
        user.write_message(1, &valid_status).unwrap();

        let parsed = parse_achievement_details(definitions.as_bytes(), user.as_bytes()).unwrap();
        assert_eq!(parsed.len(), 1);
        assert!(parsed[0].achieved);
    }
}
