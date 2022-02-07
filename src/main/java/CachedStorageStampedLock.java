import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;

public class CachedStorageStampedLock implements IStorage {

  static final int HASH_BITS = 0x7fffffff;

  private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<>();
  private final StampedLock storageStampedLock = new StampedLock();
  private final StampedLock cacheStampedLock = new StampedLock();

  private final IStorage storage;
  private final ConcurrentLinkedQueue<String> toRemove = new ConcurrentLinkedQueue<>();
  private final Semaphore semaphore;

  public CachedStorageStampedLock(IStorage storage, int maxCacheSize) {
    this.storage = storage;
    this.semaphore = new Semaphore(maxCacheSize);
  }

  @Override
  public void put(String key, String value) {
    long storageWriteLock = storageStampedLock.writeLock();
    try {
      // if we don't want to store in put, we can just replace with the above line
      //cache.replace(key, value);
      addToCache(key, value);
      storage.put(key, value);
    } finally {
      storageStampedLock.unlockWrite(storageWriteLock);
    }
  }

  @Override
  public String get(String key) {
    // try reading the cache with the optimistic approach first
    long optimisticStamp = cacheStampedLock.tryOptimisticRead();
    String cachedValue = cache.get(key);

    if (cachedValue != null) {
      if (cacheStampedLock.validate(optimisticStamp)) {
        // best scenario - cached and without locking
        return cachedValue;
      }
    }

    // pessimistic cache read
    long cacheStamp = cacheStampedLock.readLock();
    try {
      cachedValue = cache.get(key);
      if (cachedValue != null) {
        // 2nd best scenario - cached but with read locking
        return cachedValue;
      } else {
        long writeCacheStamp = cacheStampedLock.tryConvertToWriteLock(cacheStamp);
        if (writeCacheStamp != 0) {
          cacheStamp = writeCacheStamp;
        } else {
          cacheStampedLock.unlockRead(cacheStamp);
          cacheStamp = cacheStampedLock.writeLock();
        }

        // if we get here, we need to go to the storage
        long storageReadStamp = storageStampedLock.readLock();
        String storageValue;
        try {
          storageValue = storage.get(key);
        } finally {
          storageStampedLock.unlockRead(storageReadStamp);
        }

        addToCache(key, storageValue);

        return storageValue;
      }
    } finally {
      cacheStampedLock.unlock(cacheStamp);
    }
  }

  private void addToCache(String key, String value) {
    while (true) {
      if (semaphore.tryAcquire()) {
        String result = cache.put(key, value);
        if (result == null) {
          toRemove.add(key);
        } else {
          semaphore.release();
        }
        return;
      } else {
        String poll = toRemove.poll();
        if (poll == null) throw new AssertionError();
        cache.remove(poll);
        semaphore.release();
      }
    }
  }

  @Override
  public String toString() {
    return cache.toString();
  }

  // FIXME use spread to use multiple locks per key - ultimate performance improvement
  static final int spread(int h) {
    return (h ^ (h >>> 16)) & HASH_BITS;
  }
}

