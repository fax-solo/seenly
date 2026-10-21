package com.seenly.app.domain.model

/** Where a piece of metadata came from. MANUAL is set for user-created titles. */
enum class ProviderId {
    ANILIST,
    CINEMETA,
    TVMAZE,
    JIKAN,
    MANUAL,
    ;

    companion object {
        fun fromStorage(value: String?): ProviderId? =
            entries.firstOrNull { it.name == value }
    }
}

enum class MediaCategory {
    ANIME,
    MOVIES,
    SERIES,
    CARTOONS,
}

/**
 * Extensible media type model. Every animated title is not assumed to be anime:
 * the provider and the user decide.
 */
enum class MediaType(
    val category: MediaCategory,
    val hasEpisodes: Boolean,
    val isAnimation: Boolean,
    val isDocumentary: Boolean = false,
) {
    ANIME_SERIES(MediaCategory.ANIME, hasEpisodes = true, isAnimation = true),
    ANIME_MOVIE(MediaCategory.ANIME, hasEpisodes = false, isAnimation = true),
    CARTOON_SERIES(MediaCategory.CARTOONS, hasEpisodes = true, isAnimation = true),
    CARTOON_MOVIE(MediaCategory.CARTOONS, hasEpisodes = false, isAnimation = true),
    TV_SERIES(MediaCategory.SERIES, hasEpisodes = true, isAnimation = false),
    MOVIE(MediaCategory.MOVIES, hasEpisodes = false, isAnimation = false),
    DOCUMENTARY_SERIES(MediaCategory.SERIES, hasEpisodes = true, isAnimation = false, isDocumentary = true),
    DOCUMENTARY_MOVIE(MediaCategory.MOVIES, hasEpisodes = false, isAnimation = false, isDocumentary = true),
    ;

    val isSeries: Boolean get() = hasEpisodes

    companion object {
        fun fromStorage(value: String?): MediaType? = entries.firstOrNull { it.name == value }

        /** Best-effort mapping used by provider adapters and manual entry. */
        fun resolve(
            isSeries: Boolean,
            isAnimation: Boolean,
            isAnime: Boolean,
            isDocumentary: Boolean = false,
        ): MediaType = when {
            isAnime && isSeries -> ANIME_SERIES
            isAnime -> ANIME_MOVIE
            isDocumentary && isSeries -> DOCUMENTARY_SERIES
            isDocumentary -> DOCUMENTARY_MOVIE
            isAnimation && isSeries -> CARTOON_SERIES
            isAnimation -> CARTOON_MOVIE
            isSeries -> TV_SERIES
            else -> MOVIE
        }
    }
}

enum class WatchStatus {
    PLAN_TO_WATCH,
    WATCHING,
    COMPLETED,
    ON_HOLD,
    DROPPED,
    ;

    companion object {
        fun fromStorage(value: String?): WatchStatus =
            entries.firstOrNull { it.name == value } ?: PLAN_TO_WATCH
    }
}

enum class EventType {
    FIRST_WATCH,
    REWATCH,
}

enum class GoalType { WEEKLY, MONTHLY, YEARLY }

enum class GoalMetric { EPISODES, MOVIES, TITLES, MINUTES }

/** Built-in smart lists computed from data instead of stored membership. */
enum class SmartList {
    FAVORITES,
    PLAN_TO_WATCH,
    ;

    companion object {
        fun fromStorage(value: String?): SmartList? = entries.firstOrNull { it.name == value }
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage(val tag: String) {
    SYSTEM("system"),
    ENGLISH("en"),
    ARABIC("ar"),
}

enum class LibrarySort {
    RECENTLY_ADDED,
    RECENTLY_WATCHED,
    TITLE_ASC,
    TITLE_DESC,
    RATING_DESC,
    RELEASE_DATE_DESC,
}

/** Home screen sections; order and visibility are user configurable. */
enum class HomeSectionKey {
    CONTINUE_WATCHING,
    UPCOMING_EPISODES,
    RECENTLY_WATCHED,
    WATCHLIST,
    RECOMMENDATIONS,
    QUICK_STATS,
    ACTIVITY_CALENDAR,
}
