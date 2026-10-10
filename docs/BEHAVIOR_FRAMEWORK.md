# Каркас behavioral-усложнений (волна 1)

**Идея:** у каждого id своя запись в `behaviors.json` (свои параметры, свой `enabled`, свой статус в трекере).
Общий код — только шаблоны; уникальным механикам даём отдельный класс (`template: "custom_..."`).
Ошибка в одном id НЕ ломает остальные: после `behavior.errorsBeforeDisable` (по умолчанию 5) ошибок этот id отключается, остальные работают.

## Файлы
- `src/main/resources/escalation_default_behaviors.json` — встроенные реализации (обновляются со сборкой, копировать в config не нужно).
- `config/escalation/behaviors_override.json` — ТВОИ правки поверх (создаётся при первом запуске). Пример:
  ```json
  { "overrides": {
      "escalation_hand_0021": { "chance": 10 },
      "escalation_hand_0038": { "period_sec": 1800, "health_mult": 3.0 },
      "escalation_hand_0011": { "enabled": false }
  } }
  ```
  После правки: `/escalation debug behavior reload`.
- `docs/behavior_tracker.csv` — статус каждого из 1115 behavioral+flavor усложнений.
- Конфиг: `escalation_config.json -> behavior.enabled / errorsBeforeDisable`.

## Правила стакинга (одинаковые id)
- шанс: `1 - Π(1 - min(1, база × множитель_редкости_i))`;
- величина (урон/длительность/цена): `Σ множителей_редкости`;
- период (спавн мобов): `период / число копий`, но не меньше `min_period_sec` (если `stack_period: true`).

## Шаблоны
- `trigger_action` — триггер + условия + шанс + действия. Триггеры: periodic, block_break, player_hurt, player_attack,
  item_crafted, entity_killed, death_drops, sleep_attempt, right_click_block, anvil_use.
  Действия: effect, damage, drop_item, item_damage, break_equipped, destroy_item, xp_levels, cancel, cancel_sleep, rain, message, sound.
  Условия: raining, clear_weather, thundering, night, day, morning, noon, full_moon, overworld, nether, end, outdoors,
  underground, dark, in_nether_forest, in_badlands, no_helmet, no_boots, on_grass, recent_combat, low_health, no_sleep_days:N (префикс `!` = НЕ).
- `spawn_elite` — периодический спавн усиленного моба, идущего к игроку.
- `villager_price` — надбавка цены у жителя профессии X на предметы Y (при открытии торговли).
- `custom_*` — уникальные обработчики (скользкие блоки, тревога, паранойя, одиночество).

## Как добавить новое усложнение
1. Найти id в `docs/behavior_tracker.csv`.
2. Добавить запись в `escalation_default_behaviors.json` (или в override для пробы).
3. Нужна новая механика — новый шаблон/класс + регистрация в `BehaviorRegistry` (статический блок `TEMPLATES`).

## Debug
`/escalation debug behavior status | info <id> | fire <id> | reload` (id или номер: `21` = escalation_hand_0021).
