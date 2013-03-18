package org.apache.sshd.common;

/**
 * Created with IntelliJ IDEA.
 * User: cagney
 * Date: 18/03/13
 * Time: 11:56 AM
 * To change this template use File | Settings | File Templates.
 */
abstract public class AbstractName implements Name {

    protected final String name;

    protected AbstractName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
