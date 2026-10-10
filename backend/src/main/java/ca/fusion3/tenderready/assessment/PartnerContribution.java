package ca.fusion3.tenderready.assessment;

/** A partner's mutually confirmed contribution: hours of a service in one location, in a stated role. */
public record PartnerContribution(CompanyFacts partner, String area, String service, Integer weeklyHours, String role) {}
