/*
 * Copyright 2016 higherfrequencytrading.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.chronicle.wire;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.bytes.BytesUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class EnumSetMarshallingTest {
    private static final String FULL_SET_SERIALISED_FORM =
            "--- !!data #binary\n" +
                    "key: {\n" +
                    "  f: [\n" +
                    "    TIMED_WAITING,\n" +
                    "    WAITING,\n" +
                    "    BLOCKED,\n" +
                    "    RUNNABLE,\n" +
                    "    TERMINATED,\n" +
                    "    NEW\n" +
                    "  ]\n" +
                    "}\n";

    private static final String EMPTY_SET_SERIALISED_FORM =
            "--- !!data #binary\n" +
                    "key: {\n" +
                    "  f: [  ]\n" +
                    "}\n";

    private static final String SINGLETON_SET_SERIALISED_FORM =
            "--- !!data #binary\n" +
                    "key: {\n" +
                    "  f: [\n" +
                    "    TIMED_WAITING\n" +
                    "  ]\n" +
                    "}\n";

    private static final String regex =
            "--- !!data #binary\n" +
                    "key: \\{\n" +
                    " {2}f: \\[(|\n( {4}([A-Z_]+),\n)* {4}([A-Z_]+)\n) {2}]\n" +
                    "}\n";

    private final Pattern pattern = Pattern.compile(regex);

    @Test
    public void testFullSetSerialisedForm() {
        assertSerialisedFormMatches(FULL_SET_SERIALISED_FORM);
    }

    @Test
    public void testEmptySetSerialisedForm() {
        assertSerialisedFormMatches(EMPTY_SET_SERIALISED_FORM);
    }

    @Test
    public void testSingletonSetSerialisedForm() {
        assertSerialisedFormMatches(SINGLETON_SET_SERIALISED_FORM);
    }

    private void assertSerialisedFormMatches(String serialisedForm) {
        Matcher matcher = pattern.matcher(serialisedForm);
        assertTrue(matcher.matches());
    }

    @Test
    public void shouldMarshallEmptySet() {
        final Bytes<ByteBuffer> bytes = Bytes.elasticByteBuffer();
        final EnumSet<Thread.State> states = EnumSet.noneOf(Thread.State.class);
        final Foo written = new Foo(states);
        final Foo read = new Foo(EnumSet.allOf(Thread.State.class));

        @NotNull Wire tw = new BinaryWire(bytes);
        tw.writeDocument(false, w -> {
            w.write(() -> "key").marshallable(written);
        });

        assertThreadStates(bytes, states);
        tw.readingDocument().wire().read("key").marshallable(read);

        assertThat(read.f, is(written.f));
        bytes.release();
    }

    @Test
    public void shouldMarshallFullSet() {
        final Bytes<ByteBuffer> bytes = Bytes.elasticByteBuffer();
        final EnumSet<Thread.State> states = EnumSet.allOf(Thread.State.class);
        final Foo written = new Foo(states);
        final Foo read = new Foo(EnumSet.noneOf(Thread.State.class));

        @NotNull Wire tw = new BinaryWire(bytes);
        tw.writeDocument(false, w -> {
            w.write(() -> "key").marshallable(written);
        });

        assertThreadStates(bytes, states);
        tw.readingDocument().wire().read("key").marshallable(read);

        assertThat(read.f, is(written.f));
        bytes.release();
    }

    @Test
    public void shouldUnmarshallToContainerWithNullValue() {
        final Bytes<ByteBuffer> bytes = Bytes.elasticByteBuffer();
        final EnumSet<Thread.State> states = EnumSet.allOf(Thread.State.class);
        final Foo written = new Foo(states);
        final Foo read = new Foo(EnumSet.noneOf(Thread.State.class));
        // this forces the framework to allocate a new instance of EnumSet
        read.f = null;

        @NotNull Wire tw = new BinaryWire(bytes);
        tw.writeDocument(false, w -> {
            w.write(() -> "key").marshallable(written);
        });

        assertThreadStates(bytes, states);
        tw.readingDocument().wire().read("key").marshallable(read);

        assertThat(read.f, is(written.f));
        bytes.release();
    }

    @Test
    public void shouldMarshalSingletonSet() {
        final Bytes<ByteBuffer> bytes = Bytes.elasticByteBuffer();
        final EnumSet<Thread.State> states = EnumSet.of(Thread.State.RUNNABLE);
        final Foo written = new Foo(states);
        final Foo read = new Foo(EnumSet.allOf(Thread.State.class));

        @NotNull Wire tw = new BinaryWire(bytes);
        tw.writeDocument(false, w -> {
            w.write(() -> "key").marshallable(written);
        });

        assertThreadStates(bytes, states);
        tw.readingDocument().wire().read("key").marshallable(read);

        assertThat(read.f, is(written.f));
        bytes.release();
    }

    private void assertThreadStates(Bytes<ByteBuffer> bytes,
                                    EnumSet<Thread.State> expected) {
        String serialisedForm = Wires.fromSizePrefixedBlobs(bytes);
        // use a regular expression to check the serialised form -- we can't do
        // a byte-for-byte comparison because WireMarshallable.FieldAccess uses
        // Class.enumConstantDirectory() to get a map of values for an EnumSet,
        // and that map is unordered.
        // instead of fixing this test, one could have the FieldAccess class
        // use an intermediate map with lexicographically ordered entries, or
        // perhaps use the Enum.values() method to preserve the declaration
        // order.
        Matcher matcher = pattern.matcher(serialisedForm);
        assertTrue("serialisedForm=" + serialisedForm, matcher.matches());
        String listOfStates = matcher.group(1);
        EnumSet<Thread.State> actual = EnumSet.noneOf(Thread.State.class);
        for (String stateStr : listOfStates.split(" *,?\n *")) {
            if ("".equals(stateStr))
                continue;
            Thread.State state = Thread.State.valueOf(stateStr);
            assertFalse("duplicate state - " + state, actual.contains(state));
            actual.add(state);
        }
        assertThat(expected, is(actual));
    }

    @Test
    public void shouldAllowMultipleInstancesInObjectGraph() throws Exception {
        final Bytes<ByteBuffer> bytes = Bytes.elasticByteBuffer();
        final Container written = new Container();
        final Container read = new Container();

        @NotNull Wire tw = new BinaryWire(bytes);
        tw.writeDocument(false, w -> {
            w.write(() -> "key").marshallable(written);
        });

        tw.readingDocument().wire().read("key").marshallable(read);

        assertThat(read.f1.get(0).f, is(not(read.f2.get(0).f)));
        bytes.release();
    }

    @After
    public void checkRegisteredBytes() {
        BytesUtil.checkRegisteredBytes();
    }

    private static final class Container extends AbstractMarshallable {
        private List<Foo> f1 = new ArrayList<>(Arrays.asList(new Foo(EnumSet.allOf(Thread.State.class))));
        private List<Foo> f2 = new ArrayList<>(Arrays.asList(new Foo(EnumSet.noneOf(Thread.State.class))));
    }

    private static final class Foo extends AbstractMarshallable {
        private EnumSet<Thread.State> f = EnumSet.noneOf(Thread.State.class);

        private Foo(final EnumSet<Thread.State> membership) {
            f = membership;
        }
    }
}
