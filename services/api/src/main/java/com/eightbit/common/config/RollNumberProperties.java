package com.eightbit.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Config-driven roll-number scheme so the institute's exact format can be fixed
 * without a redeploy (build doc section 6).
 */
@ConfigurationProperties(prefix = "rollnumber")
public class RollNumberProperties {

    private String pattern;
    private Map<String, String> programMap = new LinkedHashMap<>();

    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }
    public Map<String, String> getProgramMap() { return programMap; }
    public void setProgramMap(Map<String, String> programMap) { this.programMap = programMap; }
}
