package Paris.optimization.testing;

import Paris.optimization.OptimizationManager;
import Paris.optimization.integration.OptimizedTrailManager;
import Paris.optimization.lockfree.*;
import Paris.optimization.pooling.*;
import Paris.optimization.primitives.*;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Comprehensive test suite for optimization systems.
 * Validates performance, thread safety, and correctness of all optimization components.
 */
public class OptimizationTestSuite {
    
    private static final Logger logger = Logger.getLogger(OptimizationTestSuite.class.getName());
    
    private final OptimizationManager optimizationManager;
    private final OptimizedTrailManager trailManager;
    private final Plugin plugin;
    
    // Test configuration
    private static final int STRESS_TEST_THREADS = 10;
    private static final int STRESS_TEST_OPERATIONS = 10000;
    private static final int PERFORMANCE_TEST_ITERATIONS = 100000;
    
    public OptimizationTestSuite(OptimizationManager optimizationManager, 
                                OptimizedTrailManager trailManager, Plugin plugin) {
        this.optimizationManager = optimizationManager;
        this.trailManager = trailManager;
        this.plugin = plugin;
    }
    
    /**
     * Run all tests and return comprehensive results
     */
    public TestResults runAllTests() {
        logger.info("[OptimizationTestSuite] Starting comprehensive test suite...");
        
        TestResults results = new TestResults();
        
        try {
            // Test lock-free systems
            if (optimizationManager.getLockFreePlayerManager() != null) {
                results.lockFreeTests = testLockFreeSystems();
            }
            
            // Test object pooling
            if (optimizationManager.getLocationPool() != null) {
                results.poolingTests = testObjectPooling();
            }
            
            // Test primitive collections
            results.primitiveTests = testPrimitiveCollections();
            
            // Test trail manager integration
            if (trailManager != null) {
                results.integrationTests = testTrailManagerIntegration();
            }
            
            // Performance benchmarks
            results.performanceTests = runPerformanceBenchmarks();
            
            // Thread safety tests
            results.threadSafetyTests = testThreadSafety();
            
            // Memory usage tests
            results.memoryTests = testMemoryUsage();
            
            results.overallSuccess = calculateOverallSuccess(results);
            
        } catch (Exception e) {
            logger.severe("[OptimizationTestSuite] Test suite failed: " + e.getMessage());
            e.printStackTrace();
            results.overallSuccess = false;
        }
        
        logger.info("[OptimizationTestSuite] Test suite completed. Overall success: " + results.overallSuccess);
        return results;
    }
    
    /**
     * Test lock-free systems
     */
    private TestResult testLockFreeSystems() {
        logger.info("[OptimizationTestSuite] Testing lock-free systems...");
        
        TestResult result = new TestResult("Lock-Free Systems");
        
        try {
            // Test LockFreePlayerManager
            LockFreePlayerManager playerManager = optimizationManager.getLockFreePlayerManager();
            UUID testPlayer = UUID.randomUUID();
            
            // Test basic operations
            LockFreePlayerManager.PlayerState state = new LockFreePlayerManager.PlayerState(
                testPlayer, null, Material.STONE, true, true, System.currentTimeMillis(), 0
            );
            
            boolean updateSuccess = playerManager.updatePlayerState(testPlayer, state);
            LockFreePlayerManager.PlayerState retrievedState = playerManager.getPlayerState(testPlayer);
            boolean removeSuccess = playerManager.removePlayer(testPlayer);
            
            result.addTest("Player state update", updateSuccess);
            result.addTest("Player state retrieval", retrievedState != null && retrievedState.playerId.equals(testPlayer));
            result.addTest("Player removal", removeSuccess);
            
            // Test LockFreeSpatialIndex
            LockFreeSpatialIndex spatialIndex = optimizationManager.getSpatialIndex();
            Location testLocation = new Location(null, 100, 64, 100);
            
            boolean addSuccess = spatialIndex.addEntry(testLocation, testPlayer, Material.STONE);
            LockFreeSpatialIndex.SpatialEntry entry = spatialIndex.checkCollision(testLocation, UUID.randomUUID());
            boolean removeSuccess2 = spatialIndex.removeEntry(testLocation, testPlayer);
            
            result.addTest("Spatial index add", addSuccess);
            result.addTest("Spatial index collision check", entry != null);
            result.addTest("Spatial index remove", removeSuccess2);
            
            // Test LockFreeEventQueue
            LockFreeEventQueue<String> eventQueue = new LockFreeEventQueue<>(64);
            
            boolean enqueueSuccess = eventQueue.enqueue("test event");
            String dequeuedEvent = eventQueue.dequeue();
            
            result.addTest("Event queue enqueue", enqueueSuccess);
            result.addTest("Event queue dequeue", "test event".equals(dequeuedEvent));
            
        } catch (Exception e) {
            logger.warning("[OptimizationTestSuite] Lock-free test failed: " + e.getMessage());
            result.addTest("Exception handling", false);
        }
        
        return result;
    }
    
