package com.vctrch.golfgps.data.opengolf

import javax.inject.Inject

/** Thin helper for UI / ViewModels to read a player's Moments. */
class OpenGolfMomentReader
    @Inject
    constructor(
        private val client: OpenGolfContributeClient,
    ) {
        suspend fun list(
            player: String? = null,
            session: String? = null,
            type: String? = null,
            limit: Int? = null,
            cursor: String? = null,
            appApiKey: String,
            accessToken: String,
        ): OpenGolfMomentsListResponse =
            client.listMoments(
                query =
                    OpenGolfMomentsQuery(
                        player = player,
                        session = session,
                        type = type,
                        limit = limit,
                        cursor = cursor,
                    ),
                appApiKey = appApiKey,
                accessToken = accessToken,
            )
    }
