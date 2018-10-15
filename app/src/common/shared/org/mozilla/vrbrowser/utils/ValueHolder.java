package org.mozilla.vrbrowser.utils;

public class ValueHolder<T> {

    private T value;

    public ValueHolder(T aValue) {
        value = aValue;
    }

    public T getValue() {
        return value;
    }

    public void setValue(Object newValue) {
        value = (T) newValue;
    }

}
