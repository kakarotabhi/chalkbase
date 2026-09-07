package in.chalkbase.document.application;

/**
 * The bytes behind one document, plus what a browser needs to render or download them.
 *
 * <p>Not classified like an {@code api} record: this never crosses into a JSON response body — it
 * is written straight onto an HTTP response as raw bytes with these two as headers, by
 * {@code DocumentController#content}, and it never leaves that one method. It exists at all only so
 * that method has one thing to return rather than three.
 */
public record DocumentContent(byte[] bytes, String contentType, String filename) {}
