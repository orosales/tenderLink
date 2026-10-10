package ca.fusion3.tenderready.demo;

import java.util.UUID;

/** Fixed identifiers of the fictional demonstration records. */
public final class DemoData {
    private DemoData() {}

    public static final UUID HARBOUR_CLEAN = UUID.fromString("0a000000-0000-4000-8000-00000000000a");
    public static final UUID FUNDY_CLEAN = UUID.fromString("0b000000-0000-4000-8000-00000000000b");
    public static final UUID DEMO_TENDER = UUID.fromString("0d000000-0000-4000-8000-0000000000d1");
    public static final UUID DEMO_DOCUMENT = UUID.fromString("0d000000-0000-4000-8000-0000000000dd");
    public static final String DEMO_TENDER_SOURCE_ID = "DEMO-NS-CLN-0426";
}
