package com.mgmresorts.order.core;

import java.lang.annotation.Annotation;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.mgmresorts.common.errors.ErrorManager.IError;

public class InjectionContext {
    private static final InjectionContext CONTEXT = new InjectionContext();
    private static InjectionContext mock;

    /*
     * This is for mock testing only
     */
    public static void setMock(InjectionContext mock) {
        InjectionContext.mock = mock;
    }

    public static InjectionContext get() {
        return mock != null ? mock : CONTEXT;
    }

    private final Injector injector;

    private InjectionContext() {
        injector = Guice.createInjector(new Module());
        injector.getInstance(IError.class);
    }

    public <T> T instanceOf(Class<T> clazz) {
        return injector.getInstance(clazz);
    }

    public <T> T instanceOf(Class<T> clazz, Class<? extends Annotation> annotation) {
        return injector.getInstance(Key.get(clazz, annotation));
    }

    public void destroy() {
        System.exit(-1);
    }

}
