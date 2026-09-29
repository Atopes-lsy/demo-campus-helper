package com.campus.helper.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * Builds a paginated request from caller-supplied numbers.
 *
 * <p>MyBatis-Plus treats a page size of zero or less as "no pagination" and runs the
 * statement unwrapped, so a caller passing {@code ?size=0} would receive the whole table.
 * Normalising here keeps every paginated endpoint bounded, including the upper end.
 */
public final class PageQuery {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 500;

    private PageQuery() {
    }

    /**
     * @param page 1-based page number; null or &lt; 1 falls back to the first page
     * @param size rows per page; null or &lt; 1 falls back to the default, and is capped at
     *             {@link #MAX_SIZE}
     */
    public static <T> Page<T> of(Integer page, Integer size) {
        int normalizedPage = (page == null || page < 1) ? DEFAULT_PAGE : page;
        int normalizedSize = (size == null || size < 1) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return new Page<>(normalizedPage, normalizedSize);
    }
}
