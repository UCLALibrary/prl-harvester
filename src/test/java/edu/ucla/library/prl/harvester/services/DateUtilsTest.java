
package edu.ucla.library.prl.harvester.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests {@link DateUtils}.
 */
public class DateUtilsTest {

    /**
     * Tests {@link DateUtils#getDecadesAscending(List)}.
     *
     * @param aDates A list of strings contained in a record's dc:date fields
     * @param anExpectedResult The expected list of decades represented by the dates
     */
    @ParameterizedTest
    @MethodSource
    void testGetDecadesAscending(final List<String> aDates, final List<Integer> anExpectedResult) {
        assertEquals(anExpectedResult, DateUtils.getDecadesAscending(aDates), aDates.toString());
    }

    /**
     * @return The arguments for the corresponding {@link ParameterizedTest}
     */
    static Stream<Arguments> testGetDecadesAscending() {
        return Stream.of( //
                Arguments.of(List.of("[186-?]"), List.of(1860)), //
                Arguments.of(List.of("c1904"), List.of(1900)), //
                Arguments.of(List.of("[1899?]"), List.of(1890)), //
                Arguments.of(List.of("1900]"), List.of(1900)), //
                Arguments.of(List.of("1903], c1895"), List.of(1890, 1900)), //
                Arguments.of(List.of("1973-08"), List.of(1970)), //
                Arguments.of(List.of("1956-09-07"), List.of(1950)), //
                Arguments.of(List.of("ca 1904"), List.of(1900)), //
                Arguments.of(List.of("500 BC"), List.of(-500)), //
                Arguments.of(List.of("2013-01-01T08:00:00Z"), List.of(2010)), //
                Arguments.of(List.of("1922-1927"), List.of(1920)), //
                Arguments.of(List.of("1903?"), List.of(1900)), //
                Arguments.of(List.of("2004/5"), List.of(2000)), //
                Arguments.of(List.of("1959 1960"), List.of(1950, 1960)), //
                Arguments.of(List.of("13-Mar-76"), List.of(1970)), //
                Arguments.of(List.of(
                        "1972 1973 1974 1975 1976 Date notes: Digital photos created 2002. Pottery found 1972-1976"),
                        List.of(1970)),
                Arguments.of(List.of("Feb-76"), List.of(1970)), //
                Arguments.of(List.of("1300-1200 BC"),
                        IntStream.range(-130, -120 + 1).map(n -> 10 * n).boxed().toList()),
                Arguments.of(List.of("2nd C BC"), List.of(-200, -190, -180, -170, -160, -150, -140, -130, -120, -110)),
                Arguments.of(List.of("3rd C AD"), List.of(200, 210, 220, 230, 240, 250, 260, 270, 280, 290)), //
                Arguments.of(List.of("1993-03 - 1993-05"), List.of(1990)), //
                Arguments.of(List.of("4th C  AD"), List.of(300, 310, 320, 330, 340, 350, 360, 370, 380, 390)), //
                Arguments.of(List.of("2800 BC [ca.]"), List.of(-2800)), //
                Arguments.of(List.of("447-432 BC"), List.of(-450, -440)), //
                Arguments.of(List.of("1965-1969?"), List.of(1960)), //
                Arguments.of(List.of("1978-03/ 1978-10"), List.of(1970)), //
                Arguments.of(List.of("c1963"), List.of(1960)), //
                Arguments.of(List.of("pre 1993/4"), List.of(1990)), //
                Arguments.of(List.of("07 Mar 1976. 7.30pm"), List.of(1970)), //
                Arguments.of(List.of("1500 [ca.]"), List.of(1500)), //
                Arguments.of(List.of("1970s"), List.of(1970)), //
                Arguments.of(List.of("1851,  modified 1853-1854"), List.of(1850)), //
                Arguments.of(List.of("c. 470-460 BC"), List.of(-470, -460)), //
                Arguments.of(List.of("2550-2530 BC [ca.]"), List.of(-2550, -2540, -2530)), //
                Arguments.of(List.of("c.1926"), List.of(1920)), //
                Arguments.of(List.of("1980-03/1980-07"), List.of(1980)), //
                Arguments.of(List.of("12 Mar 1976. 2.00am"), List.of(1970)), //
                Arguments.of(List.of("1600-1040 BC"),
                        IntStream.range(-160, -104 + 1).map(n -> 10 * n).boxed().toList()),
                Arguments.of(List.of("1600-1046 BC"),
                        IntStream.range(-160, -105 + 1).map(n -> 10 * n).boxed().toList()),
                Arguments.of(List.of("1600 BC - 1046 BC"),
                        IntStream.range(-160, -105 + 1).map(n -> 10 * n).boxed().toList()),
                Arguments.of(List.of("1600 BC-1046 BC"),
                        IntStream.range(-160, -105 + 1).map(n -> 10 * n).boxed().toList()),
                Arguments.of(List.of("Notamonth 11 (1968)"), List.of(1960)), //
                Arguments.of(List.of("Notamonth 46 (1968)"), List.of(1960)), //
                Arguments.of(List.of("2012-29-02"), List.of(2010)), //
                Arguments.of(List.of("1750-01-01"), List.of(1750)), //
                Arguments.of(List.of("1800-31-12"), List.of(1800))); //
    }
}
