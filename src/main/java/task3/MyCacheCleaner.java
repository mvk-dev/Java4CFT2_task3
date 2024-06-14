package task3;

import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Класс для очистки кэша объектов MyCache
 * Необходимость очистки определяется на основе подсчёта доли устаревших объектов CacheResult в кэше (expired < текущего момента)
 * по отношению к общему количеству записей в кэше.
 * Если устаревший результат был востребован из кэша до выполнения очистки, то время его жизни обновляется
 * с учетом значения аннотации Cache на соответствующем методе.
 * Таким образом, если значения в кэше будут регулярно переиспользоваться или доля устаревших значений не будет превышать
 * заданный порог, то чистка кэша не будет запускаться и влиять на процесс записи/чтения из него.
 * {@code cacheExpireThreshold} задаёт пороговое значение для очистки от 0 до 1 (1 = все записи в кэше устарели)
 * {@code cacheCleanInterval} задаёт интервал очистки кэша (мс)
 *
 * @see MyCache#getCacheExpireRatio()
 * @see MyCache#clearCache()
 */
public final class MyCacheCleaner {
    // Пороговое значение доли устаревших записей (от 0 до 1) в кэше для запуска очистки
    private final double cacheExpireThreshold;

    // Интервал очистки кэша в мс
    @Setter
    @Getter
    private long cacheCleanInterval;

    private final List<Object> objects = new ArrayList<>();

    public MyCacheCleaner(long cacheCleanInterval) {
        this(cacheCleanInterval, 0.3D);
    }

    public MyCacheCleaner(long cacheCleanInterval, double cacheExpireThreshold) {
        this.cacheCleanInterval = cacheCleanInterval;
        this.cacheExpireThreshold = cacheExpireThreshold;
    }

    public void add(Object object) {
        objects.add(object);
    }

    public void clean() {
        for (Object object : objects) {
            if (Proxy.isProxyClass(object.getClass())) {
                InvocationHandler handler = Proxy.getInvocationHandler(object);
                if (handler instanceof MyCache) {
                    try {
                        Method method = handler.getClass().getMethod("getCacheExpireRatio");
                        double ratio = (double) method.invoke(handler);

                        if (ratio > cacheExpireThreshold) {
                            Method cleanMethod = handler.getClass().getMethod("clearCache");
                            cleanMethod.invoke(handler);
                        }
                    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

        }
    }
}
