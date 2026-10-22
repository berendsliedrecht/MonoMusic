package com.monoapps.monomusic

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.monoapps.monomusic.ui.AlbumDetailsScreen
import com.monoapps.monomusic.ui.AlbumsScreen
import com.monoapps.monomusic.ui.ArtistDetailsScreen
import com.monoapps.monomusic.ui.ArtistsScreen
import com.monoapps.monomusic.ui.DownloadsScreen
import com.monoapps.monomusic.ui.LoginScreen
import com.monoapps.monomusic.ui.NowPlayingScreen
import com.monoapps.monomusic.ui.PlaylistDetailsScreen
import com.monoapps.monomusic.ui.PlaylistsScreen
import com.monoapps.monomusic.ui.SearchScreen
import com.monoapps.monomusic.ui.SettingsScreen
import com.monoapps.monomusic.ui.SongsScreen
import com.monoapps.monomusic.tidal.TidalAlbum
import com.monoapps.monomusic.tidal.TidalArtist
import com.monoapps.monomusic.tidal.TidalPlaylist
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.components.nav_bar.NavigationBarItemMMD
import com.mudita.mmd.components.nav_bar.NavigationBarMMD
import com.mudita.mmd.components.search_bar.SearchBarDefaultsMMD
import com.mudita.mmd.components.snackbar.SnackbarDurationMMD
import com.mudita.mmd.components.snackbar.SnackbarHostMMD
import com.mudita.mmd.components.snackbar.SnackbarHostStateMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThemeMMD {
                // Exclude the IME inset: the window pans for the keyboard
                // (adjustPan), and letting it shrink the layout squeezes
                // LazyColumnMMD to zero height, which crashes MMD's
                // scrollbar draw pass (same bug as in MonoTransit).
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.exclude(WindowInsets.ime)
                        ),
                ) {
                    Root()
                }
            }
        }
    }
}

@Composable
private fun Root(viewModel: MonoMusicViewModel = viewModel()) {
    NotificationPermissionRequest()
    MainApp(viewModel)
}

