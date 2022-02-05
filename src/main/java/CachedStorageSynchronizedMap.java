import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class CachedStorageSynchronizedMap implements IStorage {
  private final Map<String, String> cache;
  private final IStorage storage;


  public CachedStorageSynchronizedMap(IStorage storage, int maxSize) {
    this.storage = storage;

    // Collections.synchronizedMap is not so good for frequent reading because it will lock for all reading/writing/operations
    // the benefits of using a synchronizedMap are:
    // - it is easy to use
    // - low memory footprint
    // - we can control/share the mutex that is used
    cache = Collections.synchronizedMap(new LinkedHashMap<>() {
      @Override
      protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
        return size() > maxSize;
      }
    });

  }

  @Override
  public void put(String key, String value) {
    // keep updated the cached value
    cache.replace(key, value);
    synchronized (cache) {
      storage.put(key, value);
    }
  }

  @Override
  public String get(String key) {
    // computeIfAbsent is locked with the cache instance mutex
    return cache.computeIfAbsent(key, k -> storage.get(key));
  }

  @Override
  public String toString() {
    return cache.toString();
  }
}