    /**
     * Test object pooling systems
     */
    private TestResult testObjectPooling() {
        logger.info("[OptimizationTestSuite] Testing object pooling...");
        
        TestResult result = new TestResult("Object Pooling");
        
        try {
            // Test LocationPool
            LocationPool locationPool = optimizationManager.getLocationPool();
            
            Location acquired1 = locationPool.acquire();
            Location acquired2 = locationPool.acquire(null, 10, 20, 30);
            
            boolean release1 = locationPool.release(acquired1);
            boolean release2 = locationPool.release(acquired2);
            
            Location reacquired = locationPool.acquire();
            
            result.addTest("Location acquisition", acquired1 != null && acquired2 != null);
            result.addTest("Location release", release1 && release2);
            result.addTest("Location reuse", reacquired != null);
            result.addTest("Pool hit rate", locationPool.getHitRate() > 0);
            
            // Test PacketPool
            PacketPool packetPool = optimizationManager.getPacketPool();
            
            // Note: PacketContainer testing requires ProtocolLib setup
            // This is a simplified test
            result.addTest("Packet pool initialization", packetPool != null);
            result.addTest("Packet pool health", packetPool.isHealthy());
            
        } catch (Exception e) {
            logger.warning("[OptimizationTestSuite] Object pooling test failed: " + e.getMessage());
            result.addTest("Exception handling", false);
        }
        
        return result;
    }
    
    /**
     * Test primitive collections
     */
    private TestResult testPrimitiveCollections() {
        logger.info("[OptimizationTestSuite] Testing primitive collections...");
        
        TestResult result = new TestResult("Primitive Collections");
        
        try {
            // Test PlayerScoreMap
            PrimitiveCollections.PlayerScoreMap scoreMap = new PrimitiveCollections.PlayerScoreMap();
            UUID testPlayer = UUID.randomUUID();
            
            scoreMap.setScore(testPlayer, 100);
            int score = scoreMap.getScore(testPlayer);
            scoreMap.incrementScore(testPlayer);
            int incrementedScore = scoreMap.getScore(testPlayer);
            
            result.addTest("Score setting", score == 100);
            result.addTest("Score increment", incrementedScore == 101);
            result.addTest("Score map size", scoreMap.size() == 1);
            
            // Test CoordinateTrailMap
            PrimitiveCollections.CoordinateTrailMap trailMap = new PrimitiveCollections.CoordinateTrailMap();
            Location testLocation = new Location(null, 50, 64, 50);
            
            trailMap.addTrail(testLocation, testPlayer, Material.STONE);
            PrimitiveCollections.CoordinateTrailMap.TrailData trailData = trailMap.getTrail(testLocation);
            boolean hasTrail = trailMap.hasTrail(testLocation);
            
            result.addTest("Trail addition", trailData != null);
            result.addTest("Trail retrieval", trailData.playerId.equals(testPlayer));
            result.addTest("Trail existence check", hasTrail);
            
            // Test CoordinateList
            PrimitiveCollections.CoordinateList coordList = new PrimitiveCollections.CoordinateList();
            
            coordList.addCoordinates(10, 20, 30);
            coordList.addCoordinate(40);
            
            result.addTest("Coordinate addition", coordList.size() == 4);
            result.addTest("Coordinate retrieval", coordList.getCoordinate(0) == 10);
            
            // Test coordinate packing/unpacking
            long packed = PrimitiveCollections.CoordinateTrailMap.packCoordinates(100, 64, 200);
            int[] unpacked = PrimitiveCollections.CoordinateTrailMap.unpackCoordinates(packed);
            
            result.addTest("Coordinate packing", unpacked[0] == 100 && unpacked[1] == 64 && unpacked[2] == 200);
            
        } catch (Exception e) {
            logger.warning("[OptimizationTestSuite] Primitive collections test failed: " + e.getMessage());
            result.addTest("Exception handling", false);
        }
        
        return result;
    }
    
