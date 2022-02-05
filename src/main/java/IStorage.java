import net.jcip.annotations.NotThreadSafe;

@NotThreadSafe
interface IStorage {
   void put(String key, String value);
   String get(String key);
}