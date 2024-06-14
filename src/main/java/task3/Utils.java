package task3;

import java.lang.reflect.Proxy;

public class Utils {
    @SuppressWarnings("unchecked")
    public static <T> T cache(T object) {
        Class<T> objClass = (Class<T>) object.getClass();

        return (T) Proxy.newProxyInstance(
                objClass.getClassLoader(),
                objClass.getInterfaces(),
                new MyCache(object)
        );
    }
}
