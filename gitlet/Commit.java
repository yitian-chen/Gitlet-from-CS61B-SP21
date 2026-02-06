package gitlet;

import java.io.File;
import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static gitlet.Utils.writeObject;

/** Represents a gitlet commit object.
 *  This Commit class set up commits by input message, timeStamp, .etc
 *
 *  @author Chen
 */
public class Commit implements Serializable {

    /** Set the time format. */
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy Z", Locale.US);

    /**
     * Add instance variables beneath.
     *
     * List all instance variables of the Commit class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided one example for `message`.
     */

    /** The message of this Commit. */
    private final String message;
    /** The time of setting this Commit. */
    private final String timestamp;
    /** The SHA1 hash ID of this Commit. */
    private final String id;
    /** Parent of this Commit. */
    private final String parent;
    /** Second parent of this Commit. */
    private final String secondParent;
    /** Map of tracked files (filename and blob ID). */
    private final Map<String, String> trackedFiles;     // Map<FileName, blobId>

    /** Constructor of the initial commit. */
    public Commit(String message) {
        this.message = message;
        this.parent = null;
        this.trackedFiles = new HashMap<>();
        this.timestamp = ZonedDateTime.ofInstant
                (Instant.EPOCH, ZoneId.systemDefault()).format(TIMESTAMP_FORMATTER);
        this.id = generateID();
        this.secondParent = null;
    }

    /** Constructor of ordinary commit. */
    public Commit(String message, String parent,
                  String secondParent, Map<String, String> trackedFiles) {
        this.message = message;
        this.parent = parent;
        this.trackedFiles = new HashMap<>(trackedFiles);
        this.timestamp = ZonedDateTime.now().format(TIMESTAMP_FORMATTER);
        this.secondParent = secondParent;

        this.id = generateID();
    }

    private String generateID() {
        String safeParent = parent == null ? "" : parent;
        return Utils.sha1(message, timestamp, safeParent, trackedFiles.toString());
    }

    /** Useful methods of commit. */
    public String getId() {
        return this.id;
    }

    /** Write the commit into COMMIT_DIR. */
    public void save() {
        File commitFile = Utils.join(Repository.COMMITS_DIR, this.id);
        writeObject(commitFile, this);
    }

    public Map<String, String> getTrackedFiles() {
        return this.trackedFiles;
    }

    public String getMessage() {
        return message;
    }

    public String getParent() {
        return this.parent;
    }

    public String getSecondParent() {
        return this.secondParent;
    }

    public String getTimestamp() {
        return this.timestamp;
    }
}
