package task3;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class MyCache implements InvocationHandler {
    private final Object target;
    private final Map<State, Map<Method, Set<CachedResult>>> cache = new ConcurrentHashMap<>();
    private final Map<Method, Set<Annotation>> methodsAnnotations = new HashMap<>();
    private final Map<Method, Long> methodsCacheLifetime = new HashMap<>();
    private volatile State currentState;

    private final ReadWriteLock stateLock = new ReentrantReadWriteLock();
    private final ReadWriteLock writeToCacheLock = new ReentrantReadWriteLock();


    public void printCache() {
        for (State state : cache.keySet()) {
            System.out.println(state);
            for (Method method : cache.get(state).keySet()) {
                System.out.println("    " + method.getName());
                for (CachedResult cachedResult : cache.get(state).get(method)) {
                    System.out.println("     - " + cachedResult);
                }
            }
        }
    }

    public MyCache(Object object) {
        this.target = object;
        mapMethodAnnotations(object);
        mapMethodCacheLifetime();
        currentState = new State(target);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object result;
        if (isMethodAnnotated(method, Cache.class)) {
            CachedResult cachedResult;
            cachedResult = getResultFromCache(method, args);

            if (cachedResult == null) {
                try {
                    // Между вызовом метода и записью его результата в кэш не должно меняться состояние
                    // (вызываться метод-мутатор)
                    stateLock.readLock().lock();
                    result = method.invoke(target, args);
                    putResultToCache(method, args, result);
                } finally {
                    stateLock.readLock().unlock();
                }
            } else {
                return cachedResult.getResult();
            }
        } else if (isMethodAnnotated(method, Mutator.class)) {
            try {
                // Блокировка для исключения разрыва между изменением значений полей и сменой состояния
                // Для избежания записи в кэш результата по состоянию, предшествующему вызову мутатора
                stateLock.writeLock().lock();
                result = method.invoke(target, args);
                changeState();
                return result;
            } finally {
                stateLock.writeLock().unlock();
            }
        } else
            result = method.invoke(target, args);

        return result;
    }

    /**
     * Меняет текущее состояние кэшированного объекта
     */
    private void changeState() {
        State newState = new State(target);
        if (newState.equals(currentState))
            return;

        for (State state : cache.keySet()) {
            if (newState.equals(state)) {
                currentState = state;
                return;
            }
        }
        currentState = newState;
    }

    /**
     * Метод помещает результат в кэш, предварительно накладывая блокировку на запись
     */
    private void putResultToCache(Method method, Object[] args, Object result) {
        try {
            writeToCacheLock.writeLock().lock();
            Method targetMethod = getTargetMethod(method);

            // Если другой поток успел записать тот же результат в кэш,
            // то не нужно записывать повторно
            if (getResultFromCache(targetMethod, args) != null) {
                return;
            }

            long expired = System.currentTimeMillis() + methodsCacheLifetime.get(targetMethod);

            if (cache.containsKey(currentState)) {
                Map<Method, Set<CachedResult>> map = cache.get(currentState);
                if (map.containsKey(targetMethod)) {
                    map.get(targetMethod).add(new CachedResult(result, args, expired));
                } else {
                    Set<CachedResult> set = new CopyOnWriteArraySet<>();
                    set.add(new CachedResult(result, args, expired));
                    map.put(targetMethod, set);
                }
            } else {
                Map<Method, Set<CachedResult>> map = new ConcurrentHashMap<>();
                Set<CachedResult> set = new CopyOnWriteArraySet<>();
                set.add(new CachedResult(result, args, expired));
                map.put(targetMethod, set);
                cache.put(currentState, map);
            }
        } finally {
            writeToCacheLock.writeLock().unlock();
        }
    }

    /**
     * Получение результата их кэша.
     * Накладывает разделяемую блокировку для предотвращения изменения текущего состояния currentState
     * в момент чтения из кэша
     *
     * @return кэшированное значение для указанного метода и списка аргументов
     */
    private CachedResult getResultFromCache(Method method, Object[] args) {
        try {
            stateLock.readLock().lock();

            if (!cache.containsKey(currentState))
                return null;

            Method targetMethod = getTargetMethod(method);
            if (!cache.get(currentState).containsKey(targetMethod))
                return null;

            CachedResult result = null;
            Set<CachedResult> cachedList = cache.get(currentState).get(targetMethod);
            long timestamp = System.currentTimeMillis();
            for (CachedResult cachedResult : cachedList) {
                if (Arrays.equals(cachedResult.getCallParameters(), args)) {
                    result = cachedResult;
                    result.setExpired(timestamp + methodsCacheLifetime.get(targetMethod));
                    break;
                }
            }

            return result;
        } finally {
            stateLock.readLock().unlock();
        }
    }

    /**
     * Подсчитывает долю устаревших записей CacheResult в кэше (expired < текущего момента)
     *
     * @return доля устаревших записей от 0 (нет устаревших) до 1 (все устарели)
     */
    public double getCacheExpireRatio() {
        try {
            writeToCacheLock.readLock().lock();
            final long timestamp = System.currentTimeMillis();
            int total = 0;
            int expired = 0;

            for (State state : cache.keySet()) {
                for (Method method : cache.get(state).keySet()) {
                    total += cache.get(state).get(method).size();

                    for (CachedResult cachedResult : cache.get(state).get(method)) {
                        if (cachedResult.getExpired() < timestamp)
                            expired++;
                    }
                }
            }

            return total == 0 ? 0 : (double) expired / total;
        } finally {
            writeToCacheLock.readLock().unlock();
        }
    }

    /**
     * Очищает кэш от устаревших записей.
     * Блокирует другие потоки от изменения на время очистки
     */
    public void clearCache() {
        try {
            writeToCacheLock.writeLock().lock();
            final long timestamp = System.currentTimeMillis();

            Iterator<State> stateIterator = cache.keySet().iterator();
            while (stateIterator.hasNext()) {
                State nextState = stateIterator.next();

                Iterator<Method> methodIterator = cache.get(nextState).keySet().iterator();
                while (methodIterator.hasNext()) {
                    Method nextMethod = methodIterator.next();
                    Set<CachedResult> cachedResults = cache.get(nextState).get(nextMethod);
                    cachedResults.removeIf(result -> result.getExpired() < timestamp);
                    if (cachedResults.isEmpty()) {
                        methodIterator.remove();
                    }
                }
                if (cache.get(nextState).isEmpty())
                    stateIterator.remove();
            }
        } finally {
            writeToCacheLock.writeLock().unlock();
        }
    }

    /**
     * Определяет, есть ли на методе аннотации, относящиеся к управлению кэшем
     *
     * @return {@code true} если такая аннотация установлена
     */
    private boolean isMethodAnnotated(Method method, Class<? extends Annotation> annotationClass) {
        Method targetMethod = getTargetMethod(method);

        return methodsAnnotations.containsKey(targetMethod)
                && methodsAnnotations.get(targetMethod)
                .stream()
                .anyMatch(a -> a.annotationType().getName().equals(annotationClass.getName()));
    }

    /**
     * Заполняет Map, где каждому методу соответствует множество установленных на нём аннотаций для управления кэшем
     */
    private void mapMethodAnnotations(Object object) {
        Method[] methods = object.getClass().getMethods();
        Arrays.stream(methods)
                .forEach(m -> {
                            Set<Annotation> set = getAnnotationsByMethod(m, Cache.class, Mutator.class);
                            if (!set.isEmpty()) {
                                methodsAnnotations.put(m, set);
                            }
                        }
                );
    }

    /**
     * Возвращает Set установленных на методе аннотаций указанных типов
     */
    @SafeVarargs
    private Set<Annotation> getAnnotationsByMethod(Method method, Class<? extends Annotation>... types) {
        return Arrays.stream(method.getAnnotations())
                .filter(a -> Arrays.stream(types).anyMatch(c -> c.getName().equals(a.annotationType().getName())))
                .collect(Collectors.toSet());
    }

    /**
     * Для каждого метода с установленной аннотацией Cache заполняет время жизни кэша для данного метода
     */
    private void mapMethodCacheLifetime() {
        for (Method method : methodsAnnotations.keySet()) {
            long lifetime = getCacheLifetime(method);
            if (lifetime > 0) {
                methodsCacheLifetime.put(method, lifetime);
            }
        }
    }

    /**
     * Возвращает время жизни кэша для данного метода
     *
     * @return значение value аннотации Cache на методе
     */
    private long getCacheLifetime(Method method) {
        for (Annotation annotation : methodsAnnotations.get(method)) {
            if (annotation.annotationType() == Cache.class) {
                return ((Cache) annotation).lifetime();
            }
        }
        return 0;
    }

    /**
     * Возвращает ссылку на одноименный метод из проксируемого класса
     *
     * @return ссылка на метод из оригинального класса {@code target}
     */
    private Method getTargetMethod(Method method) {
        try {
            return target.getClass().getMethod(method.getName(), method.getParameterTypes());
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
