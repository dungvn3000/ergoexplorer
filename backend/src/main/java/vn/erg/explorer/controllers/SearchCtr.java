package vn.erg.explorer.controllers;

import io.jooby.MediaType;
import io.jooby.annotation.GET;
import io.jooby.annotation.Path;
import io.jooby.annotation.QueryParam;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.services.SearchService;
import vn.erg.explorer.web.JsonResult;

import static vn.erg.explorer.web.JsonResult.notfound;

/** Resolves what a search string is: height, block id, transaction id, box id, token id or address. */
@Singleton
@Path("/api/v1/search")
public class SearchCtr extends BaseCtr {

    @Inject
    private SearchService search;

    @GET(produces = MediaType.JSON)
    public JsonResult search(@QueryParam String q) {
        return search.resolve(q).map(JsonResult::new).orElseGet(() -> notfound());
    }

}
