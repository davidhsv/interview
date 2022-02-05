import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CachedStorageConcurrentHashMap implements IStorage {
  private final ConcurrentMap<String, String> cache;
  private final IStorage storage;
  private final int maxSize;
  private final AtomicInteger currentSize = new AtomicInteger(0);


  public CachedStorageConcurrentHashMap(IStorage storage, int maxSize) {
    this.storage = storage;
    this.maxSize = maxSize;

    // ConcurrentHashMap is thread-safe and doesn't use locks on readings
    // upsides:
    // - it is fast (multiple smarts mutex per operation)
    // downsides:
    // - it uses more memory than a synchronizedMap
    // - we can't control the mutex object, they are private inside the ConcurrentHashMap
    // - size and isEmpty are only estimates
    cache = new ConcurrentHashMap<>();

  }

  @Override
  public void put(String key, String value) {
    // keep the cache updated - only updates if exists in the cache
    cache.replace(key, value);
    synchronized (storage) {
      storage.put(key, value);
    }
  }

  @Override
  public String get(String key) {
    // we want to get the value from the cache if it exists
    // and if multiple threads are trying to get the same key
    // we want to ensure that only one thread will reach the slow storage

    // also, get is thread safe and doesn't use locks in ConcurrentHashMap
    synchronized (cache) {
      String cachedValue = cache.get(key);
      if (cachedValue != null) {
        return cachedValue;
      } else {
        String result = cache.computeIfAbsent(key, k -> {
          currentSize.incrementAndGet();
          synchronized (storage) {
            return storage.get(key);
          }
        });

        // if the cache is full, we need to remove one random entry
        if (currentSize.get() > maxSize) {
          currentSize.decrementAndGet();
          cache.remove(cache.keySet().stream().filter(k -> !k.equals(key)).findFirst().get());
        }

        return result;
      }
    }

  }

  @Override
  public String toString() {
    return cache.toString();
  }
}

