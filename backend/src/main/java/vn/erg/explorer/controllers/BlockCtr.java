package vn.erg.explorer.controllers;

import io.jooby.MediaType;
import io.jooby.annotation.GET;
import io.jooby.annotation.Path;
import io.jooby.annotation.PathParam;
import io.jooby.annotation.QueryParam;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.services.BlockService;
import vn.erg.explorer.web.JsonResult;

import static vn.erg.explorer.web.JsonResult.notfound;
import static vn.erg.explorer.web.JsonResult.ok;

@Singleton
@Path("/api/v1/blocks")
public class BlockCtr extends BaseCtr {

    @Inject
    private BlockService blocks;

    /** Newest first; {@code total} is the chain height. */
    @GET(produces = MediaType.JSON)
    public JsonResult list(@QueryParam Integer page, @QueryParam Integer rowsPerPage) {
        return ok(blocks.list(page(page), rows(rowsPerPage, 25)));
    }

    @GET(path = "/latest", produces = MediaType.JSON)
    public JsonResult latest(@QueryParam Integer limit) {
        return ok(blocks.latest(rows(limit, 10)));
    }

    /** By height or by block id, with its transactions. */
    @GET(path = "/{id}", produces = MediaType.JSON)
    public JsonResult get(@PathParam String id) {
        return blocks.get(id).map(JsonResult::new).orElseGet(() -> notfound());
    }

    /** The block exactly as the node serves it (header, blockTransactions, extension, adProofs). */
    @GET(path = "/{id}/raw", produces = MediaType.JSON)
    public Object raw(@PathParam String id) {
        return blocks.raw(id).map(n -> (Object) n).orElseGet(() -> notfound());
    }

}