    /**
     * Test trail manager integration
     */
    private TestResult testTrailManagerIntegration() {
        logger.info("[OptimizationTestSuite] Testing trail manager integration...");
        
        TestResult result = new TestResult("Trail Manager Integration");
        
        try {
            UUID testPlayer = UUID.randomUUID();
            Location testLocation = new Location(null, 75, 64, 75);
            
            // Test trail addition
            boolean addSuccess = trailManager.addTrailBlock(testLocation, testPlayer, Material.STONE);
            
            // Test collision detection
            boolean collision = trailManager.checkCollision(testLocation, UUID.randomUUID());
            boolean noSelfCollision = !trailManager.checkCollision(testLocation, testPlayer);
            
            // Test trail count
            int trailCount = trailManager.getPlayerTrailCount(testPlayer);
            
            // Test trail removal
            trailManager.removePlayerTrails(testPlayer);
            int trailCountAfterRemoval = trailManager.getPlayerTrailCount(testPlayer);
            
            result.addTest("Trail addition", addSuccess);
            result.addTest("Collision detection", collision);
            result.addTest("Self-collision prevention", noSelfCollision);
            result.addTest("Trail counting", trailCount > 0);
            result.addTest("Trail removal", trailCountAfterRemoval == 0);
            result.addTest("Optimization status", trailManager.isOptimized());
            
        } catch (Exception e) {
            logger.warning("[OptimizationTestSuite] Trail manager integration test failed: " + e.getMessage());
            result.addTest("Exception handling", false);
        }
        
        return result;
    }
    
