package fr.totetmatt.blueskygephi.atproto;

/**
 * Uniform error type for all XRPC calls, so callers get one consistent failure
 * mode instead of a mix of thrown {@link RuntimeException}s and {@code null}
 * returns.
 *
 * @author totetmatt
 */
public class AtProtoException extends RuntimeException {

    /** HTTP status when the failure came from a response, otherwise -1. */
    private final int statusCode;

    public AtProtoException(String message) {
        super(message);
        this.statusCode = -1;
    }

    public AtProtoException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public AtProtoException(int statusCode, String lexicon, String body) {
        super("XRPC " + lexicon + " failed (HTTP " + statusCode + "): " + body);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
