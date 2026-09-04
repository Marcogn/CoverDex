package com.marcogn.coverdex.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CatchingPokemon
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.marcogn.coverdex.R
import com.marcogn.coverdex.ui.roster.RosterEditorScreen
import com.marcogn.coverdex.ui.roster.RosterScreen
import com.marcogn.coverdex.ui.settings.SettingsScreen
import com.marcogn.coverdex.ui.surprise.SurpriseMeScreen
import com.marcogn.coverdex.ui.team.SlotEditorScreen
import com.marcogn.coverdex.ui.team.TeamDetailScreen
import com.marcogn.coverdex.ui.teams.TeamsScreen
import kotlinx.coroutines.launch

// A NavBackStackEntry only reaches RESUMED once its enter/exit transition animation has fully
// completed and it is settled on top of the back stack. Gating every navigate()/popBackStack()
// call behind this check on the *specific* entry that owns the callback is the officially
// recommended fix for a fast double-tap landing on a screen that is still being composed/torn
// down mid-transition instead of the intended one. Not exercised yet in this phase (no screen
// navigates anywhere), wired up now so Phase 2's TeamDetail navigation doesn't have to
// rediscover it — see Hall of Memories' HallOfMemoriesNavGraph.kt, which hit the same race.
private fun NavBackStackEntry.lifecycleIsResumed() =
    lifecycle.currentState == Lifecycle.State.RESUMED

/**
 * A [ModalNavigationDrawer] wraps the whole [NavHost]; `drawerState` is hoisted here so every
 * drawer-reachable screen gets only an `onMenuClick` lambda, never the drawer state itself (UDF).
 * Drawer navigation uses `popUpTo(startDestination) { saveState = true }` +
 * `launchSingleTop = true` + `restoreState = true` so switching sections never grows the back
 * stack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverDexNavGraph(navController: NavHostController = rememberNavController()) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    fun navigateFromDrawer(destination: Destination) {
        if (navController.currentBackStackEntry?.lifecycleIsResumed() != false) {
            navController.navigate(destination) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            scope.launch { drawerState.close() }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Groups, contentDescription = null) },
                    label = { Text(stringResource(R.string.drawer_teams)) },
                    selected = false,
                    onClick = { navigateFromDrawer(Destination.Teams) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.CatchingPokemon, contentDescription = null) },
                    label = { Text(stringResource(R.string.drawer_roster)) },
                    selected = false,
                    onClick = { navigateFromDrawer(Destination.Roster) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.drawer_settings)) },
                    selected = false,
                    onClick = { navigateFromDrawer(Destination.Settings) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        },
    ) {
        val onMenuClick: () -> Unit = { scope.launch { drawerState.open() } }

        NavHost(
            navController = navController,
            startDestination = Destination.Teams,
        ) {
            composable<Destination.Teams> {
                TeamsScreen(
                    onMenuClick = onMenuClick,
                    onTeamSelected = { teamId -> navController.navigate(Destination.TeamDetail(teamId)) },
                    onSurpriseMeClick = { navController.navigate(Destination.SurpriseMe) },
                )
            }
            composable<Destination.TeamDetail> { backStackEntry ->
                val route = backStackEntry.toRoute<Destination.TeamDetail>()
                TeamDetailScreen(
                    onBackClick = { navController.popBackStack() },
                    onSlotClick = { slotIndex -> navController.navigate(Destination.SlotEditor(route.teamId, slotIndex)) },
                )
            }
            composable<Destination.SlotEditor> {
                SlotEditorScreen(onBackClick = { navController.popBackStack() })
            }
            composable<Destination.Roster> {
                RosterScreen(
                    onMenuClick = onMenuClick,
                    onEntrySelected = { customId -> navController.navigate(Destination.RosterEditor(customId)) },
                )
            }
            composable<Destination.RosterEditor> {
                RosterEditorScreen(onBackClick = { navController.popBackStack() })
            }
            composable<Destination.Settings> {
                SettingsScreen(onMenuClick = onMenuClick)
            }
            composable<Destination.SurpriseMe> {
                SurpriseMeScreen(
                    onMenuClick = onMenuClick,
                    onTeamCreated = { teamId ->
                        navController.navigate(Destination.TeamDetail(teamId)) {
                            popUpTo(Destination.Teams)
                        }
                    },
                )
            }
        }
    }
}
