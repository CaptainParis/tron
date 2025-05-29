package Paris.optimization.lockfree;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.logging.Logger;

/**
 * Lock-free ring buffer for high-performance cross-thread event communication.
 * Uses atomic operations and memory barriers for thread-safe operations without blocking.
 */
public class LockFreeEventQueue<T> {
    
    private static final Logger logger = Logger.getLogger(LockFreeEventQueue.class.getName());
    
    // Ring buffer implementation
    private final AtomicReferenceArray<T> buffer;
    private final int capacity;
    private final int mask; // For fast modulo operation (capacity must be power of 2)
    
    // Atomic indices for lock-free operations
    private final AtomicLong writeIndex = new AtomicLong(0);
    private final AtomicLong readIndex = new AtomicLong(0);
    
    // Performance tracking
    private final AtomicLong totalEnqueued = new AtomicLong(0);
    private final AtomicLong totalDequeued = new AtomicLong(0);
    private final AtomicLong queueFullEvents = new AtomicLong(0);
    private final AtomicLong queueEmptyEvents = new AtomicLong(0);
    
    // Event types for Tron game
    public enum EventType {
        TRAIL_BLOCK_ADDED,
        COLLISION_DETECTED,
        PLAYER_ELIMINATED,
        GAME_STARTED,
        GAME_ENDED,
        PACKET_BATCH_READY,
        CLEANUP_REQUIRED
    }
    
    // Generic event wrapper
    public static class Event<T> {
        public final EventType type;
        public final T data;
        public final long timestamp;
        public final String source;
        
        public Event(EventType type, T data, String source) {
            this.type = type;
            this.data = data;
            this.timestamp = System.nanoTime();
            this.source = source;
        }
        
        @Override
        public String toString() {
            return String.format("Event{type=%s, source=%s, timestamp=%d, data=%s}", 
                               type, source, timestamp, data);
        }
    }
    
    /**
     * Create a lock-free event queue with specified capacity (must be power of 2)
     */
    public LockFreeEventQueue(int capacity) {
        if (capacity <= 0 || (capacity & (capacity - 1)) != 0) {
            throw new IllegalArgumentException("Capacity must be a positive power of 2");
        }
        
        this.capacity = capacity;
        this.mask = capacity - 1;
        this.buffer = new AtomicReferenceArray<>(capacity);
        
        logger.info("[LockFreeEventQueue] Initialized with capacity " + capacity);
    }
    
    /**
     * Enqueue an event (non-blocking, returns false if queue is full)
     */
    public boolean enqueue(T event) {
        long currentWrite = writeIndex.get();
        long currentRead = readIndex.get();
        
        // Check if queue is full
        if (currentWrite - currentRead >= capacity) {
            queueFullEvents.incrementAndGet();
            logger.fine("[LockFreeEventQueue] Queue full - dropping event: " + event);
            return false;
        }
        
        // Try to claim a write slot
        if (!writeIndex.compareAndSet(currentWrite, currentWrite + 1)) {
            // Another thread claimed this slot, try again
            return enqueue(event);
        }
        
        // Write to the claimed slot
        int index = (int) (currentWrite & mask);
        buffer.set(index, event);
        
        totalEnqueued.incrementAndGet();
        logger.finest("[LockFreeEventQueue] Enqueued event at index " + index + ": " + event);
        
        return true;
    }
    
    /**
     * Dequeue an event (non-blocking, returns null if queue is empty)
     */
    public T dequeue() {
        long currentRead = readIndex.get();
        long currentWrite = writeIndex.get();
        
        // Check if queue is empty
        if (currentRead >= currentWrite) {
            queueEmptyEvents.incrementAndGet();
            return null;
        }
        
        // Try to claim a read slot
        if (!readIndex.compareAndSet(currentRead, currentRead + 1)) {
            // Another thread claimed this slot, try again
            return dequeue();
        }
        
        // Read from the claimed slot
        int index = (int) (currentRead & mask);
        T event = buffer.get(index);
        
        // Clear the slot to prevent memory leaks
        buffer.set(index, null);
        
        totalDequeued.incrementAndGet();
        logger.finest("[LockFreeEventQueue] Dequeued event from index " + index + ": " + event);
        
        return event;
    }
    