    /**
     * Run performance benchmarks
     */
    private TestResult runPerformanceBenchmarks() {
        logger.info("[OptimizationTestSuite] Running performance benchmarks...");
        
        TestResult result = new TestResult("Performance Benchmarks");
        
        try {
            // Benchmark location pool performance
            long startTime = System.nanoTime();
            LocationPool pool = optimizationManager.getLocationPool();
            
            for (int i = 0; i < PERFORMANCE_TEST_ITERATIONS; i++) {
                Location loc = pool.acquire();
                pool.release(loc);
            }
            
            long poolTime = System.nanoTime() - startTime;
            
            // Benchmark standard location creation
            startTime = System.nanoTime();
            
            for (int i = 0; i < PERFORMANCE_TEST_ITERATIONS; i++) {
                Location loc = new Location(null, 0, 0, 0);
                // Simulate some usage
                loc.setX(i);
            }
            
            long standardTime = System.nanoTime() - startTime;
            
            double speedup = (double) standardTime / poolTime;
            
            result.addTest("Location pool performance", speedup > 1.0);
            result.addMetric("Location pool speedup", speedup);
            result.addMetric("Pool operations per second", PERFORMANCE_TEST_ITERATIONS * 1_000_000_000.0 / poolTime);
            
            // Benchmark primitive collections
            startTime = System.nanoTime();
            PrimitiveCollections.PlayerScoreMap primitiveMap = new PrimitiveCollections.PlayerScoreMap();
            
            for (int i = 0; i < PERFORMANCE_TEST_ITERATIONS; i++) {
                UUID player = new UUID(i, i);
                primitiveMap.setScore(player, i);
                primitiveMap.getScore(player);
            }
            
            long primitiveTime = System.nanoTime() - startTime;
            
            startTime = System.nanoTime();
            Map<UUID, Integer> standardMap = new HashMap<>();
            
            for (int i = 0; i < PERFORMANCE_TEST_ITERATIONS; i++) {
                UUID player = new UUID(i, i);
                standardMap.put(player, i);
                standardMap.get(player);
            }
            
            long standardMapTime = System.nanoTime() - startTime;
            
            double mapSpeedup = (double) standardMapTime / primitiveTime;
            
            result.addTest("Primitive collections performance", mapSpeedup > 1.0);
            result.addMetric("Primitive map speedup", mapSpeedup);
            
        } catch (Exception e) {
            logger.warning("[OptimizationTestSuite] Performance benchmark failed: " + e.getMessage());
            result.addTest("Exception handling", false);
        }
        
        return result;
    }
    
