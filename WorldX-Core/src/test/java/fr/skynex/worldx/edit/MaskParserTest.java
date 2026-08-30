package fr.skynex.worldx.edit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MaskParserTest {

    @Test
    @DisplayName("Test null or empty mask input matches all blocks")
    public void testEmptyMask() {
        Mask nullMask = MaskParser.parse(null);
        assertNotNull(nullMask);

        Mask emptyMask = MaskParser.parse("   ");
        assertNotNull(emptyMask);
    }

    @Test
    @DisplayName("Test negation prefix parsing")
    public void testNegationMask() {
        Mask mask = MaskParser.parse("!stone");
        assertNotNull(mask);
    }
}
