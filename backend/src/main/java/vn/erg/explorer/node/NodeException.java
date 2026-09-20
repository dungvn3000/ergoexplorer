package vn.erg.explorer.node;

/** The node could not be reached or answered with an error. */
public class NodeException extends RuntimeException {

    private final int status;

    public NodeException(String message) {
        this(message, 0);
    }

    public NodeException(String message, int status) {
        super(message);
        this.status = status;
    }

    public NodeException(String message, Throwable cause) {
        super(message, cause);
        this.status = 0;
    }

    /** HTTP status the node answered with, 0 when it was not reached. */
    public int getStatus() {
        return status;
    }

    public boolean isNotFound() {
        return status == 404;
    }

}