    /**
     * Test thread safety
     */
    private TestResult testThreadSafety() {
        logger.info("[OptimizationTestSuite] Testing thread safety...");
        
        TestResult result = new TestResult("Thread Safety");
        
        try {
            ExecutorService executor = Executors.newFixedThreadPool(STRESS_TEST_THREADS);
            CountDownLatch latch = new CountDownLatch(STRESS_TEST_THREADS);
            AtomicLong successCount = new AtomicLong(0);
            AtomicLong errorCount = new AtomicLong(0);
            
            // Test concurrent access to lock-free systems
            for (int i = 0; i < STRESS_TEST_THREADS; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < STRESS_TEST_OPERATIONS / STRESS_TEST_THREADS; j++) {
                            UUID playerId = new UUID(threadId, j);
                            
                            // Test lock-free player manager
                            if (optimizationManager.getLockFreePlayerManager() != null) {
                                LockFreePlayerManager.PlayerState state = new LockFreePlayerManager.PlayerState(
                                    playerId, null, Material.STONE, true, true, System.currentTimeMillis(), 0
                                );
                                optimizationManager.getLockFreePlayerManager().updatePlayerState(playerId, state);
                                optimizationManager.getLockFreePlayerManager().getPlayerState(playerId);
                                optimizationManager.getLockFreePlayerManager().removePlayer(playerId);
                            }
                            
                            // Test object pools
                            if (optimizationManager.getLocationPool() != null) {
                                Location loc = optimizationManager.getLocationPool().acquire();
                                optimizationManager.getLocationPool().release(loc);
                            }
                            
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        logger.warning("[OptimizationTestSuite] Thread safety test error: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            boolean completed = latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();
            
            result.addTest("Thread safety completion", completed);
            result.addTest("No errors in concurrent access", errorCount.get() == 0);
            result.addMetric("Successful operations", successCount.get());
            result.addMetric("Error count", errorCount.get());
            
        } catch (Exception e) {
            logger.warning("[OptimizationTestSuite] Thread safety test failed: " + e.getMessage());
            result.addTest("Exception handling", false);
        }
        
        return result;
    }
    
    /**
     * Test memory usage
     */
    private TestResult testMemoryUsage() {
        logger.info("[OptimizationTestSuite] Testing memory usage...");
        
        TestResult result = new TestResult("Memory Usage");
        
        try {
            Runtime runtime = Runtime.getRuntime();
            
            // Force garbage collection
            System.gc();
            Thread.sleep(100);
            
            long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
            
            // Create test data
            List<Location> locations = new ArrayList<>();
            for (int i = 0; i < 10000; i++) {
                if (optimizationManager.getLocationPool() != null) {
                    locations.add(optimizationManager.getLocationPool().acquire());
                } else {
                    locations.add(new Location(null, i, 64, i));
                }
            }
            
            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = memoryAfter - memoryBefore;
            
            // Clean up
            if (optimizationManager.getLocationPool() != null) {
                for (Location loc : locations) {
                    optimizationManager.getLocationPool().release(loc);
                }
            }
            
            result.addTest("Memory allocation", memoryUsed > 0);
            result.addMetric("Memory used (bytes)", memoryUsed);
            result.addMetric("Memory per location (bytes)", memoryUsed / 10000.0);
            
            // Test estimated memory usage
            if (trailManager != null) {
                long estimatedUsage = trailManager.getEstimatedMemoryUsage();
                result.addMetric("Trail manager estimated usage", estimatedUsage);
            }
            
        } catch (Exception e) {
            logger.warning("[OptimizationTestSuite] Memory usage test failed: " + e.getMessage());
            result.addTest("Exception handling", false);
        }
        
        return result;
    }
    
    /**
     * Calculate overall success based on individual test results
     */
    private boolean calculateOverallSuccess(TestResults results) {
        return results.lockFreeTests.isSuccessful() &&
               results.poolingTests.isSuccessful() &&
               results.primitiveTests.isSuccessful() &&
               results.integrationTests.isSuccessful() &&
               results.performanceTests.isSuccessful() &&
               results.threadSafetyTests.isSuccessful() &&
               results.memoryTests.isSuccessful();
    }
    
    // Test result classes
    public static class TestResults {
        public TestResult lockFreeTests;
        public TestResult poolingTests;
        public TestResult primitiveTests;
        public TestResult integrationTests;
        public TestResult performanceTests;
        public TestResult threadSafetyTests;
        public TestResult memoryTests;
        public boolean overallSuccess;
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Optimization Test Results ===\n");
            sb.append("Overall Success: ").append(overallSuccess).append("\n\n");
            
            if (lockFreeTests != null) sb.append(lockFreeTests.toString()).append("\n");
            if (poolingTests != null) sb.append(poolingTests.toString()).append("\n");
            if (primitiveTests != null) sb.append(primitiveTests.toString()).append("\n");
            if (integrationTests != null) sb.append(integrationTests.toString()).append("\n");
            if (performanceTests != null) sb.append(performanceTests.toString()).append("\n");
            if (threadSafetyTests != null) sb.append(threadSafetyTests.toString()).append("\n");
            if (memoryTests != null) sb.append(memoryTests.toString()).append("\n");
            
            return sb.toString();
        }
    }
    
    public static class TestResult {
        private final String name;
        private final Map<String, Boolean> tests = new HashMap<>();
        private final Map<String, Double> metrics = new HashMap<>();
        
        public TestResult(String name) {
            this.name = name;
        }
        
        public void addTest(String testName, boolean success) {
            tests.put(testName, success);
        }
        
        public void addMetric(String metricName, double value) {
            metrics.put(metricName, value);
        }
        
        public boolean isSuccessful() {
            return tests.values().stream().allMatch(Boolean::booleanValue);
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("--- ").append(name).append(" ---\n");
            sb.append("Success: ").append(isSuccessful()).append("\n");
            
            for (Map.Entry<String, Boolean> test : tests.entrySet()) {
                sb.append("  ").append(test.getKey()).append(": ")
                  .append(test.getValue() ? "PASS" : "FAIL").append("\n");
            }
            
            for (Map.Entry<String, Double> metric : metrics.entrySet()) {
                sb.append("  ").append(metric.getKey()).append(": ")
                  .append(String.format("%.2f", metric.getValue())).append("\n");
            }
            
            return sb.toString();
        }
    }
}
