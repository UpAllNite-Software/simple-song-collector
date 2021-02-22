package com.open.simplesongcollector.util;

import org.schabi.newpipe.extractor.exceptions.ParsingException;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IsoDuration
{
    private static final Pattern PATTERN =
            Pattern.compile("([-+]?)P(?:([-+]?[0-9]+)D)?" +
                            "(T(?:([-+]?[0-9]+)H)?(?:([-+]?[0-9]+)M)?(?:([-+]?[0-9]+)(?:[.,]([0-9]{0,9}))?S)?)?",
                    Pattern.CASE_INSENSITIVE);

    private static int parseFraction(CharSequence text, String parsed, int negate) throws ParsingException
    {
        // regex limits to [0-9]{0,9}
        if (parsed == null || parsed.length() == 0) {
            return 0;
        }
        try {
            parsed = (parsed + "000000000").substring(0, 9);
            return Integer.parseInt(parsed) * negate;
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new ParsingException("Text cannot be parsed to a Duration: fraction");
        }
    }


    private static long parseNumber(CharSequence text, String parsed, int multiplier, String errorText) throws ParsingException
    {
        // regex limits to [-+]?[0-9]+
        if (parsed == null) {
            return 0;
        }
        try {
            long val = Long.parseLong(parsed);
            return val * multiplier;
        } catch (NumberFormatException | ArithmeticException ex) {
            throw new ParsingException("Text cannot be parsed to a Duration: " + errorText);
        }
    }

    private final static int SECONDS_PER_DAY = 24*60*60;
    private final static int SECONDS_PER_HOUR = 24*60;
    private final static int SECONDS_PER_MINUTE = 60;

    public static long parseToSeconds(CharSequence text) throws ParsingException
    {
        Objects.requireNonNull(text, "text");
        Matcher matcher = PATTERN.matcher(text);
        if (matcher.matches()) {
            // check for letter T but no time sections
            if ("T".equals(matcher.group(3)) == false) {
                boolean negate = "-".equals(matcher.group(1));
                String dayMatch = matcher.group(2);
                String hourMatch = matcher.group(4);
                String minuteMatch = matcher.group(5);
                String secondMatch = matcher.group(6);
                String fractionMatch = matcher.group(7);
                if (dayMatch != null || hourMatch != null || minuteMatch != null || secondMatch != null) {
                    long daysAsSecs = parseNumber(text, dayMatch, SECONDS_PER_DAY, "days");
                    long hoursAsSecs = parseNumber(text, hourMatch, SECONDS_PER_HOUR, "hours");
                    long minsAsSecs = parseNumber(text, minuteMatch, SECONDS_PER_MINUTE, "minutes");
                    long seconds = parseNumber(text, secondMatch, 1, "seconds");
                    int nanos = parseFraction(text,  fractionMatch, seconds < 0 ? -1 : 1);

                    long totalSeconds =daysAsSecs + hoursAsSecs + minsAsSecs + seconds;

                    return totalSeconds;
                }
            }
        }
        throw new ParsingException("Text cannot be parsed to a Duration");
    }

}
