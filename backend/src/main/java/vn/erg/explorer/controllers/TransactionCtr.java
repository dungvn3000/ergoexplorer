package vn.erg.explorer.controllers;

import io.jooby.MediaType;
import io.jooby.annotation.GET;
import io.jooby.annotation.Path;
import io.jooby.annotation.PathParam;
import io.jooby.annotation.QueryParam;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.services.BlockService;
import vn.erg.explorer.services.TransactionService;
import vn.erg.explorer.utils.Hex;
import vn.erg.explorer.web.JsonResult;


import static vn.erg.explorer.web.JsonResult.notfound;
import static vn.erg.explorer.web.JsonResult.ok;

@Singleton
@Path("/api/v1/transactions")
public class TransactionCtr extends BaseCtr {

    @Inject
    private TransactionService transactions;

    @Inject
    private BlockService blocks;

    /** Blocks scanned at most for the feed: quiet hours have long runs of emission-only blocks. */
    private static final int MAX_SCAN = 100;
    /** At most this many transactions of one block, so one big block does not fill the feed. */
    private static final int PER_BLOCK = 3;

    /**
     * Latest confirmed (non-reward) transactions, at most three per block so one big block does not
     * fill the feed. Read from the index only, starting at the indexed tip: no node call.
     */
    @GET(path = "/latest", produces = MediaType.JSON)
    public JsonResult latest(@QueryParam Integer limit) {
        return ok(blocks.latestIndexedTxs(rows(limit, 10), PER_BLOCK, MAX_SCAN));
    }

    /** Confirmed or still in the mempool. */
    @GET(path = "/{id}", produces = MediaType.JSON)
    public JsonResult get(@PathParam String id) {
        if (!Hex.isHex64(id)) {
            return notfound();
        }
        return transactions.get(id).map(JsonResult::new).orElseGet(() -> notfound());
    }

}
