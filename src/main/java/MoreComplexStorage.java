import java.util.*;
import java.util.concurrent.*;

public class MoreComplexStorage implements IStorage {
  private final IStorage storage;
  private final Semaphore semaphore;
  private final Map<String, String> cache;
  private final ConcurrentLinkedQueue<String> toRemove =
      new ConcurrentLinkedQueue<>();

  public MoreComplexStorage(IStorage storage, int maxSize) {
    this.storage = storage;
    this.semaphore = new Semaphore(maxSize);
    this.cache = new ConcurrentHashMap<>();
  }

  @Override
  public void put(String key, String value) {
    Objects.requireNonNull(key, "key==null");
    Objects.requireNonNull(value, "value==null");
    addToCache(key, value);
    storage.put(key, value);
  }

  private void addToCache(String key, String value) {
    while (true) {
      if (semaphore.tryAcquire()) {
        String result = cache.put(key, value);
        if (result != null) semaphore.release();
        else toRemove.add(key);
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
  public String get(String key) {
    String result = cache.get(key);
    if (result == null) {
      result = storage.get(key);
      addToCache(key, result);
    }
    return result;
  }

  @Override
  public String toString() {
    ConcurrentSkipListMap<String, String> map = new ConcurrentSkipListMap<>(
        Comparator.comparingInt(Integer::valueOf)
    );
    map.putAll(cache);
    return map.toString();
  }
}