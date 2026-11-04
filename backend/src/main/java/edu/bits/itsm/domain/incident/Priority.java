package edu.bits.itsm.domain.incident;

/** Resulting priority. Ordinal order matters: P1 is the most severe. */
public enum Priority {
    P1("Critical"), P2("High"), P3("Medium"), P4("Low");

    private final String label;
    Priority(String label) { this.label = label; }
    public String getLabel() { return label; }
    public boolean isMoreSevereThan(Priority other) { return this.ordinal() < other.ordinal(); }
}
