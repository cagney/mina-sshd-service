package org.apache.sshd.common.service;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Factory template that doesn't hardwire a create() method.  Instead sub-classes can implement custom
 * create methods.
 */
abstract public class ServiceFactory {

    private final String name;

    protected ServiceFactory(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * locate and return the specified factory, or null if it isn't found.
     * @param factories
     * @param name
     * @param <S>
     * @return
     */
    public static <S extends ServiceFactory> S find(List<S> factories, String name) {
        for (S factory : factories) {
            if (factory.getName().equals(name)) {
                return factory;
            }
        }
        return null;
    }

    /**
     * Convert the varargs into a linked list.
     *
     * A hard-wired array list isn't returned as the caller may wish to manipulate it further.
     * @param factories
     * @param <S>
     * @return
     */
    public static <S> LinkedList<S> asList(S... factories) {
        return new LinkedList<S>(Arrays.asList(factories));
    }

}
