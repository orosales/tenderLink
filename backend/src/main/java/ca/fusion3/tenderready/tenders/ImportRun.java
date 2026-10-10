package ca.fusion3.tenderready.tenders;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "import_run")
public class ImportRun {
    @Id
    private UUID id;
    private String source;
    private String url;
    private String trigger;
    private String status;
    private Instant startedAt;
    private Instant finishedAt;
    private int rowsRead;
    private int inserted;
    private int updated;
    private int unchanged;
    private int skipped;
    private String error;

    protected ImportRun() {}

    public ImportRun(String source, String url, String trigger) {
        this.id = UUID.randomUUID();
        this.source = source;
        this.url = url;
        this.trigger = trigger;
        this.status = "RUNNING";
        this.startedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getSource() { return source; }
    public String getUrl() { return url; }
    public String getTrigger() { return trigger; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant v) { this.finishedAt = v; }
    public int getRowsRead() { return rowsRead; }
    public void setRowsRead(int v) { this.rowsRead = v; }
    public int getInserted() { return inserted; }
    public void setInserted(int v) { this.inserted = v; }
    public int getUpdated() { return updated; }
    public void setUpdated(int v) { this.updated = v; }
    public int getUnchanged() { return unchanged; }
    public void setUnchanged(int v) { this.unchanged = v; }
    public int getSkipped() { return skipped; }
    public void setSkipped(int v) { this.skipped = v; }
    public String getError() { return error; }
    public void setError(String v) { this.error = v; }
}
