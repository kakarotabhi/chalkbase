package in.chalkbase.student;

import static org.assertj.core.api.Assertions.assertThat;

import in.chalkbase.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * The student import's file never reaches the filesystem (ADR-0021 §6).
 *
 * <p>Spring's default {@code file-size-threshold} is {@code 0B}, which means every multipart upload
 * is spooled to a temp file. For most uploads that is unremarkable. This one is several hundred
 * children's names, dates of birth and admission numbers, and writing it unencrypted to the
 * container's temp directory — where it stays if the process dies mid-request — is exactly what
 * ADR-0021 §6 says does not happen.
 *
 * <p><strong>What this asserts is the relationship, not the number.</strong> A threshold of 1 MB is
 * only meaningful while it is above the largest file that can be accepted; raising
 * {@code max-file-size} to 4 MB without touching the threshold would silently put uploads back on
 * disk, and nothing else in the tree would notice. So the test compares the two, and its failure
 * message says what the consequence is rather than which line to edit.
 *
 * <p>It is a test of a YAML setting, which is a weak kind of test — and it is here because that is
 * precisely the kind of thing lost in a merge with nobody the wiser.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class UploadStaysInMemoryTests {

    @Autowired
    MultipartProperties multipart;

    @Test
    void anUploadIsNeverLargeEnoughToBeSpooledToDisk() {
        long threshold = multipart.getFileSizeThreshold().toBytes();
        long largestAccepted = multipart.getMaxFileSize().toBytes();

        assertThat(threshold).as("""
                        A multipart file larger than the threshold is written to a temp file on disk.
                        The student import's file is hundreds of children's names and dates of birth
                        (ADR-0021 §6), so the threshold has to stay above the largest file that can be
                        accepted — otherwise every import lands unencrypted on the filesystem and stays
                        there if the process dies mid-request.
                        threshold=%d bytes, max-file-size=%d bytes""".formatted(threshold, largestAccepted)).isGreaterThanOrEqualTo(largestAccepted);
    }
}
