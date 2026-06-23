package ca.joaoborges.finance.common;

import java.util.List;

/**
 * Minimal pagination envelope for infinite-scroll lists — just what the client
 * needs to append the next block and know when to stop. Avoids Spring Data's
 * verbose/unstable {@code Page} JSON.
 */
public record PageResponse<T>(List<T> content, int page, int size, boolean hasNext, long total) {
}
