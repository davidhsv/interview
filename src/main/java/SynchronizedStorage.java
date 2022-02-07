public class SynchronizedStorage implements IStorage {
  private final IStorage storage;

  public SynchronizedStorage(IStorage storage) {
    this.storage = storage;
  }

  @Override
  public synchronized void put(String key, String value) {
    storage.put(key, value);
  }

  @Override
  public synchronized String get(String key) {
    return storage.get(key);
  }

  @Override
  public synchronized String toString() {
    return storage.toString();
  }
}