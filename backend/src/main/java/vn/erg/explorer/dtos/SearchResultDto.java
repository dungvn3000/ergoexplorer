package vn.erg.explorer.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

/** What a search string resolved to: block / transaction / address / token / box, with its id. */
@Data
@AllArgsConstructor
public class SearchResultDto {
    private String type;
    private String id;
}
