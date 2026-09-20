package vn.erg.explorer.controllers;

public abstract class BaseCtr {

    protected static final int MAX_ROWS = 100;

    protected static int page(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    protected static int rows(Integer rowsPerPage, int def) {
        if (rowsPerPage == null || rowsPerPage < 1) {
            return def;
        }
        return Math.min(rowsPerPage, MAX_ROWS);
    }

}
