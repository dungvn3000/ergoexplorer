package vn.erg.explorer.controllers;

import io.jooby.MediaType;
import io.jooby.annotation.GET;
import io.jooby.annotation.Path;
import io.jooby.annotation.QueryParam;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.db.ChainRepository;
import vn.erg.explorer.db.IndexStatus;
import vn.erg.explorer.web.JsonResult;

import java.util.Map;

import static vn.erg.explorer.web.JsonResult.ok;

/** Richest ERG addresses, from the address_balance aggregate the indexer maintains. */
@Singleton
@Path("/api/v1/richlist")
public class RichListCtr extends BaseCtr {

    @Inject
    private ChainRepository repo;

    @Inject
    private IndexStatus index;

    /** `total` = number of funded addresses; `synced` says whether the figures cover the whole chain. */
    @GET(produces = MediaType.JSON)
    public JsonResult list(@QueryParam Integer page, @QueryParam Integer rowsPerPage) {
        var pageDto = repo.richList(page(page), rows(rowsPerPage, 50));
        return ok(Map.of("items", pageDto.getItems(), "total", pageDto.getTotal(), "synced", index.isSynced(), "indexedHeight", index.indexedHeight()));
    }

}
