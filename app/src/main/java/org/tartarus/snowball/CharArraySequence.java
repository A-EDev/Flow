package org.tartarus.snowball;

/**
 * Internal class used by Snowball stemmers
 */
public class CharArraySequence implements CharSequence {
    public CharArraySequence(char[] a, int len) {
        this.a = a;
        this.len = len;
    }

    final public char charAt(int index) {
        if (index < 0 || index >= len) {
            throw new StringIndexOutOfBoundsException(index);
        }
        return a[index];
    }

    final public int length() {
        return len;
    }

    final public CharSequence subSequence(int start, int end) {
        // Not needed for how we used CharSequence.
        throw new UnsupportedOperationException();
    }

    final public String toString() {
        if (a == null) return "";
        return new String(a, 0, len);
    }

    final private char[] a;

    final private int len;
}