    /**
     * Peek at the next event without removing it
     */
    public T peek() {
        long currentRead = readIndex.get();
        long currentWrite = writeIndex.get();
        
        if (currentRead >= currentWrite) {
            return null; // Queue is empty
        }
        
        int index = (int) (currentRead & mask);
        return buffer.get(index);
    }
    
    /**
     * Get current queue size (approximate due to concurrent access)
     */
    public int size() {
        long write = writeIndex.get();
        long read = readIndex.get();
        return (int) Math.max(0, write - read);
    }
    
    /**
     * Check if queue is empty
     */
    public boolean isEmpty() {
        return readIndex.get() >= writeIndex.get();
    }
    
    /**
     * Check if queue is full
     */
    public boolean isFull() {
        return writeIndex.get() - readIndex.get() >= capacity;
    }
    
    /**
     * Get queue capacity
     */
    public int getCapacity() {
        return capacity;
    }
    
    /**
     * Drain all events into a collection (useful for batch processing)
     */
    public java.util.List<T> drainAll() {
        java.util.List<T> events = new java.util.ArrayList<>();
        T event;
        while ((event = dequeue()) != null) {
            events.add(event);
        }
        return events;
    }
    
    /**
     * Drain up to maxEvents events
     */
    public java.util.List<T> drain(int maxEvents) {
        java.util.List<T> events = new java.util.ArrayList<>();
        T event;
        int count = 0;
        while (count < maxEvents && (event = dequeue()) != null) {
            events.add(event);
            count++;
        }
        return events;
    }
    
    /**
     * Clear all events from the queue
     */
    public void clear() {
        while (!isEmpty()) {
            dequeue();
        }
        logger.info("[LockFreeEventQueue] Queue cleared");
    }
    
    /**
     * Get performance statistics
     */
    public String getPerformanceStats() {
        long enqueued = totalEnqueued.get();
        long dequeued = totalDequeued.get();
        long fullEvents = queueFullEvents.get();
        long emptyEvents = queueEmptyEvents.get();
        
        return String.format(
            "LockFreeEventQueue Stats: Size=%d/%d, Enqueued=%d, Dequeued=%d, Full=%d, Empty=%d, Loss=%.2f%%",
            size(), capacity, enqueued, dequeued, fullEvents, emptyEvents,
            enqueued > 0 ? (fullEvents * 100.0 / enqueued) : 0.0
        );
    }
    
    /**
     * Reset performance counters
     */
    public void resetStats() {
        totalEnqueued.set(0);
        totalDequeued.set(0);
        queueFullEvents.set(0);
        queueEmptyEvents.set(0);
        logger.info("[LockFreeEventQueue] Performance statistics reset");
    }
    
    // Getters for individual stats
    public long getTotalEnqueued() { return totalEnqueued.get(); }
    public long getTotalDequeued() { return totalDequeued.get(); }
    public long getQueueFullEvents() { return queueFullEvents.get(); }
    public long getQueueEmptyEvents() { return queueEmptyEvents.get(); }
    
    /**
     * Get queue utilization percentage
     */
    public double getUtilization() {
        return (double) size() / capacity * 100.0;
    }
    
    /**
     * Check queue health (returns true if operating normally)
     */
    public boolean isHealthy() {
        long enqueued = totalEnqueued.get();
        long fullEvents = queueFullEvents.get();
        
        // Consider unhealthy if more than 10% of enqueue attempts fail
        if (enqueued > 100 && fullEvents > enqueued * 0.1) {
            return false;
        }
        
        // Check for potential memory leaks (read index should advance)
        return totalDequeued.get() > 0 || totalEnqueued.get() == 0;
    }
}
