//
//  AnalysisIsTriggered.java
//  This file is auto-generated by Amplitude. Run `ampli pull jetbrains` to update.
//
//  Works with versions 1.2+ of ly.iterative.itly:sdk and plugins
//  https://search.maven.org/search?q=itly
//

package snyk.analytics;

import ly.iterative.itly.Event;

import java.util.HashMap;

public class AnalysisIsTriggered extends Event {
    private static final String NAME = "Analysis Is Triggered";
    private static final String ID = "dabf569e-219c-470f-8e31-6e029723f0cd";
  private static final String VERSION = "2.0.2";

    public enum Ide {
        VISUAL_STUDIO_CODE("Visual Studio Code"), VISUAL_STUDIO("Visual Studio"), ECLIPSE("Eclipse"), JETBRAINS("JetBrains");

      private final String ide;

        public String getIde()
        {
            return this.ide;
        }

        Ide(String ide)
        {
            this.ide = ide;
        }
    }

    private AnalysisIsTriggered(Builder builder) {
        super(NAME, builder.properties, ID, VERSION);
    }

    private AnalysisIsTriggered(AnalysisIsTriggered clone) {
        super(NAME, new HashMap<>(clone.getProperties()), ID, VERSION);
    }

    public AnalysisIsTriggered clone() {
        return new AnalysisIsTriggered(this);
    }

    public static IAnalysisType builder() { return new Builder(); }

    // Inner Builder class with required properties
    public static class Builder implements IAnalysisType, IIde, ITriggeredByUser, IBuild {
      private final HashMap<String, Object> properties = new HashMap<>();

        private Builder() {
            this.properties.put("itly", true);
        }

        /**
         * Analysis types selected by the user for the scan: open source vulnerabilities, code quality issues and/or code security vulnerabilities.
         * <p>
         * Must be followed by {@link IIde#ide(Ide)
         */
        public IIde analysisType(String[] analysisType) {
            this.properties.put("analysisType", analysisType);
            return this;
        }

        /**
         * Ide family.
         * <p>
         * Must be followed by {@link ITriggeredByUser#triggeredByUser(boolean)
         */
        public ITriggeredByUser ide(Ide ide) {
            this.properties.put("ide", ide.getIde());
            return this;
        }

        /**
         * * True means that the analysis was triggered by the User.
         *
         * * False means that the analysis was triggered automatically by the plugin.
         * <p>
         * Must be followed by by additional optional properties or build() method
         */
        public Builder triggeredByUser(boolean triggeredByUser) {
            this.properties.put("triggeredByUser", triggeredByUser);
            return this;
        }

        public AnalysisIsTriggered build() {
            return new AnalysisIsTriggered(this);
        }
    }

    // Required property interfaces
    public interface IAnalysisType {
        IIde analysisType(String[] analysisType);
    }

    public interface IIde {
        ITriggeredByUser ide(Ide ide);
    }

    public interface ITriggeredByUser {
        Builder triggeredByUser(boolean triggeredByUser);
    }

    /** Build interface with optional properties */
    public interface IBuild {
        AnalysisIsTriggered build();
    }
}
