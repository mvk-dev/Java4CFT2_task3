package task3;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Теоретически можно переделать в интерфейс с методом наподобие getState для большей гибкости
 * при работе с кэшируемыми объектами разных классов
 */
public class State {
    private final Map<Field, Object> values = new HashMap<>();

    public State(Object object) {
        saveState(object);
    }

    private void saveState(Object object) {
        if (object == null)
            return;

        Class<?> objClass = object.getClass();

        while (objClass != null) {
            Arrays.stream(objClass.getDeclaredFields())
                    .forEach(field -> {
                        field.setAccessible(true);
                        Object value;
                        try {
                            value = field.get(object);
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                        add(field, value);
                    });
            objClass = objClass.getSuperclass();
        }
    }

    private void add(Field field, Object value) {
        values.put(field, value);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;

        State state = (State) object;
        return values.equals(state.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return "State{" +
                "values=" + values +
                '}';
    }
}
