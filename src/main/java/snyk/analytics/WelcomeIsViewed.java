//
//  WelcomeIsViewed.java
//  This file is auto-generated by Amplitude. Run `ampli pull jetbrains` to update.
//
//  Works with versions 1.2+ of ly.iterative.itly:sdk and plugins
//  https://search.maven.org/search?q=itly
//

package snyk.analytics;

import ly.iterative.itly.Event;

import java.util.HashMap;

public class WelcomeIsViewed extends Event {
    private static final String NAME = "Welcome Is Viewed";
    private static final String ID = "91114669-bbab-4f58-a7dd-ea7c98c79221";
  private static final String VERSION = "1.0.2";

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

    private WelcomeIsViewed(Builder builder) {
        super(NAME, builder.properties, ID, VERSION);
    }

    private WelcomeIsViewed(WelcomeIsViewed clone) {
        super(NAME, new HashMap<>(clone.getProperties()), ID, VERSION);
    }

    public WelcomeIsViewed clone() {
        return new WelcomeIsViewed(this);
    }

    public static IIde builder() { return new Builder(); }

    // Inner Builder class with required properties
    public static class Builder implements IIde, IBuild {
      private final HashMap<String, Object> properties = new HashMap<>();

        private Builder() {
            this.properties.put("itly", true);
        }

        /**
         * Ide family.
         * <p>
         * Must be followed by by additional optional properties or build() method
         */
        public Builder ide(Ide ide) {
            this.properties.put("ide", ide.getIde());
            return this;
        }

        public WelcomeIsViewed build() {
            return new WelcomeIsViewed(this);
        }
    }

    // Required property interfaces
    public interface IIde {
        Builder ide(Ide ide);
    }

    /** Build interface with optional properties */
    public interface IBuild {
        WelcomeIsViewed build();
    }
}
