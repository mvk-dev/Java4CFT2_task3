package task3;

import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;

public class CachedResult {
    @Getter
    @Setter
    private long expired;
    @Getter
    private final Object result;
    private final Object[] callParameters;

    public CachedResult(Object result, Object[] callParameters, long expired) {
        this.expired = expired;
        this.result = result;
        this.callParameters = callParameters;
    }

    public Object[] getCallParameters() {
        return callParameters == null ? null : Arrays.copyOf(callParameters, callParameters.length);
    }

    @Override
    public String toString() {
        return "CachedResult{" +
                "expired=" + expired +
                ", result=" + result +
                ", callParameters=" + Arrays.toString(callParameters) +
                '}';
    }
}
