package vn.erg.explorer.controllers;

import io.jooby.MediaType;
import io.jooby.annotation.GET;
import io.jooby.annotation.Path;
import io.jooby.annotation.PathParam;
import io.jooby.annotation.QueryParam;
import vn.erg.explorer.db.ChainRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.services.TokenService;
import vn.erg.explorer.web.JsonResult;

import static vn.erg.explorer.web.JsonResult.notfound;
import static vn.erg.explorer.web.JsonResult.ok;

@Singleton
@Path("/api/v1/tokens")
public class TokenCtr extends BaseCtr {

    @Inject
    private TokenService tokens;

    @Inject
    private ChainRepository repo;

    /** Well-known tokens from application.conf (the node cannot list all tokens). */
    @GET(produces = MediaType.JSON)
    public JsonResult list() {
        return ok(tokens.featured());
    }

    @GET(path = "/{id}", produces = MediaType.JSON)
    public JsonResult get(@PathParam String id) {
        return tokens.detail(id).map(JsonResult::new).orElseGet(() -> notfound());
    }

    /** Rich list of a token: holders by amount with rank; `total` = number of holders. Needs a complete index. */
    @GET(path = "/{id}/holders", produces = MediaType.JSON)
    public JsonResult holders(@PathParam String id, @QueryParam Integer page, @QueryParam Integer rowsPerPage) {
        if (tokens.meta(id).isEmpty()) {
            return notfound();
        }
        return ok(repo.holdersPage(id, page(page), rows(rowsPerPage, 50)));
    }

}
