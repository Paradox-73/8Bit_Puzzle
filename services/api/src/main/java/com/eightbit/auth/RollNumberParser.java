package com.eightbit.auth;

import com.eightbit.common.config.RollNumberProperties;
import com.eightbit.common.web.ApiException;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses batch year and program from a roll number using the config-driven scheme.
 * Group 1 = program prefix, group 2 = 4-digit batch year.
 */
@Component
public class RollNumberParser {

    private final Pattern pattern;
    private final RollNumberProperties props;

    public RollNumberParser(RollNumberProperties props) {
        this.props = props;
        this.pattern = Pattern.compile(props.getPattern());
    }

    public record Parsed(int batchYear, String program) {}

    public Parsed parse(String rollNumber) {
        if (rollNumber == null) {
            throw ApiException.badRequest("INVALID_ROLL", "Roll number is required");
        }
        String roll = rollNumber.trim().toUpperCase();
        Matcher m = pattern.matcher(roll);
        if (!m.matches()) {
            throw ApiException.badRequest("INVALID_ROLL",
                    "That doesn't look like a valid IIITB roll number");
        }
        String prefix = m.group(1);
        int year = Integer.parseInt(m.group(2));
        String program = props.getProgramMap().getOrDefault(prefix, prefix);
        return new Parsed(year, program);
    }

    public String normalize(String rollNumber) {
        return rollNumber == null ? null : rollNumber.trim().toUpperCase();
    }
}
