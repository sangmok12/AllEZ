package com.jobfitax.backend.jobsearch;

final class SearchPagination {
    private SearchPagination() { }

    static Integer nextPage(int page, int received, int totalAvailable, int pageSize) {
        if (received == 0 || page == Integer.MAX_VALUE) return null;
        if (totalAvailable > 0) {
            return ((long) page + 1) * pageSize < totalAvailable ? page + 1 : null;
        }
        return received >= pageSize ? page + 1 : null;
    }
}
