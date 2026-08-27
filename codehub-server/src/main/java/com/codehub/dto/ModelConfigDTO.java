package com.codehub.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ModelConfigDTO {
    @NotBlank(message = "供应商不能为空")
    private String provider;
    @NotBlank(message = "模型名称不能为空")
    private String modelName;
    private String baseUrl;
    private String apiKey;
    private Boolean enabled = true;
}
