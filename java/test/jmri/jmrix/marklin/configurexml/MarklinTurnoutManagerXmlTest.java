package jmri.jmrix.marklin.configurexml;

import jmri.util.JUnitUtil;

import org.junit.jupiter.api.*;

/**
 * MarklinTurnoutManagerXmlTest.java
 *
 * Test for the MarklinTurnoutManagerXml class
 *
 * @author   Paul Bender  Copyright (C) 2016
 */
public class MarklinTurnoutManagerXmlTest {

    @Test
    public void testCtor(){
      Assertions.assertNotNull(new MarklinTurnoutManagerXml(), "MarklinTurnoutManagerXml constructor");
    }

    @BeforeEach
    public void setUp() {
        JUnitUtil.setUp();
    }

    @AfterEach
    public void tearDown() {
        JUnitUtil.tearDown();
    }

}

