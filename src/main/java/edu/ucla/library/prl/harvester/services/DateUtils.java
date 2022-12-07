
package edu.ucla.library.prl.harvester.services;

import static info.freelibrary.util.Constants.EMPTY;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import info.freelibrary.util.StringUtils;

/**
 * A utility class for the date processing logic applied to harvested records.
 */
final class DateUtils {

    /**
     * A regex that matches one or more leading alphabetical characters or spaces.
     */
    private static final String LEADING_ALPHA_PLUS_SPACE = "^[a-zA-Z ]+";

    /**
     * A regex that matches various renderings of "before common era".
     */
    private static final String ERA_SUFFIX_BCE = "(?:BCE?)|(?:B\\.C\\.(?:E\\.))";

    /**
     * A regex that matches various renderings of "common era".
     */
    private static final String ERA_SUFFIX_CE = "(?:AD)|(?:A\\.D\\.)|(?:CE)|(?:C\\.E\\.)";

    /**
     * A regex that matches either BCE or CE.
     */
    private static final String ERA_SUFFIX = StringUtils.format("(?:{}|{})", ERA_SUFFIX_BCE, ERA_SUFFIX_CE);

    /**
     * A regex that matches either a 1- or 2-digit rendering of a year.
     */
    private static final String YEAR_1_OR_2_DIGIT = "[1-9]\\d{0,1}";

    /**
     * A regex that matches either a 3- or 4-digit rendering of a year.
     */
    private static final String YEAR_2_OR_3_DIGIT = "[1-9]\\d{2,3}";

    /**
     * A regex that matches a year with optional era suffix.
     * <p>
     * Number of capturing groups: 4.
     */
    private static final String YEAR = StringUtils.format("(?:(?:({}) ({}))|(?:({})(?: ({}))?))", YEAR_1_OR_2_DIGIT,
            ERA_SUFFIX, YEAR_2_OR_3_DIGIT, ERA_SUFFIX);

    /**
     * A regex that matches common abbreviations of month names.
     */
    private static final String MONTH_ABBREV =
            "(?:(?:Jan)|(?:Feb)|(?:Mar)|(?:Apr)|(?:May)|(?:Jun)|(?:Jul)|(?:Aug)|(?:Sep)|(?:Oct)|(?:Nov)|(?:Dec))";

    /**
     * A regex that matches the month ordinals (i.e., from 01 to 12).
     */
    private static final String MONTH_MM = "(?:(?:0[1-9])|(?:1[0-2]))";

    /**
     * A reges that matches something like 2005-07 or 2005/07 (both interpreted as July 2005).
     * <p>
     * Number of capturing groups: 4 (all nested inside {@link YEAR}).
     */
    private static final String YEAR_MM = StringUtils.format("{}(?:(?:[-/]{})|[-*?])?(?=\\D|$)", YEAR, MONTH_MM);

    /**
     * A regex that matches day-of-month ordinals (i.e., from 01 to 31).
     */
    private static final String DAY_DD = "(?:(?:0[1-9])|(?:[1-2]\\d)|(?:3[0-1]))";

    /**
     * A regex that matches a non-standard local datetime format.
     */
    private static final String LOCAL_TIME = "\\d{1,2}[.:]\\d{2}(?:[apAP]\\.?[mM]\\.?)?";

    /**
     * A regex that matches the non-standard yyyy-dd-mm date format.
     * <p>
     * Number of capturing groups: 1.
     */
    private static final String YYYY_DD_MM = "([12]\\d{3})-[0123]\\d-[01]\\d";

    /**
     * A regex that matches the non-standard dd-mon-yy date format.
     * <p>
     * Number of capturing groups: 1.
     */
    private static final String DD_MON_YEAR = StringUtils.format("(?:{}-)?{}-(\\d{2})", DAY_DD, MONTH_ABBREV);

    /**
     * A regex that matches a range of years.
     * <p>
     * Number of capturing groups: 8 (4 for each {@link YEAR} nested inside each {@link YEAR_MM}).
     */
    private static final String YEAR_RANGE = StringUtils.format("{}\\s*[-/]\\s*{}", YEAR_MM, YEAR_MM);

