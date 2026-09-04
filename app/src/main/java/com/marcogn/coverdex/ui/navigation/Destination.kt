package com.marcogn.coverdex.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Destination {

    /** The teams list. Graph start destination. */
    @Serializable
    data object Teams : Destination

    @Serializable
    data class TeamDetail(val teamId: String) : Destination

    @Serializable
    data object Roster : Destination

    @Serializable
    data object Settings : Destination
}
