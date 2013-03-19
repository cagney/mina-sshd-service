package org.apache.sshd.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Map from a named object to that object.
 *
 * Just a friendly wrapper around Map.
 *
 * Unlike NamedFactory, this does not dictate the form of the create method.
 */
public class NameMap<N extends Name> {

    private final Map<String, N> map = new LinkedHashMap<String, N>();

    public NameMap(N... names) {
        put(names);
    }

    /**
     * Locate and return the specified factory, or null if it isn't found.
     * @param name
     * @return
     */
    public N get(String name) {
        return map.get(name);
    }

    /**
     * Add the specified factories.
     */
    public NameMap<N> put(N... names) {
        for (N name : names) {
            map.put(name.getName(), name);
        }
        return this;
    }

    /**
     * Add the specified factories.
     */
    public NameMap<N> remove(N... names) {
        for (N name : names) {
            map.remove(name.getName());
        }
        return this;
    }

    /**
     * Get names as a comma separated list in the order they were added.
     */
    public String getNames() {
        StringBuilder names = new StringBuilder();
        String separator = "";
        for (Name name : map.values()) {
            names.append(separator).append(name.getName());
            separator = ",";
        }
        return names.toString();
    }
}
