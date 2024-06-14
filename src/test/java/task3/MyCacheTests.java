package task3;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyCacheTests {
    @Test
    public void should_TakeResultFromCache_When_BackToPreviousState() {
        Fraction fraction = new Fraction(2, 10);
        Fractionable fractionable = Utils.cache(fraction);

        fractionable.doubleValue(); // вызов метода (+1)
        fractionable.doubleValue(); // из кэша
        fractionable.doubleValue(); // из кэша
        Assertions.assertEquals(1, fraction.getOriginalMethodCallCounter());

        fractionable.setNum(5);     // обновление состояния
        fractionable.doubleValue(); // вызов метода (+1)
        Assertions.assertEquals(2, fraction.getOriginalMethodCallCounter());

        fractionable.setNum(2);     // возврат к предыдущему состоянию
        fractionable.doubleValue(); // из кэша
        Assertions.assertEquals(2, fraction.getOriginalMethodCallCounter());

        fractionable.setDenum(100); // обновление состояния
        fractionable.doubleValue(); // вызов метода (+1)

        fractionable.setDenum(10);  // возврат к предыдущему состоянию
        fractionable.doubleValue(); // из кэша
        Assertions.assertEquals(3, fraction.getOriginalMethodCallCounter());

    }

    @Test
    public void should_DeleteOldCacheRecords_When_ResultsExpire() throws InterruptedException {
        Fraction fraction1 = new Fraction(2, 10);
        Fractionable fractionable1 = Utils.cache(fraction1);

        Fraction fraction2 = new Fraction(12, 123213);
        Fractionable fractionable2 = Utils.cache(fraction2);

        MyCacheCleaner cleaner = new MyCacheCleaner(1000);
        // Объекты для очистки
        cleaner.add(fractionable1);
        cleaner.add(fractionable2);

        // Запуск потока очистки
        startCacheCleanThread(cleaner);

        // Для простоты тест в одном потоке
        fractionable1.doubleValue();    // вызов метода (+1)
        fractionable1.doubleValue();    // из кэша
        Assertions.assertEquals(1, fraction1.getOriginalMethodCallCounter());

        fractionable2.doubleValue();    // вызов метода (+1)
        fractionable2.doubleValue();    // из кэша
        Assertions.assertEquals(1, fraction2.getOriginalMethodCallCounter());

        // Для времени жизни 300мс и интервале очистки в 1сек
        Thread.sleep(2000);

        // После очистки
        fractionable1.doubleValue();    // вызов метода (+1)
        Assertions.assertEquals(2, fraction1.getOriginalMethodCallCounter());

        fractionable2.doubleValue();    // вызов метода (+1)
        Assertions.assertEquals(2, fraction2.getOriginalMethodCallCounter());
    }

    @Test
    public void should_ProlongCachedResultLfe_When_RequestBeforeExpireTime() {
        Fraction fraction = new Fraction(8, 20);
        Fractionable fractionable = Utils.cache(fraction);

        MyCacheCleaner cleaner = new MyCacheCleaner(200);
        // Объекты для очистки
        cleaner.add(fractionable);

        // Запуск потока очистки
        startCacheCleanThread(cleaner);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            // Время выполнения = 1 сек
            // время жизни кэша doubleValue = 300мс
            executorService.execute(() -> {
                try {
                    for (int i = 0; i < 10; i++) {
                        Thread.sleep(100);
                        fractionable.doubleValue();
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            // время жизни кэша intValue = 100мс
            executorService.execute(() -> {
                try {
                    for (int i = 0; i < 10; i++) {
                        Thread.sleep(100);
                        fractionable.intValue();
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        // Проверяем, что в итоге было только по 1 вызову оригинального метода
        // Остальные 18 (9+9) вызовов взяли результат из кэша, т.к. его время жизни обновлялось при каждом использовании
        Assertions.assertEquals(2, fraction.getOriginalMethodCallCounter());
    }

    private void startCacheCleanThread(MyCacheCleaner cleaner) {
        Thread cleanerThread = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(cleaner.getCacheCleanInterval());
                    cleaner.clean();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        cleanerThread.setDaemon(true);
        cleanerThread.start();
    }
}
