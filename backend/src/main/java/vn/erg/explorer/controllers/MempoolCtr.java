package vn.erg.explorer.controllers;

import io.jooby.MediaType;
import io.jooby.annotation.GET;
import io.jooby.annotation.Path;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.services.MempoolService;
import vn.erg.explorer.web.JsonResult;

import static vn.erg.explorer.web.JsonResult.ok;

@Singleton
@Path("/api/v1/mempool")
public class MempoolCtr extends BaseCtr {

    @Inject
    private MempoolService mempool;

    @GET(path = "/transactions", produces = MediaType.JSON)
    public JsonResult transactions() {
        return ok(mempool.list());
    }

}
