package com.ai.myutils.observable;

import java.util.*;
import java.util.function.Consumer;


public class ObservableV2 {
    public Map<String, Set<Consumer>> _observers;

    public ObservableV2() {
        this._observers = new HashMap<>();
    }

    public <T> void once(String name, Consumer<T> f) {
        this.on(name, new Consumer<T>() {
            public void accept(T args) {
                ObservableV2.this.off(name, this);
                f.accept(args);
            }
        });
    }
    public <T, U> void once(String name, BinaryConsumer<T, U> f) {
        this.on(name, new BinaryConsumer<T, U>() {
            @Override
            public void accept(T t, U u) {
                ObservableV2.this.off(name, this);
                f.accept(t, u);
            }
        });
    }

    public <T> void on(String name, Consumer<T> listener) {
        _observers.computeIfAbsent(name, k -> new LinkedHashSet<>()).add(listener);
    }

    public <T, U> void on(String name, BinaryConsumer<T, U> listener) {
        _observers.computeIfAbsent(name, k -> new LinkedHashSet<>()).add(listener);
    }

    public <T, U, V> void on(String name, TriConsumer<T, U, V> listener) {
        _observers.computeIfAbsent(name, k -> new LinkedHashSet<>()).add(listener);
    }

    public <T, U, V, W> void on(String name, QuadConsumer<T, U, V, W> listener) {
        _observers.computeIfAbsent(name, k -> new LinkedHashSet<>()).add(listener);
    }

    public <T> void emit(String name, T arg) {
        Set<Consumer> eventObservers = _observers.getOrDefault(name, new LinkedHashSet<>());
        for (Consumer listener : new LinkedHashSet<>(eventObservers)) {
            ((Consumer<T>) listener).accept(arg);
        }
    }

    public <T, U> void emit(String name, T arg, U arg2) {
        Set<Consumer> eventObservers = _observers.getOrDefault(name, new LinkedHashSet<>());
        for (Consumer listener : new LinkedHashSet<>(eventObservers)) {
            ((BinaryConsumer<T, U>) listener).accept(arg, arg2);
        }
    }

    public <T, U, V> void emit(String name, T arg, U arg2, V arg3) {
        Set<Consumer> eventObservers = _observers.getOrDefault(name, new LinkedHashSet<>());
        for (Consumer listener : new LinkedHashSet<>(eventObservers)) {
            ((TriConsumer<T, U, V>) listener).accept(arg, arg2, arg3);
        }
    }

    public <T, U, V, W> void emit(String name, T arg, U arg2, V arg3, W arg4) {
        Set<Consumer> eventObservers = _observers.getOrDefault(name, new LinkedHashSet<>());
        for (Consumer listener : new LinkedHashSet<>(eventObservers)) {
            ((QuadConsumer<T, U, V, W>) listener).accept(arg, arg2, arg3, arg4);
        }
    }

    public void off(String name, Consumer listener) {
        Set<Consumer> eventObservers = _observers.get(name);
        if (eventObservers != null) {
            eventObservers.remove(listener);
            if (eventObservers.isEmpty()) {
                _observers.remove(name);
            }
        }
    }

    public void destroy() {
        this._observers.clear();
        this._observers = new HashMap<>();
    }
}
