package vn.erg.explorer.controllers;

import io.jooby.MediaType;
import io.jooby.annotation.GET;
import io.jooby.annotation.Path;
import io.jooby.annotation.PathParam;
import io.jooby.annotation.QueryParam;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import vn.erg.explorer.dtos.BlockDto;
import vn.erg.explorer.dtos.TxDto;
import vn.erg.explorer.services.BlockService;
import vn.erg.explorer.services.TransactionService;
import vn.erg.explorer.web.JsonResult;

import java.util.ArrayList;
import java.util.List;

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
    private static final int PAGE = 20;

    /**
     * Latest confirmed (non-reward) transactions, at most three per block so one big block does not
     * fill the feed. Walks back through the chain until {@code limit} are found: only blocks with
     * more than the emission tx are loaded.
     */
    @GET(path = "/latest", produces = MediaType.JSON)
    public JsonResult latest(@QueryParam Integer limit) {
        int n = rows(limit, 10);
        List<TxDto> out = new ArrayList<>();
        for (int page = 1; out.size() < n && (page - 1) * PAGE < MAX_SCAN; page++) {
            for (BlockDto summary : blocks.list(page, PAGE).getItems()) {
                if (summary.getTxCount() <= 1) {
                    continue;
                }
                blocks.get(summary.getId()).ifPresent(block -> {
                    int k = 0;
                    for (TxDto tx : block.getTransactions()) {
                        if (tx.isCoinbase() || k >= 3 || out.size() >= n) {
                            continue;
                        }
                        out.add(tx);
                        k++;
                    }
                });
                if (out.size() >= n) {
                    break;
                }
            }
        }
        return ok(out);
    }

    /** Confirmed or still in the mempool. */
    @GET(path = "/{id}", produces = MediaType.JSON)
    public JsonResult get(@PathParam String id) {
        return transactions.get(id).map(JsonResult::new).orElseGet(() -> notfound());
    }

}
