package com.schoolsync.teacher.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.schoolsync.teacher.ui.attendance.AttendanceScreen
import com.schoolsync.teacher.ui.auth.ForceChangePasswordScreen
import com.schoolsync.teacher.ui.auth.LoginScreen
import com.schoolsync.teacher.ui.splash.SplashScreen
import com.schoolsync.teacher.ui.splash.SplashViewModel
import com.schoolsync.teacher.ui.splash.WalkthroughScreen
import com.schoolsync.teacher.ui.dashboard.DashboardScreen
import com.schoolsync.teacher.ui.leave.LeaveScreen
import com.schoolsync.teacher.ui.marks.MarksScreen
import com.schoolsync.teacher.ui.results.ResultsScreen
import com.schoolsync.teacher.ui.myattendance.MyAttendanceScreen
import com.schoolsync.teacher.ui.messages.MessagesScreen
import com.schoolsync.teacher.ui.events.EventsTeacherScreen
import com.schoolsync.teacher.ui.notices.NoticesScreen
import com.schoolsync.teacher.ui.homework.HomeworkTeacherScreen
import com.schoolsync.teacher.ui.fees.FeesTeacherScreen
import com.schoolsync.teacher.ui.payslips.PayslipsScreen
import com.schoolsync.teacher.ui.appraisals.AppraisalsScreen
import com.schoolsync.teacher.ui.recruitment.RecruitmentScreen
import com.schoolsync.teacher.ui.gallery.GalleryTeacherScreen
import com.schoolsync.teacher.ui.library.LibraryTeacherScreen
import com.schoolsync.teacher.ui.redflags.RedFlagTeacherScreen
import com.schoolsync.teacher.ui.stories.StoriesTeacherScreen
import com.schoolsync.teacher.ui.profile.MyProfileScreen
import com.schoolsync.teacher.ui.students.StudentsScreen
import com.schoolsync.teacher.ui.theme.Divider as DividerColor
import com.schoolsync.teacher.ui.theme.ErrorRed
import com.schoolsync.teacher.ui.theme.Glass
import com.schoolsync.teacher.ui.theme.GlassBorder
import com.schoolsync.teacher.ui.theme.LocalAppColors
import com.schoolsync.teacher.ui.theme.NavRailBg
import com.schoolsync.teacher.ui.theme.NavSelected
import com.schoolsync.teacher.ui.theme.NavUnselected
import com.schoolsync.teacher.ui.theme.TextPrimary
import com.schoolsync.teacher.ui.theme.TextSecondary
import com.schoolsync.teacher.ui.theme.TextTertiary
import com.schoolsync.teacher.ui.theme.Teal
import com.schoolsync.teacher.ui.theme.TealSurface
import com.schoolsync.teacher.ui.timetable.TimetableScreen
import com.schoolsync.teacher.ui.lessonplan.TodayLessonsScreen
import kotlinx.coroutines.flow.collectLatest

/** All navigation routes in the app. */
sealed class Route(val route: String) {
    data object Splash : Route("splash")
    data object Walkthrough : Route("walkthrough")
    data object Login : Route("login")
    data object ForceChangePassword : Route("force_change_password")
    data object Main : Route("main")

    // Main tabs
    data object Dashboard : Route("dashboard")
    data object Attendance : Route("attendance")
    data object MyAttendance : Route("my_attendance")
    data object Marks : Route("marks")
    data object Results : Route("results")
    data object Timetable : Route("timetable")
    data object Students : Route("students")
    data object Messages : Route("messages")
    data object Notices : Route("notices")
    data object Leave : Route("leave")
    data object Homework : Route("homework")
    data object RedFlags : Route("redflags")
    data object Stories : Route("stories")
    data object StoryViewer : Route("story_viewer/{authorId}") {
        fun create(authorId: String) = "story_viewer/$authorId"
    }
    data object Fees : Route("fees")
    data object Gallery : Route("gallery")
    data object Library : Route("library")
    data object Payslips : Route("payslips")
    data object Appraisals : Route("appraisals")
    data object Recruitment : Route("recruitment")
    data object Events : Route("events")
    data object Ptm : Route("ptm")
    data object More : Route("more")
    data object Profile : Route("profile")
    data object LessonPlan : Route("lesson_plan")
    data object Search : Route("search")
}

