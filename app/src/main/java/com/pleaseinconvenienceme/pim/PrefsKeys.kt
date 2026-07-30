package com.pleaseinconvenienceme.pim

object PrefsKeys {
    // SharedPreferences file names
    const val SETTINGS = "pim_settings"
    const val SESSIONS = "pim_sessions"
    const val USAGE_LOG = "pim_usage_log"
    const val OVERLAY_TIMER = "overlay_timer"
    const val DEFAULT_OVERLAY_TIMER = true
    const val OVERLAY_TIMER_SIZE = "overlay_timer_size"
    const val OVERLAY_SIZE_OFF = 0
    const val OVERLAY_SIZE_SMALL = 1
    const val OVERLAY_SIZE_LARGE = 2
    const val DEFAULT_OVERLAY_SIZE = OVERLAY_SIZE_SMALL
    const val OVERLAY_X = "overlay_x"
    const val OVERLAY_Y = "overlay_y"
    const val DURATIONS = "pim_app_durations"
    const val TEMP_UNRESTRICT = "pim_temp_unrestrict"
    const val APP_OVERRIDES = "pim_app_overrides"

    // Per-app lock key (stored as "$appName::lock_hash" in APP_OVERRIDES)
    const val LOCK_HASH = "lock_hash"
    // Global PIM password (stored in SETTINGS)
    const val GLOBAL_LOCK_HASH = "global_lock_hash"
    // Global lock flag — when true, every restricted app is locked
    const val LOCK_ALL_RESTRICTED = "lock_all_restricted"
    const val DEFAULT_LOCK_ALL_RESTRICTED = false

    // Keys within SETTINGS
    const val SETUP_COMPLETE = "setup_complete"
    const val RESTRICTED_APPS = "restricted_apps"
    const val DIFFICULTY_LEVEL = "difficulty_level"
    const val TASK_TYPE = "task_type"
    const val REVEAL_SECONDS = "reveal_seconds"
    const val REPEAT_DELAY_INCREMENT = "repeat_delay_increment"
    const val TYPING_WORD_LENGTH = "typing_word_length"
    const val TYPING_NUMBER_LENGTH = "typing_number_length"
    const val TYPING_CHAR_SET = "typing_char_set"
    const val TYPING_LENGTH = "typing_length"
    const val TAPPING_DOT_COUNT = "tapping_dot_count"
    const val TAPPING_DOT_DELAY = "tapping_dot_delay"
    const val CUSTOM_OPERANDS = "custom_operands"
    const val CUSTOM_OPERATIONS = "custom_operations"
    const val CUSTOM_RANGE_MIN = "custom_range_min"
    const val CUSTOM_RANGE_MAX = "custom_range_max"
    const val TYPING_DIFFICULTY = "typing_difficulty"
    const val TAPPING_DIFFICULTY = "tapping_difficulty"
    const val IS_PURCHASED = "is_purchased"
    const val SETTINGS_NUDGE_SHOWN = "settings_nudge_shown"
    const val TASK_NUDGE_SHOWN = "task_nudge_shown"
    const val FIRST_LAUNCH_HINT_SHOWN = "first_launch_hint_shown"
    const val RESTRICTION_COUNT = "restriction_count"
    const val LOCK_NUDGE_COUNT = "lock_nudge_count"
    // Trial
    const val TRIAL_START = "trial_start"
    const val TRIAL_DURATION_MS = 7L * 24 * 60 * 60 * 1000
    const val TRIAL_CARD_LAST_SHOWN = "trial_card_last_shown"
    // Review prompt
    const val INSTALL_DATE = "install_date"
    const val INTERACTION_COUNT = "interaction_count"
    const val LAST_REVIEW_PROMPT = "last_review_prompt"
    const val REVIEW_NEVER_ASK = "review_never_ask"
    const val REVIEW_EXTRA_INTERACTIONS = "review_extra_interactions"

    // Defaults
    const val DEFAULT_DIFFICULTY = 1
    const val DEFAULT_TASK_TYPE = 0
    const val DEFAULT_REVEAL_SECONDS = 0
    const val DEFAULT_REPEAT_DELAY_INCREMENT = 0
    const val SESSION_RESET_WINDOW_MS = 60L * 60 * 1000
    const val LAST_SESSION_PREFIX = "last_session_"
    const val SESSION_COUNT_PREFIX = "session_count_"
    const val DEFAULT_WORD_LENGTH = 5
    const val DEFAULT_NUMBER_LENGTH = 7
    const val DEFAULT_TYPING_CHAR_SET = 0
    const val DEFAULT_TYPING_LENGTH = 7
    const val DEFAULT_TAPPING_DOTS = 4
    const val DEFAULT_TAPPING_DOT_DELAY = 1
    const val DEFAULT_OPERANDS = 2
    const val DEFAULT_RANGE_MIN = 1
    const val DEFAULT_RANGE_MAX = 50
    const val DEFAULT_TYPING_DIFFICULTY = 0
    const val DEFAULT_TAPPING_DIFFICULTY = 0
    const val DEFAULT_SESSION_MINUTES = 5
}
