
package org.tartarus.snowball;

/**
 * Parent class of all snowball stemmers, which must implement <code>stem</code>
 */
public abstract class SnowballStemmer extends SnowballProgram {
    public abstract boolean stem();
};
