import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CachedStoragesTest {
  private static final boolean SIMULATE_STORAGE_DELAY = false;
  private static final int CACHE_SIZE = 100;
  private static final int RUNS = 5;

  public static void main(String... args) {
    CachedStorageStampedLock stampedLock = new CachedStorageStampedLock(
        new FakeRedisStorage(SIMULATE_STORAGE_DELAY), CACHE_SIZE);
    CachedStorageConcurrentHashMap concurrentHashMap = new CachedStorageConcurrentHashMap(
        new FakeRedisStorage(SIMULATE_STORAGE_DELAY), CACHE_SIZE);
    CachedStorageSynchronizedMap synchronizedMap = new CachedStorageSynchronizedMap(
        new FakeRedisStorage(SIMULATE_STORAGE_DELAY), CACHE_SIZE);

    runTestForClass(stampedLock);
    runTestForClass(concurrentHashMap);
    runTestForClass(synchronizedMap);
  }

  private static void runTestForClass(IStorage storageToTest) {
    banner(storageToTest, true);
    IntStream.range(0, RUNS).forEach(i -> performanceTest(true, storageToTest));
    System.out.println();

    banner(storageToTest, false);
    IntStream.range(0, RUNS).forEach(i -> performanceTest(false, storageToTest));
    System.out.println();

    System.out.println();
    System.out.println();
  }

  private static void banner(IStorage storageToTest, boolean parallel) {
    System.out.println("=====================================================");
    System.out.println(storageToTest.getClass().getSimpleName());
    System.out.println("=====================================================");
    System.out.println("parallel = " + parallel);
    System.out.println("simulateStorageDelay = " + SIMULATE_STORAGE_DELAY);
    System.out.println("cacheSize = " + CACHE_SIZE);
    System.out.println("-----------------------------------------------------");
  }

  private static void performanceTest(boolean parallel, IStorage storageToTest) {
    long writeTime = System.nanoTime();
    try {
      IntStream stream = IntStream.range(100, 1_000);
      if (parallel) stream = stream.parallel();
      stream
          .mapToObj(String::valueOf)
          .forEach(i -> storageToTest.put(i, i));
    } finally {
      writeTime = System.nanoTime() - writeTime;
      System.out.printf("writeTime = %dms%n", (writeTime / 1_000_000));
    }

    var zeroToHundreds = IntStream.range(0, 100)
        .mapToObj(String::valueOf)
        .collect(Collectors.toList());
    for (String newValue : zeroToHundreds) {
      storageToTest.put(newValue, newValue);
    }

    long readTime = System.nanoTime();
    try {
      IntStream stream = IntStream.range(0, 100_000);
      if (parallel) stream = stream.parallel();
      stream
          .forEach(i -> {
            // 100 * 5ms = 500ms just to retrieve from the storage
            for (String newValue : zeroToHundreds) {
              if (storageToTest.get(newValue) == null)
                throw new AssertionError("Could not find: " + newValue);
            }
          });
    } finally {
      readTime = System.nanoTime() - readTime;
      System.out.printf("readTime = %dms%n", (readTime / 1_000_000));
      System.out.println("-----------------------------------------------------");
    }
  }
}