@Composable
private fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun MainApp(viewModel: MonoMusicViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostStateMMD() }

    LaunchedEffect(viewModel.uiMessage) {
        viewModel.uiMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDurationMMD.Short,
            )
            viewModel.uiMessage = null
        }
    }

    Scaffold(
        topBar = {
            MonoMusicTopBar(
                viewModel = viewModel,
                currentDestination = currentDestination,
                onBack = { navController.popBackStack() },
                onOpenSearch = { navController.navigate(Screen.Search.route) },
                onOpenNowPlaying = { navController.navigate(Screen.NowPlaying.route) },
            )
        },
        bottomBar = {
            MonoMusicBottomBar(
                currentDestination = currentDestination,
                onNavigate = { route -> navController.navigateToTab(route) },
            )
        },
        snackbarHost = {
            // MMD's snackbar surface is translucent; on e-ink the content
            // shimmering through reads as gray. Back it with solid white.
            SnackbarHostMMD(
                hostState = snackbarHostState,
                modifier = Modifier.background(Color.White),
            )
        },
    ) { padding ->
        MonoMusicNavHost(
            viewModel = viewModel,
            navController = navController,
            padding = padding,
        )
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun MonoMusicNavHost(
    viewModel: MonoMusicViewModel,
    navController: NavHostController,
    padding: PaddingValues,
) {
    val openAlbum = { album: TidalAlbum ->
        viewModel.openAlbum(album)
        navController.navigate(Screen.AlbumDetails.route)
    }
    val openArtist = { artist: TidalArtist ->
        viewModel.openArtist(artist)
        navController.navigate(Screen.ArtistDetails.route)
    }
    val openPlaylist = { playlist: TidalPlaylist ->
        viewModel.openPlaylist(playlist)
        navController.navigate(Screen.PlaylistDetails.route)
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Songs.route,
        modifier = Modifier.padding(padding),
        // E-ink: screen transition animations cause slow, ghosting refreshes.
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(Screen.Playlists.route) {
            PlaylistsScreen(viewModel, onOpenPlaylist = openPlaylist)
        }
        composable(Screen.Artists.route) {
            ArtistsScreen(viewModel, onOpenArtist = openArtist)
        }
        composable(Screen.Songs.route) {
            SongsScreen(viewModel)
        }
        composable(Screen.Albums.route) {
            AlbumsScreen(viewModel, onOpenAlbum = openAlbum)
        }
        composable(Screen.Search.route) {
            SearchScreen(
                viewModel,
                onOpenAlbum = openAlbum,
                onOpenArtist = openArtist,
                onOpenPlaylist = openPlaylist,
            )
        }
        composable(Screen.AlbumDetails.route) {
            AlbumDetailsScreen(viewModel)
        }
        composable(Screen.ArtistDetails.route) {
            ArtistDetailsScreen(viewModel, onOpenAlbum = openAlbum)
        }
        composable(Screen.PlaylistDetails.route) {
            PlaylistDetailsScreen(viewModel, onPlaylistRemoved = { navController.popBackStack() })
        }
        composable(Screen.NowPlaying.route) {
            NowPlayingScreen(viewModel)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel,
                onOpenDownloads = { navController.navigate(Screen.Downloads.route) },
                onOpenLogin = { navController.navigate(Screen.Login.route) },
            )
        }
        composable(Screen.Downloads.route) {
            DownloadsScreen(viewModel)
        }
        composable(Screen.Login.route) {
            LoginScreen(viewModel, onLoggedIn = { navController.popBackStack() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonoMusicTopBar(
    viewModel: MonoMusicViewModel,
    currentDestination: NavDestination?,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    val route = currentDestination?.route
    val navRoutes = remember { navItems.map { it.route } }
    val keyboardController = LocalSoftwareKeyboardController.current

    TopAppBarMMD(
        navigationIcon = {
            if (route !in navRoutes) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            }
        },
        title = {
            when (route) {
                Screen.Search.route -> SearchBarDefaultsMMD.InputField(
                    query = viewModel.searchQuery,
                    onQueryChange = { viewModel.searchQuery = it },
                    onSearch = {
                        keyboardController?.hide()
                        viewModel.performSearch()
                    },
                    expanded = true,
                    onExpandedChange = { },
                    placeholder = { TextMMD(text = "Search Tidal") },
                    trailingIcon = {
                        IconButton(onClick = {
                            keyboardController?.hide()
                            viewModel.performSearch()
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Search",
                            )
                        }
                    },
                )

                Screen.AlbumDetails.route -> Column {
                    TextMMD(
                        text = viewModel.selectedAlbum?.title ?: "Album",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    viewModel.selectedAlbum?.artistName?.takeIf { it.isNotBlank() }?.let { artist ->
                        TextMMD(
                            text = artist,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Screen.ArtistDetails.route -> ScreenTitle(
                    viewModel.selectedArtist?.name ?: "Artist"
                )

                Screen.PlaylistDetails.route -> ScreenTitle(
                    viewModel.selectedPlaylist?.title ?: "Playlist"
                )

                else -> ScreenTitle(
                    when (route) {
                        Screen.Playlists.route -> "Playlists"
                        Screen.Artists.route -> "Artists"
                        Screen.Songs.route -> "Songs"
                        Screen.Albums.route -> "Albums"
                        Screen.NowPlaying.route -> "Now Playing"
                        Screen.Settings.route -> "Settings"
                        Screen.Downloads.route -> "Downloads"
                        Screen.Login.route -> "Tidal"
                        else -> "MonoMusic"
                    }
                )
            }
        },
        actions = {
            if (route in navRoutes && route != Screen.Settings.route && viewModel.isLoggedIn) {
                IconButton(onClick = onOpenSearch) {
                    Icon(imageVector = Icons.Outlined.Search, contentDescription = "Search")
                }
            }
            if (viewModel.hasNowPlaying && route != Screen.NowPlaying.route) {
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp, end = 12.dp)
                        .border(2.dp, Color.Black, RoundedCornerShape(12.dp)),
                ) {
                    IconButton(onClick = onOpenNowPlaying) {
                        Icon(
                            imageVector = Icons.Outlined.Headphones,
                            contentDescription = "Now Playing",
                        )
                    }
                }
            }
        },
        showDivider = false,
    )
}

@Composable
private fun ScreenTitle(text: String) {
    TextMMD(
        text = text,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MonoMusicBottomBar(
    currentDestination: NavDestination?,
    onNavigate: (String) -> Unit,
) {
    val navRoutes = remember { navItems.map { it.route } }
    if (currentDestination?.route !in navRoutes) return

    NavigationBarMMD(modifier = Modifier.padding(bottom = 2.dp)) {
        navItems.forEach { screen ->
            val isSelected =
                currentDestination?.hierarchy?.any { it.route == screen.route } == true
            NavigationBarItemMMD(
                icon = {
                    Icon(imageVector = screen.icon, contentDescription = screen.label)
                },
                label = {
                    TextMMD(
                        text = screen.label,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                    )
                },
                selected = isSelected,
                onClick = { onNavigate(screen.route) },
            )
        }
    }
}
