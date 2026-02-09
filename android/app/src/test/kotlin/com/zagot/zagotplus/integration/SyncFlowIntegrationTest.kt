package com.zagot.zagotplus.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import com.google.common.truth.Truth.assertThat
import com.zagot.syncengine.api.SyncEngineConfig
import com.zagot.syncengine.api.SyncEngineImpl
import com.zagot.syncengine.dao.ConflictReconciler
import com.zagot.syncengine.dao.PullCoordinator
import com.zagot.syncengine.dao.PushCoordinator
import com.zagot.syncengine.db.SyncOutboxEntity
import com.zagot.syncengine.realtime.RealtimeBuffer
import com.zagot.syncengine.realtime.RealtimeManager
import com.zagot.syncengine.state.SyncState
import com.zagot.syncengine.testing.FakeNetworkMonitor
import com.zagot.syncengine.testing.FakeRealtimeChannel
import com.zagot.syncengine.testing.FakeSupabaseClient
import com.zagot.syncengine.state.SyncStateMachine
import com.zagot.syncengine.util.SyncTableConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import io.mockk.mockk
import org.robolectric.RobolectricTestRunner

/**
 * Integration tests for the full sync engine flow.
 * Uses Robolectric + in-memory Room to exercise the real orchestration.
 */
@RunWith(RobolectricTestRunner::class)
class SyncFlowIntegrationTest {

    private lateinit var db: TestSyncDatabase
    private lateinit var fakeClient: FakeSupabaseClient
    private lateinit var fakeChannel: FakeRealtimeChannel
    private lateinit var fakeNetwork: FakeNetworkMonitor
    private lateinit var stateMachine: SyncStateMachine
    private lateinit var realtimeBuffer: RealtimeBuffer
    private lateinit var engine: SyncEngineImpl

    private lateinit var productDao: TestProductDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        db = Room.inMemoryDatabaseBuilder(context, TestSyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        productDao = db.testProductDao()
        fakeClient = FakeSupabaseClient()
        fakeChannel = FakeRealtimeChannel()
        fakeNetwork = FakeNetworkMonitor()
        stateMachine = SyncStateMachine()
        realtimeBuffer = RealtimeBuffer()

        val outboxDao = db.syncOutboxDao()
        val metadataDao = db.syncMetadataDao()
        val pullCoordinator = PullCoordinator(metadataDao, stateMachine)
        val pushCoordinator = PushCoordinator(outboxDao, stateMachine).apply {
            backoffMultiplier = 0L
        }
        val conflictReconciler = ConflictReconciler(outboxDao)
        val realtimeManager = RealtimeManager(stateMachine, realtimeBuffer)
        val workManager = mockk<WorkManager>(relaxed = true)

        val config = SyncEngineConfig(
            pullPageSize = 3,
            pushBatchSize = 100,
            realtimeBufferMaxEvents = 50,
            maxCatchUpRetries = 2,
            livePushDebounceMs = 50L,
        )

        engine = SyncEngineImpl(
            stateMachine = stateMachine,
            pullCoordinator = pullCoordinator,
            pushCoordinator = pushCoordinator,
            conflictReconciler = conflictReconciler,
            realtimeManager = realtimeManager,
            realtimeBuffer = realtimeBuffer,
            networkMonitor = fakeNetwork,
            realtimeChannel = fakeChannel,
            remoteClient = fakeClient,
            outboxDao = outboxDao,
            syncMetadataDao = metadataDao,
            database = db,
            workManager = workManager,
            config = config,
        )
    }

    @After
    fun tearDown() = runBlocking {
        engine.stop()
        db.close()
    }

    private fun productsConfig() = SyncTableConfig(
        tableName = "test_products",
        primaryKey = "id",
        timestampColumn = "server_updated_at",
        softDeleteColumn = null,
        applyToRoom = { records ->
            val entities = records.map { r ->
                TestProductEntity(
                    id = r["id"]?.toString() ?: "",
                    name = r["name"]?.toString() ?: "",
                    serverUpdatedAt = r["server_updated_at"]?.toString() ?: "",
                )
            }
            productDao.upsertAll(entities)
        },
        deleteFromRoom = { pk -> productDao.deleteById(pk) },
    )

    // ── Test 1: Full catch-up ────────────────────────────────────────────

    @Test
    fun `catch-up pulls remote records into Room`() = runBlocking {
        fakeClient.injectRemoteRecord("test_products", mapOf(
            "id" to "p1", "name" to "Cashew", "server_updated_at" to "2024-01-01T00:00:00.100Z"
        ))
        fakeClient.injectRemoteRecord("test_products", mapOf(
            "id" to "p2", "name" to "Walnut", "server_updated_at" to "2024-01-01T00:00:00.200Z"
        ))

        engine.registerTable(productsConfig())
        engine.start()
        fakeNetwork.simulateOnline()

        delay(500)

        val products = productDao.getAll()
        assertThat(products).hasSize(2)
        assertThat(products.map { it.id }).containsExactly("p1", "p2")
        assertThat(stateMachine.state.value).isEqualTo(SyncState.LIVE)
    }

    // ── Test 7: Compound cursor pagination ───────────────────────────────

    @Test
    fun `compound cursor paginates same-timestamp records`() = runBlocking {
        repeat(10) { i ->
            fakeClient.injectRemoteRecord("test_products", mapOf(
                "id" to "p${String.format("%03d", i)}",
                "name" to "Product $i",
                "server_updated_at" to "2024-01-01T00:00:00.100Z",
            ))
        }

        engine.registerTable(productsConfig())
        engine.start()
        fakeNetwork.simulateOnline()

        delay(1000)

        val products = productDao.getAll()
        assertThat(products).hasSize(10)
    }

    // ── Test 8: Safety sync ──────────────────────────────────────────────

    @Test
    fun `safety sync applies new remote records while LIVE`() = runBlocking {
        engine.registerTable(productsConfig())
        engine.start()
        fakeNetwork.simulateOnline()

        delay(300)
        assertThat(stateMachine.state.value).isEqualTo(SyncState.LIVE)

        fakeClient.injectRemoteRecord("test_products", mapOf(
            "id" to "p1", "name" to "Late Arrival", "server_updated_at" to "2024-06-01T00:00:00.100Z"
        ))

        engine.syncNow()
        delay(300)

        val product = productDao.getById("p1")
        assertThat(product).isNotNull()
        assertThat(product!!.name).isEqualTo("Late Arrival")
    }

    // ── Test 9: FAILED push status ───────────────────────────────────────

    @Test
    fun `terminal push error marks entry as FAILED`() = runBlocking {
        engine.registerTable(productsConfig())
        engine.start()
        fakeNetwork.simulateOnline()

        delay(300)

        db.syncOutboxDao().insert(
            SyncOutboxEntity(
                tableName = "test_products",
                recordId = "p1",
                operation = "INSERT",
                payload = """{"id":"p1","name":"test","server_updated_at":"2024-01-01T00:00:00Z"}""",
                createdAt = System.currentTimeMillis(),
            )
        )

        fakeClient.failNextCalls(10, RuntimeException("400 Bad Request"))

        engine.syncNow()
        delay(500)

        assertThat(db.syncOutboxDao().countFailed()).isEqualTo(1)
        assertThat(db.syncOutboxDao().countPending()).isEqualTo(0)
    }
}
