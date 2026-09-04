package com.marcogn.coverdex.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Destination {

    /** The teams list. Graph start destination. */
    @Serializable
    data object Teams : Destination

    @Serializable
    data class TeamDetail(val teamId: String) : Destination

    @Serializable
    data class SlotEditor(val teamId: String, val slotIndex: Int) : Destination

    @Serializable
    data object Roster : Destination

    /** [customId] is `null` for creating a brand-new roster entry. */
    @Serializable
    data class RosterEditor(val customId: String?) : Destination

    @Serializable
    data object Settings : Destination

    @Serializable
    data object SurpriseMe : Destination
}
