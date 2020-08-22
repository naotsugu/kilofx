package com.mammb.kilofx;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionBufferTest {

    @Test
    void test000() {
        var session = new App.SessionBuffer(new StringBuffer(), 4);
        session.add("abc");
        assertThat(session.toString()).isEqualTo("abc");

        session.add("ABC\n");
        assertThat(session.toString()).isEqualTo("abcABC\n");

        session.add("123");
        assertThat(session.toString()).isEqualTo("abcABC\n123");

        session.setPosition(0);
        assertThat(session.getLines(0, 1)).isEqualTo("abcABC\n");
        assertThat(session.getLines(7, 1)).isEqualTo("123");
    }

    @Test void lineOpenPos() {
        var session = new App.SessionBuffer(new StringBuffer("ab\n\ncd"), 4);
        assertThat(session.getHeadOfLinePos(1)).isEqualTo(0);
        assertThat(session.getHeadOfLinePos(2)).isEqualTo(0);
        assertThat(session.getHeadOfLinePos(3)).isEqualTo(3);
        assertThat(session.getHeadOfLinePos(4)).isEqualTo(4);
        assertThat(session.getHeadOfLinePos(5)).isEqualTo(4);
    }

    @Test void lineClosePos() {
        var session = new App.SessionBuffer(new StringBuffer("ab\n\ncd"), 4);
        assertThat(session.getTailOfLinePos(2)).isEqualTo(2);
        assertThat(session.getTailOfLinePos(3)).isEqualTo(3);
        assertThat(session.getTailOfLinePos(4)).isEqualTo(6);
        assertThat(session.getTailOfLinePos(5)).isEqualTo(6);
    }

    @Test void isLastLine() {
        var session = new App.SessionBuffer(new StringBuffer("012"), 4);
        assertThat(session.isLastLine(2)).isEqualTo(true);

        session = new App.SessionBuffer(new StringBuffer("01\n34"), 4);
        assertThat(session.isLastLine(2)).isEqualTo(false);
        assertThat(session.isLastLine(3)).isEqualTo(true);

        session = new App.SessionBuffer(new StringBuffer("01\n34\n"), 4);
        assertThat(session.isLastLine(2)).isEqualTo(false);
        assertThat(session.isLastLine(3)).isEqualTo(false);
        assertThat(session.isLastLine(4)).isEqualTo(false);
        assertThat(session.isLastLine(5)).isEqualTo(false);
        assertThat(session.isLastLine(6)).isEqualTo(true);

        session = new App.SessionBuffer(new StringBuffer("01\n\n"), 4);
        assertThat(session.isLastLine(2)).isEqualTo(false);
        assertThat(session.isLastLine(3)).isEqualTo(false);
        assertThat(session.isLastLine(4)).isEqualTo(true);
    }

}
