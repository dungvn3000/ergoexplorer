package vn.erg.explorer.dtos;

import lombok.Data;

@Data
public class RegisterDto {
    private String key;
    private String type;
    private String raw;
    private String value;
}