    /**
     * A regex that matches the non-standard "dd-mon-yyyy. time" format.
     * <p>
     * Number of capturing groups: 1.
     */
    private static final String DD_MON_YEAR_TIME =
            StringUtils.format("{}\\s+{}\\s+([12]\\d{3})(?:\\.\\s+{})?", DAY_DD, MONTH_ABBREV, LOCAL_TIME);

    /**
     * A regex that matches common renderings of century names.
     * <p>
     * Number of capturing groups: 1.
     */
    private static final String CENTURY = "(\\d+)(?:(?:st)|(?:nd)|(?:rd)|(?:th))\\s+[cC](?:entury)?";

    /**
     * A regex that matches a century name with optional era suffix.
     * <p>
     * Number of capturing groups: 2 (including 1 nested inside {@link CENTURY}).
     */
    private static final String CENTURY_WITH_ERA_SUFFIX = StringUtils.format("{}(?:\\s+({}))?", CENTURY, ERA_SUFFIX);

    /**
     * A regex that matches a year denoted to have an uncertain "one's place" value.
     * <p>
     * Number of capturing groups: 1.
     */
    private static final String YEAR_UNCERTAIN = "([1-9]\\d{2}\\d?)[-*?]";

    /** Patterns associated with those regexes above that should be matched against. */

    private static final Pattern P_ERA_SUFFIX_BCE = Pattern.compile(ERA_SUFFIX_BCE);

    /**
     * Number of capturing groups: 1.
     */
    private static final Pattern P_YYYY_DD_MM = Pattern.compile(YYYY_DD_MM);

    /**
     * Number of capturing groups: 4.
     */
    private static final Pattern P_YEAR = Pattern.compile(YEAR);

    /**
     * Number of capturing groups: 8.
     */
    private static final Pattern P_YEAR_RANGE = Pattern.compile(YEAR_RANGE);

    /**
     * Number of capturing groups: 1.
     */
    private static final Pattern P_DD_MON_YEAR = Pattern.compile(DD_MON_YEAR);

    /**
     * Number of capturing groups: 1.
     */
    private static final Pattern P_DD_MON_YEAR_TIME = Pattern.compile(DD_MON_YEAR_TIME);

    /**
     * Number of capturing groups: 2.
     */
    private static final Pattern P_CENTURY_WITH_ERA_SUFFIX = Pattern.compile(CENTURY_WITH_ERA_SUFFIX);

    /**
     * Number of capturing groups: 1.
     */
    private static final Pattern P_YEAR_UNCERTAIN = Pattern.compile(YEAR_UNCERTAIN);

    /**
     * Private constructor for utility class to prohibit instantiation.
     */
    private DateUtils() {
    }

    /**
     * @param aDates A list of date strings
     * @return The decades represented by them, in ascending order
     */
    static List<Integer> getDecadesAscending(final List<String> aDates) {
        // First, get the years represented by the given dates
        final Stream<Integer> years = aDates.parallelStream().map(DateUtils::getYears).flatMap(Set::stream);

        // Then, transform each year into the decade it belongs to; sort and de-dup
        return years.map(DateUtils::yearToDecade).sorted().distinct().toList();
    }

    /**
     * @param aYear A year
     * @return The decade that it belongs to
     */
    private static int yearToDecade(final Integer aYear) {
        return Math.floorDiv(aYear, 10) * 10;
    }

    /**
     * @param aDate A date string
     * @return The years represented by it
     */
    @SuppressWarnings("PMD.EmptyCatchBlock")
    private static Set<Integer> getYears(final String aDate) {
        final List<String> dates = List.of(aDate, aDate.replaceAll(LEADING_ALPHA_PLUS_SPACE, EMPTY));

        for (final String date : dates) {
            try {
                return Set.of(LocalDate.parse(date).getYear());
            } catch (final DateTimeParseException details) {
                // Try next version
            }
        }

        // If neither the raw or stripped string are parse-able, use the more complicated patterns
        return getYearsNonStandard(aDate);
    }

