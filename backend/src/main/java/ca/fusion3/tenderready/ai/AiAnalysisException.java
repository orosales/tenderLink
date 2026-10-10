package ca.fusion3.tenderready.ai;

/** Live AI analysis failed; the message is safe to show in the UI. */
public class AiAnalysisException extends RuntimeException {
    public AiAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiAnalysisException(String message) {
        super(message);
    }
}
