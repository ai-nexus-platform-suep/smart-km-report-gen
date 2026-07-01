package com.powerreport.admin.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AssetImportResultResponse {

    private int scanned;
    private int imported;
    private int skipped;
    private List<String> errors = new ArrayList<>();
}
