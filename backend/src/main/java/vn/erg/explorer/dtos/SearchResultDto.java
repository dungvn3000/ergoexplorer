package vn.erg.explorer.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

/** What a search string resolved to: block / transaction / address / token / box with its id, or tokens (several tokens by that name) with the search text. */
@Data
@AllArgsConstructor
public class SearchResultDto {
    private String type;
    private String id;
}
