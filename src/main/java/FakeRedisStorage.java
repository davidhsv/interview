import net.jcip.annotations.NotThreadSafe;

@NotThreadSafe
public class FakeRedisStorage implements IStorage {

  private final boolean simulateDelay;

  public FakeRedisStorage(boolean simulateDelay) {
    this.simulateDelay = simulateDelay;
  }

  @Override
  public void put(String key, String value) {
    if (simulateDelay) {
      // Simulate 1ms delay for write operation
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {}
    }
  }

  @Override
  public String get(String key) {
    if (simulateDelay) {
      // Simulate 5ms delay for get operation
      try {
        Thread.sleep(5);
      } catch (InterruptedException e) {}
    }
    return "fake-" + key;
  }

}