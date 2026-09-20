package vn.erg.explorer.controllers;

import io.jooby.MediaType;
import io.jooby.annotation.GET;
import io.jooby.annotation.Path;
import io.jooby.annotation.PathParam;
import io.jooby.annotation.QueryParam;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.services.ChartService;
import vn.erg.explorer.web.JsonResult;

import static vn.erg.explorer.web.JsonResult.notfound;
import static vn.erg.explorer.web.JsonResult.ok;

@Singleton
@Path("/api/v1/charts")
public class ChartCtr extends BaseCtr {

    @Inject
    private ChartService charts;

    /** Available series names. */
    @GET(produces = MediaType.JSON)
    public JsonResult list() {
        return ok(charts.names());
    }

    /** One series; {@code days} back from today (default 30, 0 = all). */
    @GET(path = "/{name}", produces = MediaType.JSON)
    public JsonResult series(@PathParam String name, @QueryParam Integer days) {
        int d = days == null ? 30 : Math.max(0, days);
        return charts.series(name, d).map(JsonResult::new).orElseGet(() -> notfound());
    }

}
