package com.schoolsync.teacher.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.schoolsync.teacher.BuildConfig
import com.google.firebase.firestore.FirebaseFirestore
import com.schoolsync.teacher.data.firebase.FirebaseAuthManager
import com.schoolsync.teacher.data.firebase.FirebaseService
import com.schoolsync.teacher.data.firebase.FirestoreService
import com.schoolsync.teacher.data.local.TokenManager
import com.schoolsync.teacher.data.remote.AuthApi
import com.schoolsync.teacher.data.remote.AuthInterceptor
import com.schoolsync.teacher.data.remote.ApiService
import com.schoolsync.teacher.data.repository.AuthRepository
import com.schoolsync.teacher.data.repository.FeeRepository
import com.schoolsync.teacher.data.repository.HomeworkTeacherRepository
import com.schoolsync.teacher.data.repository.LeaveRepository
import com.schoolsync.teacher.data.repository.RedFlagRepository
import com.schoolsync.teacher.data.repository.DataRepository
import com.schoolsync.teacher.data.repository.StudentRepository
import com.schoolsync.teacher.data.repository.TeacherRepository
import com.schoolsync.teacher.data.repository.firestore.AttendanceFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.ChatRtdbRepository
import com.schoolsync.teacher.data.repository.firestore.CommunicationFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.ExamFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.FeeFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.HomeworkFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.LeaveFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.SchoolFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.StudentFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.SectionFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.StaffFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.TimetableFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.TransportFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.CampusLifeFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.LibraryFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.HRFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.InventoryFirestoreRepository
import com.schoolsync.teacher.data.repository.firestore.AnalyticsFirestoreRepository
import com.schoolsync.teacher.data.local.OfflineQueueManager
import com.schoolsync.teacher.util.NetworkMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase = FirebaseDatabase.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext context: Context): TokenManager =
        TokenManager(context)

    @Provides
    @Singleton
    fun provideFirebaseService(database: FirebaseDatabase): FirebaseService =
        FirebaseService(database)

    @Provides
    @Singleton
    fun provideFirebaseAuthManager(firebaseAuth: FirebaseAuth): FirebaseAuthManager =
        FirebaseAuthManager(firebaseAuth)

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirestoreService(firestore: FirebaseFirestore): FirestoreService =
        FirestoreService(firestore)

    @Provides
    @Singleton
    fun provideAuthInterceptor(): AuthInterceptor =
        AuthInterceptor()

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService =
        retrofit.create(ApiService::class.java)

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideAuthRepository(
        tokenManager: TokenManager,
        firebaseAuthManager: FirebaseAuthManager,
        firebaseService: FirebaseService,
        firestoreService: FirestoreService,
        authApi: AuthApi
    ): AuthRepository = AuthRepository(tokenManager, firebaseAuthManager, firebaseService, firestoreService, authApi)

    @Provides
    @Singleton
    fun provideDataRepository(
        firestoreService: FirestoreService,
        firebaseService: FirebaseService,
        tokenManager: TokenManager
    ): DataRepository = DataRepository(firestoreService, firebaseService, tokenManager)

    @Provides
    @Singleton
    fun provideStudentRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): StudentRepository = StudentRepository(firestoreService, tokenManager)

    @Provides
    @Singleton
    fun provideTeacherRepository(
        firebaseService: FirebaseService,
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): TeacherRepository = TeacherRepository(firebaseService, firestoreService, tokenManager)

    // MessagesRepository binding removed — Direct Messaging retired (Package 2A).
    // The Messages screen now renders a Coming Soon Composable that does not
    // instantiate any ViewModel or repository.

    @Provides
    @Singleton
    fun provideLeaveRepository(
        firebaseService: FirebaseService,
        tokenManager: TokenManager
    ): LeaveRepository = LeaveRepository(firebaseService, tokenManager)

    @Provides
    @Singleton
    fun provideRedFlagRepository(
        firestoreService: FirestoreService,
        firebaseAuth: FirebaseAuth,
        tokenManager: TokenManager
    ): RedFlagRepository = RedFlagRepository(firestoreService, firebaseAuth, tokenManager)

    // Legacy StoryRepository (RTDB Social/Stories) removed — see
    // StoryFirestoreRepository for the canonical, real-time replacement.

    // GalleryRepository (RTDB) removed in Phase C-2 (2026-04-26).
    // GalleryFirestoreRepository is now the canonical reader/writer; Hilt
    // resolves it via its @Inject constructor — no explicit @Provides needed.

    @Provides
    @Singleton
    fun provideFeeRepository(
        feeFirestoreRepository: FeeFirestoreRepository,
        studentRepository: StudentRepository
    ): FeeRepository = FeeRepository(feeFirestoreRepository, studentRepository)

    @Provides
    @Singleton
    fun provideHomeworkTeacherRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): HomeworkTeacherRepository = HomeworkTeacherRepository(firestoreService, tokenManager)

    // ── Firestore Repositories ─────────────────────────────────────────

    @Provides
    @Singleton
    fun provideSchoolFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): SchoolFirestoreRepository = SchoolFirestoreRepository(firestoreService, tokenManager)

    @Provides
    @Singleton
    fun provideStudentFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): StudentFirestoreRepository = StudentFirestoreRepository(firestoreService, tokenManager)

    @Provides
    @Singleton
    fun provideSectionFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): SectionFirestoreRepository = SectionFirestoreRepository(firestoreService, tokenManager)

    @Provides
    @Singleton
    fun provideStaffFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): StaffFirestoreRepository = StaffFirestoreRepository(firestoreService, tokenManager)

    @Provides
    @Singleton
    fun provideAttendanceFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager,
        apiService: ApiService
    ): AttendanceFirestoreRepository = AttendanceFirestoreRepository(firestoreService, tokenManager, apiService)

    @Provides
    @Singleton
    fun provideHomeworkFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): HomeworkFirestoreRepository = HomeworkFirestoreRepository(firestoreService, tokenManager)

    @Provides
    @Singleton
    fun provideLeaveFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): LeaveFirestoreRepository = LeaveFirestoreRepository(firestoreService, tokenManager)

    @Provides
    @Singleton
    fun provideExamFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): ExamFirestoreRepository = ExamFirestoreRepository(firestoreService, tokenManager)

    @Provides
    @Singleton
    fun provideTimetableFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): TimetableFirestoreRepository = TimetableFirestoreRepository(firestoreService, tokenManager)

    @Provides
    @Singleton
    fun provideFeeFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): FeeFirestoreRepository = FeeFirestoreRepository(firestoreService, tokenManager)

    @Provides
    @Singleton
    fun provideCommunicationFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): CommunicationFirestoreRepository =
        CommunicationFirestoreRepository(firestoreService, tokenManager)

    @Provides
    @Singleton
    fun provideChatRtdbRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): ChatRtdbRepository =
        // Phase 5 — name retained for DI back-compat; backing store
        // moved to Firestore (notifBadges / presence collections).
        ChatRtdbRepository(firestoreService, tokenManager)

    // ── Phase 7–12 Firestore Repositories ────────────────────────────────

    @Provides
    @Singleton
    fun provideTransportFirestoreRepository(
        firestoreService: FirestoreService,
        firebaseService: FirebaseService,
        tokenManager: TokenManager
    ): TransportFirestoreRepository =
        TransportFirestoreRepository(firestoreService, firebaseService, tokenManager)

    @Provides
    @Singleton
    fun provideCampusLifeFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): CampusLifeFirestoreRepository =
        CampusLifeFirestoreRepository(firestoreService, tokenManager)

    @Provides
    @Singleton
    fun provideLibraryFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): LibraryFirestoreRepository =
        LibraryFirestoreRepository(firestoreService, tokenManager)

    @Provides
    @Singleton
    fun provideHRFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): HRFirestoreRepository =
        HRFirestoreRepository(firestoreService, tokenManager)

    @Provides
    @Singleton
    fun provideInventoryFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): InventoryFirestoreRepository =
        InventoryFirestoreRepository(firestoreService, tokenManager)

    @Provides
    @Singleton
    fun provideAnalyticsFirestoreRepository(
        firestoreService: FirestoreService,
        tokenManager: TokenManager
    ): AnalyticsFirestoreRepository =
        AnalyticsFirestoreRepository(firestoreService, tokenManager)

    // ── Utility ─────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideNetworkMonitor(
        @ApplicationContext context: Context
    ): NetworkMonitor = NetworkMonitor(context)

    @Provides
    @Singleton
    fun provideOfflineQueueManager(
        @ApplicationContext context: Context,
        firebaseService: FirebaseService,
        gson: Gson
    ): OfflineQueueManager = OfflineQueueManager(context, firebaseService, gson)
}
