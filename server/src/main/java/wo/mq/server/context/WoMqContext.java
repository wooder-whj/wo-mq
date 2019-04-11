package wo.mq.server.context;

public interface WoMqContext<K, V> {
    void publish(K key);
    void put(K key);
    K get(K key, V key1);
    K getAll(V key1);
    boolean checkRegistration(K key, V key1);
    void subscribe(K key, V value);
    void unsubscribe(K key, V value);
}
