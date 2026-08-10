package com.ftk.tpip.shared;

public record SemanticVersion(int major, int minor, int patch) {

    public SemanticVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Version numbers must be non-negative");
        }
    }

    public static SemanticVersion parse(String value) {
        String[] segments = value.split("\\.", -1);
        if (segments.length != 3) {
            throw new IllegalArgumentException("Semantic version must use major.minor.patch");
        }
        try {
            return new SemanticVersion(
                    Integer.parseInt(segments[0]),
                    Integer.parseInt(segments[1]),
                    Integer.parseInt(segments[2]));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid semantic version: " + value, exception);
        }
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
