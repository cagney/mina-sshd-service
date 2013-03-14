package org.apache.sshd.common;

/**
 * A named class.  Perhaps this should be an interface with accompanying abstract implementation.
 */
abstract public class Name {

    protected final String name;

    protected Name(String name) {
        this.name = name;
    }

    public final String getName() {
        return name;
    }
}
