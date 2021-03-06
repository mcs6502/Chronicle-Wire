package net.openhft.chronicle.wire;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Base40LongConverterTest {
    @Test
    public void parse() {
        Base40LongConverter bic = new Base40LongConverter();
        for (String s : ",A,0,ZZ,99,ABCDEF,012345,ZZZZZZZZZZZZ,999999999999".split(",")) {
            assertEquals(s, bic.asString(bic.parse(s)));
        }
        for (long l : new long[]{Long.MIN_VALUE, Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE, Long.MAX_VALUE}) {
            assertEquals(l, bic.parse(bic.asString(l)));
        }
    }
}