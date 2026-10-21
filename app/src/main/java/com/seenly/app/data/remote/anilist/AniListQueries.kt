package com.seenly.app.data.remote.anilist

/**
 * GraphQL documents. `${D}` is used where GraphQL variables start with `$`
 * (a Kotlin raw string cannot contain a bare `$`).
 */
private const val D = "$"

internal object AniListQueries {

    private const val MEDIA_FIELDS = """
        id
        idMal
        title { romaji english native userPreferred }
        synonyms
        description(asHtml: false)
        format
        status
        season
        seasonYear
        episodes
        duration
        genres
        averageScore
        popularity
        countryOfOrigin
        isAdult
        startDate { year month day }
        endDate { year month day }
        coverImage { extraLarge large medium }
        bannerImage
        studios(isMain: true) { nodes { name isAnimationStudio } }
        tags { name rank isGeneralSpoiler }
    """

    val SEARCH = """
        query (${D}search: String, ${D}page: Int, ${D}perPage: Int) {
          Page(page: ${D}page, perPage: ${D}perPage) {
            pageInfo { currentPage hasNextPage total }
            media(search: ${D}search, type: ANIME, isAdult: false, sort: SEARCH_MATCH) {
              $MEDIA_FIELDS
            }
          }
        }
    """.trimIndent()

    val TRENDING = """
        query (${D}page: Int, ${D}perPage: Int, ${D}sort: [MediaSort]) {
          Page(page: ${D}page, perPage: ${D}perPage) {
            pageInfo { currentPage hasNextPage total }
            media(type: ANIME, isAdult: false, sort: ${D}sort) {
              $MEDIA_FIELDS
            }
          }
        }
    """.trimIndent()

    val SEASONAL = """
        query (${D}season: MediaSeason, ${D}year: Int, ${D}page: Int, ${D}perPage: Int) {
          Page(page: ${D}page, perPage: ${D}perPage) {
            pageInfo { currentPage hasNextPage total }
            media(season: ${D}season, seasonYear: ${D}year, type: ANIME, isAdult: false, sort: POPULARITY_DESC) {
              $MEDIA_FIELDS
            }
          }
        }
    """.trimIndent()

    val BY_GENRE = """
        query (${D}genre: String, ${D}page: Int, ${D}perPage: Int) {
          Page(page: ${D}page, perPage: ${D}perPage) {
            pageInfo { currentPage hasNextPage total }
            media(genre: ${D}genre, type: ANIME, isAdult: false, sort: POPULARITY_DESC) {
              $MEDIA_FIELDS
            }
          }
        }
    """.trimIndent()

    /** Relations only; used by the taste-driven recommendation deck. */
    val SEQUELS = """
        query (${D}id: Int) {
          Media(id: ${D}id) {
            relations {
              edges {
                relationType
                node { $MEDIA_FIELDS }
              }
            }
          }
        }
    """.trimIndent()

    val UPCOMING_AIRING = """
        query (${D}from: Int, ${D}to: Int, ${D}page: Int, ${D}perPage: Int) {
          Page(page: ${D}page, perPage: ${D}perPage) {
            pageInfo { currentPage hasNextPage total }
            airingSchedules(airingAt_greater: ${D}from, airingAt_lesser: ${D}to, sort: TIME) {
              nodes {
                episode
                airingAt
                media {
                  $MEDIA_FIELDS
                  nextAiringEpisode { episode airingAt }
                }
              }
            }
          }
        }
    """.trimIndent()

    val DETAILS = """
        query (${D}id: Int) {
          Media(id: ${D}id) {
            $MEDIA_FIELDS
            nextAiringEpisode { episode airingAt }
            airingSchedule(perPage: 100) { nodes { episode airingAt } }
            relations {
              edges {
                relationType
                node { $MEDIA_FIELDS }
              }
            }
            recommendations(perPage: 10, sort: RATING_DESC) {
              nodes {
                rating
                mediaRecommendation { $MEDIA_FIELDS }
              }
            }
          }
        }
    """.trimIndent()

    val RECOMMENDATIONS = """
        query (${D}id: Int) {
          Media(id: ${D}id) {
            recommendations(perPage: 14, sort: RATING_DESC) {
              nodes {
                rating
                mediaRecommendation { $MEDIA_FIELDS }
              }
            }
          }
        }
    """.trimIndent()
}