    /**
     * Gets the years represented by the given non-standard formatted date.
     *
     * @param aDate A raw date string
     * @return The years represented
     */
    private static Set<Integer> getYearsNonStandard(final String aDate) {
        final LinkedHashMap<Pattern, Function<MatchResult, Set<Integer>>> patternsAndMappers = new LinkedHashMap<>();

        // Take caution when attempting to re-order these elements; this has been known to make it not work as intended
        // (the later patterns tend to be more "catch-all")
        patternsAndMappers.put(P_YYYY_DD_MM, DateUtils::matchToUnsignedYear);
        patternsAndMappers.put(P_CENTURY_WITH_ERA_SUFFIX, DateUtils::matchToCenturyYears);
        patternsAndMappers.put(P_YEAR_RANGE, DateUtils::matchToYearRange);
        patternsAndMappers.put(P_DD_MON_YEAR_TIME, DateUtils::matchToUnsignedYear);
        patternsAndMappers.put(P_YEAR_UNCERTAIN, DateUtils::matchToResolvedUnknownOnes);
        patternsAndMappers.put(P_YEAR, DateUtils::matchToSignedYear);
        patternsAndMappers.put(P_DD_MON_YEAR, DateUtils::matchToImpliedFullRecentYear);

        for (final var entry : patternsAndMappers.entrySet()) {
            final Matcher matcher = entry.getKey().matcher(aDate);
            final Function<MatchResult, Set<Integer>> matchToYears = entry.getValue();
            final Set<Integer> years = new HashSet<>();

            // Each date string may contain multiple dates...
            while (matcher.find()) {
                years.addAll(matchToYears.apply(matcher.toMatchResult()));
            }

            // ...but they must all match the same pattern (simplifying assumption).
            if (!years.isEmpty()) {
                return years;
            }
        }

        // No matches found
        return Set.of();
    }

    /**
     * @param aMatchResult A match against a pattern with one capturing group: a common era year
     * @return A set containing a single year
     */
    private static Set<Integer> matchToUnsignedYear(final MatchResult aMatchResult) {
        return Set.of(Integer.parseInt(aMatchResult.group(1)));
    }

    /**
     * @param aMatchResult A match against a pattern with two capturing groups: a century number (required) and an era
     *        suffix (optional)
     * @return A set containing the years spanning the century
     */
    private static Set<Integer> matchToCenturyYears(final MatchResult aMatchResult) {
        final int centuryNumber = Integer.parseInt(aMatchResult.group(1));
        final String eraSuffix = aMatchResult.group(2);

        return getCenturyYears(centuryNumber, eraSuffix);
    }

    /**
     * @param aMatchResult A match against a pattern with eight capturing groups, of which only four may be non-empty: a
     *        start year number (required) and its era suffix (optional), and an end year number (required) and its era
     *        suffix (optional)
     * @return A set containing the years spanning the two given years
     */
    private static Set<Integer> matchToYearRange(final MatchResult aMatchResult) {
        final int number1 =
                Integer.parseInt(aMatchResult.group(1) != null ? aMatchResult.group(1) : aMatchResult.group(3));
        final int number2 =
                Integer.parseInt(aMatchResult.group(5) != null ? aMatchResult.group(5) : aMatchResult.group(7));
        String suffix1 = aMatchResult.group(2) != null ? aMatchResult.group(2) : aMatchResult.group(4);
        final String suffix2 = aMatchResult.group(6) != null ? aMatchResult.group(6) : aMatchResult.group(8);

        // If there's only one suffix at the end, apply to both years
        if (suffix1 == null && suffix2 != null) {
            suffix1 = suffix2;
        }

        return getYearRange(getSignedYear(number1, suffix1), getSignedYear(number2, suffix2));
    }

    /**
     * @param aMatchResult A match with one capturing group: a year whose one's place value is unknown or uncertain
     * @return A set of a single year with the one's place resolved
     */
    private static Set<Integer> matchToResolvedUnknownOnes(final MatchResult aMatchResult) {
        return Set.of(resolveUnknownOnes(Integer.parseInt(aMatchResult.group(1))));
    }

