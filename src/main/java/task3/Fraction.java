package task3;

import java.util.concurrent.atomic.AtomicInteger;

public class Fraction implements Fractionable {
    private final AtomicInteger originalMethodCallCounter = new AtomicInteger(0);

    private int num;
    private int denum;

    public Fraction(int num, int denum) {
        this.num = num;
        this.denum = denum;
    }

    public int getOriginalMethodCallCounter() {
        return originalMethodCallCounter.get();
    }

    @Cache(lifetime = 300)
    @Override
    public double doubleValue() {
        System.out.println("doubleValue");
        originalMethodCallCounter.getAndIncrement();
        return (double) num / denum;
    }

    @Cache(lifetime = 100)
    @Override
    public int intValue() {
        System.out.println("intValue");
        originalMethodCallCounter.getAndIncrement();
        return num;
    }

    @Mutator
    @Override
    public void setNum(int num) {
        this.num = num;
    }


    @Mutator
    @Override
    public void setDenum(int denum) {
        this.denum = denum;
    }
}