data class NavRailItem(
    val route: Route,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

/** Primary nav items (shown directly in the rail). */
val mainNavItems = listOf(
    NavRailItem(Route.Dashboard, "Home", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    NavRailItem(Route.Attendance, "Attend.", Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle),
    NavRailItem(Route.Marks, "Marks", Icons.Filled.Grade, Icons.Outlined.Grade),
    NavRailItem(Route.Students, "Students", Icons.Filled.People, Icons.Outlined.People),
    NavRailItem(Route.Messages, "Chat", Icons.Filled.Chat, Icons.Outlined.Chat),
)

/** Sub-items revealed when "More" is expanded. */
val moreSubItems = listOf(
    NavRailItem(Route.MyAttendance, "My Att.", Icons.Filled.MyLocation, Icons.Outlined.MyLocation),
    NavRailItem(Route.Results, "Results", Icons.Filled.Leaderboard, Icons.Outlined.Leaderboard),
    NavRailItem(Route.Homework, "HW", Icons.Filled.MenuBook, Icons.Outlined.MenuBook),
    NavRailItem(Route.Fees, "Fees", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    NavRailItem(Route.RedFlags, "Flags", Icons.Filled.Flag, Icons.Outlined.Flag),
    NavRailItem(Route.Stories, "Stories", Icons.Filled.CameraAlt, Icons.Outlined.CameraAlt),
    NavRailItem(Route.Timetable, "Time", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    NavRailItem(Route.LessonPlan, "Plans", Icons.Filled.EventNote, Icons.Outlined.EventNote),
    NavRailItem(Route.Notices, "Notices", Icons.Filled.Campaign, Icons.Outlined.Campaign),
    NavRailItem(Route.Events, "Events", Icons.Filled.Event, Icons.Outlined.Event),
    NavRailItem(Route.Ptm, "PTM", Icons.Filled.Groups, Icons.Outlined.Groups),
    NavRailItem(Route.Leave, "Leave", Icons.Filled.EventNote, Icons.Outlined.EventNote),
    NavRailItem(Route.Gallery, "Gallery", Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary),
    NavRailItem(Route.Library, "Library", Icons.Filled.LocalLibrary, Icons.Outlined.LocalLibrary),
    NavRailItem(Route.Payslips, "Pay", Icons.Filled.Payments, Icons.Outlined.Payments),
    NavRailItem(Route.Appraisals, "Review", Icons.Filled.WorkspacePremium, Icons.Outlined.WorkspacePremium),
    NavRailItem(Route.Recruitment, "Jobs", Icons.Filled.WorkOutline, Icons.Outlined.WorkOutline),
)

/** Routes that belong to the "More" group (for highlight logic). */
private val moreRoutes = moreSubItems.map { it.route.route }.toSet()

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String
) {
    // ── Mid-session credential enforcement ──
    // Staff sign in on BOTH the Teacher app and the admin panel, so a password
    // reset has to end both sessions. Before this, `mustChangePassword` was only
    // read at cold start: a running app kept working until the user happened to
    // relaunch. Re-check whenever the app is foregrounded, and react to Firebase
    // dropping the user (revoked token / disabled account).
    val sessionGuard: com.schoolsync.teacher.ui.session.SessionGuardViewModel = hiltViewModel()
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) sessionGuard.recheck()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val guardContext = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        sessionGuard.sessionEnded.collect { message ->
            android.widget.Toast.makeText(guardContext, message, android.widget.Toast.LENGTH_LONG).show()
            navController.navigate(Route.Login.route) {
                // Drop the whole back stack: the session is gone, so nothing
                // behind us is still authorised to render. launchSingleTop stops
                // a second trigger (auth-state AND foreground re-check can both
                // fire) from stacking two Login screens.
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(tween(300)) },
        exitTransition = { fadeOut(tween(300)) }
    ) {
        composable(Route.Splash.route) {
            val viewModel: SplashViewModel = hiltViewModel()
            val state by viewModel.state.collectAsState()

            if (!state.isLoading) {
                SplashScreen(
                    onNavigateToWalkthrough = {
                        navController.navigate(Route.Walkthrough.route) {
                            popUpTo(Route.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToLogin = {
                        navController.navigate(Route.Login.route) {
                            popUpTo(Route.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToMain = {
                        navController.navigate(Route.Main.route) {
                            popUpTo(Route.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToForceChange = {
                        navController.navigate(Route.ForceChangePassword.route) {
                            popUpTo(Route.Splash.route) { inclusive = true }
                        }
                    },
                    isLoggedIn = state.isLoggedIn,
                    hasSeenOnboarding = state.hasSeenOnboarding,
                    mustChangePassword = state.mustChangePassword,
                )
            }
        }

        composable(Route.Walkthrough.route) {
            val viewModel: SplashViewModel = hiltViewModel()

            WalkthroughScreen(
                onFinished = {
                    viewModel.markOnboardingSeen()
                    navController.navigate(Route.Login.route) {
                        popUpTo(Route.Walkthrough.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Route.Main.route) {
                        popUpTo(Route.Login.route) { inclusive = true }
                    }
                },
                onRequiresPasswordChange = {
                    navController.navigate(Route.ForceChangePassword.route) {
                        popUpTo(Route.Login.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Route.ForceChangePassword.route) {
            ForceChangePasswordScreen(
                onDone = {
                    navController.navigate(Route.Main.route) {
                        popUpTo(Route.ForceChangePassword.route) { inclusive = true }
                    }
                },
                onLogout = {
                    navController.navigate(Route.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable(Route.Main.route) {
            MainScaffold(navController = navController)
        }
    }
}

@Composable
fun MainScaffold(navController: NavHostController) {
    val mainViewModel: MainViewModel = hiltViewModel()

    // ONE shared StoryViewerViewModel for the whole Main scaffold (M2). It is
    // scoped to the Main NavBackStackEntry (this composable's ViewModelStoreOwner),
    // so the dashboard ring, the Stories-screen ring, AND the full-screen viewer
    // overlay all reuse the SAME warm VM — a single set of collection-group
    // observeActiveStories + observeSeenStoryIds listeners instead of up to three.
    val storyViewerViewModel: com.schoolsync.teacher.ui.stories.StoryViewerViewModel = hiltViewModel()

    // Listen for logout event → navigate back to Login
    LaunchedEffect(Unit) {
        mainViewModel.logoutEvent.collectLatest {
            navController.navigate(Route.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val innerNavController = androidx.navigation.compose.rememberNavController()
    val innerBackStackEntry by innerNavController.currentBackStackEntryAsState()
    val currentRoute = innerBackStackEntry?.destination?.route ?: Route.Dashboard.route

    // Sidebar profile data
    val teacherName by mainViewModel.teacherName.collectAsStateWithLifecycle()
    val schoolName by mainViewModel.schoolName.collectAsStateWithLifecycle()
    val position by mainViewModel.position.collectAsStateWithLifecycle()

    // Live staff capabilities → nav gating. UNKNOWN (pre-rollout) shows all.
    val caps by mainViewModel.capabilities.collectAsStateWithLifecycle()

    // Capability-guarded navigation chokepoint. Every arbitrary-route jump
    // (dashboard tiles, search) routes through here; a disallowed module is a
    // no-op (its tile is also hidden). Fail-open while caps are UNKNOWN.
    val guardedNav: (String) -> Unit = guardedNav@{ route ->
        if (!com.schoolsync.teacher.util.ModuleGate.canAccess(route, caps)) return@guardedNav
        innerNavController.navigate(route) {
            popUpTo(Route.Dashboard.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    var moreExpanded by remember { mutableStateOf(false) }
    // Auto-expand More section if a sub-route is active
    LaunchedEffect(currentRoute) {
        if (currentRoute in moreRoutes) moreExpanded = true
    }

    // Consume FCM deep-link intents. MainActivity publishes a target route
    // to DeepLinkBridge when a notification is tapped; we navigate here
    // once MainScaffold is mounted, then clear the flag so tab switches
    // later don't re-route.
    //
    // Use the SAME navigate options as the NavRail click handlers below —
    // popUpTo(Dashboard) keeps the back stack clean so tapping Home later
    // actually returns to Dashboard instead of getting stuck.
    // Deep-link notice id (from a tapped notice push) to auto-open on arrival.
    var pendingNoticeId by remember { mutableStateOf<String?>(null) }

    val pendingDeepLink by com.schoolsync.teacher.util.DeepLinkBridge.pending.collectAsState()
    LaunchedEffect(pendingDeepLink) {
        val target = pendingDeepLink ?: return@LaunchedEffect
        val known = setOf(
            Route.Dashboard.route, Route.Events.route, Route.Notices.route,
            Route.Messages.route,  Route.Attendance.route, Route.Leave.route
        )
        if (target.route in known && com.schoolsync.teacher.util.ModuleGate.canAccess(target.route, caps)) {
            // Stash a notice id (if any) so NoticesScreen auto-selects it.
            if (target.route == Route.Notices.route) {
                target.arg?.let { pendingNoticeId = it }
            }
            innerNavController.navigate(target.route) {
                popUpTo(Route.Dashboard.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            // If this is a "more" sub-item, expand the More group so it's visible
            if (target.route in moreRoutes) moreExpanded = true
        }
        com.schoolsync.teacher.util.DeepLinkBridge.consume()
    }

    // Story viewer is rendered as a FULL-SCREEN OVERLAY above the nav
    // content (not a separate route) so the screen it was opened from
    // stays composed behind it — when the viewer fades on swipe-down /
    // pinch-out, that screen shows through (Instagram/WhatsApp-style).
    var storyViewerAuthorId by remember { mutableStateOf<String?>(null) }

    // Pending gallery album to preselect when arriving from the Events
    // "View Photos" jump. Set by Events, consumed by the Gallery screen.
    var pendingGalleryAlbumId by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
    Row(modifier = Modifier.fillMaxSize()) {
        // ── Side Navigation Rail (scrollable) ──
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(76.dp)
                .background(NavRailBg)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // ── School branding + teacher profile ──
            SidebarProfileHeader(
                teacherName = teacherName,
                schoolName = schoolName,
                position = position,
                onProfileClick = {
                    if (currentRoute != Route.Profile.route) {
                        innerNavController.navigate(Route.Profile.route) {
                            popUpTo(Route.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(4.dp))
            Divider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = DividerColor
            )
            Spacer(modifier = Modifier.height(4.dp))

            // App-wide unread/pending counts (e.g. Notices) → per-route badge.
            val badgeCounts by hiltViewModel<BadgeViewModel>().counts.collectAsState()

            // Primary items (capability-gated; UNKNOWN caps → all shown)
            mainNavItems.filter { com.schoolsync.teacher.util.ModuleGate.canAccess(it.route.route, caps) }.forEach { item ->
                NavRailEntry(
                    item = item,
                    isSelected = currentRoute == item.route.route,
                    badgeCount = badgeCounts[item.route.route] ?: 0,
                    onClick = {
                        if (currentRoute != item.route.route) {
                            innerNavController.navigate(item.route.route) {
                                popUpTo(Route.Dashboard.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }

            // ── More toggle ──
            val moreActive = currentRoute in moreRoutes
            val chevronRotation by animateFloatAsState(
                targetValue = if (moreExpanded) 180f else 0f,
                label = "chevron"
            )

            Column(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(bounded = false, radius = 24.dp)
                    ) { moreExpanded = !moreExpanded }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 28.dp)
                        .background(
                            color = if (moreActive) NavSelected.copy(alpha = 0.12f) else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = "More",
                        tint = if (moreActive) NavSelected else NavUnselected,
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(chevronRotation)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "More",
                    fontSize = 10.sp,
                    color = if (moreActive) NavSelected else NavUnselected,
                    maxLines = 1
                )
            }

            // ── Expanded sub-items ──
            AnimatedVisibility(
                visible = moreExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    moreSubItems.filter { com.schoolsync.teacher.util.ModuleGate.canAccess(it.route.route, caps) }.forEach { item ->
                        NavRailEntry(
                            item = item,
                            isSelected = currentRoute == item.route.route,
                            badgeCount = badgeCounts[item.route.route] ?: 0,
                            onClick = {
                                if (currentRoute != item.route.route) {
                                    innerNavController.navigate(item.route.route) {
                                        popUpTo(Route.Dashboard.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Logout moved to My Profile page (bottom) — see MyProfileScreen.
            Spacer(modifier = Modifier.weight(1f))
        }

        // Thin divider
        Divider(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp),
            color = DividerColor
        )

        // ── Main content area ──
        NavHost(
            navController = innerNavController,
            startDestination = Route.Dashboard.route,
            modifier = Modifier.weight(1f),
            enterTransition = { fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 8 } },
            exitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { -it / 8 } },
            popEnterTransition = { fadeIn(tween(200)) + slideInHorizontally(tween(200)) { -it / 8 } },
            popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { it / 8 } }
        ) {
            composable(Route.Dashboard.route) {
                DashboardScreen(
                    onNotificationsClick = {
                        innerNavController.navigate(Route.Notices.route) {
                            popUpTo(Route.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigate = guardedNav,
                    canAccess = { route -> com.schoolsync.teacher.util.ModuleGate.canAccess(route, caps) },
                    onOpenStory = { authorId -> storyViewerAuthorId = authorId },
                    storyViewerViewModel = storyViewerViewModel
                )
            }
            composable(Route.Attendance.route) {
                AttendanceScreen(canEdit = com.schoolsync.teacher.util.ModuleGate.canEdit(Route.Attendance.route, caps))
            }
            composable(Route.MyAttendance.route) { MyAttendanceScreen() }
            composable(Route.Marks.route) {
                MarksScreen(canEdit = com.schoolsync.teacher.util.ModuleGate.canEdit(Route.Marks.route, caps))
            }
            composable(Route.Results.route) { ResultsScreen() }
            composable(Route.Timetable.route) { TimetableScreen() }
            composable(Route.LessonPlan.route) { TodayLessonsScreen() }
            composable(Route.Students.route) { StudentsScreen() }
            composable(Route.Messages.route) { MessagesScreen() }
            composable(Route.Notices.route) {
                NoticesScreen(
                    deepLinkNoticeId = pendingNoticeId,
                    onDeepLinkConsumed = { pendingNoticeId = null }
                )
            }
            composable(Route.Events.route) {
                EventsTeacherScreen(
                    onOpenAlbum = { albumId ->
                        pendingGalleryAlbumId = albumId
                        innerNavController.navigate(Route.Gallery.route) {
                            popUpTo(Route.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Route.Ptm.route) {
                com.schoolsync.teacher.ui.ptm.MyPtmScreen()
            }
            composable(Route.Leave.route) { LeaveScreen() }
            composable(Route.Homework.route) {
                HomeworkTeacherScreen(canEdit = com.schoolsync.teacher.util.ModuleGate.canEdit(Route.Homework.route, caps))
            }
            composable(Route.RedFlags.route) {
                RedFlagTeacherScreen(canEdit = com.schoolsync.teacher.util.ModuleGate.canEdit(Route.RedFlags.route, caps))
            }
            composable(Route.Stories.route) {
                StoriesTeacherScreen(
                    onOpenViewer = { authorId -> storyViewerAuthorId = authorId },
                    canEdit = com.schoolsync.teacher.util.ModuleGate.canEdit(Route.Stories.route, caps),
                    viewerViewModel = storyViewerViewModel
                )
            }
            composable(Route.Fees.route) { FeesTeacherScreen() }
            composable(Route.Gallery.route) {
                GalleryTeacherScreen(
                    initialAlbumId = pendingGalleryAlbumId,
                    onInitialAlbumConsumed = { pendingGalleryAlbumId = null }
                )
            }
            composable(Route.Library.route) { LibraryTeacherScreen() }
            composable(Route.Payslips.route) { PayslipsScreen() }
            composable(Route.Appraisals.route) { AppraisalsScreen() }
            composable(Route.Recruitment.route) { RecruitmentScreen() }
            composable(Route.Profile.route) {
                MyProfileScreen(
                    onLogout = {
                        navController.navigate(Route.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            composable(Route.Search.route) {
                com.schoolsync.teacher.ui.search.SearchScreen(
                    onBack = { innerNavController.popBackStack() },
                    onNavigateRoute = { route ->
                        // Capability-gated: a disallowed module is a no-op.
                        if (com.schoolsync.teacher.util.ModuleGate.canAccess(route, caps)) {
                            // Drop Search from the back stack so Back from the
                            // target lands on the dashboard, not on search.
                            innerNavController.navigate(route) {
                                popUpTo(Route.Search.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }
        }
        } // end Row (nav rail + content)

        // Full-screen story overlay — the screen it was opened from stays
        // composed behind, so it shows through as the viewer fades on a
        // swipe-down / pinch-out dismiss.
        storyViewerAuthorId?.let { authorId ->
            com.schoolsync.teacher.ui.stories.StoryViewerScreen(
                initialAuthorId = authorId,
                onClose = { storyViewerAuthorId = null },
                viewModel = storyViewerViewModel
            )
        }
    } // end overlay Box
}

/** Reusable nav rail entry (icon + label) with ripple feedback. */
@Composable
private fun NavRailEntry(
    item: NavRailItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    badgeCount: Int = 0,
) {
    val iconColor = if (isSelected) NavSelected else NavUnselected
    val labelColor = if (isSelected) NavSelected else NavUnselected
    val haptics = com.schoolsync.teacher.ui.components.rememberAppHaptics()

    Column(
        modifier = Modifier
            .padding(vertical = 2.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = false, radius = 28.dp),
                onClick = {
                    if (!isSelected) haptics.navTick()
                    onClick()
                }
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 28.dp)
                .background(
                    color = if (isSelected) NavSelected.copy(alpha = 0.12f) else Color.Transparent,
                    shape = RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                contentDescription = item.label,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
            if (badgeCount > 0) {
                NavRailBadge(
                    count = badgeCount,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-4).dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = item.label,
            fontSize = 10.sp,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Tiny unread-count badge that sits on the corner of a NavRail icon. */
@Composable
private fun NavRailBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    val text = if (count > 99) "99+" else count.toString()
    val isDot = count == 1
    Box(
        modifier = modifier
            .then(
                if (isDot) Modifier.size(8.dp)
                else Modifier
                    .height(14.dp)
                    .widthIn(min = 14.dp)
            )
            .clip(CircleShape)
            .background(NavSelected)
            .border(width = 1.5.dp, color = NavRailBg, shape = CircleShape)
            .padding(horizontal = if (isDot) 0.dp else 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!isDot) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

/** Profile + school branding at the top of the sidebar rail. */
@Composable
private fun SidebarProfileHeader(
    teacherName: String,
    schoolName: String,
    position: String,
    onProfileClick: () -> Unit = {}
) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true)
            ) { onProfileClick() }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar circle with initials
        val initials = teacherName
            .split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "T" }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(c.accent, c.accentSecondary)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = c.textOnAccent,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Teacher name
        if (teacherName.isNotEmpty()) {
            Text(
                text = teacherName.split(" ").first(),
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        // Position badge
        if (position.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = position,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 8.sp,
                textAlign = TextAlign.Center
            )
        }

        // School name badge
        if (schoolName.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(TealSurface)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = schoolName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Teal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
