package vn.erg.explorer.controllers;

import io.jooby.MediaType;
import io.jooby.annotation.GET;
import io.jooby.annotation.Path;
import io.jooby.annotation.PathParam;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.db.ChainRepository;
import vn.erg.explorer.node.NodeClient;
import vn.erg.explorer.services.BoxMapper;
import vn.erg.explorer.web.JsonResult;

import static vn.erg.explorer.web.JsonResult.notfound;

@Singleton
@Path("/api/v1/boxes")
public class BoxCtr extends BaseCtr {

    @Inject
    private NodeClient node;

    @Inject
    private BoxMapper boxes;

    @Inject
    private ChainRepository repo;

    /** DB first, then the node's indexer. */
    @GET(path = "/{id}", produces = MediaType.JSON)
    public JsonResult get(@PathParam String id) {
        return repo.box(id).map(JsonResult::new)
                .or(() -> node.get("/blockchain/box/byId/" + id).map(n -> new JsonResult(boxes.map(n, 0))))
                .orElseGet(() -> notfound());
    }

}