    /**
     * @param aMatchResult A match against a pattern with four capturing groups, of which only two may be non-empty: a
     *        year number (required) and an era suffix (optional)
     * @return A set of a single year with a sign to match the era suffix (if any)
     */
    private static Set<Integer> matchToSignedYear(final MatchResult aMatchResult) {
        final int yearNumber =
                Integer.parseInt(aMatchResult.group(1) != null ? aMatchResult.group(1) : aMatchResult.group(3));
        final String eraSuffix = aMatchResult.group(2) != null ? aMatchResult.group(2) : aMatchResult.group(4);

        return Set.of(getSignedYear(yearNumber, eraSuffix));
    }

    /**
     * @param aMatchResult A match against a pattern with one capturing group: a two-digit abbreviation of a recent year
     * @return A set of a single year in its unabbreviated form
     */
    private static Set<Integer> matchToImpliedFullRecentYear(final MatchResult aMatchResult) {
        return Set.of(getImpliedFullRecentYear(Integer.parseInt(aMatchResult.group(1))));
    }

    /**
     * @param aCenturyNumber A century number
     * @param anEraSuffix An era suffix
     * @return The set of years represented
     */
    private static Set<Integer> getCenturyYears(final int aCenturyNumber, final String anEraSuffix) {
        final int yearPrefix;
        final int startYear;
        final int endYear;

        if (anEraSuffix != null && P_ERA_SUFFIX_BCE.matcher(anEraSuffix).matches()) {
            // E.g. -200 is a 2nd century BCE year (is prefixed with -1 * 2 = -2)
            yearPrefix = -1 * aCenturyNumber;
        } else {
            // E.g. 1970 is a 20th century year (is prefixed with 20 - 1 = 19)
            yearPrefix = aCenturyNumber - 1;
        }

        startYear = yearPrefix * 100;
        endYear = startYear + 99;

        return getYearRange(startYear, endYear);
    }

    /**
     * @param aYear A year
     * @param anEraSuffix An era suffix
     * @return The year as a signed integer
     */
    private static int getSignedYear(final int aYear, final String anEraSuffix) {
        if (anEraSuffix != null && P_ERA_SUFFIX_BCE.matcher(anEraSuffix).matches()) {
            return -1 * aYear;
        } else {
            return aYear;
        }
    }

    /**
     * @param aStartYear The first year in a range
     * @param anEndYear The last year in a range
     * @return A set of the start and end years, and all the years in-between
     */
    private static Set<Integer> getYearRange(final int aStartYear, final int anEndYear) {
        return IntStream.rangeClosed(aStartYear, anEndYear).boxed().collect(Collectors.toSet());
    }

    /**
     * @param anAmbiguousYear Either a year prefix (with the one's place value omitted), or a four-digit year with
     *        uncertain one's place value
     * @return The ambiguous year resolved to something concrete (somewhat arbitrarily due to the degenerate logic of
     *         decade determination, e.g. 1960 and 1969 belong to the same decade)
     */
    @SuppressWarnings("PMD.AvoidLiteralsInIfCondition")
    private static int resolveUnknownOnes(final Integer anAmbiguousYear) {
        if (anAmbiguousYear < 1_000) {
            return anAmbiguousYear * 10;
        } else {
            return anAmbiguousYear;
        }
    }

    /**
     * Gets the year implied by its two-digit abbreviation.
     * <p>
     * For example, "76" implies 1976 (at least, until the year 2076 arrives), and "10" implies 2010 (for now).
     *
     * @param aTwoDigitAbbreviation The two-digit abbreviation of a year
     * @return The four-digit year
     */
    private static int getImpliedFullRecentYear(final Integer aTwoDigitAbbreviation) {
        final int century;

        // Assume abbreviated years refer to past years
        if (aTwoDigitAbbreviation < LocalDate.now().getYear() % 100) {
            century = 2000;
        } else {
            century = 1900;
        }

        return century + aTwoDigitAbbreviation;
    }
}
