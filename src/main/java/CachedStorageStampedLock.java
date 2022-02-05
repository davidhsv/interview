import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;

public class CachedStorageStampedLock implements IStorage {

  private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<>();
  private final StampedLock storageStampedLock = new StampedLock();
  private final StampedLock cacheStampedLock = new StampedLock();

  private final IStorage storage;
  private final int maxSize;
  private final AtomicInteger currentSize = new AtomicInteger(0);

  public CachedStorageStampedLock(IStorage storage, int maxSize) {
    this.storage = storage;
    this.maxSize = maxSize;
  }

  @Override
  public void put(String key, String value) {
    cache.replace(key, value);
    long storageWriteLock = storageStampedLock.writeLock();
    try {
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

    if (cacheStampedLock.validate(optimisticStamp)) {
      if (cachedValue != null) {
        // best scenario - cached and without locking
        return cachedValue;
      }
    }

    // pessimistic cache read
    long cacheStamp = cacheStampedLock.readLock();
    try {
      cachedValue = cache.get(key);
      if (cachedValue != null) {
        // 2nd best scenario - cached but with locking
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

        if (currentSize.get() == maxSize) {
          currentSize.decrementAndGet();
          cache.remove(cache.keySet().stream().filter(k -> !k.equals(key)).findFirst().get());
        }
        if (cache.put(key, storageValue) == null) {
          currentSize.incrementAndGet();
        }

        return storageValue;
      }
    } finally {
      cacheStampedLock.unlock(cacheStamp);
    }
  }

  @Override
  public String toString() {
    return cache.toString();
  }
}

