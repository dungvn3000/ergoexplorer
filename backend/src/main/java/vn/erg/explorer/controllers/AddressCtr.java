package vn.erg.explorer.controllers;

import io.jooby.MediaType;
import io.jooby.annotation.GET;
import io.jooby.annotation.Path;
import io.jooby.annotation.PathParam;
import io.jooby.annotation.QueryParam;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.services.AddressService;
import vn.erg.explorer.web.JsonResult;

import static vn.erg.explorer.web.JsonResult.notfound;

@Singleton
@Path("/api/v1/addresses")
public class AddressCtr extends BaseCtr {

    @Inject
    private AddressService addresses;

    /** Balance, tokens and a page of transaction history (newest first). */
    @GET(path = "/{address}", produces = MediaType.JSON)
    public JsonResult get(@PathParam String address, @QueryParam Integer page, @QueryParam Integer rowsPerPage) {
        return addresses.get(address, page(page), rows(rowsPerPage, 20))
                .map(JsonResult::new).orElseGet(() -> notfound());
    }

    /** Unspent boxes of the address, newest first: { items: [box], total }. */
    @GET(path = "/{address}/boxes", produces = MediaType.JSON)
    public JsonResult boxes(@PathParam String address, @QueryParam Integer page, @QueryParam Integer rowsPerPage) {
        return addresses.unspentBoxes(address, page(page), rows(rowsPerPage, 20))
                .map(JsonResult::new).orElseGet(() -> notfound());
    }

}
